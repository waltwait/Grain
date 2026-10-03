package tw.luma.camera.storage

import java.io.InputStream
import java.io.OutputStream
import tw.luma.camera.gallery.PhotoViewport

internal object PhotoImportIo {
    const val MAX_BYTES = 80L * 1024 * 1024

    fun copy(input: InputStream, output: OutputStream, limit: Long = MAX_BYTES, checkActive: () -> Unit = {}) {
        require(limit > 0)
        val buffer = ByteArray(8192)
        var count = 0L
        while (true) {
            checkActive()
            val read = input.read(buffer)
            if (read < 0) break
            count += read
            require(count <= limit) { "照片檔案超過 80 MB" }
            output.write(buffer, 0, read)
        }
    }

    fun previewSampleSize(width: Int, height: Int): Int {
        require(width > 0 && height > 0) { "照片無法解碼" }
        require(width.toLong() * height <= 50_000_000L) { "請使用 50MP 以下的照片" }
        var sample = PhotoViewport.sampleSize(width, height, 1_500_000)
        while ((maxOf(width, height).toLong() + sample - 1) / sample > 2048) sample *= 2
        return sample
    }
}
