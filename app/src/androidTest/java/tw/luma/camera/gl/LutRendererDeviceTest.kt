package tw.luma.camera.gl

import android.graphics.Bitmap
import android.graphics.Color
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
        finally { input.recycle(); output.recycle() }
    }
    @Test fun strengthZeroPreservesInputEvenWithNonIdentityLut() {
        val input = Bitmap.createBitmap(intArrayOf(Color.rgb(40, 100, 200)), 1, 1, Bitmap.Config.ARGB_8888)
        val lut = CubeLut.generate("Black") { _,_,_ -> floatArrayOf(0f,0f,0f) }
        val output = LutRenderer.apply(input, FilterSettings(lut = lut, strength = 0f))
        try { assertColor(input.getPixel(0,0), output.getPixel(0,0)) }
        finally { input.recycle(); output.recycle() }
    }
    @Test fun tileBoundaryDoesNotDropOrReversePixels() {
        val pixels = IntArray(1025*3) { i -> if (i % 1025 == 1024) Color.BLUE else if (i / 1025 == 0) Color.RED else Color.GREEN }
        val input = Bitmap.createBitmap(pixels, 1025, 3, Bitmap.Config.ARGB_8888)
        val lut = CubeLut.generate("Identity") { r,g,b -> floatArrayOf(r,g,b) }
        val output = LutRenderer.apply(input, FilterSettings(lut = lut))
        try { for (y in 0..2) for (x in listOf(0,1023,1024)) assertColor(input.getPixel(x,y), output.getPixel(x,y)) }
        finally { input.recycle(); output.recycle() }
    }
    @Test fun domainMetadataIsAppliedOnGpu() {
        val identity = CubeLut.generate("Domain") { r,g,b -> floatArrayOf(r,g,b) }
        val lut = CubeLut("Domain", identity.size, identity.values, floatArrayOf(0f,0f,0f), floatArrayOf(.5f,.5f,.5f))
        val input = Bitmap.createBitmap(intArrayOf(Color.rgb(64,96,120)), 1, 1, Bitmap.Config.ARGB_8888)
        val output = LutRenderer.apply(input, FilterSettings(lut = lut))
        try { assertColor(Color.rgb(128,192,240), output.getPixel(0,0)) }
        finally { input.recycle(); output.recycle() }
    }
    private fun assertColor(expected: Int, actual: Int) {
        assertEquals(Color.red(expected).toDouble(), Color.red(actual).toDouble(), 2.0)
        assertEquals(Color.green(expected).toDouble(), Color.green(actual).toDouble(), 2.0)
        assertEquals(Color.blue(expected).toDouble(), Color.blue(actual).toDouble(), 2.0)
    }
}
