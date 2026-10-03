package tw.luma.camera.editor

import kotlin.math.min
import kotlin.math.roundToInt

internal data class PhotoPreviewRect(val x: Int, val y: Int, val width: Int, val height: Int)

internal object PhotoPreviewGeometry {
    fun fit(width: Int, height: Int, imageWidth: Int, imageHeight: Int): PhotoPreviewRect {
        require(width > 0 && height > 0 && imageWidth > 0 && imageHeight > 0)
        val scale = min(width.toDouble() / imageWidth, height.toDouble() / imageHeight)
        val w = (imageWidth * scale).roundToInt().coerceIn(1, width)
        val h = (imageHeight * scale).roundToInt().coerceIn(1, height)
        return PhotoPreviewRect((width - w) / 2, (height - h) / 2, w, h)
    }
}
