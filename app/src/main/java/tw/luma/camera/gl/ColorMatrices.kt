package tw.luma.camera.gl

import tw.luma.camera.lut.LutEncoding

/** Chromaticity-derived D65 matrices; packed column-major for OpenGL. */
object ColorMatrices {
    private val srgb = rgbToXyz(doubleArrayOf(.64, .33, .30, .60, .15, .06))
    private val fGamut = convert(doubleArrayOf(.708, .292, .170, .797, .131, .046))
    private val fGamutC = convert(doubleArrayOf(.73470, .26530, .02630, .97370, .11730, -.02240))
    fun forEncoding(encoding: LutEncoding): FloatArray = if (encoding == LutEncoding.FLOG2C) fGamutC else fGamut
    private fun convert(primaries: DoubleArray): FloatArray {
        val m = multiply(inverse(rgbToXyz(primaries)), srgb)
        return FloatArray(9) { m[it % 3 * 3 + it / 3].toFloat() }
    }
    private fun rgbToXyz(p: DoubleArray): DoubleArray {
        val m = DoubleArray(9)
        for (c in 0..2) { val x = p[c * 2]; val y = p[c * 2 + 1]; m[c] = x / y; m[3 + c] = 1.0; m[6 + c] = (1 - x - y) / y }
        val white = doubleArrayOf(.3127 / .3290, 1.0, (1 - .3127 - .3290) / .3290)
        val inv = inverse(m)
        val scale = DoubleArray(3) { row -> (0..2).sumOf { c -> inv[row * 3 + c] * white[c] } }
        return DoubleArray(9) { m[it] * scale[it % 3] }
    }
    private fun multiply(a: DoubleArray, b: DoubleArray) = DoubleArray(9) { i -> (0..2).sumOf { k -> a[i / 3 * 3 + k] * b[k * 3 + i % 3] } }
    private fun inverse(a: DoubleArray): DoubleArray {
        val c = doubleArrayOf(a[4]*a[8]-a[5]*a[7], a[2]*a[7]-a[1]*a[8], a[1]*a[5]-a[2]*a[4],
            a[5]*a[6]-a[3]*a[8], a[0]*a[8]-a[2]*a[6], a[2]*a[3]-a[0]*a[5],
            a[3]*a[7]-a[4]*a[6], a[1]*a[6]-a[0]*a[7], a[0]*a[4]-a[1]*a[3])
        val det = a[0]*c[0]+a[1]*c[3]+a[2]*c[6]
        return DoubleArray(9) { c[it] / det }
    }
}
