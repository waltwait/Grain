package tw.luma.camera.ui

import android.content.ClipData
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.withResumed
import kotlinx.coroutines.CancellationException
import tw.luma.camera.BuildConfig
import tw.luma.camera.update.AppUpdateViewModel
import kotlin.math.roundToInt

@Composable
fun AppUpdateScreen(model: AppUpdateViewModel, back: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var permissionPending by rememberSaveable { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (permissionPending) {
            permissionPending = false
            if (context.packageManager.canRequestPackageInstalls()) model.prepareInstall()
            else model.error("尚未允許 Grain 安裝更新")
        }
    }
    val close = { model.cancel(); back() }
    BackHandler(onBack = close)
    LaunchedEffect(state.installReady) {
        val file = state.installReady ?: return@LaunchedEffect
        try {
            owner.lifecycle.withResumed {
                model.consumeInstall()
                val uri = FileProvider.getUriForFile(context, context.packageName + ".updates", file)
                context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    clipData = ClipData.newRawUri("Grain update", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                })
            }
        } catch (cancelled: CancellationException) { throw cancelled }
          catch (_: Exception) { model.consumeInstall(); model.error("無法開啟 Android 安裝畫面") }
    }
    Column(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding().testTag("app-update-screen")) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            BackIconButton(onClick = close, modifier = Modifier.testTag("update-back"))
            Text("版本與更新", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("Grain", style = MaterialTheme.typography.headlineLarge)
            Text("目前版本 " + BuildConfig.VERSION_NAME, color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.info?.let { info ->
                Text(if (state.available) "新版 " + info.versionName else "已是最新版", style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.testTag("update-version"))
                tw.luma.camera.update.UpdatePrompt.notesToShow(state.available, info.notes)?.let {
                    Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (state.downloading) {
                LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth().testTag("update-progress"))
                Text((state.progress * 100).roundToInt().toString() + "%", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = model::cancel) { Text("取消下載") }
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("update-error")) }
            if (!state.downloading) {
                Button(onClick = {
                    when {
                        BuildConfig.UPDATE_FEED_URL.isBlank() -> {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW,
                                    "https://github.com/waltwait/Grain/releases/latest".toUri()))
                            }.onFailure { model.error("無法開啟 GitHub 下載頁") }
                        }
                        state.download != null && state.available -> {
                            if (context.packageManager.canRequestPackageInstalls()) model.prepareInstall()
                            else {
                                permissionPending = true
                                runCatching { permission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, ("package:" + context.packageName).toUri())) }
                                    .onFailure { permissionPending = false; model.error("無法開啟安裝來源設定") }
                            }
                        }
                        state.available -> model.download()
                        else -> model.check()
                    }
                }, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("update-primary")) {
                    if (state.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text(when { state.download != null && state.available -> "安裝"; state.available -> "下載新版"; else -> "檢查更新" })
                }
                if (state.info != null && state.available) TextButton(onClick = model::check, enabled = !state.busy) { Text("重新檢查") }
            }
        }
    }
}
