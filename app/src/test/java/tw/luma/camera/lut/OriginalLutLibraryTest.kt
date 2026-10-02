package tw.luma.camera.lut

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import kotlin.math.abs

class OriginalLutLibraryTest {
    private fun library(): List<FilmLook> = OriginalLutLibrary.load { path -> File("src/main/assets", path).inputStream() }

    // A missing/wrong asset must not silently replace an original look with another look.
    @Test fun originalsLoadAsDistinctSdrLooks() {
        val looks = library()
        assertEquals(6, looks.size)
        assertEquals(6, looks.map { it.id }.toSet().size)
        assertTrue(looks.all { it.lut.suggestedEncoding == LutEncoding.SRGB && it.lut.size == 33 })
        val probes = listOf(floatArrayOf(.8f, .6f, .45f), floatArrayOf(.2f, .55f, .3f), floatArrayOf(.25f, .5f, .8f))
        for (a in looks.indices) for (b in a + 1 until looks.size) {
            val distance = probes.sumOf { p ->
                val left = looks[a].lut.sample(p[0], p[1], p[2])
                val right = looks[b].lut.sample(p[0], p[1], p[2])
                (0..2).sumOf { abs(left[it] - right[it]).toDouble() }
            } / 9
            assertTrue("${looks[a].id} and ${looks[b].id} must be distinct", distance > .005)
        }
    }

    // Bad tone curves create reversals, crushed shadows or tinted whites in neutral gradients.
    @Test fun neutralGradientsKeepShadowDetailAndSmoothHighlights() {
        val looks = library()
        assertEquals(6, looks.size)
        for (look in looks) {
            var previous = -1f
            for (step in 0..1024) {
                val input = step / 1024f
                val rgb = look.lut.sample(input, input, input)
                val light = .2126f * rgb[0] + .7152f * rgb[1] + .0722f * rgb[2]
                assertTrue("${look.id} reversed at $step", light > previous)
                assertTrue("${look.id} tinted a neutral excessively", rgb.max() - rgb.min() < .065f)
                previous = light
            }
            val black = look.lut.sample(0f, 0f, 0f)
            val white = look.lut.sample(1f, 1f, 1f)
            assertTrue(black.all { it in .005f.. .04f })
            assertTrue(white.all { it in .95f..1f })
        }
    }

    // Hue or channel-order mistakes can turn ordinary orange skin tones red, green or purple.
    @Test fun colorLooksKeepWarmSkinTonesOrdered() {
        val looks = library()
        assertEquals(6, looks.size)
        val skin = listOf(floatArrayOf(.85f, .63f, .50f), floatArrayOf(.65f, .43f, .32f),
            floatArrayOf(.42f, .27f, .19f), floatArrayOf(.95f, .82f, .73f))
        for (look in looks.filterNot { it.id == "grain-silver" }) for (rgb in skin) {
            val mapped = look.lut.sample(rgb[0], rgb[1], rgb[2])
            assertTrue("${look.id} skin channels", mapped[0] > mapped[1] && mapped[1] > mapped[2])
            val originalHue = (rgb[1] - rgb[2]) / (rgb[0] - rgb[2]) / 6
            val mappedHue = (mapped[1] - mapped[2]) / (mapped[0] - mapped[2]) / 6
            assertEquals("${look.id} skin hue", originalHue.toDouble(), mappedHue.toDouble(), .035)
        }
    }

    // Out-of-range entries clip output; a blue/green seam appears as a band in sky gradients.
    @Test fun allTablesStayInGamutAndSkyGradientsHaveNoSeams() {
        val looks = library()
        assertEquals(6, looks.size)
        for (look in looks) {
            assertTrue(look.lut.values.all { it.isFinite() && it in 0f..1f })
            var previous: FloatArray? = null
            for (step in 0..4096) {
                val t = step / 4096f
                val mapped = look.lut.sample(.12f + .68f * t, .35f + .51f * t, .7f + .25f * t)
                previous?.let { old -> assertTrue("${look.id} sky seam", (0..2).all { abs(mapped[it] - old[it]) < .001f }) }
                previous = mapped
            }
        }
    }

    // Silver must stay monochrome for colored input, including every table corner.
    @Test fun silverIsMonochromeForEveryInputColor() {
        val looks = library()
        assertEquals(6, looks.size)
        val silver = looks.single { it.id == "grain-silver" }.lut
        for (i in silver.values.indices step 3) {
            assertEquals(silver.values[i], silver.values[i + 1], .000001f)
            assertEquals(silver.values[i], silver.values[i + 2], .000001f)
        }
    }

    // Independently written identity bytes catch endian and red/blue index swaps in the loader.
    @Test fun binaryLoaderReadsRedFastestAndClosesAssets() {
        val payload = identityBytes()
        var closed = 0
        val looks = OriginalLutLibrary.load {
            object : ByteArrayInputStream(payload) { override fun close() { super.close(); closed++ } }
        }
        assertEquals(6, looks.size)
        assertEquals(6, closed)
        for (look in looks) assertArrayEquals(floatArrayOf(.19f, .53f, .87f), look.lut.sample(.19f, .53f, .87f), .000001f)
    }

    // A corrupt header/length/value must fail explicitly rather than become a broken GPU texture.
    @Test fun binaryLoaderRejectsDamagedTables() {
        val valid = identityBytes()
        val badMagic = valid.copyOf().apply { this[0] = 0 }
        val badVersion = valid.copyOf().apply { this[11] = 2 }
        val badSize = valid.copyOf().apply { this[15] = 65 }
        val nan = valid.copyOf().apply { this[16] = 0x7f; this[17] = 0xc0.toByte() }
        for (payload in listOf(badMagic, badVersion, badSize, nan, valid.copyOf(valid.size - 1), valid + byteArrayOf(0))) {
            assertThrows(Exception::class.java) { OriginalLutLibrary.load { ByteArrayInputStream(payload) } }
        }
    }

    private fun identityBytes(): ByteArray = ByteArrayOutputStream().also { bytes ->
        DataOutputStream(bytes).use { output ->
            output.writeBytes("GRAINLUT")
            output.writeInt(1)
            output.writeInt(33)
            for (b in 0..32) for (g in 0..32) for (r in 0..32) {
                output.writeFloat(r / 32f); output.writeFloat(g / 32f); output.writeFloat(b / 32f)
            }
        }
    }.toByteArray()
}
