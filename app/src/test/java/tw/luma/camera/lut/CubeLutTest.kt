package tw.luma.camera.lut

import org.junit.Assert.*
import org.junit.Test
import java.io.Reader
import kotlin.random.Random

class CubeLutTest {
    private fun cube(size: Int = 2, domain: String = "", f: (Float, Float, Float) -> String = { r, g, b -> "$r $g $b" }): String = buildString {
        append("TITLE \"Test LUT\"\nLUT_3D_SIZE $size\n$domain\n")
        for (b in 0 until size) for (g in 0 until size) for (r in 0 until size) append(f(r.toFloat()/(size-1), g.toFloat()/(size-1), b.toFloat()/(size-1)) + "\n")
    }

    @Test fun identityInterpolatesOffGridValues() {
        val lut = CubeLut.parse(cube().reader())
        assertArrayEquals(floatArrayOf(.19f, .53f, .87f), lut.sample(.19f, .53f, .87f), .00001f)
    }
    @Test fun redIsFastestAndChannelsAreNotSwapped() {
        val lut = CubeLut.parse(cube(f = { r, g, b -> "$b $r $g" }).reader())
        assertArrayEquals(floatArrayOf(.8f, .2f, .4f), lut.sample(.2f, .4f, .8f), .00001f)
    }
    @Test fun domainNormalizesEachChannel() {
        val lut = CubeLut.parse(cube(domain = "DOMAIN_MIN -1 0 2\nDOMAIN_MAX 1 4 6").reader())
        assertArrayEquals(floatArrayOf(.5f, .5f, .5f), lut.sample(0f, 2f, 4f), .00001f)
    }
    @Test fun clampsInputsAtDomainBoundaries() {
        val lut = CubeLut.parse(cube().reader())
        assertArrayEquals(floatArrayOf(0f, 1f, .5f), lut.sample(-.4f, 2f, .5f), .00001f)
    }
    @Test fun acceptsCommentsScientificNotationAndBom() {
        val text = "\uFEFF" + cube().replace("0.0", "0e0").replace("1.0", "1e0") + "# final comment\n"
        assertEquals("Test LUT", CubeLut.parse(text.reader()).title)
    }
    @Test fun preservesStandardFloatRoundingAndNegativeZero() {
        val numbers = listOf("-0", "-0.000000", "100000004", "100000012", "99999996", ".123456",
            "+0.500000", "1.0000000596046448", "1.0000000596046449", "1.401298464324817e-45", "0x1.fffffep127f")
        for (number in numbers) {
            val lut = CubeLut.parse(cube(f = { _, _, _ -> "$number $number $number" }).reader())
            assertEquals("Exact IEEE float bits for $number", number.toFloat().toRawBits(), lut.values[0].toRawBits())
        }
        val random = Random(314159)
        val samples = List(15_000) {
            val digits = random.nextInt(0, 1_000_000_000).toString().padStart(9, '0')
            val split = random.nextInt(0, 10)
            val sign = if (random.nextBoolean()) "-" else ""
            "$sign${digits.substring(0, split)}.${digits.substring(split)}"
        }
        val source = "LUT_3D_SIZE 25\n" + (samples + List(625) { "0.0" }).joinToString("\n") { "$it $it $it" }
        val lut = CubeLut.parse(source.reader())
        samples.forEachIndexed { index, number ->
            assertEquals(number, number.toFloat().toRawBits(), lut.values[index * 3].toRawBits())
        }
    }
    @Test fun rejectsLongLineWithoutConsumingItsEntirePayload() {
        var read = 0
        val source = object : Reader() {
            override fun read(buffer: CharArray, offset: Int, length: Int): Int {
                val count = minOf(length, 1_000_000 - read)
                if (count == 0) return -1
                buffer.fill('0', offset, offset + count)
                read += count
                return count
            }
            override fun close() = Unit
        }
        assertThrows(IllegalArgumentException::class.java) { CubeLut.parse(source) }
        assertTrue("An invalid line must be rejected before reading its full 1M payload", read <= 16_384)
    }
    @Test fun handlesCrLfAndTokensAcrossReaderChunks() {
        val text = cube().replace("\n", "\r\n")
        val source = object : Reader() {
            var cursor = 0
            override fun read(buffer: CharArray, offset: Int, length: Int): Int {
                if (cursor == text.length) return -1
                val count = minOf(7, length, text.length - cursor)
                text.toCharArray(buffer, offset, cursor, cursor + count)
                cursor += count
                return count
            }
            override fun close() = Unit
        }
        assertArrayEquals(floatArrayOf(.19f, .53f, .87f), CubeLut.parse(source).sample(.19f, .53f, .87f), .00001f)
    }
    @Test fun infersOnlyRecognizedGammaMetadata() {
        assertEquals(LutEncoding.FLOG2C, CubeLut.parse(("#Gamma:F-Log2C to ACROS\n" + cube()).reader()).suggestedEncoding)
        assertEquals(LutEncoding.SRGB, CubeLut.parse(cube().reader(), "FLog2_some_file").suggestedEncoding)
    }
    @Test fun preservesOutOfRangeOutputsUntilRendering() {
        val lut = CubeLut.parse(cube(f = { _, _, _ -> "1.2 -0.1 0.5" }).reader())
        assertArrayEquals(floatArrayOf(1.2f, -.1f, .5f), lut.sample(.5f,.5f,.5f), .00001f)
    }
    @Test fun rejectsIncompleteData() { rejects(cube().trimEnd().substringBeforeLast('\n')) }
    @Test fun rejectsExtraData() { rejects(cube() + "0 0 0\n") }
    @Test fun rejectsNonFiniteData() { rejects(cube().replaceFirst("0.0 0.0 0.0", "NaN 0 0")) }
    @Test fun rejectsReversedDomain() { rejects(cube(domain = "DOMAIN_MIN 1 0 0\nDOMAIN_MAX 0 1 1")) }
    @Test fun rejectsUnsupportedShaper() { rejects("LUT_1D_SIZE 2\n" + cube()) }
    @Test fun rejectsOversizedGridBeforeAllocating() { rejects("LUT_3D_SIZE 256\n") }
    @Test fun rejectsDuplicateDimensions() { rejects("LUT_3D_SIZE 2\n" + cube()) }
    @Test fun supports65PointCube() {
        val lut = CubeLut.parse(cube(size = 65).reader())
        assertEquals(65, lut.size)
        assertArrayEquals(floatArrayOf(.123f,.456f,.789f), lut.sample(.123f,.456f,.789f), .00001f)
    }
    @Test fun monochromeBuiltInHasEqualOutputChannels() {
        val rgb = CubeLut.builtIns().last().sample(.8f, .2f, .6f)
        assertEquals(rgb[0], rgb[1], .00001f); assertEquals(rgb[1], rgb[2], .00001f)
    }
    private fun rejects(text: String) {
        assertThrows(Exception::class.java) { CubeLut.parse(text.reader()) }
    }
}
