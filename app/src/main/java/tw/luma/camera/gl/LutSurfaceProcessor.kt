package tw.luma.camera.gl

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import androidx.camera.core.CameraEffect
import androidx.camera.core.SurfaceOutput
import androidx.camera.core.SurfaceProcessor
import androidx.camera.core.SurfaceRequest
import androidx.core.graphics.createBitmap
import androidx.core.util.Consumer
import java.util.concurrent.Executor
import tw.luma.camera.performance.grainTrace

class LutSurfaceProcessor(private val onError: (Throwable) -> Unit) : SurfaceProcessor, AutoCloseable {
    private val thread = HandlerThread("Luma-preview-GL").apply { start() }
    private val handler = Handler(thread.looper)
    val executor = Executor { command -> handler.post(command) }
    private var egl: EglCore? = null
    private var renderer: LutRenderer? = null
    private var closing = false
    private var active: SurfaceTexture? = null
    private val inputs = mutableMapOf<SurfaceTexture, Int>()
    private val outputs = mutableMapOf<SurfaceOutput, EGLSurface>()
    private val originalTransform = FloatArray(16)
    private val transformed = FloatArray(16)
    @Volatile var settings = FilterSettings()
    /** Where the front camera is in a back-and-front frame, to soften skin there; null draws the picture as it is. */
    @Volatile var skin: SkinArea? = null
    /** Frames drawn so far; a camera that stops sending them leaves this still. */
    @Volatile var frames = 0L
        private set
    // A photo waiting for the next frame; touched only on the GL thread.
    private var still: ((Result<Bitmap>) -> Unit)? = null
    private val upsideDown = FloatArray(16)

    /** The film effect for the use cases a binding has: the preview alone, or the preview and a recording. */
    fun effect(targets: Int): CameraEffect = object : CameraEffect(targets, executor, this, Consumer { onError(it) }) {}

    /**
     * The next frame as drawn before the film, with the skin softened, for a back-and-front photo. Two cameras together
     * only give a recording stream, so the photo is this composed frame; the photo pipeline applies the film to it.
     */
    fun capture(deliver: (Result<Bitmap>) -> Unit) = executor.execute {
        if (closing) deliver(Result.failure(IllegalStateException("相機尚未就緒"))) else still = deliver
    }

    private fun initialize() {
        if (egl == null) grainTrace("Grain.preview.initialize") { egl = EglCore(); renderer = LutRenderer(true) }
        egl!!.makeCurrent()
    }

