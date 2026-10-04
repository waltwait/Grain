package tw.luma.camera.editor

import android.app.Application
import android.graphics.Bitmap
import android.content.Intent
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
import tw.luma.camera.storage.SaveTarget

data class PhotoEditorUiState(
    val bitmap: Bitmap? = null,
    val selection: PhotoEditSelection = PhotoEditSelection(),
    val loading: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val savedUri: Uri? = null,
    val sources: List<String> = emptyList(),
    val previewIndex: Int = 0,
    val batch: PhotoBatchProgress? = null,
) {
    val isBatch get() = sources.size > 1
    val canEdit get() = bitmap != null && !loading && !saving
    val canSave get() = canEdit && !selection.comparing && selection.renderedRevision == selection.revision &&
        (if (isBatch) batch == null || batch.remainingCount > 0 else selection.canSave) &&
        (selection.selectedId == null || selection.filter.lut != null)
    /** Every chosen photo is exported with the current settings, so there is no draft left to keep. */
    val finished get() = !saving &&
        if (isBatch) batch != null && batch.remainingCount == 0
        else selection.savedFilter != null && selection.savedFilter == selection.filter
}

class PhotoEditorViewModel(application: Application, private val savedState: SavedStateHandle) : AndroidViewModel(application) {
    private val cache = File(application.cacheDir, "photo-editor").apply { mkdirs() }
    private var source: File? = null
    private var loadingJob: Job? = null
    private var savingJob: Job? = null
    @Volatile private var stopRequested = false
    private var loadRevision = 0L
    private var entries: List<LutEntry> = emptyList()
    private var switching = FilterSwitching()
    private val ownedGrants = savedState.get<ArrayList<String>>("owned-grants")?.toMutableSet() ?: mutableSetOf()
    private val restoredSources = savedState.get<ArrayList<String>>("sources")?.distinct()?.take(PhotoBatchProgress.MAX_PHOTOS)
        ?: savedState.get<String>("source-uri")?.let(::listOf).orEmpty()
    private val restoredIndex = (savedState.get<Int>("preview-index") ?: 0).coerceIn(0, maxOf(0, restoredSources.lastIndex))
    private val restoredStrength = savedState.get<Float>("strength")?.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 1f
    private val _state = MutableStateFlow(PhotoEditorUiState(selection = PhotoEditSelection(
        selectedId = savedState["selectedId"], filter = FilterSettings(strength = restoredStrength)),
        sources = restoredSources, previewIndex = restoredIndex, batch = restoreBatch()))
    val state = _state.asStateFlow()

    init {
        val cached = savedState.get<String>("source")?.let { path ->
            val file = File(path)
            file.takeIf { it.parentFile == cache && it.name.startsWith("edit-") && it.isFile }
        }
        if (cached != null) load(null, cached)
        else restoredSources.getOrNull(restoredIndex)?.let { load(Uri.parse(it), null) }
    }

    fun setLuts(next: List<LutEntry>) {
        entries = next
        if (next.isEmpty() || _state.value.saving) return
        val selected = _state.value.selection
        selected.selectedId?.let { id ->
            val entry = next.find { it.id == id }
            adjust(selected.filter.copy(lut = entry?.lut, encoding = entry?.lut?.suggestedEncoding ?: selected.filter.encoding), entry?.id, resetBatch = false)
        }
    }

    fun open(uri: Uri) = openBatch(listOf(uri))

    fun openBatch(uris: List<Uri>) {
        if (_state.value.saving) return
        if (uris.isEmpty()) return // Cancelling the picker keeps the existing draft.
        val sources = try { PhotoBatchProgress.pending(uris.map(Uri::toString)).items.map { it.source } }
        catch (error: IllegalArgumentException) {
            _state.update { it.copy(error = error.message) }
            return
        }
        updateGrants(sources)
        switching = FilterSwitching()
        clearBatch()
        _state.value = PhotoEditorUiState(sources = sources)
        savedState["sources"] = ArrayList(sources)
        savedState["preview-index"] = 0
        savedState["selectedId"] = null
        savedState["strength"] = 1f
        savedState.remove<String>("source")
        savedState["source-uri"] = sources.first()
        load(Uri.parse(sources.first()), null)
    }

