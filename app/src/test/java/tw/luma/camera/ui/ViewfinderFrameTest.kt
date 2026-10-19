package tw.luma.camera.ui

import androidx.compose.ui.unit.IntRect
import org.junit.Assert.assertEquals
import org.junit.Test

// Sizes in dp at density 1. On an S24 the camera screen is 411 × 835 between the system bars; the toolbar takes 56
// at the top and the dock (ISO row, 照片／錄影 and shutter) 212 at the bottom, floating over the picture. Frames are
// width / height on screen; the zoom badge stands on the bottom edge of the 4:3 frame, controlsLift above the bottom.
class ViewfinderFrameTest {
    private fun place(ratio: Float, width: Int = 411, height: Int = 835, toolbar: Int = 56, dock: Int = 212) =
        ViewfinderFrame.place(width, height, ratio, toolbar, dock)

    @Test fun fourThreeStartsUnderTheToolbarAndEndsAboveTheDock() {
        assertEquals(ViewfinderLayout(IntRect(0, 56, 411, 604), controlsLift = 231), place(3f / 4))
    }

    @Test fun squareIsCenteredInTheFourThreeFrameAndTheZoomBadgeDoesNotMove() {
        assertEquals(ViewfinderLayout(IntRect(0, 124, 411, 535), controlsLift = 231), place(1f))
    }

    @Test fun nineSixteenStartsUnderTheToolbarAndRunsDownBehindTheDock() {
        assertEquals(ViewfinderLayout(IntRect(0, 56, 411, 787), controlsLift = 231), place(9f / 16))
    }

    @Test fun twoThreeStartsUnderTheToolbarToo() {
        assertEquals(ViewfinderLayout(IntRect(0, 56, 411, 673), controlsLift = 231), place(2f / 3))
    }

    @Test fun tallerFrameThatCannotClearTheToolbarRunsUnderItAtFullWidth() {
        // 9:16 needs 731 of the 704 under the toolbar but fits the full 760, so the toolbar lies over the picture.
        assertEquals(ViewfinderLayout(IntRect(0, 0, 411, 731), controlsLift = 200), place(9f / 16, height = 760, dock = 200))
    }

    @Test fun shortScreenShrinksTheFourThreeFrameBetweenToolbarAndDock() {
        assertEquals(ViewfinderLayout(IntRect(16, 56, 394, 560), controlsLift = 200), place(3f / 4, height = 760, dock = 200))
    }

    @Test fun wideViewfinderWithItsDockBesideItHoldsAWideFourThreeFrameUnderTheToolbar() {
        assertEquals(ViewfinderLayout(IntRect(70, 56, 929, 700), controlsLift = 0),
            place(4f / 3, width = 1000, height = 700, toolbar = 56, dock = 0))
    }
}
