package tw.luma.camera.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.MotionEvent
import android.view.GestureDetector
import android.view.ScaleGestureDetector
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.camera.core.CameraEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.sp
import tw.luma.camera.CaptureMode
import tw.luma.camera.RecordingStatus
import tw.luma.camera.camera.ZoomControls
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tw.luma.camera.CameraUiState
import tw.luma.camera.CameraViewModel
import tw.luma.camera.camera.CameraEngine
import tw.luma.camera.camera.FocusOutcome
import kotlinx.coroutines.delay
import tw.luma.camera.camera.CaptureSettings
import tw.luma.camera.camera.shutterLabel
import tw.luma.camera.gl.LutSurfaceProcessor
import tw.luma.camera.lut.LutEncoding
import java.util.Locale
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

@Composable
fun CameraScreen(model: CameraViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val snackbar = remember { SnackbarHostState() }
    var permitted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var permissionRequested by rememberSaveable { mutableStateOf(false) }
    var panel by rememberSaveable { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var engine by remember { mutableStateOf<CameraEngine?>(null) }
    var focusFeedback by remember { mutableStateOf<FocusFeedback?>(null) }
    var focusRequest by remember { mutableIntStateOf(0) }
    val currentEngine by rememberUpdatedState(engine)
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permitted = it }
    val audioPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        currentEngine?.let { model.startVideo(it, granted) }
        if (!granted) model.message("未授權麥克風，改為無聲錄影")
    }
    val lutImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(model::importLut) }
    val photoImport = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(model::importPhoto) }
    val view = remember(context) { PreviewView(context).apply {
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        scaleType = PreviewView.ScaleType.FILL_CENTER
    } }
    val openPhoto = { photoImport.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    val openLut = { lutImport.launch(arrayOf("*/*")) }
    val shutter = {
        val active = engine
        if (active != null) {
            when {
                state.recording -> model.stopVideo(active)
                state.mode == CaptureMode.PHOTO -> model.takePhoto(active)
                state.recordWithAudio && ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED -> audioPermission.launch(Manifest.permission.RECORD_AUDIO)
                else -> model.startVideo(active, state.recordWithAudio)
            }
        }
    }
    LaunchedEffect(Unit) { if (!permitted && !permissionRequested) { permissionRequested = true; cameraPermission.launch(Manifest.permission.CAMERA) } }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permitted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            if (event == Lifecycle.Event.ON_STOP) model.stopVideo(currentEngine)
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(view, lifecycle, permitted, state.front, state.mode, retry) {
        focusFeedback = null
        var owned: CameraEngine? = null
        if (permitted) {
            val targets = CameraEffect.PREVIEW or if (state.mode == CaptureMode.VIDEO) CameraEffect.VIDEO_CAPTURE else 0
            val processor = LutSurfaceProcessor(targets) { error -> ContextCompat.getMainExecutor(context).execute { model.cameraError(error.message ?: "GPU 預覽失敗") } }
            val next = CameraEngine(context, lifecycle, view, processor, model::ready, model::actual, model::cameraError, model::videoQuality)
            owned = next; engine = next; processor.settings = state.filter
            var multiTouch = false
            val scale = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean { model.zoom(model.state.value.capture.zoom * detector.scaleFactor); return true }
            })
            val taps = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(event: MotionEvent) = true
                override fun onSingleTapUp(event: MotionEvent): Boolean {
                    if (multiTouch || model.state.value.busy || !model.state.value.ready) return false
                    val request = ++focusRequest
                    focusFeedback = FocusFeedback(request, Offset(event.x, event.y))
                    next.focus(event.x, event.y) { outcome ->
                        focusFeedback?.takeIf { it.request == request }?.let { focusFeedback = it.copy(outcome = outcome) }
                    }
                    view.performClick()
                    return true
                }
            })
            view.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) multiTouch = false
                if (event.pointerCount > 1) multiTouch = true
                scale.onTouchEvent(event)
                taps.onTouchEvent(event)
                true
            }
            view.post { next.bind(model.state.value.front, model.state.value.mode == CaptureMode.VIDEO) }
        }
        onDispose { owned?.close(); view.setOnTouchListener(null); view.keepScreenOn = false; engine = null; focusFeedback = null }
    }
    LaunchedEffect(focusFeedback) {
        val feedback = focusFeedback ?: return@LaunchedEffect
        if (feedback.outcome == null) {
            delay(7000L)
            focusFeedback = feedback.copy(outcome = FocusOutcome.NOT_FOCUSED)
        }
    }
    LaunchedEffect(engine, state.capture, state.ready) { if (state.ready) engine?.apply(state.capture) }
    LaunchedEffect(engine, state.filter) { engine?.setFilter(state.filter) }
    SideEffect { view.keepScreenOn = state.recording }
    BackHandler(enabled = state.recording) { model.stopVideo(engine) }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); model.clearMessage() } }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, containerColor = Color.Black, contentWindowInsets = WindowInsets(0, 0, 0, 0)) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding).safeDrawingPadding()) {
            val landscape = maxWidth > maxHeight && maxWidth > 600.dp
            val preview: @Composable (Modifier) -> Unit = { modifier ->
                Box(modifier) {
                    PreviewArea(state, view, permitted, Modifier.fillMaxSize(),
                        { cameraPermission.launch(Manifest.permission.CAMERA) }, { model.retry(); retry++ },
                        { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))) })
                    if (permitted && state.ready && state.cameraError == null) focusFeedback?.let { feedback ->
                        key(feedback.request) {
                            FocusIndicator(feedback) {
                                if (focusFeedback?.request == feedback.request) focusFeedback = null
                            }
                        }
                    }
                    if (permitted && state.cameraError == null) {
                        CameraToolbar(state, Modifier.align(Alignment.TopCenter)) { panel = it }
                        ZoomBar(state, model, { panel = "zoom" }, Modifier.align(Alignment.BottomCenter))
                    }
                }
            }
            if (landscape) Row(Modifier.fillMaxSize()) {
                preview(Modifier.weight(1f).fillMaxHeight())
                CameraDock(state, model, shutter, openPhoto, { panel = "tools" }, Modifier.width(164.dp).fillMaxHeight(), true)
            } else Column(Modifier.fillMaxSize()) {
                preview(Modifier.weight(1f).fillMaxWidth())
                CameraDock(state, model, shutter, openPhoto, { panel = "tools" }, Modifier.fillMaxWidth(), false)
            }
        }
    }
    panel?.let { ControlsSheet(it, state, model, engine, { panel = it }, openLut, openPhoto) { panel = null } }
}

