package tw.luma.camera.editor

import org.junit.Assert.*
import org.junit.Test
import tw.luma.camera.gl.FilterSettings

class PhotoEditorUiStateTest {
    private fun batch(vararg status: PhotoBatchStatus) = PhotoBatchProgress(
        status.mapIndexed { index, value -> PhotoBatchItem("photo-$index", value) })

    @Test fun emptyOrUntouchedImportIsNotFinished() {
        assertFalse(PhotoEditorUiState().finished)
        assertFalse(PhotoEditorUiState(sources = listOf("a")).finished)
        assertFalse(PhotoEditorUiState(sources = listOf("a", "b")).finished)
    }

    @Test fun singlePhotoIsFinishedOnlyWhileTheSavedSettingsAreCurrent() {
        val filter = FilterSettings(strength = .6f)
        val saved = PhotoEditorUiState(sources = listOf("a"),
            selection = PhotoEditSelection(filter = filter, savedFilter = filter))
        assertTrue(saved.finished)
        val changed = saved.copy(selection = saved.selection.adjusted(FilterSettings(strength = .3f)))
        assertFalse("A new filter is an unsaved draft", changed.finished)
        assertFalse("Still exporting", saved.copy(saving = true).finished)
    }

    @Test fun batchIsFinishedOnlyWhenEveryPhotoIsSaved() {
        val sources = listOf("photo-0", "photo-1", "photo-2")
        fun state(progress: PhotoBatchProgress?) = PhotoEditorUiState(sources = sources, batch = progress)
        assertTrue(state(batch(PhotoBatchStatus.SAVED, PhotoBatchStatus.SAVED, PhotoBatchStatus.SAVED)).finished)
        assertFalse("Stopped early", state(batch(PhotoBatchStatus.SAVED, PhotoBatchStatus.SAVED, PhotoBatchStatus.PENDING)).finished)
        assertFalse("A failed photo can still be retried", state(batch(PhotoBatchStatus.SAVED, PhotoBatchStatus.FAILED, PhotoBatchStatus.SAVED)).finished)
        assertFalse("Not started", state(null).finished)
        assertFalse(state(batch(PhotoBatchStatus.SAVED, PhotoBatchStatus.SAVED, PhotoBatchStatus.SAVED)).copy(saving = true).finished)
    }
}
