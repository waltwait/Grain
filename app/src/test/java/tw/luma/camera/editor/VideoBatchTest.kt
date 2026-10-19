package tw.luma.camera.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.luma.camera.gl.FilterSettings
import tw.luma.camera.lut.CubeLut

class VideoBatchTest {
    @Test fun statusFollowsTheConversion() {
        assertEquals("正在轉檔 2 / 3 · 45%", VideoBatch(3, saved = setOf("a"), current = 1, progress = .451f).status)
        assertEquals("正在停止 · 已儲存 1 / 3", VideoBatch(3, saved = setOf("a"), current = 1, stopping = true).status)
        assertEquals("已停止 · 已儲存 1 支", VideoBatch(3, saved = setOf("a"), stopped = true).status)
        assertEquals("已儲存 2 支 · 失敗 1 支", VideoBatch(3, saved = setOf("a", "b"), failed = 1).status)
        assertEquals("已儲存 3 支", VideoBatch(3, saved = setOf("a", "b", "c")).status)
    }

    @Test fun aFinishedRunCannotBeSavedAgainUntilTheFilmChanges() {
        val done = VideoEditorUiState(sources = listOf("a", "b"), batch = VideoBatch(2, saved = setOf("a", "b")))
        assertTrue(done.finished)
        assertFalse(done.canSave)
        assertTrue(done.copy(batch = null).canSave)
    }

    @Test fun aStoppedRunCanContinue() {
        val stopped = VideoEditorUiState(sources = listOf("a", "b"), batch = VideoBatch(2, saved = setOf("a"), stopped = true))
        assertFalse(stopped.finished)
        assertTrue(stopped.canSave)
    }

    @Test fun aFilmThatIsNotLoadedYetCannotBeSaved() {
        val waiting = VideoEditorUiState(sources = listOf("a"), selectedId = "kodak-portra")
        assertFalse(waiting.canSave)
        assertTrue(waiting.copy(filter = FilterSettings(lut = CubeLut.builtIns().first())).canSave)
    }

    @Test fun aRefusedPickIsShownWithoutVideos() {
        val refused = VideoEditorUiState(error = "照片和影片請分開選")
        assertTrue(refused.active)
        assertFalse(refused.canSave)
    }

    @Test fun convertOnlyDisablesFilterEditingWhileAllowingSave() {
        val converting = VideoEditorUiState(sources = listOf("a"), convertOnly = true)
        assertFalse(converting.canEdit)
        assertTrue(converting.canSave)
        val stopped = converting.copy(batch = VideoBatch(2, saved = setOf("a"), stopped = true))
        assertFalse(stopped.canEdit)
        assertTrue(stopped.canSave)
        val done = converting.copy(batch = VideoBatch(1, saved = setOf("a")))
        assertTrue(done.finished)
        assertFalse(done.canSave)
    }
}
