package tw.luma.camera.gl

import android.graphics.Bitmap
import android.graphics.Color
import android.opengl.GLES30
import android.opengl.GLUtils
import tw.luma.camera.lut.LutEncoding
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import tw.luma.camera.lut.CubeLut

/** Requires an Android ES3 device. This tests real shaders and readback, not a mocked GL API. */
@RunWith(AndroidJUnit4::class)
class LutRendererDeviceTest {
    @Test fun identityPreservesCornerColorsAndImageOrientation() {
        val pixels = intArrayOf(Color.RED, Color.GREEN, Color.BLUE, Color.WHITE)
        val input = Bitmap.createBitmap(pixels, 2, 2, Bitmap.Config.ARGB_8888)
        val identity = CubeLut.generate("Identity", 33) { r,g,b -> floatArrayOf(r,g,b) }
        val output = LutRenderer.apply(input, FilterSettings(lut = identity))
        try { for (y in 0..1) for (x in 0..1) assertColor(pixels[y*2+x], output.getPixel(x,y)) }
        finally { input.recycle(); if (output !== input) output.recycle() }
    }
    @Test fun interpolated65GridMatchesCpuReference() {
        val lut = CubeLut.generate("Swap", 65) { r,g,b -> floatArrayOf(b,r,g) }
        val color = Color.rgb(37, 119, 213)
        val input = Bitmap.createBitmap(intArrayOf(color), 1, 1, Bitmap.Config.ARGB_8888)
        val output = LutRenderer.apply(input, FilterSettings(lut = lut))
        try { assertColor(Color.rgb(213,37,119), output.getPixel(0,0)) }
        finally { input.recycle(); if (output !== input) output.recycle() }
    }
    @Test fun strengthZeroPreservesInputEvenWithNonIdentityLut() {
        val input = Bitmap.createBitmap(intArrayOf(Color.rgb(40, 100, 200)), 1, 1, Bitmap.Config.ARGB_8888)
        val lut = CubeLut.generate("Black") { _,_,_ -> floatArrayOf(0f,0f,0f) }
        val output = LutRenderer.apply(input, FilterSettings(lut = lut, strength = 0f))
        try {
            assertSame("A no-op photo must skip GL initialization and bitmap allocation", input, output)
            assertColor(input.getPixel(0,0), output.getPixel(0,0))
        }
        finally { input.recycle(); if (output !== input) output.recycle() }
    }
    @Test fun tileBoundaryDoesNotDropOrReversePixels() {
        val pixels = IntArray(1025*3) { i -> if (i % 1025 == 1024) Color.BLUE else if (i / 1025 == 0) Color.RED else Color.GREEN }
        val input = Bitmap.createBitmap(pixels, 1025, 3, Bitmap.Config.ARGB_8888)
        val lut = CubeLut.generate("Identity") { r,g,b -> floatArrayOf(r,g,b) }
        val output = LutRenderer.apply(input, FilterSettings(lut = lut))
        try { for (y in 0..2) for (x in listOf(0,1023,1024)) assertColor(input.getPixel(x,y), output.getPixel(x,y)) }
        finally { input.recycle(); if (output !== input) output.recycle() }
    }
    @Test fun domainMetadataIsAppliedOnGpu() {
        val identity = CubeLut.generate("Domain") { r,g,b -> floatArrayOf(r,g,b) }
        val lut = CubeLut("Domain", identity.size, identity.values, floatArrayOf(0f,0f,0f), floatArrayOf(.5f,.5f,.5f))
        val input = Bitmap.createBitmap(intArrayOf(Color.rgb(64,96,120)), 1, 1, Bitmap.Config.ARGB_8888)
        val output = LutRenderer.apply(input, FilterSettings(lut = lut))
        try { assertColor(Color.rgb(128,192,240), output.getPixel(0,0)) }
        finally { input.recycle(); if (output !== input) output.recycle() }
    }
    @Test fun cachedUniformsChangeWhenFilterChangesOnTheSameRenderer() {
        val black = CubeLut.generate("Black") { _, _, _ -> floatArrayOf(0f, 0f, 0f) }
        val settings = listOf(
            FilterSettings(),
            FilterSettings(lut = black),
            FilterSettings(lut = black, strength = 0f),
            FilterSettings(lut = black, strength = 1f),
            FilterSettings(),
        )
        val original = Color.rgb(40, 100, 200)
        val colors = drawSequence(original, settings)
        for (i in listOf(0, 2, 4)) assertColor(original, colors[i])
        for (i in listOf(1, 3)) assertColor(Color.BLACK, colors[i])
    }