private data class FocusFeedback(val request: Int, val point: Offset, val outcome: FocusOutcome? = null)

@Composable
private fun FocusIndicator(feedback: FocusFeedback, onFinished: () -> Unit) {
    val label = when (feedback.outcome) {
        null -> "對焦中"
        FocusOutcome.FOCUSED -> "已對焦"
        FocusOutcome.NOT_FOCUSED -> "請再點一次"
        FocusOutcome.METERED -> "已測光"
        FocusOutcome.UNAVAILABLE -> "此鏡頭不支援"
    }
    val color = if (feedback.outcome == FocusOutcome.NOT_FOCUSED || feedback.outcome == FocusOutcome.UNAVAILABLE) Color.White else MaterialTheme.colorScheme.primary
    val scale = remember { Animatable(1.16f) }
    val opacity = remember { Animatable(1f) }
    val finish by rememberUpdatedState(onFinished)
    LaunchedEffect(feedback.outcome) {
        if (feedback.outcome == null) {
            scale.animateTo(1f, tween(150, easing = FastOutSlowInEasing))
        } else {
            val succeeded = feedback.outcome == FocusOutcome.FOCUSED || feedback.outcome == FocusOutcome.METERED
            scale.animateTo(if (succeeded) .9f else 1.08f, tween(140, easing = FastOutSlowInEasing))
            delay(250L)
            opacity.animateTo(0f, tween(160))
            finish()
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val extent = with(density) { 80.dp.toPx() }
        val maxX = (with(density) { maxWidth.toPx() } - extent).coerceAtLeast(0f)
        val maxY = (with(density) { maxHeight.toPx() } - extent).coerceAtLeast(0f)
        Box(Modifier.offset { IntOffset((feedback.point.x - extent / 2).coerceIn(0f, maxX).roundToInt(), (feedback.point.y - extent / 2).coerceIn(0f, maxY).roundToInt()) }
            .size(80.dp).testTag("focus-indicator").semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
            Box(Modifier.size(64.dp).graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                alpha = opacity.value
            }.border(1.5.dp, color, RoundedCornerShape(8.dp)))
        }
    }
}