    override fun onInputSurface(request: SurfaceRequest) {
        if (closing) { request.willNotProvideSurface(); return }
        try {
            initialize()
            val id = LutRenderer.texture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES)
            val texture = SurfaceTexture(id)
            texture.setDefaultBufferSize(request.resolution.width, request.resolution.height)
            val surface = Surface(texture)
            inputs[texture] = id
            active = texture
            texture.setOnFrameAvailableListener({ frame ->
                if (!closing && active === frame && inputs.containsKey(frame)) {
                    try {
                        grainTrace("Grain.preview.frame") {
                            egl!!.makeCurrent()
                            grainTrace("Grain.preview.acquire") { frame.updateTexImage() }
                            frame.getTransformMatrix(originalTransform)
                            val snapshot = settings
                            // The area is in the composed frame, which the input buffer holds through its own transform.
                            val softened = skin?.inBuffer(originalTransform)
                            for ((output, window) in outputs) {
                                egl!!.makeCurrent(window)
                                output.updateTransformMatrix(transformed, originalTransform)
                                grainTrace("Grain.preview.draw") { renderer!!.draw(id, output.size.width, output.size.height, snapshot, transformed, skin = softened) }
                                if (output.targets and CameraEffect.VIDEO_CAPTURE != 0) egl!!.timestamp(window, frame.timestamp)
                                grainTrace(if (output.targets and CameraEffect.VIDEO_CAPTURE != 0) "Grain.video.present" else "Grain.preview.present") { egl!!.swap(window) }
                            }
                            frames++
                            still?.let { deliver ->
                                still = null
                                deliver(runCatching { grainTrace("Grain.still.read") { readStill(id, request.resolution.width, request.resolution.height, softened) } })
                            }
                        }
                    } catch (e: Throwable) {
                        active = null
                        request.invalidate()
                        onError(e)
                    }
                }
            }, handler)
            request.provideSurface(surface, executor) {
                texture.setOnFrameAvailableListener(null)
                if (active === texture) active = null
                surface.release(); texture.release()
                inputs.remove(texture)?.let { name -> egl?.makeCurrent(); GLES30.glDeleteTextures(1, intArrayOf(name), 0) }
                finishIfReleased()
            }
        } catch (e: Throwable) { request.willNotProvideSurface(); onError(e) }
    }

    override fun onOutputSurface(output: SurfaceOutput) {
        if (closing) { output.close(); return }
        try {
            initialize()
            val surface = output.getSurface(executor) {
                outputs.remove(output)?.let { window -> egl?.destroy(window) }
                output.close()
            }
            outputs[output] = egl!!.window(surface)
        } catch (e: Throwable) { output.close(); onError(e) }
    }

    /** Draws the input upright into an offscreen picture and reads it back top row first. */
    private fun readStill(texture: Int, bufferWidth: Int, bufferHeight: Int, skin: SkinPass?): Bitmap {
        egl!!.makeCurrent()
        // A buffer held a quarter turn from upright swaps the picture's sides.
        val turned = kotlin.math.abs(originalTransform[0]) < .5f
        val width = if (turned) bufferHeight else bufferWidth
        val height = if (turned) bufferWidth else bufferHeight
        val target = LutRenderer.texture(GLES30.GL_TEXTURE_2D)
        val framebuffer = IntArray(1)
        try {
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, width, height, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
            GLES30.glGenFramebuffers(1, framebuffer, 0)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffer[0])
            GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, target, 0)
            check(GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) == GLES30.GL_FRAMEBUFFER_COMPLETE) { "無法讀取前後畫面" }
            // Rows read back bottom first, so the picture is drawn upside down to come back top first.
            android.opengl.Matrix.multiplyMM(upsideDown, 0, originalTransform, 0, FLIP_VERTICAL, 0)
            renderer!!.draw(texture, width, height, FilterSettings(), upsideDown, skin = skin)
            val pixels = java.nio.ByteBuffer.allocateDirect(width * height * 4).order(java.nio.ByteOrder.nativeOrder())
            GLES30.glReadPixels(0, 0, width, height, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, pixels)
            check(GLES30.glGetError() == GLES30.GL_NO_ERROR) { "無法讀取前後畫面" }
            pixels.rewind()
            return createBitmap(width, height).apply { copyPixelsFromBuffer(pixels) }
        } finally {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            GLES30.glDeleteFramebuffers(1, framebuffer, 0)
            GLES30.glDeleteTextures(1, intArrayOf(target), 0)
        }
    }

    override fun close() {
        executor.execute {
            still?.invoke(Result.failure(IllegalStateException("相機已關閉")))
            still = null
            closing = true
            active = null
            inputs.keys.forEach { it.setOnFrameAvailableListener(null) }
            outputs.forEach { (output, window) -> egl?.destroy(window); output.close() }
            outputs.clear()
            finishIfReleased()
        }
    }

    private companion object {
        /** Texture v → 1 − v, column-major. */
        val FLIP_VERTICAL = floatArrayOf(1f, 0f, 0f, 0f, 0f, -1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 1f)
    }

    private fun finishIfReleased() {
        if (closing && inputs.isEmpty()) {
            egl?.makeCurrent(); renderer?.close(); renderer = null
            egl?.close(); egl = null
            thread.quitSafely()
        }
    }
}
