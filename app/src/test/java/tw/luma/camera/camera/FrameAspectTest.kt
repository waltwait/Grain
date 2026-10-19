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

    @Test fun pickerListsTheIosRatiosThenTheFilmRatioUpright() {
        assertEquals(listOf("3:4", "9:16", "1:1", "2:3"), FrameAspect.entries.map { it.label })
    }

    @Test fun photoIsCutFromTheMiddleOfTheFullFrameKeepingItsOrientation() {
        assertEquals(PhotoCrop(0, 375, 4000, 2250), FrameAspect.RATIO_16_9.centerCrop(4000, 3000))
        assertEquals(PhotoCrop(375, 0, 2250, 4000), FrameAspect.RATIO_16_9.centerCrop(3000, 4000))
        assertEquals(PhotoCrop(500, 0, 3000, 3000), FrameAspect.RATIO_1_1.centerCrop(4000, 3000))
        assertEquals(PhotoCrop(166, 0, 2667, 4000), FrameAspect.RATIO_3_2.centerCrop(3000, 4000))
        assertEquals(PhotoCrop(0, 166, 4000, 2667), FrameAspect.RATIO_3_2.centerCrop(4000, 3000))
    }

    @Test fun fourThreeKeepsTheWholeFourThreePhoto() {
        assertEquals(PhotoCrop(0, 0, 4000, 3000), FrameAspect.RATIO_4_3.centerCrop(4000, 3000))
        assertEquals(PhotoCrop(0, 0, 3000, 4000), FrameAspect.RATIO_4_3.centerCrop(3000, 4000))
    }

    @Test fun savedChoiceIsRestoredAndAMissingOrUnknownValueFallsBack() {
        assertEquals(FrameAspect.RATIO_1_1, FrameAspect.parse("RATIO_1_1", FrameAspect.RATIO_4_3))
        assertEquals(FrameAspect.RATIO_16_9, FrameAspect.parse("RATIO_16_9", FrameAspect.RATIO_4_3))
        assertEquals(FrameAspect.RATIO_4_3, FrameAspect.parse(null, FrameAspect.RATIO_4_3))
        assertEquals(FrameAspect.RATIO_16_9, FrameAspect.parse("FULL", FrameAspect.RATIO_16_9))
    }
}
