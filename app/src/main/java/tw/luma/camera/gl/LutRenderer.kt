package tw.luma.camera.gl

import android.graphics.Bitmap
import android.opengl.EGL14
import android.opengl.EGLExt
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.GLUtils
import android.opengl.Matrix
import android.view.Surface
import tw.luma.camera.lut.CubeLut
import tw.luma.camera.lut.LutEncoding
import java.nio.ByteBuffer
import java.nio.ByteOrder
import tw.luma.camera.performance.grainTrace
import kotlin.math.ceil
import kotlin.math.sqrt

data class FilterSettings(
    val lut: CubeLut? = null,
    val strength: Float = 1f,
    val brightnessEv: Float = 0f,
    val encoding: LutEncoding = LutEncoding.SRGB,
)

/** All methods run on the owning GL thread. Separate contexts are used for preview and export. */
class EglCore : AutoCloseable {
    private val display: EGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
    private val config: EGLConfig
    private val context: EGLContext
    private val parking: EGLSurface

    init {
        check(display != EGL14.EGL_NO_DISPLAY && EGL14.eglInitialize(display, IntArray(2), 0, IntArray(2), 1)) { "無法初始化 GPU 顯示" }
        val configs = arrayOfNulls<EGLConfig>(1)
        val attrs = intArrayOf(EGL14.EGL_RENDERABLE_TYPE, 0x40, EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT or EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
            EGLExt.EGL_RECORDABLE_ANDROID, 1, EGL14.EGL_NONE)
        check(EGL14.eglChooseConfig(display, attrs, 0, configs, 0, 1, IntArray(1), 0) && configs[0] != null) { "此裝置無法建立 OpenGL ES 3 濾鏡" }
        config = configs[0]!!
        context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0)
        check(context != EGL14.EGL_NO_CONTEXT) { "此裝置不支援 OpenGL ES 3" }
        parking = EGL14.eglCreatePbufferSurface(display, config, intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
        makeCurrent(parking)
    }

    fun window(surface: Surface): EGLSurface = EGL14.eglCreateWindowSurface(display, config, surface, intArrayOf(EGL14.EGL_NONE), 0).also {
        check(it != EGL14.EGL_NO_SURFACE) { "無法建立相機預覽輸出" }
    }
    fun makeCurrent(surface: EGLSurface = parking) { check(EGL14.eglMakeCurrent(display, surface, surface, context)) { "GPU context 已失效" } }
    fun swap(surface: EGLSurface) { check(EGL14.eglSwapBuffers(display, surface)) { "相機預覽 Surface 已失效" } }
    fun timestamp(surface: EGLSurface, ns: Long) { check(EGLExt.eglPresentationTimeANDROID(display, surface, ns)) { "無法設定影片時間戳" } }
    fun destroy(surface: EGLSurface) { makeCurrent(); EGL14.eglDestroySurface(display, surface) }
    override fun close() {
        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        EGL14.eglDestroySurface(display, parking)
        EGL14.eglDestroyContext(display, context)
        EGL14.eglTerminate(display)
    }
}