    fun preview(index: Int) {
        val current = _state.value
        if (current.saving || index !in current.sources.indices) return
        if (index == current.previewIndex && (current.loading || source != null)) return
        savedState["preview-index"] = index
        savedState["source-uri"] = current.sources[index]
        _state.update { it.copy(previewIndex = index, selection = it.selection.copy(comparing = false)) }
        load(Uri.parse(current.sources[index]), null)
    }

    fun openForViewer(uri: Uri) {
        if (savedState.get<String>("source-uri") == uri.toString() &&
            (_state.value.loading || source != null)) return
        open(uri)
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

    private fun adjust(filter: FilterSettings, id: String? = _state.value.selection.selectedId, resetBatch: Boolean = true) {
        val changed = filter != _state.value.selection.filter || id != _state.value.selection.selectedId
        if (resetBatch && changed) clearBatch()
        _state.update {
            val next = it.selection.adjusted(filter, id)
            it.copy(selection = next, error = if (next === it.selection) it.error else null,
                batch = if (resetBatch && changed) null else it.batch)
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
        if (current.isBatch) { saveBatch(current, file, onSaved); return }
        val filter = current.selection.filter
        _state.update { it.copy(saving = true, error = null) }
        savingJob = viewModelScope.launch {
            try {
                val uri = withContext(Dispatchers.IO) { PhotoStorage.processAndSave(getApplication(), file, filter, SaveTarget.EDIT) }
                _state.update { it.copy(savedUri = uri, selection = it.selection.copy(savedFilter = filter)) }
                onSaved(uri)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { _state.update { it.copy(error = error.message ?: "照片未儲存") } }
            finally { _state.update { it.copy(saving = false) }; setLuts(entries) }
        }
    }

    private fun saveBatch(current: PhotoEditorUiState, cached: File, onSaved: (Uri) -> Unit) {
        val filter = current.selection.filter
        val cachedSource = current.sources[current.previewIndex]
        val previous = current.batch ?: PhotoBatchProgress.pending(current.sources)
        val alreadySaved = previous.items.filter { it.status == PhotoBatchStatus.SAVED }.map { it.source }.toSet()
        stopRequested = false
        _state.update { it.copy(saving = true, error = null) }
        savingJob = viewModelScope.launch {
            try {
                val result = PhotoBatchProcessor.run(previous, { stopRequested }, { id ->
                    withContext(Dispatchers.IO) {
                        val reused = id == cachedSource
                        val file = if (reused) cached else File.createTempFile("batch-", ".image", cache)
                        try {
                            if (!reused) {
                                val activeContext = coroutineContext
                                requireNotNull(getApplication<Application>().contentResolver.openInputStream(Uri.parse(id))) { "照片無法讀取" }.use { input ->
                                    file.outputStream().use { output -> PhotoImportIo.copy(input, output) { activeContext.ensureActive() } }
                                }
                                PhotoEditorSource.validate(file)
                            }
                            ensureActive()
                            PhotoStorage.processAndSave(getApplication(), file, filter, SaveTarget.EDIT).toString()
                        } finally { if (!reused) file.delete() }
                    }
                }, { progress ->
                    persistBatch(progress)
                    _state.update { it.copy(batch = progress) }
                })
                val saved = result.items.lastOrNull { it.status == PhotoBatchStatus.SAVED && it.source !in alreadySaved }?.output?.let(Uri::parse)
                _state.update { it.copy(savedUri = saved ?: it.savedUri,
                    selection = if (result.remainingCount == 0) it.selection.copy(savedFilter = filter) else it.selection) }
                // Refresh the gallery and camera thumbnail once, rather than for every exported photo.
                saved?.let(onSaved)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { _state.update { it.copy(error = error.message ?: "照片未儲存") } }
            finally {
                _state.update { it.copy(saving = false, batch = it.batch?.copy(running = false, currentIndex = null)) }
                setLuts(entries)
            }
        }
    }

    fun cancelBatch() {
        if (!_state.value.saving || !_state.value.isBatch) return
        stopRequested = true
        _state.update { it.copy(batch = it.batch?.copy(cancelRequested = true)) }
    }

    private fun persistBatch(progress: PhotoBatchProgress) {
        val saved = progress.items.filter { it.status == PhotoBatchStatus.SAVED }
        savedState["batch-started"] = true
        savedState["batch-sources"] = ArrayList(saved.map { it.source })
        savedState["batch-outputs"] = ArrayList(saved.map { requireNotNull(it.output) })
        savedState["batch-failed"] = ArrayList(progress.items.filter { it.status == PhotoBatchStatus.FAILED }.map { it.source })
    }

    private fun restoreBatch(): PhotoBatchProgress? {
        if (restoredSources.size < 2 || savedState.get<Boolean>("batch-started") != true) return null
        val saved = savedState.get<ArrayList<String>>("batch-sources").orEmpty()
            .zip(savedState.get<ArrayList<String>>("batch-outputs").orEmpty()).toMap()
        val failed = savedState.get<ArrayList<String>>("batch-failed").orEmpty().toSet()
        val items = restoredSources.map { id ->
            when {
                saved[id] != null -> PhotoBatchItem(id, PhotoBatchStatus.SAVED, output = saved[id])
                id in failed -> PhotoBatchItem(id, PhotoBatchStatus.FAILED, error = "照片未儲存")
                else -> PhotoBatchItem(id)
            }
        }
        return PhotoBatchProgress(items, stopped = items.any { it.status == PhotoBatchStatus.PENDING })
    }

    private fun clearBatch() {
        listOf("batch-started", "batch-sources", "batch-outputs", "batch-failed").forEach { savedState.remove<Any>(it) }
    }

    private fun updateGrants(sources: List<String>) {
        val resolver = getApplication<Application>().contentResolver
        val existing = resolver.persistedUriPermissions.map { it.uri.toString() }.toSet()
        val expired = ownedGrants.filter { it !in sources }
        expired.forEach { runCatching { resolver.releasePersistableUriPermission(Uri.parse(it), Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
        ownedGrants.removeAll(expired.toSet())
        sources.filter { it !in existing }.forEach { id ->
            runCatching { resolver.takePersistableUriPermission(Uri.parse(id), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                .onSuccess { ownedGrants += id }
        }
        savedState["owned-grants"] = ArrayList(ownedGrants)
    }

    private fun releaseGrants(grants: List<String>) {
        val resolver = getApplication<Application>().contentResolver
        grants.forEach { runCatching { resolver.releasePersistableUriPermission(Uri.parse(it), Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
    }

    fun discard() {
        if (_state.value.saving) return
        ++loadRevision
        loadingJob?.cancel()
        source?.delete()
        source = null
        releaseGrants(ownedGrants.toList())
        ownedGrants.clear()
        clearBatch()
        savedState.remove<ArrayList<String>>("owned-grants")
        savedState.remove<ArrayList<String>>("sources")
        savedState.remove<Int>("preview-index")
        savedState.remove<String>("source")
        savedState.remove<String>("source-uri")
        savedState.remove<String>("selectedId")
        savedState.remove<Float>("strength")
        _state.value = PhotoEditorUiState()
    }

    override fun onCleared() {
        loadingJob?.cancel()
        val file = source
        val grants = ownedGrants.toList()
        val saving = savingJob
        saving?.cancel()
        if (saving != null && !saving.isCompleted) saving.invokeOnCompletion { file?.delete(); releaseGrants(grants) }
        else { file?.delete(); releaseGrants(grants) }
        super.onCleared()
    }
}
