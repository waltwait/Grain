package tw.luma.camera.camera

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

/** A logarithmic scale keeps low zoom/ISO/shutter values usable over large sensor ranges. */
object LiveControlMath {
    fun position(value: Double, min: Double, max: Double): Float {
        require(min > 0 && max >= min)
        if (max == min) return 0f
        return ((ln(value.coerceIn(min, max)) - ln(min)) / (ln(max) - ln(min))).toFloat().coerceIn(0f, 1f)
    }

    fun value(position: Float, min: Double, max: Double): Double {
        require(min > 0 && max >= min)
        return exp(ln(min) + position.coerceIn(0f, 1f) * (ln(max) - ln(min))).coerceIn(min, max)
    }

    // Up is brighter. One EV per 80 dp, quantized to the camera's actual EV step.
    fun exposureIndex(start: Int, displacementY: Float, density: Float, step: Float, min: Int, max: Int): Int {
        if (density <= 0 || step <= 0) return start.coerceIn(min, max)
        return (start - displacementY / (80f * density * step)).roundToInt().coerceIn(min, max)
    }
}
