package tw.luma.camera.gallery

import org.junit.Assert.*
import org.junit.Test

class PhotoViewportTest {
    @Test fun boundsLargePhotoDecodeWithoutChangingSmallPhotos() {
        assertEquals(1, PhotoViewport.sampleSize(1000, 1000, 8_000_000))
        assertEquals(2, PhotoViewport.sampleSize(4000, 3000, 8_000_000))
        assertEquals(4, PhotoViewport.sampleSize(8000, 6000, 8_000_000))
    }
    @Test fun roundsOddDimensionsUpForTheDecodeBudget() {
        assertEquals(4, PhotoViewport.sampleSize(4001, 8001, 8_000_000))
    }
    @Test fun centersImagesSmallerThanTheViewport() {
        assertEquals(0f, PhotoViewport.clampPan(100f, 300f, 500f), 0f)
        assertEquals(0f, PhotoViewport.clampPan(-100f, 300f, 500f), 0f)
    }
    @Test fun zoomKeepsThePinchAnchorInPlace() {
        assertEquals(-50f, PhotoViewport.zoomPan(0f, 50f, 2f, 0f), 0f)
        assertEquals(45f, PhotoViewport.zoomPan(20f, 0f, 2f, 5f), 0f)
        assertEquals(17f, PhotoViewport.zoomPan(10f, 50f, 1f, 7f), 0f)
    }
    @Test fun cannotPanBeyondTheImageEdge() {
        assertEquals(250f, PhotoViewport.clampPan(900f, 1000f, 500f), 0f)
        assertEquals(-250f, PhotoViewport.clampPan(-900f, 1000f, 500f), 0f)
        assertEquals(30f, PhotoViewport.clampPan(30f, 1000f, 500f), 0f)
    }
    @Test fun rejectsInvalidPhotoDimensions() {
        assertThrows(IllegalArgumentException::class.java) { PhotoViewport.sampleSize(0, 100, 8_000_000) }
        assertThrows(IllegalArgumentException::class.java) { PhotoViewport.sampleSize(100, 100, 0) }
    }
}
