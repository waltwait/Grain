package tw.luma.camera.camera

import android.view.Surface
import androidx.camera.core.CameraSelector.LENS_FACING_BACK
import androidx.exifinterface.media.ExifInterface
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
    @Test fun photosTurnUprightForHowThePhoneWasHeld() {
        // The fixed upright camera screen composes an upright frame; held sideways, the photo turns with the phone.
        assertEquals(0, DualCamera.photoRotation(Surface.ROTATION_0, Surface.ROTATION_0))
        assertEquals(270, DualCamera.photoRotation(Surface.ROTATION_0, Surface.ROTATION_90))
        assertEquals(180, DualCamera.photoRotation(Surface.ROTATION_0, Surface.ROTATION_180))
        assertEquals(90, DualCamera.photoRotation(Surface.ROTATION_0, Surface.ROTATION_270))
        // A screen already turned with the phone composes the frame that way.
        assertEquals(0, DualCamera.photoRotation(Surface.ROTATION_90, Surface.ROTATION_90))
        assertEquals(ExifInterface.ORIENTATION_ROTATE_270, DualCamera.exifOrientation(270))
        assertEquals(ExifInterface.ORIENTATION_ROTATE_90, DualCamera.exifOrientation(90))
        assertEquals(ExifInterface.ORIENTATION_NORMAL, DualCamera.exifOrientation(0))
    }
    @Test fun devicesShowTheTwoIds() {
        assertEquals("0＋1", DualCamera.label(back to front))
    }
    @Test fun skinIsSoftenedWhereTheFrontCameraIs() {
        val inset = DualCamera.inset(9, 16)
        val small = DualCamera.skinArea(9, 16, frontSmall = true)
        // Texture coordinates run 0–1 with y up, the inset's normalized coordinates −1–1.
        assertEquals((inset.offsetX - inset.scale + 1) / 2, small.left, 1e-6f)
        assertEquals((inset.offsetY + inset.scale + 1) / 2, small.top, 1e-6f)
        assertTrue(small.inside && small.right < 1f && small.top < 1f && small.left > .5f && small.bottom > .5f)
        val big = DualCamera.skinArea(9, 16, frontSmall = false)
        assertFalse("A big front picture is softened around the inset", big.inside)
        assertEquals(small.copy(inside = false, radiusX = big.radiusX, radiusY = big.radiusY), big)
        assertEquals("The blur reaches the same share of either picture", big.radiusX * DualCamera.SCALE, small.radiusX, 1e-6f)
        assertEquals("Round in pixels", small.radiusX * 9 / 16, small.radiusY, 1e-6f)
    }
    @Test fun smallPictureSitsInTheTopRightWithEqualGapsInsideTheFrame() {
        for ((width, height) in listOf(9 to 16, 3 to 4, 1 to 1, 16 to 9)) {
            val inset = DualCamera.inset(width, height)
            // Normalized coordinates span 2 across, and +y points up.
            val left = (inset.offsetX - inset.scale + 1) / 2 * width
            val top = (1 - (inset.offsetY + inset.scale)) / 2 * height
            val right = (inset.offsetX + inset.scale + 1) / 2 * width
            val bottom = (1 - (inset.offsetY - inset.scale)) / 2 * height
            assertTrue(left > 0 && top > 0 && right < width && bottom < height)
            assertTrue("Right half of the frame", left > width / 2f)
            assertEquals("Same gap above and beside in a $width:$height frame", width - right, top, 1e-4f * width)
            assertEquals(DualCamera.SCALE * width, right - left, 1e-4f * width)
            assertTrue("A third of the frame or less", abs((right - left) / width - (bottom - top) / height) < 1e-4f)
        }
    }
}
