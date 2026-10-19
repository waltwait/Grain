package tw.luma.camera.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import tw.luma.camera.LutEntry
import tw.luma.camera.camera.ZoomControls
import tw.luma.camera.editor.PhotoPreviewView
import tw.luma.camera.editor.VideoEditorUiState
import tw.luma.camera.editor.VideoEditorViewModel
import tw.luma.camera.lut.FilterGroup
import kotlin.math.roundToInt

/** The edit tab with videos picked: a frame of the chosen clip shows the film, and saving converts every clip to MP4. */
@Composable
internal fun VideoEditorBody(state: VideoEditorUiState, model: VideoEditorViewModel, entries: List<LutEntry>, modifier: Modifier) {
    val view = LocalView.current
    // A long conversion must not be cut short by the screen turning off.
    DisposableEffect(view, state.saving) {
        view.keepScreenOn = state.saving
        onDispose { view.keepScreenOn = false }
    }
    Column(modifier.testTag("video-editor")) {
        if (state.sources.size > 1 || state.batch != null) VideoBatchControls(state, model)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val panelHeight = minOf(280.dp, maxHeight * .42f)
            if (state.sources.isEmpty()) VideoMessage(state.error, Modifier.fillMaxSize())
            else if (state.convertOnly) VideoPreview(state, model, Modifier.fillMaxSize())
            else if (maxWidth >= 700.dp && maxWidth > maxHeight) Row(Modifier.fillMaxSize()) {
                VideoPreview(state, model, Modifier.weight(1f).fillMaxHeight())
                VideoFilters(state, entries, model, Modifier.width(320.dp).fillMaxHeight())
            } else Column(Modifier.fillMaxSize()) {
                VideoPreview(state, model, Modifier.weight(1f).fillMaxWidth())
                VideoFilters(state, entries, model, Modifier.fillMaxWidth().heightIn(max = panelHeight))
            }
        }
    }
}

