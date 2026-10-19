package tw.luma.camera.gl

import tw.luma.camera.lut.LutEncoding
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The photo filter as a plain colour function. Every setting only maps one colour to another, so the whole filter
 * can be baked into a single 3D LUT for a video encoder that cannot run [LutRenderer]'s shader.
 */
object FilterCube {
    /** What [LutRenderer] draws for one sRGB-encoded colour, channels in 0..1. */
    fun color(settings: FilterSettings, r: Float, g: Float, b: Float): FloatArray {
        val original = floatArrayOf(r, g, b)
        val whiteBalance = settings.warmth != 0f || settings.tint != 0f
        val adjusted = if (settings.brightnessEv == 0f && !whiteBalance) original else {
            val gains = if (whiteBalance) WhiteBalanceColorMath.gains(settings.warmth, settings.tint) else floatArrayOf(1f, 1f, 1f)
            val exposure = 2f.pow(settings.brightnessEv)
            FloatArray(3) { encodeSrgb(decodeSrgb(original[it]) * exposure * gains[it]).coerceIn(0f, 1f) }
        }
        val lut = settings.lut
        val strength = settings.strength.coerceIn(0f, 1f)
        if (lut == null || strength <= 0f) return adjusted
        val log = settings.encoding != LutEncoding.SRGB
        val input = if (!log) adjusted else {
            // Column-major, as the shader's uGamut.
            val gamut = ColorMatrices.forEncoding(settings.encoding)
            val linear = FloatArray(3) { decodeSrgb(adjusted[it]) }
            FloatArray(3) { row -> encodeLog((0..2).sumOf { gamut[it * 3 + row] * linear[it].toDouble() }.toFloat(), settings.encoding) }
        }
        val sampled = lut.sample(input[0], input[1], input[2])
        // Experimental Fuji adaptation, as in the shader: the LUT output is taken as display gamma 2.2.
        val mapped = if (log) FloatArray(3) { encodeSrgb(max(sampled[it], 0f).pow(2.2f)) } else sampled
        return FloatArray(3) { (adjusted[it] + (mapped[it] - adjusted[it]) * strength).coerceIn(0f, 1f) }
    }

    /** [settings] sampled on a [size]³ grid as opaque ARGB_8888 colours indexed [red][green][blue], as Media3's LUT takes them. */
    fun argb(settings: FilterSettings, size: Int = 33): Array<Array<IntArray>> {
        require(size >= 2)
        val step = 1f / (size - 1)
        return Array(size) { r -> Array(size) { g -> IntArray(size) { b ->
            val c = color(settings, r * step, g * step, b * step)
            (0xFF shl 24) or (channel(c[0]) shl 16) or (channel(c[1]) shl 8) or channel(c[2])
        } } }
    }

    private fun channel(value: Float) = (value * 255f).roundToInt().coerceIn(0, 255)

    private fun decodeSrgb(v: Float) = if (v >= .04045f) ((v + .055f) / 1.055f).pow(2.4f) else v / 12.92f

    private fun encodeSrgb(value: Float): Float {
        val v = max(value, 0f)
        return if (v >= .0031308f) 1.055f * v.pow(1f / 2.4f) - .055f else v * 12.92f
    }

    private fun encodeLog(value: Float, encoding: LutEncoding): Float {
        val v = max(value, 0f)
        return if (encoding == LutEncoding.FLOG) {
            if (v >= .00089f) .344676f * log10(.555556f * v + .009468f) + .790453f else 8.735631f * v + .092864f
        } else {
            if (v >= .000889f) .245281f * log10(5.555556f * v + .064829f) + .384316f else 8.799461f * v + .092864f
        }
    }
}
