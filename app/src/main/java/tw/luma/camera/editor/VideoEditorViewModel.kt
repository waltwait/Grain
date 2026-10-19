package tw.luma.camera.editor

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tw.luma.camera.LutEntry
import tw.luma.camera.gl.FilterSettings
import tw.luma.camera.lut.FilterSwitching
import tw.luma.camera.storage.PhotoStorage
import tw.luma.camera.storage.VideoStorage

/** Converting the picked videos one after another; [saved] are the sources already written with the current film. */
data class VideoBatch(val total: Int, val saved: Set<String> = emptySet(), val failed: Int = 0, val current: Int? = null,
    val progress: Float = 0f, val stopping: Boolean = false, val stopped: Boolean = false) {
    val running get() = current != null

    val status: String get() = when {
        running && stopping -> "正在停止 · 已儲存 ${saved.size} / $total"
        running -> "正在轉檔 ${current!! + 1} / $total · ${(progress.coerceIn(0f, 1f) * 100).roundToInt()}%"
        stopped -> "已停止 · 已儲存 ${saved.size} 支"
        failed > 0 -> "已儲存 ${saved.size} 支 · 失敗 $failed 支"
        else -> "已儲存 ${saved.size} 支"
    }
}

data class VideoEditorUiState(
    val sources: List<String> = emptyList(),
    val previewIndex: Int = 0,
    val poster: Bitmap? = null,
    val durationMs: Long = 0,
    val loading: Boolean = false,
    val convertOnly: Boolean = false,
    val selectedId: String? = null,
    val filter: FilterSettings = FilterSettings(),
    val comparing: Boolean = false,
    /** Bumped whenever the preview must be drawn again. */
    val revision: Long = 0,
    val batch: VideoBatch? = null,
    val error: String? = null,
) {
    /** Videos are picked, or a pick was refused and says why. */
    val active get() = sources.isNotEmpty() || error != null
    val saving get() = batch?.running == true
    val canEdit get() = sources.isNotEmpty() && !saving && !convertOnly
    val canSave get() = sources.isNotEmpty() && !saving && (convertOnly || selectedId == null || filter.lut != null) && batch?.let { it.saved.size < it.total } != false
    val previewSettings get() = if (comparing) FilterSettings() else filter
    /** Every video is saved with the current film, so there is no draft left to keep. */
    val finished get() = !saving && batch != null && batch.saved.size == batch.total
}

/** The edit tab's videos: preview a frame with a film, then convert every picked clip to an MP4 in Movies/Grain Edits. */
class VideoEditorViewModel(application: Application) : AndroidViewModel(application) {
    private val cache = File(application.cacheDir, "video-editor").apply { mkdirs() }
    private var entries: List<LutEntry> = emptyList()
    private var switching = FilterSwitching()
    private var loadingJob: Job? = null
    private var savingJob: Job? = null
    private val _state = MutableStateFlow(VideoEditorUiState())
    val state = _state.asStateFlow()

    fun setLuts(next: List<LutEntry>) {
        entries = next
        if (next.isEmpty() || _state.value.saving) return
        val current = _state.value
        current.selectedId?.let { id ->
            val entry = next.find { it.id == id }
            adjust(current.filter.copy(lut = entry?.lut, encoding = entry?.lut?.suggestedEncoding ?: current.filter.encoding), entry?.id)
        }
    }

    fun open(uris: List<Uri>) {
        if (_state.value.saving || uris.isEmpty()) return
        val sources = uris.map(Uri::toString).distinct()
        if (sources.size > PhotoBatchProgress.MAX_PHOTOS) { reject("一次最多選 ${PhotoBatchProgress.MAX_PHOTOS} 支影片"); return }
        switching = FilterSwitching()
        _state.value = VideoEditorUiState(sources = sources)
        load(0)
    }

    /** Opens [uris] in plain MP4 conversion mode without filter controls and starts converting immediately. */
    fun convert(uris: List<Uri>, onSaved: (Uri) -> Unit) {
        if (_state.value.saving || uris.isEmpty()) return
        val sources = uris.map(Uri::toString).distinct()
        if (sources.size > PhotoBatchProgress.MAX_PHOTOS) { reject("一次最多選 ${PhotoBatchProgress.MAX_PHOTOS} 支影片"); return }
        switching = FilterSwitching()
        _state.value = VideoEditorUiState(sources = sources, convertOnly = true)
        load(0)
        save(onSaved)
    }

    /** Shows why a pick cannot be edited, in place of the videos. */
    fun reject(message: String) {
        if (_state.value.saving) return
        loadingJob?.cancel()
        _state.value = VideoEditorUiState(error = message)
    }

