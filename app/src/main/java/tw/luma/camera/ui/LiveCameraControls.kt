package tw.luma.camera.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tw.luma.camera.CameraUiState
import tw.luma.camera.CameraViewModel
import tw.luma.camera.CaptureMode
import tw.luma.camera.camera.CameraEngine
import tw.luma.camera.camera.LiveControlMath
import tw.luma.camera.camera.ZoomControls
import tw.luma.camera.camera.shutterLabel
import java.util.Locale
import kotlin.math.roundToInt

enum class LiveControl(val title: String) { ISO("ISO"), SHUTTER("快門"), EV("曝光"), WB("白平衡"), APERTURE("光圈"), ZOOM("變焦") }

@Composable
internal fun LiveCameraControls(state: CameraUiState, model: CameraViewModel, engine: CameraEngine?, active: LiveControl?, select: (LiveControl?) -> Unit, modifier: Modifier) {
    NativeCameraControls(state, active, select = { next ->
        if (next != null && next.entersManual && !state.capture.manual) {
            engine?.enterManual()?.let { manual ->
                val caps = state.capabilities
                val safeManual = if (state.mode == CaptureMode.VIDEO) {
                    val shutter = manual.shutterNs.coerceAtMost(33_333_333L).coerceAtLeast(caps.shutterRange?.lower ?: 1L)
                    val iso = (manual.iso.toDouble() * manual.shutterNs / shutter).roundToInt()
                    manual.copy(shutterNs = shutter, iso = caps.isoRange?.clamp(iso) ?: iso)
                } else manual
                model.changeCapture { safeManual }
            }
        }
        select(next)
    }, reset = { control ->
        when (control) {
            LiveControl.ISO, LiveControl.SHUTTER, LiveControl.APERTURE -> model.changeCapture { it.copy(manual = false, wbLocked = false) }
            LiveControl.EV -> model.changeCapture { it.copy(evIndex = 0) }
            LiveControl.WB -> model.resetWhiteBalance()
            LiveControl.ZOOM -> model.zoom(1f)
        }
        if (control != LiveControl.WB) select(null)
    }, slider = { control, width, enabled -> ControlSlider(control, state, model, width, enabled) }, modifier = modifier)
}

