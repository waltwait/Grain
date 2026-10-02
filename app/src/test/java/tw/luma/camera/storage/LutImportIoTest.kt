package tw.luma.camera.storage

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test

class LutImportIoTest {
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
