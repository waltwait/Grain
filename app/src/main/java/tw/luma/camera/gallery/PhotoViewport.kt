package tw.luma.camera.gallery

/** Image-transform math kept independent from Compose and Android for boundary tests. */
internal object PhotoViewport {
    fun sampleSize(width: Int, height: Int, maxPixels: Long): Int {
        require(width > 0 && height > 0 && maxPixels > 0)
        var sample = 1
        while (((width.toLong() + sample - 1) / sample) * ((height.toLong() + sample - 1) / sample) > maxPixels) {
            sample = (sample.toLong() * 2).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
        return sample
    }
    fun zoomPan(pan: Float, centroidFromCenter: Float, zoomRatio: Float, drag: Float): Float =
        centroidFromCenter - (centroidFromCenter - pan) * zoomRatio + drag
    fun clampPan(pan: Float, scaledImage: Float, viewport: Float): Float {
        val extent = ((scaledImage - viewport) / 2).coerceAtLeast(0f)
        return pan.coerceIn(-extent, extent)
    }
}
