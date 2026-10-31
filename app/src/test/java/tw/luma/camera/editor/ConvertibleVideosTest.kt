package tw.luma.camera.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConvertibleVideosTest {
    private val types = ConvertibleVideos.MIME_TYPES.toList()

    @Test fun listsTheContainersThatNeedConverting() {
        listOf("video/quicktime", "video/x-matroska", "video/webm", "video/avi", "video/x-msvideo").forEach {
            assertTrue("$it should be listed", it in types)
        }
    }

    @Test fun leavesMp4Out() {
        assertFalse("video/mp4" in types)
        assertFalse("video/x-m4v" in types)
    }

    @Test fun everyEntryIsADistinctVideoType() {
        assertTrue(types.all { it.startsWith("video/") })
        assertEquals(types.distinct(), types)
    }
}
