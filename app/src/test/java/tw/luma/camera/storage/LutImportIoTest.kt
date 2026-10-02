package tw.luma.camera.storage

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test
import tw.luma.camera.lut.CubeLut
import java.io.OutputStream
import java.security.MessageDigest

class LutImportIoTest {
    private val cube = "\uFEFFTITLE \"測試 LUT\"\r\nLUT_3D_SIZE 2\r\n" + "0.1 0.2 0.3\r\n".repeat(8)

    @Test fun parsesWhilePreservingAndHashingOriginalUtf8Bytes() {
        val bytes = cube.toByteArray(Charsets.UTF_8)
        val output = ByteArrayOutputStream()
        val result = LutImportIo.parseAndCopy(ByteArrayInputStream(bytes), output, bytes.size, "Fallback")
        assertArrayEquals(bytes, output.toByteArray())
        assertEquals(MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }, result.id)
        assertEquals("測試 LUT", result.lut.title)
        assertArrayEquals(floatArrayOf(.1f, .2f, .3f), result.lut.sample(.4f, .6f, .8f), .00001f)
    }
    @Test fun parserCannotWriteBeyondImportByteLimit() {
        val output = ByteArrayOutputStream()
        val bytes = cube.toByteArray(Charsets.UTF_8)
        assertThrows(IllegalArgumentException::class.java) { LutImportIo.parseAndCopy(ByteArrayInputStream(bytes), output, bytes.size - 1, "Fallback") }
        assertTrue(output.size() <= bytes.size - 1)
    }
    @Test fun invalidCubeAndStorageFailuresAreNotAccepted() {
        assertThrows(IllegalArgumentException::class.java) {
            LutImportIo.parseAndCopy("LUT_3D_SIZE 2\n0 0 0\n".byteInputStream(), ByteArrayOutputStream(), CubeLut.MAX_FILE_BYTES, "Fallback")
        }
        val failingOutput = object : OutputStream() {
            override fun write(value: Int) { throw java.io.IOException("Storage full") }
        }
        assertThrows(java.io.IOException::class.java) { LutImportIo.parseAndCopy(cube.byteInputStream(), failingOutput, CubeLut.MAX_FILE_BYTES, "Fallback") }
    }
    @Test fun streamingImportHandlesUtf8AcrossReadBoundaries() {
        val bytes = ("#" + "測".repeat(3000) + "\r\n" + cube).toByteArray(Charsets.UTF_8)
        val input = object : ByteArrayInputStream(bytes) {
            override fun read(buffer: ByteArray, offset: Int, length: Int) = super.read(buffer, offset, minOf(length, 7))
        }
        val output = ByteArrayOutputStream()
        val result = LutImportIo.parseAndCopy(input, output, bytes.size, "Fallback")
        assertArrayEquals(bytes, output.toByteArray())
        assertEquals("測試 LUT", result.lut.title)
        assertArrayEquals(floatArrayOf(.1f, .2f, .3f), result.lut.sample(.4f, .6f, .8f), .00001f)
    }
    @Test fun streamingImportLeavesCallerOwnedStreamsOpen() {
        var inputClosed = false
        var outputClosed = false
        val input = object : ByteArrayInputStream(cube.toByteArray(Charsets.UTF_8)) {
            override fun close() { inputClosed = true; super.close() }
        }
        val output = object : ByteArrayOutputStream() {
            override fun close() { outputClosed = true; super.close() }
        }
        LutImportIo.parseAndCopy(input, output, CubeLut.MAX_FILE_BYTES, "Fallback")
        assertFalse(inputClosed)
        assertFalse(outputClosed)
        input.close(); output.close()
    }
    @Test fun copiesExactLimitAndHashesOriginalBytes() {
        val output = ByteArrayOutputStream()
        val digest = LutImportIo.copyAndHash("abc".byteInputStream(), output, 3)
        assertArrayEquals(byteArrayOf(97, 98, 99), output.toByteArray())
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", digest)
    }

    @Test fun acceptsEmptyStreamAtZeroLimit() {
        val digest = LutImportIo.copyAndHash(ByteArrayInputStream(byteArrayOf()), ByteArrayOutputStream(), 0)
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", digest)
    }

    @Test fun rejectsOversizeBeforeWritingBeyondTheLimit() {
        val output = ByteArrayOutputStream()
        assertThrows(IllegalArgumentException::class.java) { LutImportIo.copyAndHash("abcd".byteInputStream(), output, 3) }
        assertTrue(output.size() <= 3)
    }

    @Test fun hashesAcrossReadBoundaries() {
        val bytes = ByteArray(20_000) { (it % 256).toByte() }
        val output = ByteArrayOutputStream()
        val digest = LutImportIo.copyAndHash(ByteArrayInputStream(bytes), output, bytes.size)
        assertArrayEquals(bytes, output.toByteArray())
        assertEquals("290c84b9b148f3bc4dc2c6cbc847910f611e446e722eae6969438db9f4aecd57", digest)
    }
}
