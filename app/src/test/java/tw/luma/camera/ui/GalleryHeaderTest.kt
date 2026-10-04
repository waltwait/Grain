package tw.luma.camera.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class GalleryHeaderTest {
    @Test fun photosTabShowsHowManyPhotosAndVideos() {
        assertEquals("12", GalleryHeader.subtitle(editing = false, photoCount = 12, editCount = 3, batchPosition = null))
    }

    @Test fun editTabShowsHowManyEditedPhotos() {
        assertEquals("3", GalleryHeader.subtitle(editing = true, photoCount = 12, editCount = 3, batchPosition = null))
    }

    @Test fun anEmptyEditTabSaysZeroLikeAnEmptyPhotosTab() {
        assertEquals("0", GalleryHeader.subtitle(editing = true, photoCount = 12, editCount = 0, batchPosition = null))
        assertEquals("0", GalleryHeader.subtitle(editing = false, photoCount = 0, editCount = 3, batchPosition = null))
    }

    @Test fun aBatchInProgressShowsItsPositionInsteadOfTheTotal() {
        assertEquals("2 / 5", GalleryHeader.subtitle(editing = true, photoCount = 12, editCount = 3, batchPosition = 2 to 5))
    }

    @Test fun theBatchPositionOnlyAppliesToTheEditTab() {
        assertEquals("12", GalleryHeader.subtitle(editing = false, photoCount = 12, editCount = 3, batchPosition = 2 to 5))
    }
}
