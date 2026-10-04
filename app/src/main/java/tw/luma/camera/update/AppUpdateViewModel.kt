package tw.luma.camera.update

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tw.luma.camera.BuildConfig
import java.io.File
import java.util.UUID

data class AppUpdateState(val info: UpdateInfo? = null, val checking: Boolean = false,
    val downloading: Boolean = false, val verifying: Boolean = false, val progress: Float = 0f,
    val download: File? = null, val installReady: File? = null, val error: String? = null) {
    val busy get() = checking || downloading || verifying
    val available get() = info?.isNewerThan(BuildConfig.VERSION_CODE.toLong()) == true
}

class AppUpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val mutableState = MutableStateFlow(AppUpdateState())
    val state = mutableState.asStateFlow()
    private var operation: Job? = null
    @Volatile private var revision = 0L
    private val directory = File(application.cacheDir, "grain-updates")

    private var launchChecked = false

    /** Runs at most once per launch; a failure stays silent until the user opens the update page. */
    fun checkOnLaunch() {
        if (!UpdatePrompt.shouldCheckOnLaunch(BuildConfig.UPDATE_FEED_URL, launchChecked)) return
        launchChecked = true
        check(silent = true)
    }

    fun check(silent: Boolean = false) {
        if (state.value.busy) return
        if (BuildConfig.UPDATE_FEED_URL.isBlank()) { error("更新下載網站尚未設定"); return }
        val id = ++revision
        val previous = operation
        mutableState.update { it.copy(checking = true, error = null) }
        operation = viewModelScope.launch {
            previous?.join()
            try {
                val next = withContext(Dispatchers.IO) {
                    val coroutine = currentCoroutineContext()
                    UpdateHttp.readInfo(BuildConfig.UPDATE_FEED_URL) { coroutine.ensureActive() }.also {
                        it.requireCompatible(getApplication<Application>().packageName, BuildConfig.UPDATE_CHANNEL, Build.VERSION.SDK_INT)
                        require(it.certificateSha256 in UpdateApk.signers(UpdateApk.installed(getApplication()))) { "新版使用不同簽章，無法直接更新" }
                    }
                }
                if (id == revision) mutableState.update { old ->
                    val sameFile = old.info?.let { it.versionCode == next.versionCode && it.apkSha256 == next.apkSha256 } == true
                    old.copy(info = next, checking = false, download = old.download.takeIf { sameFile }, error = null)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
              catch (failure: Exception) { if (id == revision && !silent) error(readable(failure, "無法連線到更新網站")) }
              finally { if (id == revision) mutableState.update { it.copy(checking = false) } }
        }
    }

    fun download() {
        val current = state.value
        val info = current.info ?: return
        if (current.busy || !current.available) return
        val id = ++revision
        val previous = operation
        mutableState.update { it.copy(downloading = true, progress = 0f, download = null, error = null) }
        operation = viewModelScope.launch {
            previous?.join()
            try {
                val file = withContext(Dispatchers.IO) {
                    val coroutine = currentCoroutineContext()
                    check(directory.isDirectory || directory.mkdirs()) { "無法準備更新暫存目錄" }
                    val destination = File(directory, "grain-" + info.channel + "-" + info.versionCode + ".apk")
                    if (destination.isFile) {
                        try { UpdateApk.verify(getApplication(), destination, info) { coroutine.ensureActive() }; return@withContext destination }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { destination.delete() }
                    }
                    directory.listFiles()?.filter { it.name.endsWith(".part") || it.name.endsWith(".apk") }?.forEach { it.delete() }
                    val pending = File(directory, UUID.randomUUID().toString() + ".part")
                    val connection = UpdateHttp.open(info.apkUrl) { coroutine.ensureActive() }
                    try {
                        val length = connection.contentLengthLong
                        require(length == -1L || length == info.apkSize) { "更新檔案大小與版本資訊不同" }
                        require(directory.usableSpace >= info.apkSize + 8 * 1024 * 1024) { "手機空間不足，無法下載更新" }
                        var lastProgressNs = 0L
                        connection.inputStream.use { input -> pending.outputStream().use { output ->
                            UpdateDownloadIo.copy(input, output, info.apkSize, info.apkSha256, { coroutine.ensureActive() }) { count ->
                                val now = System.nanoTime()
                                if (count == info.apkSize || now - lastProgressNs >= 200_000_000L) {
                                    lastProgressNs = now
                                    if (id == revision) mutableState.update { it.copy(progress = count.toFloat() / info.apkSize) }
                                }
                            }
                        } }
                        coroutine.ensureActive()
                        require(pending.renameTo(destination)) { "無法保存更新檔案" }
                        try { UpdateApk.verify(getApplication(), destination, info) { coroutine.ensureActive() } }
                        catch (failure: Exception) { destination.delete(); throw failure }
                        destination
                    } finally { connection.disconnect(); pending.delete() }
                }
                if (id == revision) mutableState.update { it.copy(download = file, downloading = false, progress = 1f) }
            } catch (cancelled: CancellationException) { throw cancelled }
              catch (failure: Exception) { if (id == revision) error(readable(failure, "更新下載失敗，請再試一次")) }
              finally { if (id == revision) mutableState.update { it.copy(downloading = false) } }
        }
    }

    fun prepareInstall() {
        val current = state.value
        val file = current.download ?: return
        val info = current.info ?: return
        if (current.busy || !current.available) return
        val id = ++revision
        val previous = operation
        mutableState.update { it.copy(verifying = true, error = null) }
        operation = viewModelScope.launch {
            previous?.join()
            try {
                withContext(Dispatchers.IO) {
                    val coroutine = currentCoroutineContext()
                    UpdateApk.verify(getApplication(), file, info) { coroutine.ensureActive() }
                }
                if (id == revision) mutableState.update { it.copy(installReady = file, verifying = false) }
            } catch (cancelled: CancellationException) { throw cancelled }
              catch (failure: Exception) { if (id == revision) { file.delete(); mutableState.update { it.copy(download = null) }; error(readable(failure, "無法安裝更新")) } }
              finally { if (id == revision) mutableState.update { it.copy(verifying = false) } }
        }
    }

    fun consumeInstall() { mutableState.update { it.copy(installReady = null) } }
    fun cancel() {
        revision++
        operation?.cancel()
        mutableState.update { it.copy(checking = false, downloading = false, verifying = false, installReady = null) }
    }
    fun error(message: String) { mutableState.update { it.copy(error = message) } }
    private fun readable(error: Exception, fallback: String) = if (error is IllegalArgumentException) error.message ?: fallback else fallback
}