@Composable
private fun VideoMessage(message: String?, modifier: Modifier) {
    Box(modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        Text(message.orEmpty(), Modifier.testTag("video-editor-error"), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun VideoBatchControls(state: VideoEditorUiState, model: VideoEditorViewModel) {
    val strip = rememberLazyListState()
    LaunchedEffect(state.sources, state.previewIndex) { strip.animateScrollToItem(state.previewIndex) }
    Column(Modifier.fillMaxWidth()) {
        if (state.sources.size > 1) LazyRow(state = strip, modifier = Modifier.nestedScroll(EditorHorizontalScroll),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(state.sources, key = { _, uri -> uri }) { index, uri ->
                val chosen = index == state.previewIndex
                Surface(onClick = { model.preview(index) }, enabled = !state.saving,
                    shape = MaterialTheme.shapes.small, color = GrainSurfaces.raised,
                    border = BorderStroke(if (chosen) 2.dp else 1.dp, if (chosen) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = .15f)),
                    modifier = Modifier.size(56.dp).testTag("video-source-$index").semantics {
                        selected = chosen
                        contentDescription = "預覽影片 ${index + 1}，共 ${state.sources.size} 支"
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
                Text(batch.status, Modifier.weight(1f).testTag("video-batch-status"), style = MaterialTheme.typography.labelMedium)
                if (batch.running) IconButton(onClick = model::stop, enabled = !batch.stopping,
                    modifier = Modifier.size(48.dp).testTag("video-batch-stop").semantics { contentDescription = "停止轉檔" }) {
                    CameraGlyph("close", Modifier.size(20.dp), LocalContentColor.current)
                } else Spacer(Modifier.height(48.dp))
            }
            if (batch.running) LinearProgressIndicator(progress = { (batch.saved.size + batch.progress.coerceIn(0f, 1f)) / batch.total },
                modifier = Modifier.fillMaxWidth().height(2.dp).testTag("video-batch-progress"))
        }
    }
}

@Composable
private fun VideoPreview(state: VideoEditorUiState, model: VideoEditorViewModel, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        val poster = state.poster
        if (poster != null) {
            VideoPoster(state, model, Modifier.fillMaxSize())
            if (!state.convertOnly) {
                Row(Modifier.align(Alignment.TopEnd).padding(8.dp).background(Color.Black.copy(alpha = PHOTO_OVERLAY_SCRIM_ALPHA), RoundedCornerShape(12.dp))) {
                    TextButton(onClick = model::compare, enabled = state.canEdit,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("video-compare").semantics { selected = state.comparing }) {
                        Text(if (state.comparing) "濾鏡" else "原圖")
                    }
                }
            }
            Text("▶ ${ZoomControls.recordingTime(state.durationMs.coerceAtLeast(0) * 1_000_000)}",
                Modifier.align(Alignment.BottomStart).padding(8.dp).background(Color.Black.copy(alpha = .7f), RoundedCornerShape(5.dp))
                    .padding(horizontal = 7.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = Color.White)
        } else if (state.loading) CircularProgressIndicator(Modifier.size(28.dp))
        state.error?.let { message ->
            Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = .7f)).padding(horizontal = 12.dp, vertical = 12.dp)) {
                Text(message, Modifier.testTag("video-editor-error"), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun VideoPoster(state: VideoEditorUiState, model: VideoEditorViewModel, modifier: Modifier) {
    val lifecycle = LocalLifecycleOwner.current
    var view by remember { mutableStateOf<PhotoPreviewView?>(null) }
    AndroidView(factory = { context -> PhotoPreviewView(context, { _, _ -> }, { model.previewFailed() }).also { view = it } },
        modifier = modifier.testTag("video-editor-preview").semantics {
            contentDescription = if (state.convertOnly) "影片預覽" else "影片濾鏡預覽"
            stateDescription = if (state.convertOnly || state.comparing) "原圖" else "濾鏡預覽"
        }, onRelease = { it.releasePreview() }, update = {
            state.poster?.let { poster -> it.submit(poster, state.previewSettings, state.revision) }
        })
    DisposableEffect(view, lifecycle) {
        val surface = view
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) surface?.onResume()
            if (event == Lifecycle.Event.ON_PAUSE) surface?.onPause()
        }
        lifecycle.lifecycle.addObserver(observer)
        if (lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) surface?.onResume()
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
}

@Composable
private fun VideoFilters(state: VideoEditorUiState, entries: List<LutEntry>, model: VideoEditorViewModel, modifier: Modifier) {
    val active = remember(entries, state.selectedId) { entries.find { it.id == state.selectedId } }
    var group by rememberSaveable { mutableStateOf(FilterGroup.initial(entries, state.selectedId)) }
    val visible = remember(entries, group) { group.entries(entries) }
    // Keep horizontal filter gestures inside this panel, even at the carousel's ends.
    Surface(modifier.nestedScroll(EditorHorizontalScroll), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 4.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(active?.let(FilterGroup::title) ?: NO_FILTER_LABEL, Modifier.weight(1f).testTag("video-filter-summary"),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = { model.selectLut(null) }, enabled = state.canEdit,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("video-original").semantics { selected = state.selectedId == null }) { Text(NO_FILTER_LABEL) }
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items(FilterGroup.entries) { item ->
                    FilterChip(item == group, { group = item }, { Text(item.label) }, enabled = state.canEdit, colors = grainChipColors(),
                        modifier = Modifier.heightIn(min = 48.dp).testTag("video-group-${item.name}"))
                }
            }
            if (visible.isEmpty()) Box(Modifier.fillMaxWidth().heightIn(min = 72.dp), contentAlignment = Alignment.Center) {
                Text("尚無濾鏡", style = MaterialTheme.typography.bodyMedium)
            } else key(group, visible.map { it.id }) {
                FilmPicker(visible, state.selectedId, state.canEdit, true, model::selectLut)
            }
            if (active != null) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("強度", style = MaterialTheme.typography.labelMedium)
                HorizontalControlSlider(state.filter.strength, model::strength, 0f..1f, 0, state.canEdit,
                    Modifier.weight(1f).padding(horizontal = 8.dp).testTag("video-strength").semantics { contentDescription = "濾鏡強度" })
                Text("${(state.filter.strength * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
