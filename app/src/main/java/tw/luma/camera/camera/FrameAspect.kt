package tw.luma.camera.camera

import androidx.camera.core.AspectRatio

/** Shape of the saved photo or video as long side : short side; the viewfinder frame shows the same crop. */
enum class FrameAspect(val long: Int, val short: Int) {
    RATIO_4_3(4, 3), RATIO_3_2(3, 2), RATIO_16_9(16, 9), RATIO_1_1(1, 1);

    val label: String get() = "$long:$short"

    fun next(): FrameAspect = entries[(ordinal + 1) % entries.size]

    /** Width to height on screen: a portrait viewfinder holds an upright frame, a landscape one a wide frame. */
    fun screenShape(landscape: Boolean): Pair<Int, Int> = if (landscape) long to short else short to long

    /** Recorder stream to crop from; only 16:9 is wider than the sensor's own 4:3. */
    val videoStream: Int get() = if (this == RATIO_16_9) AspectRatio.RATIO_16_9 else AspectRatio.RATIO_4_3

    companion object {
        val PHOTO_DEFAULT = RATIO_4_3
        val VIDEO_DEFAULT = RATIO_16_9

        fun parse(saved: String?, fallback: FrameAspect): FrameAspect = entries.firstOrNull { it.name == saved } ?: fallback
    }
}
