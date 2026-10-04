package tw.luma.camera.ui

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.pow

class GrainStyleTest {
    private val gold = 0xFFD45B
    private val white = 0xFFFFFF

    private fun channel(value: Int): Double {
        val c = value / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(rgb: Int) = 0.2126 * channel(rgb shr 16 and 0xFF) + 0.7152 * channel(rgb shr 8 and 0xFF) + 0.0722 * channel(rgb and 0xFF)

    private fun contrast(foreground: Int, background: Int): Double {
        val a = luminance(foreground); val b = luminance(background)
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }

    /** A black scrim of [alpha] over a backdrop colour. */
    private fun darkened(backdrop: Int, alpha: Float): Int {
        fun mix(shift: Int) = ((backdrop shr shift and 0xFF) * (1 - alpha)).toInt()
        return (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    @Test fun goldButtonsOnAPhotoKeepReadableContrastEvenOnWhite() {
        val ratio = contrast(gold, darkened(white, PHOTO_OVERLAY_SCRIM_ALPHA))
        assertTrue("gold over the scrim on a white photo is only $ratio:1", ratio >= 4.5)
    }

    @Test fun theOldFortyPercentScrimWasNotEnough() {
        assertTrue(contrast(gold, darkened(white, 0.4f)) < 3.0)
    }

    @Test fun theNoFilterLabelIsNotConfusedWithTheOriginalPhotoLabels() {
        val others = listOf("原圖", "看原圖", "看改完", "濾鏡")
        assertFalse(NO_FILTER_LABEL in others)
        assertFalse("It must not start with 原 like 原圖 and 原色", NO_FILTER_LABEL.startsWith("原"))
    }
}
