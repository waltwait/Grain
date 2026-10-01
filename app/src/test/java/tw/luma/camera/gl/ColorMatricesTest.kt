package tw.luma.camera.gl

import org.junit.Assert.*
import org.junit.Test
import tw.luma.camera.lut.LutEncoding

class ColorMatricesTest {
    private fun transform(m: FloatArray, rgb: FloatArray) = FloatArray(3) { row -> (0..2).sumOf { col -> (m[col * 3 + row] * rgb[col]).toDouble() }.toFloat() }
    @Test fun d65NeutralRemainsNeutralInBothGamuts() {
        for (encoding in listOf(LutEncoding.FLOG2, LutEncoding.FLOG2C)) assertArrayEquals(floatArrayOf(.18f,.18f,.18f), transform(ColorMatrices.forEncoding(encoding), floatArrayOf(.18f,.18f,.18f)), .00001f)
    }
    @Test fun fGamutMatchesRec709ToRec2020Reference() {
        val red = transform(ColorMatrices.forEncoding(LutEncoding.FLOG2), floatArrayOf(1f,0f,0f))
        assertArrayEquals(floatArrayOf(.627404f,.069097f,.016391f), red, .00001f)
    }
    @Test fun fGamutCUsesDifferentPrimaries() {
        val red = floatArrayOf(1f,0f,0f)
        assertFalse(transform(ColorMatrices.forEncoding(LutEncoding.FLOG2), red).contentEquals(transform(ColorMatrices.forEncoding(LutEncoding.FLOG2C), red)))
    }
}