@Composable
private fun PreviewArea(state: CameraUiState, view: PreviewView, granted: Boolean, modifier: Modifier, permission: () -> Unit, retry: () -> Unit, appSettings: () -> Unit) {
    Box(modifier.background(Color.Black).testTag("viewfinder"), contentAlignment = Alignment.Center) {
        if (granted) {
            AndroidView(factory = { view }, modifier = Modifier.fillMaxSize().semantics { contentDescription = "相機即時預覽，可點擊對焦及雙指變焦" })
            if (state.grid && state.ready) Canvas(Modifier.fillMaxSize()) {
                for (i in 1..2) {
                    drawLine(Color.White.copy(alpha = .2f), Offset(size.width * i / 3, 0f), Offset(size.width * i / 3, size.height), 1.dp.toPx())
                    drawLine(Color.White.copy(alpha = .2f), Offset(0f, size.height * i / 3), Offset(size.width, size.height * i / 3), 1.dp.toPx())
                }
            }
            if (!state.ready && state.cameraError == null) CircularProgressIndicator(Modifier.size(28.dp), color = Color.White)
            state.cameraError?.let { error -> Column(Modifier.padding(24.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp)).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("相機暫時無法使用", style = MaterialTheme.typography.titleMedium)
                Text(error, Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.bodySmall)
                Button(onClick = retry, enabled = !state.busy && !state.recording) { Text("重新啟動相機") }
            } }
        } else Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("允許相機，開始拍攝", style = MaterialTheme.typography.titleLarge)
            Text("照片與影片儲存在手機內。", Modifier.padding(vertical = 16.dp))
            Button(onClick = permission) { Text("允許使用相機") }
            TextButton(onClick = appSettings) { Text("開啟權限設定") }
        }
        if (state.ready) Box(Modifier.size(1.dp).testTag("camera-ready"))
        if (state.capture.manual && state.ready && !state.recording) Text("ISO ${state.actual.iso ?: "—"}  ·  ${shutterLabel(state.actual.shutterNs)}", Modifier.align(Alignment.TopCenter).padding(top = 72.dp).background(Color.Black.copy(alpha = .55f), CircleShape).padding(horizontal = 12.dp, vertical = 6.dp), color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun CameraToolbar(state: CameraUiState, modifier: Modifier, panel: (String) -> Unit) {
    Row(modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .65f), Color.Transparent))).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (state.mode == CaptureMode.VIDEO) Surface(color = Color.Black.copy(alpha = .45f), shape = CircleShape, modifier = Modifier.heightIn(min = 48.dp), onClick = { panel("settings") }, enabled = !state.recording) {
            Box(Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) { Text(state.videoQuality, color = Color.White, style = MaterialTheme.typography.labelMedium) }
        } else GlassIcon("flash", if (state.capture.flash) "閃光燈已開啟" else "閃光燈設定", !state.recording && !state.busy, state.capture.flash) { panel("settings") }
        Spacer(Modifier.weight(1f))
        if (state.recording) Surface(shape = CircleShape, color = Color(0xFFE43F45)) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).background(Color.White, CircleShape)); Spacer(Modifier.width(8.dp))
                Text(if (state.recordingStatus == RecordingStatus.STARTING) "準備錄影" else if (state.recordingStatus == RecordingStatus.STOPPING) "儲存中" else ZoomControls.recordingTime(state.recordingNs), color = Color.White, modifier = Modifier.testTag("recording-time"), fontWeight = FontWeight.SemiBold)
            }
        } else Surface(onClick = { panel("filters") }, shape = CircleShape, color = Color.Black.copy(alpha = .5f), enabled = !state.busy, modifier = Modifier.widthIn(max = 235.dp).heightIn(min = 48.dp).testTag("filter-picker")) {
            Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                CameraGlyph("lut", Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                Text(state.filter.lut?.title?.removePrefix("富士 ") ?: "原色", color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
            }
        }
        Spacer(Modifier.weight(1f))
        GlassIcon("tune", "相機控制", !state.busy && !state.recording) { panel("tools") }
    }
}

