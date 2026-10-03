package tw.luma.camera.update

import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

object UpdateDownloadIo {
    fun copy(input: InputStream, output: OutputStream, expectedSize: Long, expectedHash: String,
        checkActive: () -> Unit = {}, progress: (Long) -> Unit = {}) {
        require(expectedSize in 1..UpdateInfo.MAX_APK_BYTES)
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(32 * 1024)
        var total = 0L
        while (true) {
            checkActive()
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            require(total + count <= expectedSize) { "下載檔案大小與更新資訊不同" }
            output.write(buffer, 0, count)
            digest.update(buffer, 0, count)
            total += count
            progress(total)
        }
        checkActive()
        require(total == expectedSize) { "更新檔案下載不完整" }
        require(digest.digest().joinToString("") { "%02x".format(it) }.equals(expectedHash, true)) { "更新檔案校驗失敗，請重新下載" }
    }
}
