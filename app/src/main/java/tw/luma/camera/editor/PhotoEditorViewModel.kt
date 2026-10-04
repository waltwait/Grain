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
import tw.luma.camera.storage.PhotoNames
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
    /** One output per photo and checked filter; more than one is exported as a batch. */
    val jobCount get() = FilterChoice.outputs(sources.size, selection.chosen.size)
    val isBatch get() = jobCount > 1
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
    private val restoredChosen = (savedState.get<ArrayList<String>>("chosen")?.distinct()
        ?: listOfNotNull(savedState.get<String>("selectedId"))).take(PhotoBatchProgress.MAX_OUTPUTS)
    private val stemBook = StemBook(StemBook.decode(savedState.get<ArrayList<String>>("batch-stems").orEmpty())) {
        savedState["batch-stems"] = ArrayList(StemBook.encode(it))
    }
    private val _state = MutableStateFlow(PhotoEditorUiState(selection = PhotoEditSelection(
        selectedId = savedState["selectedId"], chosen = restoredChosen, filter = FilterSettings(strength = restoredStrength)),
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
        savedState.remove<ArrayList<String>>("chosen")
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

    /** Picks exactly one filter, or none for null; this is also what the "no filter" button does. */
    fun selectLut(id: String?) {
        if (!_state.value.canEdit) return
        val current = _state.value.selection
        val entry = entries.find { it.id == id }
        adjust(switching.select(current.filter, current.selectedId, entry), entry?.id, chosen = listOfNotNull(entry?.id))
    }

    /** Checks or unchecks a filter. Every checked filter is rendered for every photo; the preview shows the one checked last. */
    fun toggleLut(id: String) {
        val state = _state.value
        if (!state.canEdit) return
        val current = state.selection
        if (entries.none { it.id == id }) return
        val chosen = FilterChoice.toggle(current.chosen, id, state.sources.size)
        if (chosen == current.chosen) return // Refused: it would pass the output limit.
        val primaryId = FilterChoice.primary(chosen, current.selectedId, id)
        val primary = entries.find { it.id == primaryId }
        val filter = if (current.chosen.isEmpty() || primary == null) switching.select(current.filter, current.selectedId, primary)
        else current.filter.copy(lut = primary.lut, encoding = primary.lut.suggestedEncoding) // One strength for all checked filters.
        adjust(filter, primaryId, chosen = chosen)
    }

    fun strength(value: Float) {
        if (!_state.value.canEdit || !value.isFinite()) return
        adjust(_state.value.selection.filter.copy(strength = value.coerceIn(0f, 1f)))
    }

    private fun adjust(filter: FilterSettings, id: String? = _state.value.selection.selectedId, resetBatch: Boolean = true,
        chosen: List<String> = _state.value.selection.chosen) {
        val current = _state.value.selection
        val changed = filter != current.filter || id != current.selectedId || chosen != current.chosen
        if (resetBatch && changed) clearBatch()
        _state.update {
            val next = it.selection.adjusted(filter, id, chosen)
            it.copy(selection = next, error = if (next === it.selection) it.error else null,
                batch = if (resetBatch && changed) null else it.batch)
        }
        savedState["selectedId"] = id
        savedState["chosen"] = ArrayList(chosen)
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
        val base = current.selection.filter
        val chosen = current.selection.chosen
        val filterIds: List<String?> = chosen.ifEmpty { listOf(null) }
        val filters: Map<String?, FilterSettings> = filterIds.associateWith { id ->
            entries.find { it.id == id }?.let { base.copy(lut = it.lut, encoding = it.lut.suggestedEncoding) } ?: base
        }
        val outputNumbers = filterIds.withIndex().associate { it.value to it.index + 1 }
        val cachedSource = current.sources[current.previewIndex]
        val previous = current.batch ?: PhotoBatchProgress.pending(current.sources, filterIds)
        val jobs = previous.items.associateBy { it.id }
        val alreadySaved = previous.items.filter { it.status == PhotoBatchStatus.SAVED }.map { it.id }.toSet()
        // The first output of a photo that gets saved also writes the one original its outputs share.
        val withOriginal = previous.items.filter { it.status == PhotoBatchStatus.SAVED }.map { it.source }.toMutableSet()
        stopRequested = false
        _state.update { it.copy(saving = true, error = null) }
        savingJob = viewModelScope.launch {
            var working: Pair<String, File>? = null // The photo being rendered; its copy is reused for its next filter.
            try {
                val result = PhotoBatchProcessor.run(previous, { stopRequested }, { id ->
                    val job = jobs.getValue(id)
                    // Saved before the first output is written, on the main thread, so a restored session names the rest the same way.
                    val time = stemBook.stemFor(job.source, PhotoStorage::timeStamp)
                    val number = outputNumbers.getValue(job.filterId)
                    val app = getApplication<Application>()
                    val published = withContext(Dispatchers.IO) { PhotoStorage.findPublished(app, SaveTarget.EDIT, PhotoNames.edited(SaveTarget.EDIT, time, number)) }
                    if (published != null) {
                        // An earlier run already saved this output but the restored state did not know: adopt it, do not render a duplicate.
                        withOriginal += job.source
                        published.toString()
                    } else withContext(Dispatchers.IO) {
                        val file = working?.takeIf { it.first == job.source }?.second ?: run {
                            working?.second?.takeIf { it != cached }?.delete()
                            working = null
                            val reused = job.source == cachedSource
                            val copy = if (reused) cached else File.createTempFile("batch-", ".image", cache)
                            try {
                                if (!reused) {
                                    val activeContext = coroutineContext
                                    requireNotNull(getApplication<Application>().contentResolver.openInputStream(Uri.parse(job.source))) { "照片無法讀取" }.use { input ->
                                        copy.outputStream().use { output -> PhotoImportIo.copy(input, output) { activeContext.ensureActive() } }
                                    }
                                    PhotoEditorSource.validate(copy)
                                }
                            } catch (error: Throwable) { if (!reused) copy.delete(); throw error }
                            working = job.source to copy
                            copy
                        }
                        ensureActive()
                        PhotoStorage.processAndSave(app, file, filters.getValue(job.filterId), SaveTarget.EDIT,
                            saveOriginal = job.source !in withOriginal && !PhotoStorage.originalExists(app, SaveTarget.EDIT, time),
                            time = time, index = number).toString().also { withOriginal += job.source }
                    }
                }, { progress ->
                    persistBatch(progress)
                    _state.update { it.copy(batch = progress) }
                })
                val saved = result.items.lastOrNull { it.status == PhotoBatchStatus.SAVED && it.id !in alreadySaved }?.output?.let(Uri::parse)
                _state.update { it.copy(savedUri = saved ?: it.savedUri,
                    selection = if (result.remainingCount == 0) it.selection.copy(savedFilter = base) else it.selection) }
                // Refresh the gallery and camera thumbnail once, rather than for every exported photo.
                saved?.let(onSaved)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { _state.update { it.copy(error = error.message ?: "照片未儲存") } }
            finally {
                working?.second?.takeIf { it != cached }?.delete()
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
        savedState["batch-sources"] = ArrayList(saved.map { it.id })
        savedState["batch-outputs"] = ArrayList(saved.map { requireNotNull(it.output) })
        savedState["batch-failed"] = ArrayList(progress.items.filter { it.status == PhotoBatchStatus.FAILED }.map { it.id })
    }

    private fun restoreBatch(): PhotoBatchProgress? {
        val jobs = runCatching { PhotoBatchProgress.pending(restoredSources, restoredChosen).items }.getOrNull() ?: return null
        if (jobs.size < 2 || savedState.get<Boolean>("batch-started") != true) return null
        val saved = savedState.get<ArrayList<String>>("batch-sources").orEmpty()
            .zip(savedState.get<ArrayList<String>>("batch-outputs").orEmpty()).toMap()
        val failed = savedState.get<ArrayList<String>>("batch-failed").orEmpty().toSet()
        val items = jobs.map { job ->
            when {
                saved[job.id] != null -> job.copy(status = PhotoBatchStatus.SAVED, output = saved[job.id])
                job.id in failed -> job.copy(status = PhotoBatchStatus.FAILED, error = "照片未儲存")
                else -> job
            }
        }
        return PhotoBatchProgress(items, stopped = items.any { it.status == PhotoBatchStatus.PENDING })
    }

    private fun clearBatch() {
        listOf("batch-started", "batch-sources", "batch-outputs", "batch-failed", "batch-stems").forEach { savedState.remove<Any>(it) }
        stemBook.clear()
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
        savedState.remove<ArrayList<String>>("chosen")
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
