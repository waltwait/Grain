package tw.luma.camera.editor

import org.junit.Assert.*
import org.junit.Test

class PhotoPreviewGeometryTest {
    @Test fun landscapePhotoFitsWithoutCroppingInPortrait() {
        assertEquals(PhotoPreviewRect(0, 200, 400, 300), PhotoPreviewGeometry.fit(400, 700, 4000, 3000))
    }
    @Test fun portraitPhotoFitsWithoutCroppingInLandscape() {
        assertEquals(PhotoPreviewRect(250, 0, 300, 400), PhotoPreviewGeometry.fit(800, 400, 3000, 4000))
    }
    @Test fun veryThinPhotosStillHaveAVisiblePixel() {
        assertEquals(PhotoPreviewRect(0, 49, 100, 1), PhotoPreviewGeometry.fit(100, 100, 20000, 1))
    }
    @Test fun invalidDimensionsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { PhotoPreviewGeometry.fit(0, 100, 100, 100) }
        assertThrows(IllegalArgumentException::class.java) { PhotoPreviewGeometry.fit(100, 100, 100, 0) }
    }
}
