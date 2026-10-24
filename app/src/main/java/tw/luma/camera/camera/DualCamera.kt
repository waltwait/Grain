package tw.luma.camera.camera

import androidx.camera.core.CameraSelector
import androidx.exifinterface.media.ExifInterface
import tw.luma.camera.gl.SkinArea

/** Front and back recording at once, on phones that list the two lenses for concurrent use. */
object DualCamera {
    /** A camera by its Camera2 id and [CameraSelector] lens facing. */
    data class Lens(val id: String, val facing: Int)

    /** Where the small picture sits, in the composed frame's normalized coordinates (centre 0, edges ±1, +y up). */
    data class Inset(val scale: Float, val offsetX: Float, val offsetY: Float)

    /** The small picture's share of the frame's width and height. */
    const val SCALE = .3f
    /** Its gap to the frame's top and right edges, as a share of the frame's width. */
    private const val MARGIN = .045f

    /**
     * The back and front lenses of the first concurrent set that has both, preferring the phone's main back and front
     * cameras ([mainBack], [mainFront]) so the big picture is the usual 1× lens. Null when no set pairs a back with a front.
     */
    fun pick(sets: List<List<Lens>>, mainBack: String?, mainFront: String?): Pair<Lens, Lens>? = sets.mapNotNull { set ->
        val back = set.firstOrNull { it.facing == CameraSelector.LENS_FACING_BACK }
        val front = set.firstOrNull { it.facing == CameraSelector.LENS_FACING_FRONT }
        if (back != null && front != null) back to front else null
    }.maxByOrNull { (back, front) -> (if (back.id == mainBack) 2 else 0) + (if (front.id == mainFront) 1 else 0) }

    /** Shown in device details: the two camera ids recorded together. */
    fun label(pair: Pair<Lens, Lens>): String = "${pair.first.id}＋${pair.second.id}"

    /** The small picture in the top-right corner of a [width] × [height] frame, the same gap from both edges. */
    fun inset(width: Int, height: Int): Inset {
        val gap = 2 * MARGIN
        return Inset(SCALE, 1 - gap - SCALE, 1 - gap * width / height - SCALE)
    }

    /**
     * Clockwise degrees that turn a composed frame upright. The frame is upright for the screen at [displayRotation];
     * the photo should be upright for how the phone was held at the shutter, [captureRotation] (both `Surface.ROTATION_*`).
     */
    fun photoRotation(displayRotation: Int, captureRotation: Int): Int = ((displayRotation - captureRotation) * 90 + 360) % 360

    /** The EXIF orientation tag for [degrees] clockwise. */
    fun exifOrientation(degrees: Int): Int = when (degrees) {
        90 -> ExifInterface.ORIENTATION_ROTATE_90
        180 -> ExifInterface.ORIENTATION_ROTATE_180
        270 -> ExifInterface.ORIENTATION_ROTATE_270
        else -> ExifInterface.ORIENTATION_NORMAL
    }

    /**
     * Where the front camera is in a [width] × [height] composed frame: the inset when it is the small picture
     * ([frontSmall]), or the rest of the frame when it is the big one. Its skin blur reaches 1% of that picture's width.
     */
    fun skinArea(width: Int, height: Int, frontSmall: Boolean): SkinArea {
        val inset = inset(width, height)
        val reach = .01f * if (frontSmall) inset.scale else 1f
        return SkinArea((inset.offsetX - inset.scale + 1) / 2, (inset.offsetY - inset.scale + 1) / 2,
            (inset.offsetX + inset.scale + 1) / 2, (inset.offsetY + inset.scale + 1) / 2, frontSmall,
            reach, reach * width / height)
    }
}
