package tw.luma.camera.ui

import androidx.compose.ui.unit.IntRect
import org.junit.Assert.assertEquals
import org.junit.Test

// A portrait viewfinder 1080 px wide and 1750 px tall: the toolbar takes 150 px at the top, the zoom badge and
// ISO row take 280 px at the bottom, and the frame keeps 30 px clear of the toolbar when it sits below it.
class ViewfinderFrameTest {
    private fun place(ratio: Float, width: Int = 1080, height: Int = 1750) =
        ViewfinderFrame.place(width, height, ratio, toolbar = 150, controls = 280, gap = 30)

    @Test fun frameWithRoomForBothRowsStaysCenteredBetweenThem() {
        assertEquals(ViewfinderLayout(IntRect(0, 335, 1080, 1415), controlsLift = 0), place(1f))
    }

    @Test fun centeringGivesWayToTheControlsBelow() {
        // Centered would leave 260 px under the square, short of the 280 px the controls need.
        assertEquals(ViewfinderLayout(IntRect(0, 240, 1080, 1320), controlsLift = 0), place(1f, height = 1600))
    }

    @Test fun frameWithoutRoomForBothRowsMovesUnderTheToolbarSoTheControlsStayOnBlack() {
        // 3:4 is 1440 tall, leaving 310 px: enough for the controls but not for both rows.
        assertEquals(ViewfinderLayout(IntRect(0, 0, 1080, 1440), controlsLift = 0), place(3f / 4))
    }

    @Test fun controlsThatCannotFitBelowRideOnThePictureInsteadOfStraddlingItsEdge() {
        // 2:3 is 1620 tall, leaving 130 px: the controls stand on the frame's bottom edge, over the picture.
        assertEquals(ViewfinderLayout(IntRect(0, 0, 1080, 1620), controlsLift = 130), place(2f / 3))
    }

    @Test fun frameTallerThanTheViewfinderFillsItsHeightAndCentersAcross() {
        // 9:16 would be 1920 tall, so it shrinks to 1750 × 984.
        assertEquals(ViewfinderLayout(IntRect(48, 0, 1032, 1750), controlsLift = 0), place(9f / 16))
    }

    @Test fun wideViewfinderHoldsAWideFrameAcrossItsHeight() {
        assertEquals(ViewfinderLayout(IntRect(200, 0, 1800, 1200), controlsLift = 0), place(4f / 3, width = 2000, height = 1200))
    }
}
