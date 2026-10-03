package tw.luma.camera.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
import tw.luma.camera.camera.ZoomControls
import tw.luma.camera.gallery.GalleryItem
import tw.luma.camera.gallery.GalleryViewModel
import tw.luma.camera.gallery.PhotoViewport
import kotlin.math.min

@Composable
internal fun GalleryScreen(model: GalleryViewModel, close: () -> Unit, importPhoto: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val grid = rememberLazyGridState()
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(model, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) model.refresh() }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer); model.releasePhotos() }
    }
    val items = remember(state.items, filter) { state.items.filter { filter == 0 || it.video == (filter == 2) } }
    val selectedIndex = items.indexOfFirst { it.uri.toString() == selected }
    BackHandler { if (selected != null) selected = null else close() }
    Column(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding().testTag("grain-gallery")) {
        if (selectedIndex >= 0) {
            GalleryViewer(items, selectedIndex, model, { selected = null })
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = close, modifier = Modifier.heightIn(min = 48.dp).testTag("gallery-close")) { Text("‹ 相機") }
                Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    Text("Grain", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text("${state.items.size} 個拍攝片刻", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = .6f))
                }
                TextButton(onClick = model::refresh, enabled = !state.loading) { Text("重新整理") }
            }
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("全部", "照片", "影片").forEachIndexed { index, text ->
                    FilterChip(selected = filter == index, onClick = { filter = index }, label = { Text(text) }, modifier = Modifier.testTag("gallery-filter-$index"))
                }
            }
            when {
                state.error != null -> GalleryEmpty(state.error!!, "重試", model::refresh)
                state.loading && items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                items.isEmpty() -> GalleryEmpty(if (filter == 2) "還沒有影片" else if (filter == 1) "還沒有照片" else "把下一個片刻留在 Grain", "匯入照片", importPhoto)
                else -> LazyVerticalGrid(columns = GridCells.Adaptive(112.dp), state = grid,
                    contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    items(items, key = { it.uri.toString() }, contentType = { "media" }) { item ->
                        Surface(onClick = { selected = item.uri.toString() }, shape = RoundedCornerShape(8.dp), color = Color(0xFF18191C),
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

@Composable
private fun GalleryEmpty(message: String, action: String, click: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(message, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = click) { Text(action) }
    }
}

@Composable
private fun GalleryThumbnail(item: GalleryItem, model: GalleryViewModel, modifier: Modifier, contentScale: ContentScale = ContentScale.Crop) {
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
private fun ColumnScope.GalleryViewer(items: List<GalleryItem>, initialPage: Int, model: GalleryViewModel, back: () -> Unit) {
    val pager = rememberPagerState(initialPage = initialPage) { items.size }
    val positions = remember { mutableMapOf<String, Long>() }
    var zoomed by remember { mutableStateOf(false) }
    LaunchedEffect(pager.currentPage) { zoomed = false }
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = back, modifier = Modifier.heightIn(min = 48.dp).testTag("viewer-back")) { Text("‹ 相簿") }
        Spacer(Modifier.weight(1f))
        Text("${pager.currentPage + 1} / ${items.size}", color = Color.White.copy(alpha = .6f))
    }
    HorizontalPager(state = pager, userScrollEnabled = !zoomed, key = { items[it].uri.toString() }, beyondViewportPageCount = 0,
        modifier = Modifier.fillMaxWidth().weight(1f).testTag("gallery-pager")) { page ->
        val current = items[page]
        val active = page == pager.settledPage
        key(current.uri) {
            if (current.video && active) GalleryVideo(current, positions[current.uri.toString()] ?: 0) { positions[current.uri.toString()] = it }
            else if (current.video) GalleryThumbnail(current, model, Modifier.fillMaxSize())
            else GalleryPhoto(current, model, page == pager.settledPage) { zoomed = it }
        }
    }
}

@Composable
private fun GalleryPhoto(item: GalleryItem, model: GalleryViewModel, active: Boolean, onZoom: (Boolean) -> Unit) {
    var bitmap by remember(item.uri) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(item.uri) { mutableStateOf(false) }
    var scale by remember(item.uri) { mutableFloatStateOf(1f) }
    var pan by remember(item.uri) { mutableStateOf(Offset.Zero) }
    val zoomChanged by rememberUpdatedState(onZoom)
    LaunchedEffect(active, item.uri) {
        if (active) {
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
                    .pointerInput(item.uri) { detectTapGestures(onDoubleTap = { scale = 1f; pan = Offset.Zero; zoomChanged(false) }) }
                    .semantics {
                        stateDescription = if (scale > 1.01f) "已放大" else "原始比例"
                        customActions = if (!active) emptyList() else listOf(
                            CustomAccessibilityAction(if (scale > 1.01f) "還原比例" else "放大照片") {
                                scale = if (scale > 1.01f) 1f else 2f
                                pan = Offset.Zero
                                zoomChanged(scale > 1.01f)
                                true
                            },
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
    Column(Modifier.fillMaxSize().testTag("gallery-video")) {
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
        FilledTonalButton(onClick = { if (player.isPlaying) player.pause() else { if (player.playbackState == Player.STATE_ENDED) player.seekTo(0); player.play() } }, enabled = enabled, modifier = Modifier.testTag("video-play")) { Text(if (playing) "暫停" else "播放") }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Slider(value = (drag ?: if (duration > 0) position.toFloat() / duration else 0f).coerceIn(0f, 1f),
                onValueChange = { drag = it }, onValueChangeFinished = { drag?.let { player.seekTo((it * duration).toLong()) }; drag = null },
                enabled = enabled && duration > 0, modifier = Modifier.testTag("video-seek").semantics { contentDescription = "影片播放進度" })
            Text("${durationLabel(position)} / ${durationLabel(duration)}", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .6f))
        }
    }
}

private fun durationLabel(ms: Long) = ZoomControls.recordingTime(ms.coerceAtLeast(0).coerceAtMost(Long.MAX_VALUE / 1_000_000) * 1_000_000)