class LutRenderer(private val external: Boolean) : AutoCloseable {
    private val program: Int
    private val position: Int
    private val uv: Int
    private var appliedSettings: FilterSettings? = null
    private val defaultMin = floatArrayOf(0f, 0f, 0f)
    private val defaultMax = floatArrayOf(1f, 1f, 1f)
    private val lutTexture = texture(GLES30.GL_TEXTURE_2D)
    private var uploaded: CubeLut? = null
    private var columns = 1
    private val vertices = ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(floatArrayOf(-1f, -1f, 0f, 0f, 1f, -1f, 1f, 0f, -1f, 1f, 0f, 1f, 1f, 1f, 1f, 1f)); position(0)
    }
    private val identity = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
    private val uniforms = mutableMapOf<String, Int>()

    init {
        val vertex = """
            attribute vec2 aPosition;
            attribute vec2 aUv;
            uniform mat4 uTransform;
            varying vec2 vUv;
            void main() { gl_Position = vec4(aPosition, 0.0, 1.0); vUv = (uTransform * vec4(aUv, 0.0, 1.0)).xy; }
        """.trimIndent()
        val prefix = if (external) "#extension GL_OES_EGL_image_external : require\n" else ""
        val sampler = if (external) "samplerExternalOES" else "sampler2D"
        val fragment = prefix + """
            precision highp float;
            uniform $sampler uImage;
            uniform sampler2D uLut;
            uniform float uSize, uColumns, uRows, uStrength, uBrightness;
            uniform int uEncoding;
            uniform vec3 uMin, uMax;
            uniform mat3 uGamut;
            varying vec2 vUv;
            vec3 decodeSrgb(vec3 v) { return mix(v / 12.92, pow((v + 0.055) / 1.055, vec3(2.4)), step(vec3(0.04045), v)); }
            vec3 encodeSrgb(vec3 v) { v = max(v, vec3(0.0)); return mix(v * 12.92, 1.055 * pow(v, vec3(1.0/2.4)) - 0.055, step(vec3(0.0031308), v)); }
            vec3 encodeLog(vec3 v) {
                v = max(v, vec3(0.0));
                if (uEncoding == 1) return mix(8.735631 * v + 0.092864, 0.344676 * log(0.555556 * v + 0.009468) / log(10.0) + 0.790453, step(vec3(0.00089), v));
                return mix(8.799461 * v + 0.092864, 0.245281 * log(5.555556 * v + 0.064829) / log(10.0) + 0.384316, step(vec3(0.000889), v));
            }
            vec3 slice(vec2 rg, float blue) {
                vec2 tile = vec2(mod(blue, uColumns), floor(blue / uColumns));
                vec2 pixel = tile * uSize + rg * (uSize - 1.0) + 0.5;
                return texture2D(uLut, pixel / vec2(uColumns * uSize, uRows * uSize)).rgb;
            }
            void main() {
                vec3 original = texture2D(uImage, vUv).rgb;
                vec3 adjusted = original;
                if (uBrightness != 0.0) adjusted = clamp(encodeSrgb(decodeSrgb(original) * exp2(uBrightness)), 0.0, 1.0);
                if (uSize < 2.0 || uStrength <= 0.0) {
                    gl_FragColor = vec4(adjusted, 1.0);
                    return;
                }
                vec3 inputColor = adjusted;
                if (uEncoding != 0) inputColor = encodeLog(uGamut * decodeSrgb(adjusted));
                vec3 mapped = adjusted;
                if (uSize > 1.0) {
                    vec3 p = clamp((inputColor - uMin) / (uMax - uMin), 0.0, 1.0);
                    float blue = p.b * (uSize - 1.0);
                    mapped = mix(slice(p.rg, floor(blue)), slice(p.rg, min(floor(blue) + 1.0, uSize - 1.0)), fract(blue));
                    // Experimental Fuji adaptation: assume the LUT output uses display gamma 2.2.
                    if (uEncoding != 0) mapped = encodeSrgb(pow(max(mapped, vec3(0.0)), vec3(2.2)));
                }
                gl_FragColor = vec4(clamp(mix(adjusted, mapped, uStrength), 0.0, 1.0), 1.0);
            }
        """.trimIndent()
        fun compile(type: Int, source: String): Int {
            val shader = GLES30.glCreateShader(type)
            GLES30.glShaderSource(shader, source); GLES30.glCompileShader(shader)
            val status = IntArray(1); GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
            if (status[0] == 0) { val log = GLES30.glGetShaderInfoLog(shader); GLES30.glDeleteShader(shader); error("濾鏡 shader 編譯失敗：$log") }
            return shader
        }
        val vs = compile(GLES30.GL_VERTEX_SHADER, vertex)
        val fs = compile(GLES30.GL_FRAGMENT_SHADER, fragment)
        program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vs); GLES30.glAttachShader(program, fs); GLES30.glLinkProgram(program)
        GLES30.glDeleteShader(vs); GLES30.glDeleteShader(fs)
        val status = IntArray(1); GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, status, 0)
        check(status[0] != 0) { "無法連結濾鏡 shader：${GLES30.glGetProgramInfoLog(program)}" }
        position = GLES30.glGetAttribLocation(program, "aPosition")
        uv = GLES30.glGetAttribLocation(program, "aUv")
        GLES30.glUseProgram(program)
        GLES30.glUniform1i(uniform("uImage"), 0)
        GLES30.glUniform1i(uniform("uLut"), 1)
        upload(null)
    }

    private fun uniform(name: String) = uniforms.getOrPut(name) { GLES30.glGetUniformLocation(program, name) }

    private fun upload(lut: CubeLut?) {
        val n = lut?.size ?: 2
        columns = ceil(sqrt(n.toDouble())).toInt()
        val rows = ceil(n.toDouble() / columns).toInt()
        val width = columns * n
        val height = rows * n
        val buffer = ByteBuffer.allocateDirect(width * height * 4 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        val table = FloatArray(width * height * 4)
        for (b in 0 until n) for (g in 0 until n) for (r in 0 until n) {
            val dst = (((b / columns) * n + g) * width + (b % columns) * n + r) * 4
            val src = (b * n * n + g * n + r) * 3
            for (c in 0..2) table[dst + c] = lut?.values?.get(src + c) ?: 0f
            table[dst + 3] = 1f
        }
        buffer.put(table).position(0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, lutTexture)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA16F, width, height, 0, GLES30.GL_RGBA, GLES30.GL_FLOAT, buffer)
        check(GLES30.glGetError() == GLES30.GL_NO_ERROR) { "GPU 不支援此 LUT 格式" }
        uploaded = lut
    }

    fun draw(image: Int, width: Int, height: Int, settings: FilterSettings, transform: FloatArray = identity) {
        if (uploaded !== settings.lut) grainTrace("Grain.lut.upload") { upload(settings.lut) }
        GLES30.glViewport(0, 0, width, height)
        GLES30.glUseProgram(program)
        vertices.position(0); GLES30.glVertexAttribPointer(position, 2, GLES30.GL_FLOAT, false, 16, vertices)
        vertices.position(2); GLES30.glVertexAttribPointer(uv, 2, GLES30.GL_FLOAT, false, 16, vertices)
        GLES30.glEnableVertexAttribArray(position); GLES30.glEnableVertexAttribArray(uv)
        GLES30.glUniformMatrix4fv(uniform("uTransform"), 1, false, transform, 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(if (external) GLES11Ext.GL_TEXTURE_EXTERNAL_OES else GLES30.GL_TEXTURE_2D, image)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1); GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, lutTexture)
        if (appliedSettings != settings) {
            val n = settings.lut?.size ?: 0
            GLES30.glUniform1f(uniform("uSize"), n.toFloat())
            GLES30.glUniform1f(uniform("uColumns"), columns.toFloat())
            GLES30.glUniform1f(uniform("uRows"), if (n == 0) 1f else ceil(n.toDouble() / columns).toFloat())
            GLES30.glUniform1f(uniform("uStrength"), settings.strength.coerceIn(0f, 1f))
            GLES30.glUniform1f(uniform("uBrightness"), settings.brightnessEv)
            GLES30.glUniform1i(uniform("uEncoding"), if (settings.lut == null) 0 else settings.encoding.ordinal)
            GLES30.glUniform3fv(uniform("uMin"), 1, settings.lut?.domainMin ?: defaultMin, 0)
            GLES30.glUniform3fv(uniform("uMax"), 1, settings.lut?.domainMax ?: defaultMax, 0)
            GLES30.glUniformMatrix3fv(uniform("uGamut"), 1, false, ColorMatrices.forEncoding(settings.encoding), 0)
            appliedSettings = settings
        }
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glDisableVertexAttribArray(position); GLES30.glDisableVertexAttribArray(uv)
        check(GLES30.glGetError() == GLES30.GL_NO_ERROR) { "GPU 濾鏡處理失敗" }
    }

    override fun close() { GLES30.glDeleteTextures(1, intArrayOf(lutTexture), 0); GLES30.glDeleteProgram(program) }

    companion object {
        fun texture(target: Int): Int {
            val ids = IntArray(1); GLES30.glGenTextures(1, ids, 0); GLES30.glBindTexture(target, ids[0])
            GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
            return ids[0]
        }

        /** Tile export avoids a full-resolution input/output pair of GPU textures. */
        fun apply(bitmap: Bitmap, settings: FilterSettings): Bitmap {
            if ((settings.lut == null || settings.strength <= 0f) && settings.brightnessEv == 0f) return bitmap
            EglCore().use {
                LutRenderer(false).use { renderer ->
                    val max = IntArray(1); GLES30.glGetIntegerv(GLES30.GL_MAX_TEXTURE_SIZE, max, 0)
                    val tileSize = minOf(1024, max[0])
                    require(tileSize > 0) { "GPU 無法處理圖片" }
                    val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
                    val readback = RgbaReadback(minOf(tileSize, bitmap.width) * minOf(tileSize, bitmap.height))
                    val input = texture(GLES30.GL_TEXTURE_2D)
                    val output = texture(GLES30.GL_TEXTURE_2D)
                    val fb = IntArray(1); GLES30.glGenFramebuffers(1, fb, 0)
                    try {
                        for (top in 0 until bitmap.height step tileSize) for (left in 0 until bitmap.width step tileSize) {
                            val w = minOf(tileSize, bitmap.width - left); val h = minOf(tileSize, bitmap.height - top)
                            val tile = Bitmap.createBitmap(bitmap, left, top, w, h)
                            GLES30.glActiveTexture(GLES30.GL_TEXTURE0); GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, input)
                            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, tile, 0)
                            if (tile !== bitmap) tile.recycle()
                            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, output)
                            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, w, h, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
                            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fb[0])
                            GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, output, 0)
                            check(GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) == GLES30.GL_FRAMEBUFFER_COMPLETE)
                            grainTrace("Grain.photo.tile.draw") { renderer.draw(input, w, h, settings) }
                            readback.pixels.clear()
                            grainTrace("Grain.photo.tile.readback") { GLES30.glReadPixels(0, 0, w, h, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, readback.pixels) }
                            // Input row zero maps to FBO row zero; readback retains bitmap row order.
                            val colors = grainTrace("Grain.photo.tile.unpack") { readback.decode(w * h) }
                            result.setPixels(colors, 0, w, left, top, w, h)
                        }
                        return result
                    } catch (e: Throwable) { result.recycle(); throw e }
                    finally { GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0); GLES30.glDeleteFramebuffers(1, fb, 0); GLES30.glDeleteTextures(2, intArrayOf(input, output), 0) }
                }
            }
        }
    }
}
