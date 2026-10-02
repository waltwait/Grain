package tw.luma.camera.gl

import kotlin.math.pow

/** Relative grading in linear sRGB; these controls are not sensor Kelvin calibration. */
object WhiteBalanceColorMath {
    fun gains(warmth: Float, tint: Float): FloatArray {
        val w = if (warmth.isFinite()) warmth.coerceIn(-100f, 100f) / 100.0 else 0.0
        val t = if (tint.isFinite()) tint.coerceIn(-100f, 100f) / 100.0 else 0.0
        val red = 2.0.pow(.45 * w + .18 * t)
        val green = 2.0.pow(-.36 * t)
        val blue = 2.0.pow(-.45 * w + .18 * t)
        // Normalize neutral luminance so a color adjustment does not act like exposure.
        val luminance = .2126 * red + .7152 * green + .0722 * blue
        return floatArrayOf((red / luminance).toFloat(), (green / luminance).toFloat(), (blue / luminance).toFloat())
    }
}
