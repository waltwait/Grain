package tw.luma.camera.ui

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tw.luma.camera.CameraUiState
import tw.luma.camera.CaptureMode
import tw.luma.camera.RecordingStatus
import tw.luma.camera.camera.PhotoPhase

private val CaptureEaseOut = CubicBezierEasing(.22f, 1f, .36f, 1f)

@Composable
private fun motionEnabled() = rememberCoroutineScope().coroutineContext[MotionDurationScale]?.scaleFactor != 0f

@Composable
private fun delayedProgress(working: Boolean): Boolean {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(working) {
        visible = false
        if (working) { delay(250); visible = true }
    }
    return working && visible
}

@Composable
internal fun CameraCaptureButton(state: CameraUiState, onClick: () -> Unit) {
    val video = state.mode == CaptureMode.VIDEO
    val stopping = state.recordingStatus == RecordingStatus.STOPPING
    val enabled = (state.ready && !state.busy || state.recording) && !stopping
    val scale = remember { Animatable(1f) }
    val motion = motionEnabled()
    var lastRequest by remember { mutableLongStateOf(state.captureFeedback.requestId) }
    LaunchedEffect(state.captureFeedback.requestId, motion) {
        if (!motion) scale.snapTo(1f)
        val changed = lastRequest != state.captureFeedback.requestId
        lastRequest = state.captureFeedback.requestId
        if (changed && motion) {
            scale.snapTo(.97f)
            scale.animateTo(1f, tween(160, easing = CaptureEaseOut))
        }
    }
    val showProgress = delayedProgress(!video && state.captureFeedback.phase == PhotoPhase.CAPTURING)
    Box(Modifier.size(82.dp)) {
        Button(onClick, enabled = enabled,
            modifier = Modifier.fillMaxSize().border(3.dp, Color.White.copy(alpha = .95f), CircleShape).padding(6.dp)
                .testTag("shutter").semantics {
                    contentDescription = if (state.recording) "停止錄影" else if (video) "開始錄影" else "拍照"
                    if (!video) stateDescription = when (state.captureFeedback.phase) {
                        PhotoPhase.CAPTURING -> "拍攝中"
                        PhotoPhase.SAVING -> "存檔中"
                        PhotoPhase.IDLE -> if (state.ready) "就緒" else "等待相機"
                    }
                }, shape = CircleShape, contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (video) Color(0xFFFF444C) else Color.Transparent,
                disabledContainerColor = if (video) Color.DarkGray else Color.Transparent)) {
            if (!video) Box(Modifier.fillMaxSize().graphicsLayer { scaleX = scale.value; scaleY = scale.value }
                .background(if (state.ready) Color.White else Color.DarkGray, CircleShape))
            else when {
                state.busy || state.recordingStatus == RecordingStatus.STARTING || stopping -> CircularProgressIndicator(Modifier.size(24.dp), color = Color.White)
                state.recording -> Box(Modifier.size(27.dp).clip(RoundedCornerShape(5.dp)).background(Color.White))
            }
        }
        if (showProgress) {
            if (motion) CircularProgressIndicator(Modifier.fillMaxSize().testTag("photo-capturing"), color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp, trackColor = Color.Transparent)
            else StaticBusyRing(Modifier.fillMaxSize().testTag("photo-capturing"))
        }
    }
}

/** Only the camera's completed image callback changes this ID; a tap or failed capture does not. */
@Composable
internal fun ViewfinderCaptureFeedback(capturedId: Long, modifier: Modifier = Modifier) {
    val shade = remember { Animatable(0f) }
    val motion = motionEnabled()
    var lastCaptured by remember { mutableLongStateOf(capturedId) }
    LaunchedEffect(capturedId, motion) {
        if (!motion) shade.snapTo(0f)
        val changed = lastCaptured != capturedId
        lastCaptured = capturedId
        if (changed && motion) {
            shade.snapTo(.16f)
            shade.animateTo(0f, tween(140))
        }
    }
    // Read animation values in the draw phase, without resizing or copying the camera image.
    Canvas(modifier.testTag("photo-capture-feedback")) {
        if (shade.value > 0f) drawRect(Color.Black.copy(alpha = shade.value))
    }
}

@Composable
internal fun SavedMediaButton(thumbnail: Bitmap?, revision: Long, saving: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val scale = remember { Animatable(1f) }
    val highlight = remember { Animatable(0f) }
    val motion = motionEnabled()
    var lastSaved by remember { mutableLongStateOf(revision) }
    LaunchedEffect(revision, motion) {
        if (!motion) { scale.snapTo(1f); highlight.snapTo(0f) }
        val changed = revision != lastSaved
        lastSaved = revision
        if (changed && motion) {
            scale.snapTo(.97f); highlight.snapTo(.9f)
            launch { scale.animateTo(1f, tween(200, easing = CaptureEaseOut)) }
            highlight.animateTo(0f, tween(260))
        }
    }
    val showProgress = delayedProgress(saving)
    val gold = MaterialTheme.colorScheme.primary
    Surface(onClick, enabled = enabled, shape = RoundedCornerShape(12.dp), color = GrainSurfaces.control,
        modifier = Modifier.size(48.dp).testTag("open-gallery").semantics {
            contentDescription = "開啟 Grain 相簿"
            stateDescription = if (saving) "存檔中" else if (revision > 0) "已儲存" else "相簿"
        }) {
        Box(Modifier.fillMaxSize()) {
            Crossfade(thumbnail, animationSpec = tween(if (motion) 180 else 0), label = "saved-thumbnail",
                modifier = Modifier.fillMaxSize().cameraControlRotation().graphicsLayer { scaleX = scale.value; scaleY = scale.value }) { bitmap ->
                if (bitmap != null) Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize().testTag("saved-thumbnail"), contentScale = ContentScale.Crop)
                else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CameraGlyph("gallery", Modifier.size(25.dp)) }
            }
            Canvas(Modifier.matchParentSize()) {
                if (highlight.value > 0f) {
                    val inset = 1.dp.toPx()
                    drawRoundRect(gold.copy(alpha = highlight.value), topLeft = Offset(inset, inset),
                        size = Size(size.width - inset * 2, size.height - inset * 2), cornerRadius = CornerRadius(11.dp.toPx()), style = Stroke(1.5.dp.toPx()))
                }
            }
            if (showProgress) {
                val progress = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp).testTag("media-saving")
                if (motion) LinearProgressIndicator(progress, color = gold, trackColor = Color.Transparent)
                else Canvas(progress.semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate }) {
                    drawLine(gold, Offset(size.width * .2f, size.height / 2), Offset(size.width * .7f, size.height / 2), strokeWidth = size.height)
                }
            }
        }
    }
}

@Composable
private fun StaticBusyRing(modifier: Modifier) {
    val gold = MaterialTheme.colorScheme.primary
    Canvas(modifier.semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate }) {
        val inset = 1.dp.toPx()
        drawArc(gold, -90f, 90f, false, Offset(inset, inset), Size(size.width - inset * 2, size.height - inset * 2), style = Stroke(2.dp.toPx()))
    }
}

/** A fixed text slot in the dock, not a toast or floating panel. Empty success state stays quiet. */
@Composable
internal fun CameraStatusLine(message: String?) {
    Text(message.orEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 18.dp).testTag("camera-status")
        .semantics { if (message != null) liveRegion = LiveRegionMode.Polite },
        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall,
        maxLines = 1, overflow = TextOverflow.Ellipsis)
}