    fun preview(index: Int) {
        val current = _state.value
        if (current.saving || index !in current.sources.indices) return
        if (index == current.previewIndex && (current.loading || current.poster != null)) return
        load(index)
    }

    private fun load(index: Int) {
        loadingJob?.cancel()
        val source = _state.value.sources[index]
        _state.update { it.copy(previewIndex = index, poster = null, loading = true, error = null, comparing = false) }
        loadingJob = viewModelScope.launch {
            try {
                val info = withContext(Dispatchers.IO) { VideoConverter.probe(getApplication(), source.toUri()) }
                _state.update { it.copy(poster = info.poster, durationMs = info.durationMs, loading = false, revision = it.revision + 1) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { _state.update { it.copy(loading = false, error = error.message ?: "影片無法讀取") } }
        }
    }

    /** Picks one film, or none for null. */
    fun selectLut(id: String?) {
        val current = _state.value
        if (!current.canEdit) return
        val entry = entries.find { it.id == id }
        adjust(switching.select(current.filter, current.selectedId, entry), entry?.id)
    }

    fun strength(value: Float) {
        if (!_state.value.canEdit || !value.isFinite()) return
        adjust(_state.value.filter.copy(strength = value.coerceIn(0f, 1f)))
    }

    fun previewFailed() { _state.update { it.copy(error = "預覽暫時無法顯示") } }

    fun compare() {
        if (_state.value.canEdit) _state.update { it.copy(comparing = !it.comparing, revision = it.revision + 1) }
    }

    private fun adjust(filter: FilterSettings, id: String? = _state.value.selectedId) {
        _state.update {
            if (filter == it.filter && id == it.selectedId) it
            // Videos saved with another film are not this film's; the next save converts them all again.
            else it.copy(filter = filter, selectedId = id, comparing = false, revision = it.revision + 1, batch = null)
        }
    }

    /** Converts every picked video not yet saved with this film, one after another. */
    fun save(onSaved: (Uri) -> Unit) {
        val current = _state.value
        if (!current.canSave) return
        val filter = current.filter
        val app = getApplication<Application>()
        val previous = current.batch ?: VideoBatch(current.sources.size)
        val pending = current.sources.filter { it !in previous.saved }
        _state.update { it.copy(error = null, comparing = false,
            batch = previous.copy(failed = 0, stopped = false, stopping = false, current = current.sources.indexOf(pending.first()), progress = 0f)) }
        savingJob = viewModelScope.launch {
            var last: Uri? = null
            try {
                for (source in pending) {
                    _state.update { it.copy(batch = it.batch?.copy(current = current.sources.indexOf(source), progress = 0f)) }
                    val output = File.createTempFile("convert-", ".mp4", cache)
                    try {
                        val uri = source.toUri()
                        val hasAudio = withContext(Dispatchers.IO) { VideoConverter.hasAudio(app, uri) }
                        VideoConverter.convert(app, uri, hasAudio, filter, output) { done ->
                            _state.update { it.copy(batch = it.batch?.copy(progress = done)) }
                        }
                        last = withContext(Dispatchers.IO) { VideoStorage.saveEdit(app, output, VideoStorage.editName(PhotoStorage.timeStamp())) }
                        _state.update { it.copy(batch = it.batch?.let { batch -> batch.copy(saved = batch.saved + source) }) }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) {
                        _state.update { it.copy(error = error.message ?: "影片未儲存", batch = it.batch?.let { batch -> batch.copy(failed = batch.failed + 1) }) }
                    } finally { output.delete() }
                }
            } finally {
                _state.update { it.copy(batch = it.batch?.let { batch -> batch.copy(current = null, stopped = batch.stopping, stopping = false) }) }
                // Refresh the gallery once, rather than for every saved clip.
                last?.let(onSaved)
            }
        }
    }

    fun stop() {
        if (!_state.value.saving) return
        _state.update { it.copy(batch = it.batch?.copy(stopping = true)) }
        savingJob?.cancel()
    }

    fun discard() {
        if (_state.value.saving) return
        loadingJob?.cancel()
        cache.listFiles()?.forEach { it.delete() }
        _state.value = VideoEditorUiState()
    }

    override fun onCleared() {
        loadingJob?.cancel()
        val saving = savingJob
        saving?.cancel()
        if (saving != null && !saving.isCompleted) saving.invokeOnCompletion { cache.listFiles()?.forEach { it.delete() } }
        else cache.listFiles()?.forEach { it.delete() }
        super.onCleared()
    }
}
