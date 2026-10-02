package tw.luma.camera.lut

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

class PackedLutTest {
    @Test fun byteTablesPreserveRgbOrderAndFullRange() {
        val lut = PackedLut.read(ByteArrayInputStream(identityBytes()), "Fixture")
        assertArrayEquals(floatArrayOf(.19f, .53f, .87f), lut.sample(.19f, .53f, .87f), .000001f)
        assertArrayEquals(floatArrayOf(1f, 0f, 0f), lut.sample(1f, 0f, 0f), 0f)
        assertArrayEquals(floatArrayOf(0f, 1f, 0f), lut.sample(0f, 1f, 0f), 0f)
        assertArrayEquals(floatArrayOf(0f, 0f, 1f), lut.sample(0f, 0f, 1f), 0f)
    }

    @Test fun closesByteStreamsAfterSuccessfulOrFailedRead() {
        for (payload in listOf(identityBytes(), identityBytes().copyOf(17))) {
            var closed = false
            val input = object : ByteArrayInputStream(payload) { override fun close() { closed = true; super.close() } }
            runCatching { PackedLut.read(input, "Fixture") }
            assertTrue(closed)
        }
    }

    @Test fun rejectsUnsupportedHeaderSizeAndIncompleteBytePayload() {
        val valid = identityBytes()
        val badVersion = valid.copyOf().apply { this[11] = 3 }
        val badSize = valid.copyOf().apply { this[15] = 127 }
        val badMagic = valid.copyOf().apply { this[0] = 0 }
        for (payload in listOf(badMagic, badVersion, badSize, valid.copyOf(valid.size - 1), valid + byteArrayOf(0))) {
            val failure = runCatching { PackedLut.read(ByteArrayInputStream(payload), "Fixture") }.exceptionOrNull()
            assertTrue("Corrupt packed table must fail validation/EOF", failure is IllegalArgumentException || failure is java.io.EOFException)
        }
    }

    private fun identityBytes(): ByteArray = ByteArrayOutputStream().also { bytes ->
        DataOutputStream(bytes).use { output ->
            output.writeBytes("GRAINLUT"); output.writeInt(2); output.writeInt(4)
            for (b in intArrayOf(0, 85, 170, 255)) for (g in intArrayOf(0, 85, 170, 255)) for (r in intArrayOf(0, 85, 170, 255)) {
                output.writeByte(r); output.writeByte(g); output.writeByte(b)
            }
        }
    }.toByteArray()
}