@Composable
private fun ZoomBar(state: CameraUiState, model: CameraViewModel, expand: () -> Unit, modifier: Modifier) {
    if (!state.ready) return
    Row(modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .65f)))).padding(top = 22.dp, bottom = 14.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = Color.Black.copy(alpha = .55f)) {
            Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                ZoomControls.presets(state.minZoom, state.maxZoom).forEach { ratio ->
                    TextButton(onClick = { model.zoom(ratio) }, enabled = !state.busy, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp).testTag("zoom-$ratio")) {
                        Text(ZoomControls.label(ratio), color = if (kotlin.math.abs(state.capture.zoom - ratio) < .06f) MaterialTheme.colorScheme.primary else Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
                TextButton(onClick = expand, enabled = !state.busy, contentPadding = PaddingValues(horizontal = 10.dp), modifier = Modifier.heightIn(min = 48.dp).testTag("zoom-continuous")) {
                    Text("${ZoomControls.label(state.capture.zoom)} ⌄", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun CameraDock(state: CameraUiState, model: CameraViewModel, shutter: () -> Unit, importPhoto: () -> Unit, tools: () -> Unit, modifier: Modifier, landscape: Boolean) {
    val context = LocalContext.current
    val album: () -> Unit = {
        state.savedUri?.let { uri -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, state.savedMime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }.onFailure { model.message("檔案已儲存，可從系統相簿開啟") } } ?: importPhoto()
    }
    val enabled = !state.busy && !state.recording
    val modes: @Composable () -> Unit = {
        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            CaptureMode.entries.forEach { mode -> TextButton(onClick = { model.mode(mode) }, enabled = enabled, modifier = Modifier.testTag(if (mode == CaptureMode.PHOTO) "mode-photo" else "mode-video")) {
                Text(if (mode == CaptureMode.PHOTO) "照片" else "錄影", color = if (state.mode == mode) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = .6f), fontWeight = FontWeight.SemiBold)
            } }
        }
    }
    val capture: @Composable () -> Unit = {
        val video = state.mode == CaptureMode.VIDEO
        val stopping = state.recordingStatus == RecordingStatus.STOPPING
        Button(onClick = shutter, enabled = (state.ready && !state.busy || state.recording) && !stopping,
            modifier = Modifier.size(82.dp).border(3.dp, Color.White.copy(alpha = .95f), CircleShape).padding(6.dp).testTag("shutter").semantics { contentDescription = if (state.recording) "停止錄影" else if (video) "開始錄影" else "拍照" },
            colors = ButtonDefaults.buttonColors(containerColor = if (video) Color(0xFFFF444C) else Color.White, disabledContainerColor = Color.DarkGray), shape = CircleShape, contentPadding = PaddingValues(0.dp)) {
            when {
                state.busy || state.recordingStatus == RecordingStatus.STARTING || stopping -> CircularProgressIndicator(Modifier.size(24.dp), color = if (video) Color.White else Color.Black)
                state.recording -> Box(Modifier.size(27.dp).clip(RoundedCornerShape(5.dp)).background(Color.White))
            }
        }
    }
    val gallery: @Composable () -> Unit = {
        Surface(onClick = album, enabled = enabled, shape = RoundedCornerShape(12.dp), color = Color(0xFF202124), modifier = Modifier.size(48.dp).semantics { contentDescription = "開啟上次照片或影片" }) {
            state.thumbnail?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } ?: Box(contentAlignment = Alignment.Center) { CameraGlyph("gallery", Modifier.size(25.dp)) }
        }
    }
    Column(modifier.background(Color.Black).padding(horizontal = 22.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = if (landscape) Arrangement.SpaceEvenly else Arrangement.Top) {
        modes()
        if (landscape) {
            capture()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                gallery(); GlassIcon("flip", "切換前後鏡頭", enabled && state.ready, false, model::toggleFront)
            }
            TextButton(onClick = tools, enabled = enabled) { Text(if (state.capture.manual) "PRO" else "AUTO", color = Color.White.copy(alpha = .7f)) }
        } else {
            Row(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                gallery(); capture(); GlassIcon("flip", "切換前後鏡頭", enabled && state.ready, false, model::toggleFront)
            }
        }
    }
}

@Composable
private fun GlassIcon(icon: String, description: String, enabled: Boolean = true, selected: Boolean = false, click: () -> Unit) {
    Surface(onClick = click, enabled = enabled, shape = CircleShape, color = Color.Black.copy(alpha = .5f), modifier = Modifier.size(48.dp).semantics { contentDescription = description }) {
        Box(contentAlignment = Alignment.Center) { CameraGlyph(icon, Modifier.size(25.dp), if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = if (enabled) 1f else .35f)) }
    }
}

