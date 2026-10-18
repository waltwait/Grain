package tw.luma.camera.camera

import androidx.camera.core.AspectRatio
import org.junit.Assert.*
import org.junit.Test

class FrameAspectTest {
    @Test fun portraitViewfinderHoldsAnUprightFrameAndLandscapeAWideOne() {
        assertEquals(3 to 4, FrameAspect.RATIO_4_3.screenShape(landscape = false))
        assertEquals(2 to 3, FrameAspect.RATIO_3_2.screenShape(landscape = false))
        assertEquals(9 to 16, FrameAspect.RATIO_16_9.screenShape(landscape = false))
        assertEquals(1 to 1, FrameAspect.RATIO_1_1.screenShape(landscape = false))
        assertEquals(4 to 3, FrameAspect.RATIO_4_3.screenShape(landscape = true))
        assertEquals(16 to 9, FrameAspect.RATIO_16_9.screenShape(landscape = true))
    }

    @Test fun onlySixteenNineRecordsFromAWideStreamSoOtherRatiosKeepTheTallerView() {
        assertEquals(AspectRatio.RATIO_16_9, FrameAspect.RATIO_16_9.videoStream)
        assertEquals(AspectRatio.RATIO_4_3, FrameAspect.RATIO_4_3.videoStream)
        assertEquals(AspectRatio.RATIO_4_3, FrameAspect.RATIO_3_2.videoStream)
        assertEquals(AspectRatio.RATIO_4_3, FrameAspect.RATIO_1_1.videoStream)
    }

    @Test fun savedChoiceIsRestoredAndAMissingOrUnknownValueFallsBack() {
        assertEquals(FrameAspect.RATIO_1_1, FrameAspect.parse("RATIO_1_1", FrameAspect.RATIO_4_3))
        assertEquals(FrameAspect.RATIO_16_9, FrameAspect.parse("RATIO_16_9", FrameAspect.RATIO_4_3))
        assertEquals(FrameAspect.RATIO_4_3, FrameAspect.parse(null, FrameAspect.RATIO_4_3))
        assertEquals(FrameAspect.RATIO_16_9, FrameAspect.parse("FULL", FrameAspect.RATIO_16_9))
    }
}
