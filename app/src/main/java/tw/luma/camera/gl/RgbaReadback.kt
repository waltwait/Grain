package tw.luma.camera.gl

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Reusable RGBA staging for one tile. Row order is retained; exports are opaque. */
internal class RgbaReadback(capacity: Int) {
    val pixels: ByteBuffer = ByteBuffer.allocateDirect(capacity * 4).order(ByteOrder.LITTLE_ENDIAN)
    val colors = IntArray(capacity)
    private val words = pixels.asIntBuffer()

    fun decode(count: Int): IntArray {
        require(count in 0..colors.size)
        words.clear()
        words.get(colors, 0, count)
        for (i in 0 until count) {
            val rgba = colors[i]
            colors[i] = 0xff000000.toInt() or ((rgba and 255) shl 16) or
                (rgba and 0xff00) or ((rgba ushr 16) and 255)
        }
        return colors
    }
}
