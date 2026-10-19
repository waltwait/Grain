package tw.luma.camera.ui

import androidx.compose.ui.unit.IntRect
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The frame inside the viewfinder, how far above the viewfinder's bottom the ISO pill stands, and the space between
 * the zoom badge and the pill.
 */
data class ViewfinderLayout(val frame: IntRect, val controlsLift: Int, val zoomGap: Int)

/**
 * Lays the viewfinder out like iOS: a 4:3 frame sits right under the toolbar and every ratio is placed against it.
 * Shorter frames are centered inside it; taller ones start under the toolbar, or run under it when they would
 * otherwise need side bars. The ISO pill stays [gap] under the 4:3 frame; when that would leave a frame edge across
 * it, the pill moves into the black under the frame or onto the picture.
 */
object ViewfinderFrame {
    fun place(width: Int, height: Int, ratio: Float, toolbar: Int, strip: Int, gap: Int): ViewfinderLayout {
        val boxRatio = if (width > height) 4f / 3 else 3f / 4
        val box = fit(width, height - toolbar, boxRatio, top = toolbar, areaWidth = width)
        val frame = if (ratio >= boxRatio) {
            val inside = fit(box.width, box.height, ratio, top = 0, areaWidth = box.width)
            IntRect(box.left + inside.left, box.top + (box.height - inside.height) / 2,
                box.left + inside.right, box.top + (box.height - inside.height) / 2 + inside.height)
        } else {
            val below = fit(width, height - toolbar, ratio, top = toolbar, areaWidth = width)
            val full = fit(width, height, ratio, top = 0, areaWidth = width)
            if (below.width == full.width) below else full
        }
        fun clear(bottom: Int) = frame.bottom <= bottom - strip || frame.bottom >= bottom
        val room = height - box.bottom
        val under = box.bottom + min(gap, room - strip) + strip
        val inBlack = box.bottom - gap
        val (stripBottom, zoomGap) = when {
            room >= strip && clear(under) -> under to under - strip - box.bottom
            inBlack - strip >= frame.bottom -> inBlack to 0
            else -> frame.bottom - gap to 0
        }
        return ViewfinderLayout(frame, height - stripBottom, zoomGap)
    }

    /** The largest [ratio] (width / height) rect in [width] × [height], centered across [areaWidth] from [top]. */
    private fun fit(width: Int, height: Int, ratio: Float, top: Int, areaWidth: Int): IntRect {
        val (w, h) = if (width.toFloat() / height > ratio) (height * ratio).roundToInt() to height
            else width to (width / ratio).roundToInt()
        val left = (areaWidth - w) / 2
        return IntRect(left, top, left + w, top + h)
    }
}
