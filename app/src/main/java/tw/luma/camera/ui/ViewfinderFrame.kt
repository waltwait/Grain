package tw.luma.camera.ui

import androidx.compose.ui.unit.IntRect
import kotlin.math.roundToInt

/** The frame inside the viewfinder, and how far above the viewfinder's bottom the zoom badge and ISO row stand. */
data class ViewfinderLayout(val frame: IntRect, val controlsLift: Int)

/**
 * Places the frame so the toolbar and the controls each sit wholly on black or wholly on the picture, never across
 * its edge. With room for both the frame stays centered between them; otherwise it moves under the toolbar so the
 * controls keep the black below, and controls that still do not fit there stand on the picture's bottom edge.
 */
object ViewfinderFrame {
    fun place(width: Int, height: Int, ratio: Float, toolbar: Int, controls: Int, gap: Int): ViewfinderLayout {
        val frameWidth: Int
        val frameHeight: Int
        if (width.toFloat() / height > ratio) { frameHeight = height; frameWidth = (height * ratio).roundToInt() }
        else { frameWidth = width; frameHeight = (width / ratio).roundToInt() }
        val spare = height - frameHeight
        val top = if (spare >= toolbar + gap + controls) (spare / 2).coerceIn(toolbar + gap, spare - controls) else 0
        val below = spare - top
        val left = (width - frameWidth) / 2
        return ViewfinderLayout(IntRect(left, top, left + frameWidth, top + frameHeight), if (below >= controls) 0 else below)
    }
}