/** One floating slider, with fixed anchors so opening it never moves the buttons or viewfinder. */
@Composable
internal fun NativeCameraControls(
    state: CameraUiState,
    active: LiveControl?,
    select: (LiveControl?) -> Unit,
    reset: (LiveControl) -> Unit,
    slider: @Composable (LiveControl, androidx.compose.ui.unit.Dp, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.ready) return
    val controls = remember(state.capabilities.adjustableAperture) {
        listOf(LiveControl.ISO, LiveControl.SHUTTER, LiveControl.EV, LiveControl.WB) +
            if (state.capabilities.adjustableAperture) listOf(LiveControl.APERTURE) else emptyList()
    }
    var lastControl by remember { mutableStateOf<LiveControl?>(null) }
    LaunchedEffect(active) { if (active != null) lastControl = active }
    val popupControl = active ?: lastControl
    val density = LocalDensity.current
    var stripHeight by remember(density) { mutableIntStateOf(with(density) { 56.dp.roundToPx() }) }
    var zoomHeight by remember(density) { mutableIntStateOf(with(density) { 52.dp.roundToPx() }) }
    val lowerGradient = remember { Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .5f))) }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val anchorHeight = with(density) { stripHeight.toDp() + zoomHeight.toDp() }
        val popupWidth = minOf(480.dp, maxWidth - 24.dp)
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(lowerGradient)) {
            Box(Modifier.fillMaxWidth().onSizeChanged { zoomHeight = it.height }.padding(bottom = 4.dp), contentAlignment = Alignment.Center) {
                val selected = active == LiveControl.ZOOM
                val enabled = state.liveControlsEnabled && LiveControl.ZOOM.supported(state)
                val value = LiveControl.ZOOM.value(state)
                Surface(onClick = { select(if (selected) null else LiveControl.ZOOM) }, enabled = enabled,
                    shape = CircleShape, color = Color.Black.copy(alpha = .28f),
                    border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = .24f)),
                    modifier = Modifier.size(48.dp).testTag("control-zoom").semantics {
                        contentDescription = "變焦 $value"
                    }) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(value, style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold, color = if (selected && enabled) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = if (enabled) 1f else .55f))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().onSizeChanged { stripHeight = it.height }.padding(horizontal = 12.dp, vertical = 4.dp).testTag("camera-control-strip"),
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                controls.forEach { control ->
                    val selected = active == control
                    val enabled = state.liveControlsEnabled && control.supported(state)
                    val value = control.value(state)
                    Surface(onClick = { select(if (selected) null else control) }, enabled = enabled,
                        shape = RoundedCornerShape(10.dp), color = Color.Transparent,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("control-${control.name.lowercase()}").semantics {
                            contentDescription = "${control.title} $value"
                        }) {
                        Column(Modifier.padding(horizontal = 2.dp, vertical = 3.dp), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center) {
                            Text(control.title, style = MaterialTheme.typography.labelSmall, maxLines = 1,
                                color = Color.White.copy(alpha = if (enabled) .62f else .4f))
                            Text(value, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.Medium, color = if (selected && enabled) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = if (enabled) .95f else .5f))
                            Box(Modifier.padding(top = 3.dp).width(14.dp).height(2.dp)
                                .background(if (selected && enabled) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape))
                        }
                    }
                }
            }
        }
        if (popupControl != null && popupControl.supported(state)) {
            val enabled = state.liveControlsEnabled && active == popupControl
            AnimatedVisibility(visible = active != null && state.liveControlsEnabled,
                enter = fadeIn(tween(100)) + slideInVertically(tween(100, easing = LinearOutSlowInEasing)) { it / 12 },
                exit = fadeOut(tween(90)) + slideOutVertically(tween(90, easing = LinearOutSlowInEasing)) { it / 16 },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = anchorHeight + 6.dp).width(popupWidth)) {
                Surface(shape = RoundedCornerShape(18.dp), color = Color.Black.copy(alpha = .78f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = .12f))) {
                    Column(Modifier.heightIn(max = (maxHeight - anchorHeight - 12.dp).coerceAtLeast(96.dp))
                        .verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(popupControl.title, Modifier.padding(end = 8.dp),
                                style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .7f))
                            Text(popupControl.value(state), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            TextButton(onClick = { reset(popupControl) }, enabled = enabled, contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp).testTag("live-reset-${popupControl.name.lowercase()}")) {
                                Text(when (popupControl) {
                                    LiveControl.EV -> "0"
                                    LiveControl.ZOOM -> "1×"
                                    LiveControl.WB -> "重設"
                                    else -> "AUTO"
                                }, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        slider(popupControl, popupWidth - 24.dp, enabled)
                    }
                }
            }
        }
    }
}

private val LiveControl.entersManual get() = this == LiveControl.ISO || this == LiveControl.SHUTTER || this == LiveControl.APERTURE

private fun LiveControl.supported(state: CameraUiState): Boolean {
    val caps = state.capabilities
    return when (this) {
        LiveControl.ISO -> caps.manualSensor && caps.isoRange?.let { it.upper > it.lower } == true
        LiveControl.SHUTTER -> caps.manualSensor && caps.shutterRange?.let { it.upper > it.lower } == true
        LiveControl.EV -> caps.hasEv && !state.capture.manual
        LiveControl.WB -> true
        LiveControl.APERTURE -> caps.adjustableAperture
        LiveControl.ZOOM -> state.maxZoom > state.minZoom
    }
}

