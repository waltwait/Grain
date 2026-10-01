package tw.luma.camera.gl

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
import androidx.core.util.Consumer
import java.util.concurrent.Executor

class LutSurfaceProcessor(private val targets: Int = CameraEffect.PREVIEW, private val onError: (Throwable) -> Unit) : SurfaceProcessor, AutoCloseable {
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

    val effect = object : CameraEffect(targets, executor, this, Consumer { onError(it) }) {}

    private fun initialize() {
        if (egl == null) { egl = EglCore(); renderer = LutRenderer(true) }
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
                        egl!!.makeCurrent()
                        frame.updateTexImage(); frame.getTransformMatrix(originalTransform)
                        val snapshot = settings
                        for ((output, window) in outputs) {
                            egl!!.makeCurrent(window)
                            output.updateTransformMatrix(transformed, originalTransform)
                            renderer!!.draw(id, output.size.width, output.size.height, snapshot, transformed)
                            if (output.targets and CameraEffect.VIDEO_CAPTURE != 0) egl!!.timestamp(window, frame.timestamp)
                            egl!!.swap(window)
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

    override fun close() {
        executor.execute {
            closing = true
            active = null
            inputs.keys.forEach { it.setOnFrameAvailableListener(null) }
            outputs.forEach { (output, window) -> egl?.destroy(window); output.close() }
            outputs.clear()
            finishIfReleased()
        }
    }

    private fun finishIfReleased() {
        if (closing && inputs.isEmpty()) {
            egl?.makeCurrent(); renderer?.close(); renderer = null
            egl?.close(); egl = null
            thread.quitSafely()
        }
    }
}
