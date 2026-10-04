package tw.luma.camera.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tw.luma.camera.LutEntry
import tw.luma.camera.camera.ZoomControls
import tw.luma.camera.editor.PhotoEditorViewModel
import tw.luma.camera.editor.PhotoBatchProgress
import tw.luma.camera.gallery.GalleryItem
import tw.luma.camera.gallery.GalleryViewModel
import tw.luma.camera.gallery.PhotoViewport
import kotlin.math.min

@Composable
internal fun GalleryScreen(model: GalleryViewModel, close: () -> Unit,
    editor: PhotoEditorViewModel, entries: List<LutEntry>, onSaved: (Uri) -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val importEditor: PhotoEditorViewModel = viewModel(key = "gallery-import-editor")
    val importState by importEditor.state.collectAsStateWithLifecycle()
    val tabs = rememberSaveableStateHolder()
    val tabPager = rememberPagerState { 2 }
    val tabScope = rememberCoroutineScope()
    val editing = tabPager.currentPage == 1
    val changeTab: (Boolean) -> Unit = { next ->
        if (!importState.saving) tabScope.launch { tabPager.animateScrollToPage(if (next) 1 else 0) }
    }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedEdit by rememberSaveable { mutableStateOf(false) }
    val grid = rememberLazyGridState()
    val photoImport = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(PhotoBatchProgress.MAX_PHOTOS)) { uris ->
        importEditor.openBatch(uris)
    }
    val choosePhoto = { photoImport.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    LaunchedEffect(entries) { importEditor.setLuts(entries) }
    // Unfinished drafts survive tab switches, but a fully exported import starts fresh once the editor tab is left.
    LaunchedEffect(tabPager.settledPage) {
        if (tabPager.settledPage == 0 && importEditor.state.value.finished) importEditor.discard()
    }
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(model, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) model.refresh() }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer); model.releasePhotos() }
    }
    val items = remember(state.items, filter) { state.items.filter { filter == 0 || it.video == (filter == 2) } }
    val viewerItems = if (selectedEdit) state.edits else items
    val selectedIndex = viewerItems.indexOfFirst { it.uri.toString() == selected }
    val goBack: () -> Unit = {
        if (!importState.saving) {
            if (editing || tabPager.targetPage == 1) changeTab(false)
            else { importEditor.discard(); editor.discard(); close() }
        }
    }
    BackHandler(enabled = selectedIndex < 0, onBack = goBack)
    Box(Modifier.fillMaxSize().background(Color.Black).testTag("grain-gallery")) {
        if (selectedIndex >= 0) {
            GalleryViewer(viewerItems, selectedIndex, model, editor, entries, { selected = null }, onSaved)
        } else Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                BackIconButton(onClick = goBack, enabled = !importState.saving,
                    modifier = Modifier.align(Alignment.CenterStart).testTag("gallery-close"),
                    description = if (editing) "返回照片" else "返回相機")
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Grain", modifier = Modifier.testTag("gallery-title"), fontFamily = NewsreaderBrand, style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold, color = Color(0xFFF5F3EB))
                    if (!editing) Text("${state.items.size}", style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = .78f))
                    else if (importState.isBatch) Text("${importState.previewIndex + 1} / ${importState.sources.size}",
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = .78f))
                }
                if (editing) IconButton(onClick = { importEditor.save(onSaved) }, enabled = importState.canSave && !tabPager.isScrollInProgress,
                    modifier = Modifier.align(Alignment.CenterEnd).size(48.dp).testTag("editor-save")
                        .semantics { contentDescription = if (importState.isBatch) "批次儲存照片" else "儲存新照片" }) {
                    if (importState.saving) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    else CameraGlyph("check", Modifier.size(24.dp), LocalContentColor.current)
                }
            }
            HorizontalPager(state = tabPager, userScrollEnabled = !importState.saving,
                key = { if (it == 0) "photos" else "editor" }, beyondViewportPageCount = 0,
                modifier = Modifier.weight(1f).fillMaxWidth().testTag("gallery-tabs-pager")) { page ->
                if (page == 1) tabs.SaveableStateProvider("editor") {
                    if (importState.bitmap == null && !importState.loading && importState.error == null) {
                        EditedHome(state.edits, model, choosePhoto) { item -> selected = item.uri.toString(); selectedEdit = true }
                    } else Column(Modifier.fillMaxSize().testTag("photo-editor")) {
                        if (importState.isBatch) PhotoBatchControls(importState, importEditor, onSaved)
                        PhotoEditorBody(importState, importEditor, entries, choosePhoto,
                            { importEditor.preview(importState.previewIndex) }, onSaved, Modifier.weight(1f).fillMaxWidth())
                    }
                } else tabs.SaveableStateProvider("photos") {
                    Column(Modifier.fillMaxSize()) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("全部", "照片", "影片").forEachIndexed { index, text ->
                                FilterChip(selected = filter == index, onClick = { filter = index }, label = { Text(text) }, modifier = Modifier.testTag("gallery-filter-$index"))
                            }
                        }
                        when {
                            state.error != null -> GalleryEmpty(state.error!!, "重試", model::refresh)
                            state.loading && items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                            items.isEmpty() -> GalleryEmpty(if (filter == 2) "還沒有影片" else if (filter == 1) "還沒有照片" else "把下一個片刻留在 Grain", "編輯照片", { changeTab(true) })
                            else -> LazyVerticalGrid(columns = GridCells.Adaptive(112.dp), state = grid,
                                contentPadding = PaddingValues(2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                items(items, key = { it.uri.toString() }, contentType = { "media" }) { item ->
                                    Surface(onClick = { selected = item.uri.toString(); selectedEdit = false }, shape = RoundedCornerShape(2.dp), color = Color(0xFF18191C),
                                        modifier = Modifier.aspectRatio(1f).testTag("gallery-item").semantics { contentDescription = "${if (item.video) "影片" else "照片"} ${item.name}" }) {
                                        Box {
                                            GalleryThumbnail(item, model, Modifier.fillMaxSize())
                                            if (item.video) Text("▶ ${durationLabel(item.durationMs)}", Modifier.align(Alignment.BottomEnd).padding(5.dp).background(Color.Black.copy(alpha = .7f), RoundedCornerShape(5.dp)).padding(horizontal = 7.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = Color.White)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            PhotoEditTabs(editing, !importState.saving, changeTab, "gallery-photo-tab", "gallery-edit-tab",
                Modifier.align(Alignment.CenterHorizontally).padding(vertical = 6.dp))
        }
    }
}

@Composable
private fun GalleryEmpty(message: String, action: String, click: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(message, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = click) { Text(action) }
    }
}

@Composable
internal fun GalleryThumbnail(item: GalleryItem, model: GalleryViewModel, modifier: Modifier, contentScale: ContentScale = ContentScale.Crop) {
    var bitmap by remember(item.uri) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(item.uri) { mutableStateOf(false) }
    LaunchedEffect(item.uri) {
        try { bitmap = model.thumbnail(item) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { failed = true }
    }
    Box(modifier.background(Color(0xFF18191C)), contentAlignment = Alignment.Center) {
        bitmap?.let { Image(remember(it) { it.asImageBitmap() }, null, Modifier.fillMaxSize(), contentScale = contentScale) }
        if (failed) Text("無法讀取", color = Color.White.copy(alpha = .5f), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun GalleryViewer(items: List<GalleryItem>, initialPage: Int, model: GalleryViewModel,
    editor: PhotoEditorViewModel, entries: List<LutEntry>, back: () -> Unit, onSaved: (Uri) -> Unit) {
    val pager = rememberPagerState(initialPage = initialPage) { items.size }
    val positions = remember { mutableMapOf<String, Long>() }
    val savedTabs = rememberSaveableStateHolder()
    val editState by editor.state.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf(false) }
    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    var originalShownFor by rememberSaveable { mutableStateOf<String?>(null) }
    var currentUri by rememberSaveable { mutableStateOf(items[initialPage].uri.toString()) }
    val latestItems by rememberUpdatedState(items)
    // Keep the same source photo selected when saving inserts a new item at the start of the album.
    LaunchedEffect(pager) {
        var orderedUris = latestItems.map { it.uri.toString() }
        val select = { uri: String ->
            originalShownFor = OriginalChoice.afterCurrentChanged(originalShownFor, currentUri, uri)
            currentUri = uri
        }
        snapshotFlow { latestItems to pager.settledPage }.collect { (nextItems, page) ->
            val nextUris = nextItems.map { it.uri.toString() }
            if (orderedUris != nextUris) {
                val index = nextUris.indexOf(currentUri).takeIf { it >= 0 } ?: page.coerceIn(nextItems.indices)
                orderedUris = nextUris
                select(nextUris[index])
                pager.scrollToPage(index)
            } else select(nextUris[page.coerceIn(nextItems.indices)])
        }
    }
    val current = items.firstOrNull { it.uri.toString() == currentUri } ?: items[pager.settledPage.coerceIn(items.indices)]
    LaunchedEffect(entries) { editor.setLuts(entries) }
    LaunchedEffect(editing, current.uri) {
        if (current.video) editing = false
        else if (editing) editor.openForViewer(current.uri)
    }
    val goBack: () -> Unit = {
        if (!editState.saving) {
            if (editing) editing = false else { editor.discard(); back() }
        }
    }
    BackHandler(onBack = goBack)
    Box(Modifier.fillMaxSize()) {
        if (editing) savedTabs.SaveableStateProvider("editor") {
            PhotoEditorBody(editState, editor, entries, null, { editor.openForViewer(current.uri) }, onSaved,
                Modifier.fillMaxSize().safeDrawingPadding().padding(top = 56.dp, bottom = 64.dp).testTag("photo-editor"))
        } else savedTabs.SaveableStateProvider("photo") {
            HorizontalPager(state = pager, userScrollEnabled = !zoomed && !editState.saving,
                key = { items[it].uri.toString() }, beyondViewportPageCount = 0,
                modifier = Modifier.fillMaxSize().testTag("gallery-pager")) { page ->
                val active = page == pager.settledPage
                val item = items[page].let {
                    if (active && it.original != null && OriginalChoice.isShown(originalShownFor, it.uri.toString())) it.copy(uri = it.original, original = null) else it
                }
                key(item.uri) {
                    if (item.video && active) GalleryVideo(item, positions[item.uri.toString()] ?: 0) { positions[item.uri.toString()] = it }
                    else if (item.video) GalleryThumbnail(item, model, Modifier.fillMaxSize())
                    else GalleryPhoto(item, model, active, controlsVisible, { controlsVisible = !controlsVisible }, { zoomed = it })
                }
            }
        }
        val chromeVisible = editing || current.video || controlsVisible
        AnimatedVisibility(chromeVisible, Modifier.align(Alignment.TopCenter), enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .8f), Color.Transparent)))
                .statusBarsPadding().displayCutoutPadding().padding(horizontal = 8.dp, vertical = 4.dp)) {
                BackIconButton(onClick = goBack, enabled = !editState.saving,
                    modifier = Modifier.align(Alignment.CenterStart).testTag(if (editing) "editor-back" else "viewer-back"),
                    description = if (editing) "返回照片" else "返回相簿")
                Text("${items.indexOf(current) + 1} / ${items.size}", Modifier.align(Alignment.Center),
                    color = Color.White.copy(alpha = .8f), style = MaterialTheme.typography.labelLarge)
                if (editing) IconButton(onClick = { editor.save(onSaved) }, enabled = editState.canSave,
                    modifier = Modifier.align(Alignment.CenterEnd).size(48.dp).testTag("editor-save")
                        .semantics { contentDescription = if (editState.savedUri != null && editState.selection.savedFilter == editState.selection.filter) "已儲存" else "儲存新照片" }) {
                    if (editState.saving) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    else CameraGlyph("check", Modifier.size(24.dp), LocalContentColor.current)
                } else if (current.original != null) TextButton(onClick = { originalShownFor = OriginalChoice.toggle(originalShownFor, current.uri.toString()) },
                    modifier = Modifier.align(Alignment.CenterEnd).heightIn(min = 48.dp).testTag("viewer-original-toggle")) {
                    Text(if (OriginalChoice.isShown(originalShownFor, current.uri.toString())) "看改完" else "看原圖")
                }
            }
        }
        AnimatedVisibility(!current.video && chromeVisible, Modifier.align(Alignment.BottomCenter), enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .9f))))
                .navigationBarsPadding().displayCutoutPadding().padding(top = 8.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
                PhotoEditTabs(editing, !editState.saving && !pager.isScrollInProgress, { next ->
                    if (next) editor.openForViewer(current.uri)
                    editing = next
                    controlsVisible = true
                }, "viewer-photo-tab", "viewer-edit")
            }
        }
    }
}

