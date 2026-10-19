package tw.luma.camera.ui

import androidx.compose.ui.unit.IntRect
import org.junit.Assert.assertEquals
import org.junit.Test

// Sizes in dp at density 1. An S24-like viewfinder is 411 × 666 under the status bar; the toolbar takes 56 at the
// top, the ISO pill is 48 tall, and the pill keeps 12 from the 4:3 frame. Frames are width / height on screen.
class ViewfinderFrameTest {
    private fun place(ratio: Float, width: Int = 411, height: Int = 666) =
        ViewfinderFrame.place(width, height, ratio, toolbar = 56, strip = 48, gap = 12)

    @Test fun fourThreeStartsUnderTheToolbarAndThePillSitsBelowIt() {
        assertEquals(ViewfinderLayout(IntRect(0, 56, 411, 604), controlsLift = 2, zoomGap = 12), place(3f / 4))
    }

    @Test fun squareIsCenteredInTheFourThreeFrameAndThePillDoesNotMove() {
        assertEquals(ViewfinderLayout(IntRect(0, 124, 411, 535), controlsLift = 2, zoomGap = 12), place(1f))
    }

    @Test fun pillStaysUnderTheFourThreeFrameEvenWithBlackToSpare() {
        assertEquals(ViewfinderLayout(IntRect(0, 56, 411, 604), controlsLift = 136, zoomGap = 12), place(3f / 4, height = 800))
    }

    @Test fun tallerFrameThatFitsUnderTheToolbarStartsThereAndRunsUnderThePill() {
        assertEquals(ViewfinderLayout(IntRect(0, 56, 411, 673), controlsLift = 136, zoomGap = 12), place(2f / 3, height = 800))
    }

    @Test fun tallerFrameThatCannotClearTheToolbarRunsUnderItAndCarriesThePillAboveItsEdge() {
        // 2:3 needs 617 of the 610 under the toolbar, so it fills from the top; its edge at 617 would cut the pill at 616.
        assertEquals(ViewfinderLayout(IntRect(0, 0, 411, 617), controlsLift = 61, zoomGap = 0), place(2f / 3))
    }

    @Test fun nineSixteenFillsTheViewfinderHeightWithThePillOnThePicture() {
        assertEquals(ViewfinderLayout(IntRect(18, 0, 393, 666), controlsLift = 2, zoomGap = 12), place(9f / 16))
    }

    @Test fun shortViewfinderKeepsTheFourThreeFrameUnderTheToolbarAndThePillOnThePicture() {
        assertEquals(ViewfinderLayout(IntRect(16, 56, 394, 560), controlsLift = 12, zoomGap = 0), place(3f / 4, height = 560))
    }

    @Test fun shortViewfinderPutsThePillInTheBlackUnderASquare() {
        assertEquals(ViewfinderLayout(IntRect(16, 119, 394, 497), controlsLift = 12, zoomGap = 0), place(1f, height = 560))
    }

    @Test fun wideViewfinderHoldsAWideFourThreeFrameUnderTheToolbar() {
        assertEquals(ViewfinderLayout(IntRect(70, 56, 929, 700), controlsLift = 12, zoomGap = 0), place(4f / 3, width = 1000, height = 700))
    }
}
