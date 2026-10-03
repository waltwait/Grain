package tw.luma.camera.editor

import android.content.Context
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import tw.luma.camera.gl.FilterSettings
import tw.luma.camera.gl.LutRenderer

/** A Compose-hosted GPU surface. Decode/upload once; settings changes render only the latest request. */
// Compose supplies these callbacks directly; this surface is never inflated from XML.
@SuppressLint("ViewConstructor")
internal class PhotoPreviewView(
    context: Context,
    private val presented: (Bitmap, Long) -> Unit,
    private val failed: (Long) -> Unit,
) : GLSurfaceView(context) {
    private data class Frame(val bitmap: Bitmap, val settings: FilterSettings, val revision: Long)
    @Volatile private var frame: Frame? = null
    @Volatile private var released = false

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 0, 0, 0)
        preserveEGLContextOnPause = false
        setRenderer(PhotoRenderer())
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    fun submit(bitmap: Bitmap, settings: FilterSettings, revision: Long) {
        val previous = frame
        if (released || (previous?.bitmap === bitmap && previous.settings == settings && previous.revision == revision)) return
        frame = Frame(bitmap, settings, revision)
        requestRender()
    }

    fun releasePreview() { released = true; frame = null; onPause() }

    private inner class PhotoRenderer : Renderer {
        private var renderer: LutRenderer? = null
        private var image = 0
        private var uploaded: Bitmap? = null
        private var width = 0
        private var height = 0
        // Bitmap row zero is its top; window surfaces use a bottom-left origin.
        private val transform = FloatArray(16).also {
            Matrix.setIdentityM(it, 0)
            Matrix.translateM(it, 0, 0f, 1f, 0f)
            Matrix.scaleM(it, 0, 1f, -1f, 1f)
        }

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            uploaded = null
            renderer = null
            try {
                renderer = LutRenderer(false)
                GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
                image = LutRenderer.texture(GLES30.GL_TEXTURE_2D)
            } catch (_: Exception) { notifyError(frame?.revision ?: -1) }
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) { this.width = width; this.height = height }

        override fun onDrawFrame(gl: GL10?) {
            if (released) return
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            GLES30.glClearColor(0f, 0f, 0f, 1f)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            val snapshot = frame ?: return
            val lutRenderer = renderer ?: run { notifyError(snapshot.revision); return }
            if (width <= 0 || height <= 0) return
            try {
                if (uploaded !== snapshot.bitmap) {
                    GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
                    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, image)
                    GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, snapshot.bitmap, 0)
                    uploaded = snapshot.bitmap
                }
                val rect = PhotoPreviewGeometry.fit(width, height, snapshot.bitmap.width, snapshot.bitmap.height)
                lutRenderer.draw(image, rect.width, rect.height, snapshot.settings, transform, rect.x, rect.y)
                post { if (!released) presented(snapshot.bitmap, snapshot.revision) }
            } catch (_: Exception) { notifyError(snapshot.revision) }
        }

        private fun notifyError(revision: Long) { post { if (!released) failed(revision) } }
    }
}
