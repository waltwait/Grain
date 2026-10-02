package tw.luma.camera.lut

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

class KodakLutLibraryTest {
    private fun library() = KodakLutLibrary.load { path -> File("src/main/assets", path).inputStream() }

    // Transcoding must preserve each source PNG's full 64-cube table, including index order.
    @Test fun packedProfilesMatchPinnedSourceHaldPixels() {
        val looks = library()
        assertEquals(5, looks.size)
        assertEquals(5, looks.map { it.id }.toSet().size)
        val fixtures = mapOf("kodak-portra-160" to "kodak_portra_160.png", "kodak-portra-400" to "kodak_portra_400.png",
            "kodak-portra-800" to "kodak_portra_800.png", "kodak-ektachrome-100-vs" to "kodak_ektachrome_100_vs.png",
            "kodak-tri-x-400" to "kodak_tri-x_400.png")
        for (look in looks) {
            assertEquals(64, look.lut.size)
            assertEquals(LutEncoding.SRGB, look.lut.suggestedEncoding)
            val image = ImageIO.read(File("../third_party/luts/natron", fixtures.getValue(look.id)))
            for (b in 0..63) for (g in 0..63) for (r in 0..63) {
                val index = (b * 64 + g) * 64 + r
                val pixel = image.getRGB(index % 512, index / 512)
                val expected = floatArrayOf((pixel shr 16 and 255) / 255f, (pixel shr 8 and 255) / 255f, (pixel and 255) / 255f)
                for (channel in 0..2) assertEquals("${look.id} node=$index channel=$channel", expected[channel], look.lut.values[index * 3 + channel], 0f)
            }
        }
    }

    // The source Tri-X table is grayscale; channel swaps/corrupt bytes must not add a tint.
    @Test fun triXStaysMonochromeForColoredInput() {
        val looks = library()
        assertEquals(5, looks.size)
        val lut = looks.single { it.id == "kodak-tri-x-400" }.lut
        for (rgb in listOf(floatArrayOf(.18f, .52f, .9f), floatArrayOf(.85f, .60f, .41f), floatArrayOf(1f, 0f, .3f))) {
            val sample = lut.sample(rgb[0], rgb[1], rgb[2])
            assertEquals(sample[0], sample[1], .000001f)
            assertEquals(sample[0], sample[2], .000001f)
        }
    }
}