@Composable
private fun CameraGlyph(name: String, modifier: Modifier, tint: Color = Color.White) {
    Canvas(modifier) {
        val w = size.width; val h = size.height; val stroke = Stroke(width = 1.6.dp.toPx())
        fun line(x: Float, y: Float, x2: Float, y2: Float) = drawLine(tint, Offset(w*x,h*y), Offset(w*x2,h*y2), stroke.width)
        when (name) {
            "flash" -> drawPath(Path().apply { moveTo(w*.6f,h*.07f); lineTo(w*.22f,h*.55f); lineTo(w*.48f,h*.55f); lineTo(w*.4f,h*.93f); lineTo(w*.8f,h*.42f); lineTo(w*.55f,h*.42f); close() }, tint, style = stroke)
            "tune" -> { for ((y,x) in listOf(.24f to .32f,.5f to .68f,.76f to .4f)) { line(.1f,y,.9f,y); drawCircle(Color.Black, w*.09f, Offset(w*x,h*y)); drawCircle(tint, w*.09f, Offset(w*x,h*y), style = stroke) } }
            "lut" -> { drawCircle(tint,w*.24f,Offset(w*.36f,h*.36f),style=stroke); drawCircle(tint,w*.24f,Offset(w*.64f,h*.36f),style=stroke); drawCircle(tint,w*.24f,Offset(w*.5f,h*.65f),style=stroke) }
            "flip" -> { drawRoundRect(tint, Offset(w*.12f,h*.22f), androidx.compose.ui.geometry.Size(w*.76f,h*.59f), androidx.compose.ui.geometry.CornerRadius(w*.13f), style=stroke); drawArc(tint,35f,270f,false,Offset(w*.32f,h*.35f),androidx.compose.ui.geometry.Size(w*.36f,h*.36f),style=stroke); line(.67f,.48f,.67f,.35f); line(.67f,.48f,.54f,.48f) }
            else -> { drawRoundRect(tint,Offset(w*.1f,h*.15f),androidx.compose.ui.geometry.Size(w*.8f,h*.7f),androidx.compose.ui.geometry.CornerRadius(w*.1f),style=stroke); line(.13f,.7f,.38f,.46f); line(.38f,.46f,.57f,.65f); line(.57f,.65f,.72f,.5f); line(.72f,.5f,.87f,.68f); drawCircle(tint,w*.06f,Offset(w*.7f,h*.34f)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ControlsSheet(panel: String, state: CameraUiState, model: CameraViewModel, engine: CameraEngine?, navigate: (String) -> Unit, importLut: () -> Unit, importPhoto: () -> Unit, dismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = dismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            val caps = state.capabilities
            Text(when (panel) { "tools" -> "相機控制"; "filters" -> "選擇濾鏡"; "zoom" -> "變焦"; "ev" -> "曝光"; "shutter" -> "快門"; "iso" -> "ISO"; "wb" -> "白平衡"; "aperture" -> "光圈"; "filter" -> "調色設定"; else -> "設定" }, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))
            when (panel) {
                "tools" -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilterChip(selected = !state.capture.manual, enabled = state.ready && !state.recording && !state.busy,
                            onClick = { model.changeCapture { it.copy(manual = false, wbLocked = false) } }, label = { Text("AUTO") })
                        FilterChip(selected = state.capture.manual, enabled = state.ready && caps.manualSensor && !state.recording && !state.busy,
                            onClick = { engine?.enterManual()?.let { value -> model.changeCapture { value } } }, label = { Text("PRO") })
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        ControlTile("曝光", "%+.1f EV".format(Locale.US, state.capture.evIndex * caps.exposureStep), caps.hasEv && !state.capture.manual && state.ready, Modifier.weight(1f)) { navigate("ev") }
                        ControlTile("白平衡", state.capture.kelvin?.let { "$it K" } ?: caps.whiteBalances.find { it.mode == state.capture.wbMode }?.label ?: "自動", state.ready, Modifier.weight(1f)) { navigate("wb") }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        ControlTile("ISO", if (state.capture.manual) state.capture.iso.toString() else "自動", state.capture.manual, Modifier.weight(1f)) { navigate("iso") }
                        ControlTile("快門", if (state.capture.manual) shutterLabel(state.capture.shutterNs) else "自動", state.capture.manual, Modifier.weight(1f)) { navigate("shutter") }
                    }
                    if (caps.apertures.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        ControlTile("光圈", state.actual.aperture?.let { "f/%.1f".format(Locale.US, it) } ?: "固定", caps.adjustableAperture && state.capture.manual, Modifier.fillMaxWidth()) { navigate("aperture") }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { navigate("filters") }, enabled = !state.busy) { Text("濾鏡") }
                        OutlinedButton(onClick = { navigate("settings") }) { Text("更多設定") }
                    }
                    if (!caps.manualSensor) Text("這個鏡頭未提供手動快門與 ISO。", style = MaterialTheme.typography.bodySmall)
                }
                "filters" -> {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        item { FilterChip(selected = state.selectedLut == null, onClick = { model.selectLut(null) }, label = { Text("原色") }, modifier = Modifier.testTag("filter-original")) }
                        items(state.luts, key = { it.id }) { entry -> FilterChip(selected = state.selectedLut == entry.id,
                            onClick = { model.selectLut(entry.id) }, label = { Text(entry.lut.title.removePrefix("富士 ")) }, modifier = Modifier.testTag("filter-${entry.id}")) }
                    }
                    state.filter.lut?.let { lut ->
                        Spacer(Modifier.height(12.dp)); Text(lut.title, style = MaterialTheme.typography.titleMedium)
                        Text("強度 ${(state.filter.strength * 100).roundToInt()}%", Modifier.padding(top = 12.dp))
                        Slider(state.filter.strength, { value -> model.changeFilter { it.copy(strength = value) } }, modifier = Modifier.testTag("filter-strength"))
                        if (state.filter.encoding != LutEncoding.SRGB) Text("Log 近似適配，色彩與富士機身可能不同。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = importLut, enabled = !state.busy && !state.recording) { Text("匯入 LUT") }
                        OutlinedButton(onClick = { navigate("filter") }) { Text("調色設定") }
                    }
                }
                "zoom" -> {
                    Text(ZoomControls.label(state.capture.zoom), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
                    if (state.maxZoom > state.minZoom) LogSlider(state.capture.zoom.toDouble(), state.minZoom.toDouble(), state.maxZoom.toDouble()) { model.zoom(it.toFloat()) }
                    Text("${ZoomControls.label(state.minZoom)}–${ZoomControls.label(state.maxZoom)} · 也可以在觀景窗雙指縮放", style = MaterialTheme.typography.bodyMedium)
                    Text("倍率依目前鏡頭能力提供，可能包含數位裁切。", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
                }
                "ev" -> {
                    Text("調整自動曝光目標。全手動模式由快門與 ISO 決定曝光。")
                    Text("%+.2f EV".format(Locale.US, state.capture.evIndex * caps.exposureStep), Modifier.padding(top = 16.dp))
                    if (caps.hasEv) Slider(state.capture.evIndex.toFloat(), { value -> model.changeCapture { it.copy(evIndex = value.roundToInt()) } }, valueRange = caps.exposureRange.lower.toFloat()..caps.exposureRange.upper.toFloat())
                }
                "shutter" -> caps.shutterRange?.let { range ->
                    Text(shutterLabel(state.capture.shutterNs), style = MaterialTheme.typography.titleLarge)
                    LogSlider(state.capture.shutterNs.toDouble(), range.lower.toDouble(), range.upper.toDouble()) { value -> model.changeCapture { it.copy(shutterNs = value.toLong()) } }
                    Text("慢快門增加動態模糊，也會降低即時預覽更新速度。", style = MaterialTheme.typography.bodySmall)
                }
                "iso" -> caps.isoRange?.let { range ->
                    Text("ISO ${state.capture.iso}", style = MaterialTheme.typography.titleLarge)
                    LogSlider(state.capture.iso.toDouble(), range.lower.toDouble(), range.upper.toDouble()) { value -> model.changeCapture { it.copy(iso = value.roundToInt()) } }
                }
                "wb" -> {
                    caps.whiteBalances.forEach { wb ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = state.capture.kelvin == null && state.capture.wbMode == wb.mode,
                                onClick = { model.changeCapture { it.copy(wbMode = wb.mode, kelvin = null, wbLocked = false) } })
                            TextButton(onClick = { model.changeCapture { it.copy(wbMode = wb.mode, kelvin = null, wbLocked = false) } }) { Text(wb.label) }
                        }
                    }
                    if (caps.awbLock && state.capture.kelvin == null && state.capture.wbMode == android.hardware.camera2.CaptureRequest.CONTROL_AWB_MODE_AUTO) ToggleRow("鎖定自動白平衡", state.capture.wbLocked, !state.capture.manual) { checked -> model.changeCapture { it.copy(wbLocked = checked) } }
                    caps.cctRange?.let { range ->
                        ToggleRow("手動色溫與色偏", state.capture.kelvin != null) { checked -> model.changeCapture { it.copy(kelvin = if (checked) range.clamp(5500) else null) } }
                        state.capture.kelvin?.let { kelvin ->
                            Text("${kelvin} K")
                            Slider(kelvin.toFloat(), { value -> model.changeCapture { it.copy(kelvin = value.roundToInt()) } }, valueRange = range.lower.toFloat()..range.upper.toFloat())
                            Text("色偏 ${state.capture.tint}")
                            Slider(state.capture.tint.toFloat(), { value -> model.changeCapture { it.copy(tint = value.roundToInt()) } }, valueRange = -50f..50f)
                        }
                    } ?: Text("此鏡頭未提供直接 K 色溫控制；可使用上方白平衡預設。", style = MaterialTheme.typography.bodySmall)
                    if (state.capture.manual) Text("M 模式下自動白平衡會在支援時鎖定，以減少色彩漂移。", style = MaterialTheme.typography.bodySmall)
                }
                "aperture" -> {
                    caps.apertures.forEach { f -> FilterChip(selected = state.capture.aperture == f, onClick = { model.changeCapture { it.copy(aperture = f) } }, label = { Text("f/%.1f".format(Locale.US, f)) }) }
                    Text("這是鏡頭實體光圈。固定光圈鏡頭無法調整。", style = MaterialTheme.typography.bodySmall)
                }
                "filter" -> {
                    Text("影像亮度：%+.1f EV".format(Locale.US, state.filter.brightnessEv))
                    Slider(state.filter.brightnessEv, { value -> model.changeFilter { it.copy(brightnessEv = value) } }, valueRange = -2f..2f)
                    Text("這是拍攝後的調色，不會改變快門或 ISO。", style = MaterialTheme.typography.bodySmall)
                    if (state.filter.lut != null) {
                        Spacer(Modifier.height(16.dp)); Text("LUT 輸入色彩", style = MaterialTheme.typography.titleMedium)
                        LutEncoding.entries.forEach { encoding -> Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(state.filter.encoding == encoding, { model.changeFilter { it.copy(encoding = encoding) } })
                            TextButton(onClick = { model.changeFilter { it.copy(encoding = encoding) } }) { Text(encoding.label) }
                        } }
                        Text("一般 LUT 請依作者指定的輸入空間設定。Log 適配將手機 SDR 近似轉為場景亮度，並假設 LUT 輸出 gamma 2.2；無法重現完整富士機身處理。", style = MaterialTheme.typography.bodySmall)
                        if (state.luts.any { it.id == state.selectedLut && it.imported }) TextButton(onClick = { model.deleteSelectedLut(); dismiss() }) { Text("移除這個匯入 LUT") }
                    }
                }
                else -> {
                    if (state.mode == CaptureMode.VIDEO) {
                        Text("${state.videoQuality} · SDR · 即時 LUT", style = MaterialTheme.typography.titleMedium)
                        ToggleRow("錄製聲音", state.recordWithAudio, !state.recording, model::recordWithAudio)
                        Text("首次錄影會詢問麥克風權限；未授權仍可錄製無聲影片。", style = MaterialTheme.typography.bodySmall)
                    }
                    ToggleRow("同時儲存原圖", state.saveOriginal, !state.busy, model::saveOriginal)
                    ToggleRow("顯示構圖格線", state.grid) { model.toggleGrid() }
                    if (state.mode == CaptureMode.PHOTO) ToggleRow("拍照閃光燈", state.capture.flash, state.hasFlash && !state.capture.manual && !state.busy) { checked -> model.changeCapture { it.copy(flash = checked) } }
                    OutlinedButton(onClick = importPhoto, enabled = !state.busy && !state.recording) { Text("匯入照片套用濾鏡") }
                    Spacer(Modifier.height(16.dp))
                    Text("鏡頭能力", style = MaterialTheme.typography.titleMedium)
                    Text("鏡頭 ID：${caps.id.ifBlank { "尚未連接" }}\n手動快門／ISO：${if (caps.manualSensor) "支援" else "未提供"}\n光圈：${if (caps.apertures.isEmpty()) "未回報" else if (caps.apertures.size == 1) "固定 f/${caps.apertures[0]}" else caps.apertures.joinToString { "f/$it" }}\n直接色溫：${caps.cctRange?.let { "${it.lower}–${it.upper} K" } ?: "未提供"}", Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.bodyMedium)
                    Text("照片：Pictures/Grain\n影片：Movies/Grain\n富士底片模擬採近似色彩適配；個人測試版含十款官方 LUT。", style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(onClick = dismiss, modifier = Modifier.fillMaxWidth()) { Text("完成") }
        }
    }
}

@Composable
private fun ControlTile(label: String, value: String, enabled: Boolean, modifier: Modifier, click: () -> Unit) {
    Surface(onClick = click, enabled = enabled, modifier = modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else .5f))
            Text(value, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.titleMedium, color = if (enabled) Color.White else Color.White.copy(alpha = .45f))
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onChange, enabled = enabled)
    }
}

@Composable
private fun LogSlider(value: Double, min: Double, max: Double, onChange: (Double) -> Unit) {
    if (max <= min) return
    val low = ln(min); val high = ln(max)
    Slider(((ln(value.coerceIn(min, max)) - low) / (high - low)).toFloat(), { t -> onChange(exp(low + t * (high - low))) })
}
