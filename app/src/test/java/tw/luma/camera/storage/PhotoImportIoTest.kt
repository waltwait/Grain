package tw.luma.camera.storage

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test

class PhotoImportIoTest {
    @Test fun copiesTheOriginalBytesWithoutReencoding() {
        val bytes = ByteArray(9000) { it.toByte() }
        val output = ByteArrayOutputStream()
        PhotoImportIo.copy(ByteArrayInputStream(bytes), output, 9000)
        assertArrayEquals(bytes, output.toByteArray())
    }
    @Test fun rejectsOversizedInputBeforeWritingBeyondTheLimit() {
        val output = ByteArrayOutputStream()
        assertThrows(IllegalArgumentException::class.java) { PhotoImportIo.copy(ByteArrayInputStream(ByteArray(9001)), output, 9000) }
        assertTrue(output.size() <= 9000)
    }
    @Test fun importCopyCanBeCancelledBetweenChunks() {
        val output = ByteArrayOutputStream()
        assertThrows(IllegalStateException::class.java) {
            PhotoImportIo.copy(ByteArrayInputStream(ByteArray(9000)), output, 9000) { error("cancelled") }
        }
        assertEquals(0, output.size())
    }
    @Test fun previewBoundsBothPixelsAndLongImageEdges() {
        assertEquals(4, PhotoImportIo.previewSampleSize(4000, 3000))
        assertEquals(16, PhotoImportIo.previewSampleSize(20000, 100))
        assertEquals(1, PhotoImportIo.previewSampleSize(640, 480))
        assertThrows(IllegalArgumentException::class.java) { PhotoImportIo.previewSampleSize(10000, 10000) }
        assertThrows(IllegalArgumentException::class.java) { PhotoImportIo.previewSampleSize(0, 100) }
    }
}