@Composable
private fun PhotoEditTabs(editing: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit,
    photoTag: String, editTag: String, modifier: Modifier = Modifier) {
    Row(modifier.widthIn(max = 300.dp).fillMaxWidth().selectableGroup()) {
        listOf("照片", "編輯").forEachIndexed { index, label ->
            val selected = editing == (index == 1)
            Tab(selected = selected, onClick = { onChange(index == 1) }, enabled = enabled,
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContentColor = Color.White.copy(alpha = .6f),
                modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag(if (index == 0) photoTag else editTag)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.labelLarge)
                    Box(Modifier.width(24.dp).height(2.dp).background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(1.dp)))
                }
            }
        }
    }
}

private val PanSaver = Saver<Offset, List<Float>>(save = { listOf(it.x, it.y) }, restore = { Offset(it[0], it[1]) })

@Composable
private fun GalleryPhoto(item: GalleryItem, model: GalleryViewModel, active: Boolean, controlsVisible: Boolean,
    onTap: () -> Unit, onZoom: (Boolean) -> Unit) {
    var bitmap by remember(item.uri) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(item.uri) { mutableStateOf(false) }
    var scale by rememberSaveable(item.uri.toString()) { mutableFloatStateOf(1f) }
    var pan by rememberSaveable(item.uri.toString(), stateSaver = PanSaver) { mutableStateOf(Offset.Zero) }
    val tapped by rememberUpdatedState(onTap)
    val zoomChanged by rememberUpdatedState(onZoom)
    LaunchedEffect(active, item.uri) {
        if (active) {
            zoomChanged(scale > 1.01f)
            try { bitmap = model.photo(item); failed = false }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { failed = true }
        } else { scale = 1f; pan = Offset.Zero; bitmap = null }
    }
    BoxWithConstraints(Modifier.fillMaxSize().clip(RoundedCornerShape(0.dp)), contentAlignment = Alignment.Center) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val viewportW = with(density) { maxWidth.toPx() }
        val viewportH = with(density) { maxHeight.toPx() }
        val image = bitmap
        if (image != null) {
            val fit = min(viewportW / image.width, viewportH / image.height)
            LaunchedEffect(viewportW, viewportH, image) {
                pan = Offset(PhotoViewport.clampPan(pan.x, image.width * fit * scale, viewportW),
                    PhotoViewport.clampPan(pan.y, image.height * fit * scale, viewportH))
            }
            val transform = rememberTransformableState { centroid, zoom, offset, _ ->
                val nextScale = (scale * zoom).coerceIn(1f, 5f)
                val ratio = nextScale / scale
                val anchor = if (centroid.x.isFinite() && centroid.y.isFinite()) centroid else Offset(viewportW / 2, viewportH / 2)
                pan = Offset(
                    PhotoViewport.clampPan(PhotoViewport.zoomPan(pan.x, anchor.x - viewportW / 2, ratio, offset.x), image.width * fit * nextScale, viewportW),
                    PhotoViewport.clampPan(PhotoViewport.zoomPan(pan.y, anchor.y - viewportH / 2, ratio, offset.y), image.height * fit * nextScale, viewportH),
                )
                scale = nextScale
                zoomChanged(scale > 1.01f)
            }
            Image(remember(image) { image.asImageBitmap() }, "照片 ${item.name}", contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().transformable(transform, enabled = active, canPan = { scale > 1.01f })
                    .pointerInput(item.uri, active) {
                        if (active) detectTapGestures(onTap = { tapped() }, onDoubleTap = { scale = 1f; pan = Offset.Zero; zoomChanged(false) })
                    }
                    .semantics {
                        stateDescription = if (scale > 1.01f) "已放大" else "原始比例"
                        customActions = if (!active) emptyList() else listOf(
                            CustomAccessibilityAction(if (scale > 1.01f) "還原比例" else "放大照片") {
                                scale = if (scale > 1.01f) 1f else 2f
                                pan = Offset.Zero
                                zoomChanged(scale > 1.01f)
                                true
                            },
                            CustomAccessibilityAction(if (controlsVisible) "隱藏工具列" else "顯示工具列") { tapped(); true },
                        )
                    }
                    .graphicsLayer { scaleX = scale; scaleY = scale; translationX = pan.x; translationY = pan.y }.testTag("gallery-photo"))
        } else {
            GalleryThumbnail(item, model, Modifier.fillMaxSize(), ContentScale.Fit)
            if (active && !failed) CircularProgressIndicator(Modifier.size(28.dp))
            if (failed) Text("照片無法讀取，請返回相簿重新整理", Modifier.background(Color.Black.copy(alpha = .8f)).padding(16.dp), color = Color.White)
        }
    }
}

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@Composable
private fun GalleryVideo(item: GalleryItem, resumeMs: Long, savePosition: (Long) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val view = LocalView.current
    val player = remember(item.uri) { ExoPlayer.Builder(context).build().apply {
        setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
        setHandleAudioBecomingNoisy(true)
        setMediaItem(MediaItem.fromUri(item.uri)); seekTo(resumeMs); prepare()
    } }
    val save by rememberUpdatedState(savePosition)
    var error by remember(player) { mutableStateOf<String?>(null) }
    var playing by remember(player) { mutableStateOf(false) }
    var buffering by remember(player) { mutableStateOf(player.playbackState == Player.STATE_BUFFERING) }
    DisposableEffect(player, lifecycle) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlaybackStateChanged(state: Int) { buffering = state == Player.STATE_BUFFERING }
            override fun onPlayerError(failure: PlaybackException) { error = "影片無法播放，請返回相簿重新整理" }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> player.pause()
                Lifecycle.Event.ON_STOP -> player.stop()
                Lifecycle.Event.ON_START -> if (player.playbackState == Player.STATE_IDLE) player.prepare()
                else -> Unit
            }
        }
        player.addListener(listener)
        lifecycle.lifecycle.addObserver(observer)
        onDispose { save(player.currentPosition.coerceAtLeast(0)); lifecycle.lifecycle.removeObserver(observer); player.removeListener(listener); player.release() }
    }
    SideEffect { view.keepScreenOn = playing }
    DisposableEffect(view) { onDispose { view.keepScreenOn = false } }
    Column(Modifier.fillMaxSize().safeDrawingPadding().testTag("gallery-video")) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            ContentFrame(player, Modifier.fillMaxSize(), surfaceType = SURFACE_TYPE_TEXTURE_VIEW)
            if (buffering && error == null) CircularProgressIndicator(Modifier.size(28.dp))
            error?.let { Text(it, Modifier.padding(16.dp), color = Color.White) }
        }
        VideoControls(player, playing, error == null)
    }
}

