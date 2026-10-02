package tw.luma.camera.storage

import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

internal object LutImportIo {
    /** Copies original bytes once; the caller owns streams and publishes only after parsing. */
    fun copyAndHash(input: InputStream, output: OutputStream, maxBytes: Int): String {
        require(maxBytes >= 0)
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= maxBytes) { "LUT 檔案超過 24 MB" }
            output.write(buffer, 0, count)
            digest.update(buffer, 0, count)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