    @Test fun strengthZeroStillAppliesBrightnessBeforeSkippingLogAndLut() {
        val black = CubeLut.generate("Black") { _, _, _ -> floatArrayOf(0f, 0f, 0f) }
        // sRGB 64 linearized, doubled (+1 EV) and re-encoded is approximately 90.
        val settings = listOf(
            FilterSettings(brightnessEv = 1f),
            FilterSettings(lut = black, strength = 0f, brightnessEv = 1f, encoding = LutEncoding.FLOG2),
        )
        val colors = drawSequence(Color.rgb(64, 64, 64), settings)
        for (color in colors) assertColor(Color.rgb(90, 90, 90), color)
    }

    @Test fun tileBoundaryPreservesBothRightAndBottomEdges() {
        val pixels = IntArray(1025 * 1025) { i ->
            when {
                i == 1025 * 1025 - 1 -> Color.WHITE
                i / 1025 == 1024 -> Color.BLUE
                i % 1025 == 1024 -> Color.GREEN
                else -> Color.RED
            }
        }
        val input = Bitmap.createBitmap(pixels, 1025, 1025, Bitmap.Config.ARGB_8888)
        val lut = CubeLut.generate("Identity") { r, g, b -> floatArrayOf(r, g, b) }
        val output = LutRenderer.apply(input, FilterSettings(lut = lut))
        try { for (y in listOf(0, 1023, 1024)) for (x in listOf(0, 1023, 1024)) assertColor(pixels[y * 1025 + x], output.getPixel(x, y)) }
        finally { input.recycle(); if (output !== input) output.recycle() }
    }

    /** Draw directly so zero-strength tests exercise the shader rather than photo's no-op path. */
    private fun drawSequence(color: Int, settings: List<FilterSettings>): List<Int> = EglCore().use {
        LutRenderer(false).use { renderer ->
            val bitmap = Bitmap.createBitmap(intArrayOf(color), 1, 1, Bitmap.Config.ARGB_8888)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            val input = LutRenderer.texture(GLES30.GL_TEXTURE_2D)
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
            val output = LutRenderer.texture(GLES30.GL_TEXTURE_2D)
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, 1, 1, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
            val fb = IntArray(1)
            GLES30.glGenFramebuffers(1, fb, 0)
            val readback = RgbaReadback(1)
            try {
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fb[0])
                GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, output, 0)
                check(GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) == GLES30.GL_FRAMEBUFFER_COMPLETE)
                settings.map { filter ->
                    renderer.draw(input, 1, 1, filter)
                    readback.pixels.clear()
                    GLES30.glReadPixels(0, 0, 1, 1, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, readback.pixels)
                    readback.decode(1)[0]
                }
            } finally {
                bitmap.recycle()
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
                GLES30.glDeleteFramebuffers(1, fb, 0)
                GLES30.glDeleteTextures(2, intArrayOf(input, output), 0)
            }
        }
    }

    private fun assertColor(expected: Int, actual: Int) {
        assertEquals(Color.red(expected).toDouble(), Color.red(actual).toDouble(), 2.0)
        assertEquals(Color.green(expected).toDouble(), Color.green(actual).toDouble(), 2.0)
        assertEquals(Color.blue(expected).toDouble(), Color.blue(actual).toDouble(), 2.0)
    }
}
