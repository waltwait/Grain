package tw.luma.camera.ui

import androidx.compose.ui.unit.IntRect
import org.junit.Assert.assertEquals
import org.junit.Test

// Sizes in dp at density 1. On an S24 the viewfinder is 411 × 620 once the ISO row sits in the black above the
// shutter, and the toolbar takes 63 at the top. Frames are width / height on screen; the zoom badge stands on the
// bottom edge of the 4:3 frame, controlsLift above the viewfinder's bottom.
class ViewfinderFrameTest {
    private fun place(ratio: Float, width: Int = 411, height: Int = 620, toolbar: Int = 63) =
        ViewfinderFrame.place(width, height, ratio, toolbar)

    @Test fun fourThreeStartsUnderTheToolbarWithItsWholePictureClear() {
        assertEquals(ViewfinderLayout(IntRect(0, 63, 411, 611), controlsLift = 9), place(3f / 4))
    }

    @Test fun squareIsCenteredInTheFourThreeFrameAndTheZoomBadgeDoesNotMove() {
        assertEquals(ViewfinderLayout(IntRect(0, 131, 411, 542), controlsLift = 9), place(1f))
    }

    @Test fun tallerFrameThatFitsUnderTheToolbarStartsThere() {
        assertEquals(ViewfinderLayout(IntRect(0, 63, 411, 680), controlsLift = 189), place(2f / 3, height = 800))
    }

    @Test fun tallerFrameThatCannotClearTheToolbarRunsUnderItAtFullWidth() {
        // 2:3 needs 617 of the 557 under the toolbar but fits the full 620, so the toolbar lies over the picture.
        assertEquals(ViewfinderLayout(IntRect(0, 0, 411, 617), controlsLift = 9), place(2f / 3))
    }

    @Test fun nineSixteenFillsTheViewfinderHeight() {
        assertEquals(ViewfinderLayout(IntRect(31, 0, 380, 620), controlsLift = 9), place(9f / 16))
    }

    @Test fun shortViewfinderKeepsTheFourThreeFrameUnderTheToolbar() {
        assertEquals(ViewfinderLayout(IntRect(19, 63, 392, 560), controlsLift = 0), place(3f / 4, height = 560))
    }

    @Test fun wideViewfinderHoldsAWideFourThreeFrameUnderTheToolbar() {
        assertEquals(ViewfinderLayout(IntRect(70, 56, 929, 700), controlsLift = 0), place(4f / 3, width = 1000, height = 700, toolbar = 56))
    }
}
