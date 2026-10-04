package tw.luma.camera

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import tw.luma.camera.camera.ZoomControls
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tw.luma.camera.camera.ActualCapture
import tw.luma.camera.camera.CaptureFeedback
import tw.luma.camera.camera.CameraCapabilities
import tw.luma.camera.camera.CameraEngine
import tw.luma.camera.camera.CaptureSettings
import tw.luma.camera.camera.WhiteBalanceControls
import tw.luma.camera.gl.FilterSettings
import tw.luma.camera.lut.CubeLut
import tw.luma.camera.lut.BundledLutLibrary
import tw.luma.camera.lut.OriginalLutLibrary
import tw.luma.camera.lut.KodakLutLibrary
import tw.luma.camera.lut.FilterSwitching
import android.content.ContentUris
import android.provider.MediaStore
import tw.luma.camera.gallery.LatestMedia
import tw.luma.camera.gallery.MediaRow
import tw.luma.camera.storage.PhotoStorage
import tw.luma.camera.storage.SaveTarget
import androidx.camera.video.VideoRecordEvent
import java.io.File
import tw.luma.camera.storage.LutImportIo
import tw.luma.camera.performance.grainTrace
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.CancellationException

data class LutEntry(val id: String, val lut: CubeLut, val imported: Boolean = false,
    val description: String? = null, val sourceUrl: String? = null, val licenseUrl: String? = null)
enum class CaptureMode { PHOTO, VIDEO }
enum class RecordingStatus { IDLE, STARTING, RECORDING, STOPPING }

data class CameraUiState(
    val luts: List<LutEntry> = emptyList(),
    val selectedLut: String? = null,
    val filter: FilterSettings = FilterSettings(),
    val capture: CaptureSettings = CaptureSettings(),
    val capabilities: CameraCapabilities = CameraCapabilities(),
    val actual: ActualCapture = ActualCapture(),
    val front: Boolean = false,
    val grid: Boolean = true,
    val ready: Boolean = false,
    val busy: Boolean = false,
    val captureFeedback: CaptureFeedback = CaptureFeedback(),
    val hasFlash: Boolean = false,
    val minZoom: Float = 1f,
    val maxZoom: Float = 1f,
    val saveOriginal: Boolean = false,
    val message: String? = null,
    val cameraError: String? = null,
    val savedUri: Uri? = null,
    val thumbnail: Bitmap? = null,
    val mode: CaptureMode = CaptureMode.PHOTO,
    val recordingStatus: RecordingStatus = RecordingStatus.IDLE,
    val recordingNs: Long = 0,
    val recordingAudio: Boolean = false,
    val recordWithAudio: Boolean = true,
    val videoQuality: String = "HD",
    val savedMime: String = "image/jpeg",
) {
    val recording get() = recordingStatus != RecordingStatus.IDLE
    val liveControlsEnabled get() = ready && !busy && recordingStatus != RecordingStatus.STARTING && recordingStatus != RecordingStatus.STOPPING
}

class CameraViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(CameraUiState())
    val state: StateFlow<CameraUiState> = _state
    // Camera metadata and recorder events must not invalidate the whole camera screen.
    val uiState = state.map { it.copy(actual = ActualCapture(), recordingNs = 0) }
        .distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CameraUiState())
    val actualCapture = state.map { it.actual }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActualCapture())
    val recordingTime = state.map { it.recordingNs.coerceAtLeast(0) / 1_000_000_000L }.distinctUntilChanged()
        .map { ZoomControls.recordingTime(it * 1_000_000_000L) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ZoomControls.recordingTime(0))
    private val prefs = application.getSharedPreferences("luma", 0)
    private val filterSwitching = FilterSwitching { id ->
        if (prefs.contains("lutStrength-$id")) prefs.getFloat("lutStrength-$id", 1f) else null
    }
    private val lutDirectory = File(application.filesDir, "luts").apply { mkdirs() }
    private val captureExecutor = Executors.newSingleThreadExecutor()
    private var libraryReady = false

    init {
        _state.update { it.copy(saveOriginal = prefs.getBoolean("saveOriginal", false), grid = prefs.getBoolean("grid", true), recordWithAudio = prefs.getBoolean("recordWithAudio", true)) }
        loadLatestMedia()
        viewModelScope.launch {
            val entries = withContext(Dispatchers.IO) {
                val originals = runCatching { grainTrace("Grain.lut.loadOriginals") {
                    OriginalLutLibrary.load { application.assets.open(it) }
                }.map { LutEntry(it.id, it.lut, description = it.description) } }
                    .onFailure { message(it.message ?: "Grain Originals 載入失敗") }.getOrDefault(emptyList())
                val kodak = runCatching { grainTrace("Grain.lut.loadKodak") {
                    KodakLutLibrary.load { application.assets.open(it) }
                }.map { LutEntry(it.id, it.lut, description = it.description, sourceUrl = it.sourceUrl, licenseUrl = it.licenseUrl) } }
                    .onFailure { message(it.message ?: "Kodak 模擬 LUT 載入失敗") }.getOrDefault(emptyList())
                val builtIns = CubeLut.builtIns().mapIndexed { i, lut -> LutEntry("builtin-$i", lut) }
                val official = runCatching { grainTrace("Grain.lut.loadBundled") { BundledLutLibrary.load(application.assets) }.map { (id, lut) -> LutEntry(id, lut) } }
                    .onFailure { message(it.message ?: "內建 LUT 載入失敗") }.getOrDefault(emptyList())
                val imported = lutDirectory.listFiles()?.filter { it.extension == "cube" }?.mapNotNull { file ->
                    runCatching { file.reader().use { LutEntry(file.nameWithoutExtension,
                        CubeLut.parse(it, prefs.getString("lutTitle-${file.nameWithoutExtension}", "匯入 LUT") ?: "匯入 LUT"), true) } }.getOrNull()
                }.orEmpty()
                kodak + originals + official + builtIns + imported
            }
            _state.update { it.copy(luts = entries) }
            libraryReady = true
            selectLut(prefs.getString("selectedLut", null))
        }
    }

    fun selectLut(id: String?) {
        val current = _state.value
        val entry = current.luts.find { it.id == id }
        if (current.selectedLut == entry?.id && current.filter.lut === entry?.lut) return
        saveFilterStrength()
        val filter = filterSwitching.select(current.filter, current.selectedLut, entry)
        _state.update { it.copy(selectedLut = entry?.id, filter = filter) }
        if (libraryReady) prefs.edit().putString("selectedLut", entry?.id).apply()
    }

    fun saveFilterStrength() {
        val current = _state.value
        current.selectedLut?.let { id ->
            prefs.edit().putFloat("lutStrength-$id", current.filter.strength.coerceIn(0f, 1f)).apply()
        }
    }

    fun changeFilter(transform: (FilterSettings) -> FilterSettings) { _state.update {
        val next = transform(it.filter)
        if (next == it.filter) it else it.copy(filter = next)
    } }
    fun changeCapture(transform: (CaptureSettings) -> CaptureSettings) { _state.update {
        val next = transform(it.capture)
        if (next == it.capture) it else it.copy(capture = next)
    } }
    fun resetWhiteBalance() { _state.update {
        it.copy(capture = WhiteBalanceControls.reset(it.capture), filter = WhiteBalanceControls.reset(it.filter))
    } }
    fun toggleFront() { if (!_state.value.busy && !_state.value.recording) _state.update { it.copy(front = !it.front, ready = false, actual = ActualCapture(), cameraError = null, capture = CaptureSettings()) } }
    fun mode(mode: CaptureMode) { if (!_state.value.busy && !_state.value.recording && mode != _state.value.mode) _state.update { it.copy(mode = mode, ready = false, actual = ActualCapture(), cameraError = null, capture = it.capture.copy(manual = false, flash = false)) } }
    fun zoom(value: Float) { _state.update {
        val zoom = value.coerceIn(it.minZoom, it.maxZoom)
        if (it.liveControlsEnabled && zoom != it.capture.zoom) it.copy(capture = it.capture.copy(zoom = zoom)) else it
    } }
    fun recordWithAudio(enabled: Boolean) { if (!_state.value.recording) { _state.update { it.copy(recordWithAudio = enabled) }; prefs.edit().putBoolean("recordWithAudio", enabled).apply() } }
    fun videoQuality(value: String) { _state.update { it.copy(videoQuality = value) } }
    fun toggleGrid() { _state.update { it.copy(grid = !it.grid) }; prefs.edit().putBoolean("grid", _state.value.grid).apply() }
    fun saveOriginal(enabled: Boolean) { _state.update { it.copy(saveOriginal = enabled) }; prefs.edit().putBoolean("saveOriginal", enabled).apply() }
    fun message(text: String) { _state.update { it.copy(message = text) } }
    fun clearMessage(expected: String? = null) { _state.update {
        if (expected == null || it.message == expected) it.copy(message = null) else it
    } }
    fun cameraError(text: String) { _state.update { it.copy(ready = false, cameraError = text) } }
    fun pausePreview() { _state.update { it.copy(ready = false, actual = ActualCapture()) } }
    fun retry() { _state.update { it.copy(cameraError = null, ready = false) } }
    fun actual(value: ActualCapture) { _state.update { it.copy(actual = value) } }

    fun ready(caps: CameraCapabilities, flash: Boolean, min: Float, max: Float) {
        _state.update { it.copy(capabilities = caps, hasFlash = flash, minZoom = min, maxZoom = max, ready = true, cameraError = null,
            capture = it.capture.copy(manual = false, aperture = caps.apertures.firstOrNull(), zoom = it.capture.zoom.coerceIn(min, max),
                iso = caps.isoRange?.clamp(100) ?: 100, shutterNs = caps.shutterRange?.clamp(8_000_000L) ?: 8_000_000L)) }
    }

    fun importLut(uri: Uri) {
        if (_state.value.busy || !libraryReady) { message("請稍後再匯入 LUT"); return }
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                val entry = withContext(Dispatchers.IO) {
                    val resolver = getApplication<Application>().contentResolver
                    val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else "匯入 LUT" } ?: "匯入 LUT"
                    val temp = File.createTempFile("import-", ".tmp", lutDirectory)
                    try {
                        val imported = requireNotNull(resolver.openInputStream(uri)).use { input ->
                            grainTrace("Grain.lut.importReadParse") { temp.outputStream().use { output ->
                                LutImportIo.parseAndCopy(input, output, CubeLut.MAX_FILE_BYTES, name.removeSuffix(".cube"))
                            } }
                        }
                        val (id, lut) = imported
                        check(temp.renameTo(File(lutDirectory, "$id.cube"))) { "LUT 儲存失敗" }
                        prefs.edit().putString("lutTitle-$id", lut.title).apply()
                        LutEntry(id, lut, true)
                    } finally { temp.delete() }
                }
                _state.update { it.copy(luts = it.luts.filterNot { previous -> previous.id == entry.id } + entry) }
                selectLut(entry.id)
            } catch (e: Exception) { message(e.message ?: "LUT 匯入失敗") }
            finally { _state.update { it.copy(busy = false) } }
        }
    }

    fun deleteSelectedLut() {
        val entry = _state.value.luts.find { it.id == _state.value.selectedLut && it.imported } ?: return
        if (_state.value.busy) return
        viewModelScope.launch {
            val deleted = withContext(Dispatchers.IO) { File(lutDirectory, "${entry.id}.cube").delete() }
            if (deleted) { prefs.edit().remove("lutTitle-${entry.id}").apply(); _state.update { it.copy(luts = it.luts.filterNot { lut -> lut.id == entry.id }) }; selectLut(null) }
            else message("無法移除 LUT")
        }
    }

    fun takePhoto(engine: CameraEngine) {
        if (_state.value.busy || !_state.value.ready || _state.value.mode != CaptureMode.PHOTO) return
        val snapshot = _state.value
        _state.update { it.copy(busy = true, message = null, captureFeedback = it.captureFeedback.startPhoto()) }
        val requestId = _state.value.captureFeedback.requestId
        viewModelScope.launch {
            var pendingFile: File? = null
            try {
                val file = File.createTempFile("luma-capture-", ".jpg", getApplication<Application>().cacheDir)
                pendingFile = file
                suspendCancellableCoroutine { continuation ->
                    engine.capture(file, captureExecutor) { result ->
                        if (continuation.isActive) result.fold({ continuation.resume(Unit) }, { continuation.resumeWithException(it) })
                    }
                }
                _state.update { it.copy(captureFeedback = it.captureFeedback.capturedPhoto(requestId)) }
                val uri = withContext(Dispatchers.IO) { PhotoStorage.processAndSave(getApplication(), file, snapshot.filter, SaveTarget.CAMERA, snapshot.saveOriginal) }
                val thumb = loadMediaThumbnail(uri)
                _state.update { it.copy(savedUri = uri, thumbnail = thumb, savedMime = "image/jpeg",
                    captureFeedback = it.captureFeedback.savedPhoto(requestId)) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message(e.message ?: "拍照失敗") }
            finally { pendingFile?.delete(); _state.update {
                if (it.captureFeedback.requestId == requestId) it.copy(busy = false,
                    captureFeedback = it.captureFeedback.failedPhoto(requestId)) else it
            } }
        }
    }

    fun startVideo(engine: CameraEngine, audio: Boolean) {
        if (_state.value.busy || _state.value.recording || !_state.value.ready || _state.value.mode != CaptureMode.VIDEO) return
        _state.update { it.copy(recordingStatus = RecordingStatus.STARTING, recordingNs = 0, recordingAudio = audio) }
        try {
            engine.startVideo(audio) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> _state.update { it.copy(recordingStatus = if (it.recordingStatus == RecordingStatus.STOPPING) RecordingStatus.STOPPING else RecordingStatus.RECORDING) }
                    is VideoRecordEvent.Status -> _state.update { it.copy(recordingNs = event.recordingStats.recordedDurationNanos) }
                    is VideoRecordEvent.Finalize -> {
                        val uri = event.outputResults.outputUri
                        val usable = uri != Uri.EMPTY && event.recordingStats.recordedDurationNanos > 0 &&
                            (!event.hasError() || event.error == VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE)
                        _state.update { it.copy(recordingStatus = RecordingStatus.IDLE, recordingNs = event.recordingStats.recordedDurationNanos) }
                        if (usable) {
                            _state.update { it.copy(savedUri = uri, savedMime = "video/mp4", thumbnail = null,
                                captureFeedback = it.captureFeedback.mediaSaved()) }
                            viewModelScope.launch {
                                val thumb = loadMediaThumbnail(uri)
                                _state.update { if (it.savedUri == uri) it.copy(thumbnail = thumb) else it }
                            }
                        } else {
                            if (uri != Uri.EMPTY) viewModelScope.launch(Dispatchers.IO) { runCatching { getApplication<Application>().contentResolver.delete(uri, null, null) } }
                            message("錄影未儲存：${event.cause?.message ?: "錯誤 ${event.error}"}")
                        }
                    }
                }
            }
        } catch (e: Exception) { _state.update { it.copy(recordingStatus = RecordingStatus.IDLE) }; message(e.message ?: "錄影啟動失敗") }
    }

    /** Fills the corner button with the newest camera shot at launch; a capture made in the meantime wins. */
    private fun loadLatestMedia() {
        viewModelScope.launch {
            val latest = withContext(Dispatchers.IO) { queryLatestMedia() } ?: return@launch
            val uri = ContentUris.withAppendedId(
                if (latest.video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI, latest.id)
            val thumb = loadMediaThumbnail(uri)
            _state.update {
                if (it.savedUri != null) it
                else it.copy(savedUri = uri, savedMime = if (latest.video) "video/mp4" else "image/jpeg", thumbnail = thumb)
            }
        }
    }

    /** Camera shots only: edits live in their own folder and never replace this thumbnail. */
    private fun queryLatestMedia(): MediaRow? {
        val app = getApplication<Application>()
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.OWNER_PACKAGE_NAME} = ? AND ${MediaStore.MediaColumns.IS_PENDING} = 0"
        fun first(collection: Uri, path: String, video: Boolean): MediaRow? = try {
            app.contentResolver.query(collection, arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATE_ADDED), selection,
                arrayOf(path, app.packageName), "${MediaStore.MediaColumns.DATE_ADDED} DESC, ${MediaStore.MediaColumns._ID} DESC")
                ?.use { if (it.moveToFirst()) MediaRow(it.getLong(0), it.getLong(1), video) else null }
        } catch (_: Exception) { null }
        return LatestMedia.newest(listOfNotNull(
            first(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, SaveTarget.CAMERA.queryPath, false),
            first(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "Movies/Grain/", true)))
    }

    // Publishing a photo succeeded even if the optional small preview cannot be loaded.
    private suspend fun loadMediaThumbnail(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try { getApplication<Application>().contentResolver.loadThumbnail(uri, android.util.Size(160, 160), null) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { null }
    }

    fun stopVideo(engine: CameraEngine?) {
        if (_state.value.recording && _state.value.recordingStatus != RecordingStatus.STOPPING) {
            _state.update { it.copy(recordingStatus = RecordingStatus.STOPPING) }
            engine?.stopVideo()
        }
    }

    override fun onCleared() { captureExecutor.shutdown(); super.onCleared() }
}
