package tw.luma.camera.lut

import java.io.InputStream
import java.io.DataInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object PackedLut {
    /** Version 1 preserves authored float32 tables; version 2 preserves 8-bit Hald data. */
    fun read(input: InputStream, title: String): CubeLut = DataInputStream(input).use { source ->
        val magic = ByteArray(8).also(source::readFully)
        require(magic.contentEquals("GRAINLUT".toByteArray(Charsets.US_ASCII))) { "Grain LUT 標頭錯誤" }
        val version = source.readInt()
        val size = source.readInt()
        require(version in 1..2 && size in 2..65) { "Grain LUT 版本或格點錯誤" }
        val count = size * size * size * 3
        val bytes = ByteArray(count * if (version == 1) 4 else 1).also(source::readFully)
        require(source.read() == -1) { "Grain LUT 資料長度錯誤" }
        val values = if (version == 1) FloatArray(count).also {
            ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).asFloatBuffer().get(it)
        } else FloatArray(count) { (bytes[it].toInt() and 255) / 255f }
        require(values.all { it.isFinite() && it in 0f..1f }) { "Grain LUT 色彩數值錯誤" }
        CubeLut(title, size, values)
    }
}
