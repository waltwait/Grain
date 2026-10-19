package tw.luma.camera.ui

import androidx.compose.ui.unit.IntRect
import kotlin.math.roundToInt

/** The frame inside the viewfinder, and how far above the viewfinder's bottom the zoom badge stands. */
data class ViewfinderLayout(val frame: IntRect, val controlsLift: Int)

/**
 * Lays the viewfinder out like iOS: a 4:3 frame sits right under the toolbar and every ratio is placed against it.
 * Shorter frames are centered inside it; taller ones start under the toolbar, or run under it when they would
 * otherwise need side bars. The zoom badge stands on the 4:3 frame's bottom edge whatever the ratio.
 */
object ViewfinderFrame {
    fun place(width: Int, height: Int, ratio: Float, toolbar: Int): ViewfinderLayout {
        val boxRatio = if (width > height) 4f / 3 else 3f / 4
        val box = fit(width, height - toolbar, boxRatio, top = toolbar, areaWidth = width)
        val frame = if (ratio >= boxRatio) {
            val inside = fit(box.width, box.height, ratio, top = 0, areaWidth = box.width)
            val top = box.top + (box.height - inside.height) / 2
            IntRect(box.left + inside.left, top, box.left + inside.right, top + inside.height)
        } else {
            val below = fit(width, height - toolbar, ratio, top = toolbar, areaWidth = width)
            val full = fit(width, height, ratio, top = 0, areaWidth = width)
            if (below.width == full.width) below else full
        }
        return ViewfinderLayout(frame, height - box.bottom)
    }

    /** The largest [ratio] (width / height) rect in [width] × [height], centered across [areaWidth] from [top]. */
    private fun fit(width: Int, height: Int, ratio: Float, top: Int, areaWidth: Int): IntRect {
        val (w, h) = if (width.toFloat() / height > ratio) (height * ratio).roundToInt() to height
            else width to (width / ratio).roundToInt()
        val left = (areaWidth - w) / 2
        return IntRect(left, top, left + w, top + h)
    }
}
