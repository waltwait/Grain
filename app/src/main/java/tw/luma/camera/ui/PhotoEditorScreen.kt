package tw.luma.camera.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import tw.luma.camera.LutEntry
import tw.luma.camera.editor.PhotoEditorUiState
import tw.luma.camera.editor.PhotoEditorViewModel
import tw.luma.camera.editor.PhotoPreviewView
import tw.luma.camera.lut.FilterGroup
import kotlin.math.roundToInt

@Composable
internal fun PhotoEditorBody(state: PhotoEditorUiState, model: PhotoEditorViewModel, entries: List<LutEntry>,
    choosePhoto: (() -> Unit)?, retryPhoto: () -> Unit, onSaved: (Uri) -> Unit, modifier: Modifier) {
    BoxWithConstraints(modifier) {
        val panelHeight = minOf(280.dp, maxHeight * .42f)
        if (maxWidth >= 700.dp && maxWidth > maxHeight) {
            Row(Modifier.fillMaxSize()) {
                EditorPreview(state, model, choosePhoto, retryPhoto, onSaved, Modifier.weight(1f).fillMaxHeight())
                EditorFilters(state, entries, model, Modifier.width(320.dp).fillMaxHeight())
            }
        } else Column(Modifier.fillMaxSize()) {
            EditorPreview(state, model, choosePhoto, retryPhoto, onSaved, Modifier.weight(1f).fillMaxWidth())
            EditorFilters(state, entries, model, Modifier.fillMaxWidth().heightIn(max = panelHeight))
        }
    }
}

@Composable
private fun EditorPreview(state: PhotoEditorUiState, model: PhotoEditorViewModel, choosePhoto: (() -> Unit)?,
    retryPhoto: () -> Unit, onSaved: (Uri) -> Unit, modifier: Modifier) {
    var retry by rememberSaveable { mutableIntStateOf(0) }
    Box(modifier, contentAlignment = Alignment.Center) {
        val bitmap = state.bitmap
        if (bitmap != null) {
            key(retry) { PhotoPreview(state, model, Modifier.fillMaxSize()) }
            Row(Modifier.align(Alignment.TopEnd).padding(8.dp).background(Color.Black.copy(alpha = .4f), RoundedCornerShape(12.dp))) {
                TextButton(onClick = model::compare, enabled = state.canEdit,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("editor-compare").semantics { selected = state.selection.comparing }) {
                    Text(if (state.selection.comparing) "濾鏡" else "原圖")
                }
                if (choosePhoto != null) TextButton(onClick = choosePhoto, enabled = !state.saving,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("editor-replace")) { Text("換照片") }
            }
            state.error?.let { message ->
                Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = .7f)).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { if (state.canSave) model.save(onSaved) else retry++ }, enabled = !state.saving) { Text("重試") }
                }
            }
        } else if (state.loading) CircularProgressIndicator(Modifier.size(28.dp))
        else Column(horizontalAlignment = Alignment.CenterHorizontally) {
            state.error?.let { Text(it, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium) }
            FilledTonalButton(onClick = choosePhoto ?: retryPhoto, modifier = Modifier.testTag("editor-choose")) {
                Text(if (choosePhoto != null) "選擇照片" else "重試")
            }
        }
    }
}

@Composable
private fun PhotoPreview(state: PhotoEditorUiState, model: PhotoEditorViewModel, modifier: Modifier) {
    val lifecycle = LocalLifecycleOwner.current
    var view by remember { mutableStateOf<PhotoPreviewView?>(null) }
    AndroidView(factory = { context -> PhotoPreviewView(context, model::presented,
        { revision -> model.previewError(revision, "預覽暫時無法顯示") }).also { view = it } },
        modifier = modifier.testTag("photo-editor-preview").semantics {
            contentDescription = "照片濾鏡預覽"
            stateDescription = if (state.selection.comparing) "原圖" else "濾鏡預覽"
        }, onRelease = { it.releasePreview() }, update = {
            state.bitmap?.let { bitmap -> it.submit(bitmap, state.selection.previewSettings, state.selection.revision) }
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
private fun EditorFilters(state: PhotoEditorUiState, entries: List<LutEntry>, model: PhotoEditorViewModel, modifier: Modifier) {
    val selection = state.selection
    val active = remember(entries, selection.selectedId) { entries.find { it.id == selection.selectedId } }
    var group by rememberSaveable { mutableStateOf(FilterGroup.initial(entries, selection.selectedId)) }
    LaunchedEffect(selection.selectedId) { active?.let { group = FilterGroup.of(it) } }
    val visible = remember(entries, group) { group.entries(entries) }
    Surface(modifier, color = Color(0xFF18191C)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 4.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(active?.let(FilterGroup::title) ?: "原色", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = { model.selectLut(null) }, enabled = state.canEdit,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("editor-original").semantics { selected = selection.selectedId == null }) { Text("原色") }
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items(FilterGroup.entries) { item ->
                    FilterChip(item == group, { group = item }, { Text(item.label) }, enabled = state.canEdit,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("editor-group-${item.name}"))
                }
            }
            if (visible.isEmpty()) Box(Modifier.fillMaxWidth().heightIn(min = 72.dp), contentAlignment = Alignment.Center) {
                Text("尚無濾鏡", style = MaterialTheme.typography.bodyMedium)
            } else key(group, visible.map { it.id }) {
                FilmPicker(visible, selection.selectedId, state.canEdit, true, model::selectLut)
            }
            if (active != null) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("強度", style = MaterialTheme.typography.labelMedium)
                HorizontalControlSlider(selection.filter.strength, model::strength, 0f..1f, 0, state.canEdit,
                    Modifier.weight(1f).padding(horizontal = 8.dp).testTag("editor-strength").semantics { contentDescription = "濾鏡強度" })
                Text("${(selection.filter.strength * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
