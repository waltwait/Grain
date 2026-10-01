package tw.luma.camera.gl

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import tw.luma.camera.lut.BundledLutLibrary
import tw.luma.camera.lut.LutEncoding
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/** Run with the optional personal debug pack prepared. Exercises the actual official assets. */
@RunWith(AndroidJUnit4::class)
class FujiLutDeviceTest {
    @Test fun personalPackContainsAllTenDistinctOfficialLooks() {
        val luts = BundledLutLibrary.load(InstrumentationRegistry.getInstrumentation().targetContext.assets)
        assumeTrue("Personal Fuji pack is optional; prepare it to run this test", luts.isNotEmpty())
        assertEquals(10, luts.size)
        assertEquals(10, luts.map { it.first }.toSet().size)
        assertTrue(luts.all { it.second.size == 33 && it.second.suggestedEncoding == LutEncoding.FLOG2 })
        assertFalse(luts[0].second.values.contentEquals(luts[1].second.values))
    }

    @Test fun officialLutsRenderNeutralRampLikeIndependentCpuReference() {
        val luts = BundledLutLibrary.load(InstrumentationRegistry.getInstrumentation().targetContext.assets)
        assumeTrue("Personal Fuji pack is optional; prepare it to run this test", luts.isNotEmpty())
        assertEquals(10, luts.size)
        val levels = listOf(32, 96, 160, 224)
        val input = Bitmap.createBitmap(levels.map { Color.rgb(it, it, it) }.toIntArray(), 4, 1, Bitmap.Config.ARGB_8888)
        try {
            for ((id, lut) in luts) {
                val output = LutRenderer.apply(input, FilterSettings(lut = lut, encoding = LutEncoding.FLOG2))
                try {
                    for ((x, level) in levels.withIndex()) {
                        val srgb = level / 255.0
                        val linear = if (srgb <= .04045) srgb / 12.92 else ((srgb + .055) / 1.055).pow(2.4)
                        // Neutral stays neutral through the D65 sRGB -> F-Gamut matrix.
                        val log = if (linear < .000889) 8.799461 * linear + .092864
                            else .245281 * log10(5.555556 * linear + .064829) + .384316
                        val mapped = lut.sample(log.toFloat(), log.toFloat(), log.toFloat())
                        val actual = output.getPixel(x, 0)
                        val channels = listOf(Color.red(actual), Color.green(actual), Color.blue(actual))
                        for (channel in 0..2) {
                            val displayLinear = mapped[channel].toDouble().coerceAtLeast(0.0).pow(2.2)
                            val encoded = if (displayLinear <= .0031308) 12.92 * displayLinear
                                else 1.055 * displayLinear.pow(1.0 / 2.4) - .055
                            val expected = (encoded.coerceIn(0.0, 1.0) * 255).roundToInt()
                            assertEquals("$id level=$level channel=$channel", expected.toDouble(), channels[channel].toDouble(), 3.0)
                        }
                    }
                } finally { output.recycle() }
            }
        } finally { input.recycle() }
    }
}
