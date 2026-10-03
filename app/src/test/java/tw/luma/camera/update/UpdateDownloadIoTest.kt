package tw.luma.camera.update

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.CancellationException
import java.security.MessageDigest

class UpdateDownloadIoTest {
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    @Test fun streamsTheExactApkAndVerifiesTheHash() {
        val bytes = ByteArray(100_000) { (it % 251).toByte() }
        val output = ByteArrayOutputStream()
        var progress = 0L
        UpdateDownloadIo.copy(ByteArrayInputStream(bytes), output, bytes.size.toLong(), hash(bytes), {}, { progress = it })
        assertArrayEquals(bytes, output.toByteArray())
        assertEquals(bytes.size.toLong(), progress)
    }
    @Test fun rejectsTruncatedData() {
        val bytes = byteArrayOf(1, 2)
        assertThrows(IllegalArgumentException::class.java) { UpdateDownloadIo.copy(ByteArrayInputStream(bytes), ByteArrayOutputStream(), 3, hash(bytes)) }
    }
    @Test fun rejectsExtraBytesBeforeWritingBeyondTheLimit() {
        val output = ByteArrayOutputStream()
        assertThrows(IllegalArgumentException::class.java) { UpdateDownloadIo.copy(ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)), output, 3, "a".repeat(64)) }
        assertTrue(output.size() <= 3)
    }
    @Test fun rejectsDifferentContentOfTheSameSize() {
        assertThrows(IllegalArgumentException::class.java) { UpdateDownloadIo.copy(ByteArrayInputStream(byteArrayOf(1, 2)), ByteArrayOutputStream(), 2, "a".repeat(64)) }
    }
    @Test fun cooperatesWithCancellation() {
        assertThrows(CancellationException::class.java) { UpdateDownloadIo.copy(ByteArrayInputStream(byteArrayOf(1)), ByteArrayOutputStream(), 1, "a".repeat(64), { throw CancellationException() }) }
    }
}
