package tw.luma.camera.camera

import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToInt

object ZoomControls {
    /** Anchor each drag to its starting ratio; 160 dp to the right doubles magnification. */
    fun dragRatio(start: Float, displacementX: Float, density: Float, min: Float, max: Float): Float {
        if (!min.isFinite() || !max.isFinite() || min <= 0f || max < min) return start
        val initial = (start.takeIf { it.isFinite() && it > 0f } ?: min).coerceIn(min, max)
        if (!displacementX.isFinite() || !density.isFinite() || density <= 0f) return initial
        return (initial.toDouble() * 2.0.pow(displacementX / (160.0 * density)))
            .coerceIn(min.toDouble(), max.toDouble()).toFloat()
    }

    /** The zoom buttons, like the Galaxy's own camera: the ultra-wide when there is one, then 1, 2, 3 and 10 as far as the lens reaches. */
    fun presets(min: Float, max: Float): List<Float> {
        if (!min.isFinite() || !max.isFinite() || min <= 0f || max < min) return emptyList()
        val common = listOf(1f, 2f, 3f, 10f).filter { it in min..max }
        val wide = if (min < .99f) listOf(min) else emptyList()
        return (wide + common).ifEmpty { listOf(min) }
    }

    /** ".6", "1", "2.4": a ratio as the zoom buttons print it; the chosen one adds "×". */
    fun short(ratio: Float): String {
        val tenths = (ratio * 10).roundToInt()
        val text = if (tenths % 10 == 0) (tenths / 10).toString() else "%.1f".format(Locale.US, tenths / 10f)
        return if (tenths < 10) text.removePrefix("0") else text
    }

    /** The button that shows the current [zoom]: the last preset at or below it, as on iOS, else the first. */
    fun activePreset(presets: List<Float>, zoom: Float): Int = presets.indexOfLast { it <= zoom + .01f }.coerceAtLeast(0)

    /** Ratios a preset tap steps through, on the same multiplicative scale as a pinch, ending exactly on [target]. */
    fun glide(start: Float, target: Float, steps: Int = 12): List<Float> {
        if (!start.isFinite() || start <= 0f || start == target || steps < 2) return listOf(target)
        return List(steps) { i -> if (i == steps - 1) target else (start * (target / start).toDouble().pow((i + 1.0) / steps).toFloat()) }
    }

    fun label(ratio: Float): String = if (ratio % 1f < .01f) "${ratio.toInt()}×" else "%.1f×".format(Locale.US, ratio)
    fun recordingTime(ns: Long): String {
        val seconds = ns.coerceAtLeast(0) / 1_000_000_000L
        return if (seconds >= 3600) "%02d:%02d:%02d".format(Locale.US, seconds / 3600, seconds / 60 % 60, seconds % 60)
            else "%02d:%02d".format(Locale.US, seconds / 60, seconds % 60)
    }
}
