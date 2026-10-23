package tw.luma.camera.camera

import androidx.camera.core.CameraSelector.LENS_FACING_BACK
import androidx.camera.core.CameraSelector.LENS_FACING_FRONT
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class DualCameraTest {
    private val back = DualCamera.Lens("0", LENS_FACING_BACK)
    private val front = DualCamera.Lens("1", LENS_FACING_FRONT)
    private val wide = DualCamera.Lens("2", LENS_FACING_BACK)
    private val selfieWide = DualCamera.Lens("3", LENS_FACING_FRONT)

    @Test fun mainBackAndFrontWinOverOtherListedPairs() {
        val sets = listOf(listOf(wide, selfieWide), listOf(wide, front), listOf(back, front))
        assertEquals(back to front, DualCamera.pick(sets, mainBack = "0", mainFront = "1"))
    }
    @Test fun anyBackAndFrontPairServesWhenTheMainOnesAreNotListed() {
        assertEquals(wide to front, DualCamera.pick(listOf(listOf(front, wide)), mainBack = "0", mainFront = "1"))
    }
    @Test fun noPairWithoutBothFacings() {
        assertNull(DualCamera.pick(emptyList(), "0", "1"))
        assertNull(DualCamera.pick(listOf(listOf(back, wide), listOf(front, selfieWide)), "0", "1"))
    }
    @Test fun devicesShowTheTwoIds() {
        assertEquals("0＋1", DualCamera.label(back to front))
    }
    @Test fun smallPictureSitsInTheTopLeftWithEqualGapsInsideTheFrame() {
        for ((width, height) in listOf(9 to 16, 3 to 4, 1 to 1, 16 to 9)) {
            val inset = DualCamera.inset(width, height)
            // Normalized coordinates span 2 across, and +y points up.
            val left = (inset.offsetX - inset.scale + 1) / 2 * width
            val top = (1 - (inset.offsetY + inset.scale)) / 2 * height
            val right = (inset.offsetX + inset.scale + 1) / 2 * width
            val bottom = (1 - (inset.offsetY - inset.scale)) / 2 * height
            assertTrue(left > 0 && top > 0 && right < width && bottom < height)
            assertEquals("Same gap above and beside in a $width:$height frame", left, top, 1e-4f * width)
            assertEquals(DualCamera.SCALE * width, right - left, 1e-4f * width)
            assertTrue("A third of the frame or less", abs((right - left) / width - (bottom - top) / height) < 1e-4f)
        }
    }
}
