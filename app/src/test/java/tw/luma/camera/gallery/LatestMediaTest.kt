package tw.luma.camera.gallery

import org.junit.Assert.*
import org.junit.Test

class LatestMediaTest {
    private val photo = MediaRow(id = 10, addedSeconds = 100, video = false)
    private val video = MediaRow(id = 11, addedSeconds = 200, video = true)

    @Test fun theNewestOfPhotosAndVideosWins() {
        assertEquals(video, LatestMedia.newest(listOf(photo, video)))
        assertEquals(photo, LatestMedia.newest(listOf(photo, video.copy(addedSeconds = 50))))
    }

    @Test fun aTieIsBrokenByTheHigherId() {
        val sameSecond = photo.copy(id = 12, addedSeconds = 200)
        assertEquals(sameSecond, LatestMedia.newest(listOf(video, sameSecond)))
    }

    @Test fun nothingSavedYetGivesNoThumbnail() {
        assertNull(LatestMedia.newest(emptyList()))
    }

    @Test fun aSingleKindStillWorks() {
        assertEquals(photo, LatestMedia.newest(listOf(photo)))
        assertEquals(video, LatestMedia.newest(listOf(video)))
    }
}