@Composable
private fun VideoControls(player: Player, playing: Boolean, enabled: Boolean) {
    val lifecycle = LocalLifecycleOwner.current
    var position by remember(player) { mutableLongStateOf(0) }
    var duration by remember(player) { mutableLongStateOf(0) }
    var drag by remember(player) { mutableStateOf<Float?>(null) }
    LaunchedEffect(player, lifecycle) {
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                position = player.currentPosition.coerceAtLeast(0)
                duration = player.duration.coerceAtLeast(0)
                delay(250)
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = { if (player.isPlaying) player.pause() else { if (player.playbackState == Player.STATE_ENDED) player.seekTo(0); player.play() } }, enabled = enabled,
            modifier = Modifier.size(48.dp).testTag("video-play").semantics { contentDescription = if (playing) "暫停" else "播放" }) {
            CameraGlyph(if (playing) "pause" else "play", Modifier.size(24.dp), LocalContentColor.current)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Slider(value = (drag ?: if (duration > 0) position.toFloat() / duration else 0f).coerceIn(0f, 1f),
                onValueChange = { drag = it }, onValueChangeFinished = { drag?.let { player.seekTo((it * duration).toLong()) }; drag = null },
                enabled = enabled && duration > 0, modifier = Modifier.testTag("video-seek").semantics { contentDescription = "影片播放進度" })
            Text("${durationLabel(position)} / ${durationLabel(duration)}", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .6f))
        }
    }
}

private fun durationLabel(ms: Long) = ZoomControls.recordingTime(ms.coerceAtLeast(0).coerceAtMost(Long.MAX_VALUE / 1_000_000) * 1_000_000)
