package tw.luma.camera.gl

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import tw.luma.camera.lut.OriginalLutLibrary
import tw.luma.camera.lut.KodakLutLibrary
import kotlin.math.roundToInt

/** Compares authored and community tables on the actual shader with CPU trilinear sampling. */
@RunWith(AndroidJUnit4::class)
class OriginalLutDeviceTest {
    @Test fun builtInFilmLooksRenderSkinNeutralsAndSaturatedColorsLikeCpuReference() {
        val assets = InstrumentationRegistry.getInstrumentation().targetContext.assets
        val looks = OriginalLutLibrary.load { assets.open(it) } + KodakLutLibrary.load { assets.open(it) }
        assertEquals(11, looks.size)
        val pixels = intArrayOf(Color.BLACK, Color.WHITE, Color.RED, Color.GREEN, Color.BLUE,
            Color.rgb(217, 161, 128), Color.rgb(166, 110, 82), Color.rgb(107, 69, 48),
            Color.rgb(242, 209, 186), Color.rgb(31, 89, 179), Color.rgb(82, 143, 71), Color.MAGENTA) +
            IntArray(20) { i -> val gray = (i * 255 / 19); Color.rgb(gray, gray, gray) }
        val input = Bitmap.createBitmap(pixels, 8, 4, Bitmap.Config.ARGB_8888)
        try {
            for (look in looks) {
                val output = LutRenderer.apply(input, FilterSettings(lut = look.lut))
                try {
                    pixels.forEachIndexed { index, pixel ->
                        val expected = look.lut.sample(Color.red(pixel) / 255f, Color.green(pixel) / 255f, Color.blue(pixel) / 255f)
                        val actual = output.getPixel(index % 8, index / 8)
                        val channels = intArrayOf(Color.red(actual), Color.green(actual), Color.blue(actual))
                        for (channel in 0..2) assertEquals("${look.id} pixel=$index channel=$channel",
                            (expected[channel] * 255).roundToInt().toDouble(), channels[channel].toDouble(), 2.0)
                    }
                } finally { if (output !== input) output.recycle() }
            }
        } finally { input.recycle() }
    }
}
