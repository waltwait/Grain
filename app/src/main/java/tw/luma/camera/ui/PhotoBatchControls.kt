package tw.luma.camera.ui

import android.graphics.Bitmap
import android.net.Uri
import android.os.CancellationSignal
import android.util.Size
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tw.luma.camera.editor.PhotoEditorUiState
import tw.luma.camera.editor.PhotoEditorViewModel

@Composable
internal fun PhotoBatchControls(state: PhotoEditorUiState, model: PhotoEditorViewModel, onSaved: (Uri) -> Unit) {
    val strip = rememberLazyListState()
    LaunchedEffect(state.sources, state.previewIndex) { strip.animateScrollToItem(state.previewIndex) }
    Column(Modifier.fillMaxWidth()) {
        LazyRow(state = strip, modifier = Modifier.nestedScroll(EditorHorizontalScroll),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(state.sources, key = { _, uri -> uri }) { index, uri ->
                val chosen = index == state.previewIndex
                Surface(onClick = { model.preview(index) }, enabled = !state.saving,
                    shape = MaterialTheme.shapes.small, color = GrainSurfaces.raised,
                    border = BorderStroke(if (chosen) 2.dp else 1.dp, if (chosen) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = .15f)),
                    modifier = Modifier.size(56.dp).testTag("editor-source-$index").semantics {
                        selected = chosen
                        contentDescription = "預覽照片 ${index + 1}，共 ${state.sources.size} 張"
                    }) {
                    Box {
                        BatchThumbnail(uri, Modifier.fillMaxSize())
                        Text("${index + 1}", Modifier.align(Alignment.BottomEnd).background(Color.Black.copy(alpha = .6f))
                            .padding(horizontal = 5.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        state.batch?.let { batch ->
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                val label = when {
                    batch.running && batch.cancelRequested -> "正在停止 · ${batch.savedCount} / ${batch.items.size}"
                    batch.running -> "正在處理 ${(batch.currentIndex ?: 0) + 1} / ${batch.items.size}"
                    batch.stopped -> "已停止 · 已儲存 ${batch.savedCount} 張"
                    batch.failedCount > 0 -> "已儲存 ${batch.savedCount} 張 · 失敗 ${batch.failedCount} 張"
                    else -> "已儲存 ${batch.savedCount} 張"
                }
                Text(label, Modifier.weight(1f).testTag("editor-batch-status"), style = MaterialTheme.typography.labelMedium)
                if (batch.running) IconButton(onClick = model::cancelBatch, enabled = !batch.cancelRequested,
                    modifier = Modifier.size(48.dp).testTag("editor-batch-cancel").semantics { contentDescription = "停止批次處理" }) {
                    CameraGlyph("close", Modifier.size(20.dp), LocalContentColor.current)
                } else if (batch.remainingCount > 0) {
                    TextButton(onClick = { model.save(onSaved) }, enabled = state.canSave,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("editor-batch-retry")) {
                        Text(if (batch.stopped) "繼續" else "重試")
                    }
                } else Spacer(Modifier.height(36.dp))
            }
            if (batch.running) LinearProgressIndicator(progress = { batch.processedCount.toFloat() / batch.items.size },
                modifier = Modifier.fillMaxWidth().height(2.dp).testTag("editor-batch-progress"))
        }
    }
}

@Composable
private fun BatchThumbnail(source: String, modifier: Modifier) {
    val context = LocalContext.current
    var bitmap by remember(source) { mutableStateOf<Bitmap?>(null) }
    val cancellation = remember(source) { CancellationSignal() }
    DisposableEffect(cancellation) { onDispose { cancellation.cancel() } }
    LaunchedEffect(source) {
        try {
            bitmap = withContext(Dispatchers.IO) { context.contentResolver.loadThumbnail(Uri.parse(source), Size(128, 128), cancellation) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* Unsupported providers still show the numbered selection. */ }
    }
    Box(modifier.background(GrainSurfaces.raised)) {
        bitmap?.let { Image(remember(it) { it.asImageBitmap() }, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}
