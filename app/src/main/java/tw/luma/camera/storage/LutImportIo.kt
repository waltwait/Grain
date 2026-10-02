package tw.luma.camera.storage

import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import tw.luma.camera.lut.CubeLut

internal object LutImportIo {
    data class ParsedImport(val id: String, val lut: CubeLut)

    /** Parses while copying/hashing original bytes; the caller owns streams and the temp file. */
    fun parseAndCopy(input: InputStream, output: OutputStream, maxBytes: Int, fallbackTitle: String): ParsedImport {
        val copying = CopyingInput(input, output, maxBytes)
        val lut = CubeLut.parse(copying.reader(Charsets.UTF_8), fallbackTitle)
        return ParsedImport(copying.id(), lut)
    }

    /** Copies original bytes once; the caller owns streams and publishes only after parsing. */
    fun copyAndHash(input: InputStream, output: OutputStream, maxBytes: Int): String {
        val copying = CopyingInput(input, output, maxBytes)
        val buffer = ByteArray(8192)
        while (copying.read(buffer) >= 0) { /* Bounded bulk copy, preserving byte identity. */ }
        return copying.id()
    }

    private class CopyingInput(private val source: InputStream, private val output: OutputStream, private val maxBytes: Int) : InputStream() {
        private val digest = MessageDigest.getInstance("SHA-256")
        private var total = 0L
        init { require(maxBytes >= 0) }

        private fun count(bytes: Int) {
            require(total + bytes <= maxBytes) { "LUT 檔案超過 24 MB" }
            total += bytes
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val read = source.read(buffer, offset, length)
            if (read > 0) {
                count(read)
                output.write(buffer, offset, read)
                digest.update(buffer, offset, read)
            }
            return read
        }

        override fun read(): Int {
            val value = source.read()
            if (value >= 0) {
                count(1)
                output.write(value)
                digest.update(value.toByte())
            }
            return value
        }

        fun id(): String {
            val bytes = digest.digest()
            val hex = "0123456789abcdef"
            return String(CharArray(bytes.size * 2) { index ->
                val value = bytes[index / 2].toInt() and 255
                hex[if (index % 2 == 0) value ushr 4 else value and 15]
            })
        }
    }
}
