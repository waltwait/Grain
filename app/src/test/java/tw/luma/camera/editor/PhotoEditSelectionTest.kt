package tw.luma.camera.editor

import org.junit.Assert.*
import org.junit.Test
import tw.luma.camera.gl.FilterSettings

class PhotoEditSelectionTest {
    @Test fun saveWaitsForTheCurrentPreview() {
        val original = PhotoEditSelection()
        assertFalse(original.canSave)
        val shown = original.presented(original.revision)
        assertTrue(shown.canSave)
        val changed = shown.adjusted(FilterSettings(strength = .5f))
        assertFalse(changed.canSave)
        assertSame(changed, changed.presented(original.revision))
        assertTrue(changed.presented(changed.revision).canSave)
    }
    @Test fun comparingOriginalKeepsTheExportSettings() {
        val filter = FilterSettings(brightnessEv = 1f)
        val edited = PhotoEditSelection().adjusted(filter, "test-film")
        val original = edited.compare(true)
        assertEquals(FilterSettings(), original.previewSettings)
        assertEquals(filter, original.filter)
        assertEquals("test-film", original.selectedId)
        assertFalse(original.presented(original.revision).canSave)
        assertEquals(filter, original.compare(false).previewSettings)
    }
    @Test fun savedSettingsCannotBeExportedAgainUntilChanged() {
        val edited = PhotoEditSelection().presented(0)
        val saved = edited.copy(savedFilter = edited.filter)
        assertFalse(saved.canSave)
        val changed = saved.adjusted(FilterSettings(brightnessEv = .5f))
        assertTrue(changed.presented(changed.revision).canSave)
    }
    @Test fun repeatedValuesDoNotRequestAnotherGpuFrame() {
        val selection = PhotoEditSelection().presented(0)
        assertSame(selection, selection.adjusted(selection.filter, selection.selectedId))
        assertSame(selection, selection.compare(false))
    }
}
