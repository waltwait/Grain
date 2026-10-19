package tw.luma.camera.camera

import androidx.camera.core.AspectRatio
import kotlin.math.roundToInt

/** Pixels kept from a photo: [left], [top], [width] and [height] in the source bitmap. */
data class PhotoCrop(val left: Int, val top: Int, val width: Int, val height: Int)

/** Shape of the saved photo or video as long side : short side; the viewfinder frame shows the same crop. */
enum class FrameAspect(val long: Int, val short: Int) {
    // The picker's order: iOS's 4:3, 16:9 and 1:1, then the film ratio.
    RATIO_4_3(4, 3), RATIO_16_9(16, 9), RATIO_1_1(1, 1), RATIO_3_2(3, 2);

    /** Upright, like the phone's fixed camera screen: 3:4, 9:16, 1:1 and 2:3. */
    val label: String get() = "$short:$long"

    /** The largest part of a [width] × [height] photo with this shape, from its middle and in its own orientation. */
    fun centerCrop(width: Int, height: Int): PhotoCrop {
        val (w, h) = screenShape(landscape = width > height)
        return if (width.toLong() * h > height.toLong() * w) {
            val cropWidth = (height.toDouble() * w / h).roundToInt()
            PhotoCrop((width - cropWidth) / 2, 0, cropWidth, height)
        } else {
            val cropHeight = (width.toDouble() * h / w).roundToInt()
            PhotoCrop(0, (height - cropHeight) / 2, width, cropHeight)
        }
    }

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