@Composable
private fun LiveControl.value(state: CameraUiState): String {
    val capture = state.capture
    val caps = state.capabilities
    // Dragging zoom must not repeatedly format unrelated exposure or shutter labels.
    return when (this) {
        LiveControl.ISO -> remember(capture.manual, capture.iso) { if (capture.manual) capture.iso.toString() else "AUTO" }
        LiveControl.SHUTTER -> remember(capture.manual, capture.shutterNs) { if (capture.manual) shutterLabel(capture.shutterNs).removeSuffix(" s") else "AUTO" }
        LiveControl.EV -> remember(capture.manual, capture.evIndex, caps.exposureStep) { if (capture.manual) "M" else "%+.1f".format(Locale.US, capture.evIndex * caps.exposureStep) }
        LiveControl.WB -> remember(capture.kelvin, capture.wbMode, caps.whiteBalances, state.filter.warmth, state.filter.tint) {
            capture.kelvin?.let { "$it K" } ?: if (state.filter.warmth != 0f || state.filter.tint != 0f) "微調"
            else caps.whiteBalances.find { it.mode == capture.wbMode }?.label ?: "自動"
        }
        LiveControl.APERTURE -> remember(capture.aperture, caps.apertures) { "f/%.1f".format(Locale.US, capture.aperture ?: caps.apertures.first()) }
        LiveControl.ZOOM -> remember(capture.zoom) { ZoomControls.label(capture.zoom) }
    }
}

@Composable
private fun ControlSlider(control: LiveControl, state: CameraUiState, model: CameraViewModel, width: androidx.compose.ui.unit.Dp, enabled: Boolean) {
    val caps = state.capabilities
    val capture = state.capture
    if (control == LiveControl.WB) {
        // Metadata updates only invalidate the open white-balance panel.
        val actual by model.actualCapture.collectAsStateWithLifecycle()
        WhiteBalancePanel(capture, state.filter, caps, actual, enabled, model::changeCapture, model::changeFilter)
        return
    }
    fun log(value: Double, min: Double, max: Double, update: (Double) -> Unit) = SliderValue(LiveControlMath.position(value, min, max)) { update(LiveControlMath.value(it, min, max)) }
    val slider = when (control) {
        LiveControl.ISO -> caps.isoRange!!.let { r -> log(capture.iso.toDouble(), r.lower.toDouble(), r.upper.toDouble()) { v -> model.changeCapture { it.copy(iso = v.roundToInt()) } } }
        LiveControl.SHUTTER -> caps.shutterRange!!.let { r ->
            val upper = if (state.mode == CaptureMode.VIDEO) minOf(r.upper, 33_333_333L).coerceAtLeast(r.lower) else r.upper
            log(capture.shutterNs.toDouble(), r.lower.toDouble(), upper.toDouble()) { v -> model.changeCapture { it.copy(shutterNs = v.toLong()) } }
        }
        LiveControl.ZOOM -> log(capture.zoom.toDouble(), state.minZoom.toDouble(), state.maxZoom.toDouble()) { model.zoom(it.toFloat()) }
        LiveControl.EV -> SliderValue(capture.evIndex.toFloat(), caps.exposureRange.lower.toFloat()..caps.exposureRange.upper.toFloat(), (caps.exposureRange.upper - caps.exposureRange.lower - 1).coerceAtLeast(0)) { v -> model.changeCapture { it.copy(evIndex = v.roundToInt()) } }
        LiveControl.APERTURE -> SliderValue(caps.apertures.indexOf(capture.aperture).coerceAtLeast(0).toFloat(), 0f..caps.apertures.lastIndex.toFloat(), (caps.apertures.size - 2).coerceAtLeast(0)) { v -> model.changeCapture { it.copy(aperture = caps.apertures[v.roundToInt()]) } }
        LiveControl.WB -> return
    }
    HorizontalControlSlider(slider.value, slider.onChange, slider.range, slider.steps, enabled,
        Modifier.width(width).testTag("live-slider-${control.name.lowercase()}").semantics { contentDescription = "${control.title}，左右滑動調整" })
}

private data class SliderValue(val value: Float, val range: ClosedFloatingPointRange<Float> = 0f..1f, val steps: Int = 0, val onChange: (Float) -> Unit)

/** Keep a direct horizontal drag; right always increases the requested value. */
@Composable
internal fun HorizontalControlSlider(value: Float, onChange: (Float) -> Unit, range: ClosedFloatingPointRange<Float>, steps: Int, enabled: Boolean, modifier: Modifier) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Slider(value.coerceIn(range.start, range.endInclusive), onChange, enabled = enabled, valueRange = range, steps = steps,
            modifier = modifier.heightIn(min = 48.dp))
    }
}
