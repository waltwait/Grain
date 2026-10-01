package tw.luma.camera.camera

import java.util.Locale

object ZoomControls {
    fun presets(min: Float, max: Float): List<Float> {
        if (!min.isFinite() || !max.isFinite() || min <= 0f || max < min) return emptyList()
        val common = listOf(1f, 2f, 3f, 5f).filter { it in min..max }
        val wide = if (min < .99f) listOf(min) else emptyList()
        return (wide + common).ifEmpty { listOf(min) }.take(5)
    }
    fun label(ratio: Float): String = if (ratio % 1f < .01f) "${ratio.toInt()}×" else "%.1f×".format(Locale.US, ratio)
    fun recordingTime(ns: Long): String {
        val seconds = ns.coerceAtLeast(0) / 1_000_000_000L
        return if (seconds >= 3600) "%02d:%02d:%02d".format(Locale.US, seconds / 3600, seconds / 60 % 60, seconds % 60)
            else "%02d:%02d".format(Locale.US, seconds / 60, seconds % 60)
    }
}
