package tw.luma.camera.editor

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tw.luma.camera.LutEntry
import tw.luma.camera.gl.FilterSettings
import tw.luma.camera.lut.FilterSwitching
import tw.luma.camera.storage.PhotoImportIo
import tw.luma.camera.storage.PhotoStorage

data class PhotoEditorUiState(
    val bitmap: Bitmap? = null,
    val selection: PhotoEditSelection = PhotoEditSelection(),
    val loading: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val savedUri: Uri? = null,
) {
    val canEdit get() = bitmap != null && !loading && !saving
    val canSave get() = canEdit && selection.canSave &&
        (selection.selectedId == null || selection.filter.lut != null)
}

class PhotoEditorViewModel(application: Application, private val savedState: SavedStateHandle) : AndroidViewModel(application) {
    private val cache = File(application.cacheDir, "photo-editor").apply { mkdirs() }
    private var source: File? = null
    private var loadingJob: Job? = null
    private var loadRevision = 0L
    private var entries: List<LutEntry> = emptyList()
    private var switching = FilterSwitching()
    private val restoredStrength = savedState.get<Float>("strength")?.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 1f
    private val _state = MutableStateFlow(PhotoEditorUiState(selection = PhotoEditSelection(
        selectedId = savedState["selectedId"], filter = FilterSettings(strength = restoredStrength))))
    val state = _state.asStateFlow()

    init {
        savedState.get<String>("source")?.let { path ->
            val file = File(path)
            if (file.parentFile == cache && file.name.startsWith("edit-") && file.isFile) load(null, file)
            else _state.update { it.copy(error = "請重新選擇照片") }
        }
    }

    fun setLuts(next: List<LutEntry>) {
        entries = next
        if (next.isEmpty()) return
        val selected = _state.value.selection
        selected.selectedId?.let { id ->
            val entry = next.find { it.id == id }
            adjust(selected.filter.copy(lut = entry?.lut, encoding = entry?.lut?.suggestedEncoding ?: selected.filter.encoding), entry?.id)
        }
    }

    fun open(uri: Uri) {
        if (_state.value.saving) return
        switching = FilterSwitching()
        _state.value = PhotoEditorUiState()
        savedState["selectedId"] = null
        savedState["strength"] = 1f
        savedState.remove<String>("source")
        load(uri, null)
    }

    private fun load(uri: Uri?, cached: File?) {
        loadingJob?.cancel()
        source?.takeUnless { it == cached }?.delete()
        source = null
        val revision = ++loadRevision
        _state.update { it.copy(bitmap = null, loading = true, error = null) }
        loadingJob = viewModelScope.launch {
            var candidate = cached
            var accepted = false
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    val file = candidate ?: File.createTempFile("edit-", ".image", cache).also { candidate = it }
                    if (uri != null) {
                        val activeContext = coroutineContext
                        requireNotNull(getApplication<Application>().contentResolver.openInputStream(uri)) { "照片無法讀取" }.use { input ->
                            file.outputStream().use { output -> PhotoImportIo.copy(input, output) { activeContext.ensureActive() } }
                        }
                    }
                    ensureActive()
                    PhotoEditorSource.preview(file)
                }
                if (revision != loadRevision) return@launch
                source = requireNotNull(candidate)
                savedState["source"] = source!!.absolutePath
                _state.update { it.copy(bitmap = bitmap, loading = false,
                    selection = it.selection.copy(revision = it.selection.revision + 1, renderedRevision = -1)) }
                accepted = true
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (revision == loadRevision) {
                    savedState.remove<String>("source")
                    _state.update { it.copy(loading = false, error = error.message ?: "照片無法讀取") }
                }
            } finally { if (!accepted) candidate?.delete() }
        }
    }

    fun selectLut(id: String?) {
        if (!_state.value.canEdit) return
        val current = _state.value.selection
        val entry = entries.find { it.id == id }
        adjust(switching.select(current.filter, current.selectedId, entry), entry?.id)
    }

    fun strength(value: Float) {
        if (!_state.value.canEdit || !value.isFinite()) return
        adjust(_state.value.selection.filter.copy(strength = value.coerceIn(0f, 1f)))
    }

    private fun adjust(filter: FilterSettings, id: String? = _state.value.selection.selectedId) {
        _state.update {
            val next = it.selection.adjusted(filter, id)
            it.copy(selection = next, error = if (next === it.selection) it.error else null)
        }
        savedState["selectedId"] = id
        savedState["strength"] = filter.strength
    }

    fun compare() {
        if (_state.value.canEdit) _state.update { it.copy(selection = it.selection.compare(!it.selection.comparing)) }
    }

    fun presented(bitmap: Bitmap, revision: Long) {
        _state.update {
            if (it.bitmap !== bitmap || it.selection.revision != revision) it
            else it.copy(selection = it.selection.presented(revision), error = null)
        }
    }

    fun previewError(revision: Long, message: String) {
        _state.update { if (revision < 0 || it.selection.revision == revision)
            it.copy(error = message, selection = it.selection.copy(renderedRevision = -1)) else it }
    }

    fun save(onSaved: (Uri) -> Unit) {
        val current = _state.value
        val file = source ?: return
        if (!current.canSave) return
        val filter = current.selection.filter
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                val uri = withContext(Dispatchers.IO) { PhotoStorage.processAndSave(getApplication(), file, filter, false) }
                _state.update { it.copy(savedUri = uri, selection = it.selection.copy(savedFilter = filter)) }
                onSaved(uri)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { _state.update { it.copy(error = error.message ?: "照片未儲存") } }
            finally { _state.update { it.copy(saving = false) } }
        }
    }

    fun discard() {
        if (_state.value.saving) return
        ++loadRevision
        loadingJob?.cancel()
        source?.delete()
        source = null
        savedState.remove<String>("source")
        savedState.remove<String>("selectedId")
        savedState.remove<Float>("strength")
        _state.value = PhotoEditorUiState()
    }

    override fun onCleared() { loadingJob?.cancel(); source?.delete(); super.onCleared() }
}
