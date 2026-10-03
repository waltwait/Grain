package tw.luma.camera.editor

import tw.luma.camera.gl.FilterSettings

/** Editor selections never write camera preferences. Only the currently rendered revision can be saved. */
data class PhotoEditSelection(
    val selectedId: String? = null,
    val filter: FilterSettings = FilterSettings(),
    val comparing: Boolean = false,
    val revision: Long = 0,
    val renderedRevision: Long = -1,
    val savedFilter: FilterSettings? = null,
) {
    val previewSettings get() = if (comparing) FilterSettings() else filter
    val canSave get() = !comparing && renderedRevision == revision && filter != savedFilter

    fun adjusted(next: FilterSettings, id: String? = selectedId): PhotoEditSelection =
        if (next == filter && id == selectedId && !comparing) this
        else copy(selectedId = id, filter = next, comparing = false, revision = revision + 1)

    fun compare(original: Boolean): PhotoEditSelection =
        if (original == comparing) this else copy(comparing = original, revision = revision + 1)

    fun presented(frameRevision: Long): PhotoEditSelection =
        if (frameRevision != revision || renderedRevision == revision) this else copy(renderedRevision = revision)
}
