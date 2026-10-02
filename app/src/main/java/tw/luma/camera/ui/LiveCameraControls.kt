package tw.luma.camera.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
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
    if (!state.ready) return
    val caps = state.capabilities
    val allowed = state.liveControlsEnabled
    val controls = listOf(LiveControl.ISO, LiveControl.SHUTTER, LiveControl.EV, LiveControl.WB) +
        (if (caps.adjustableAperture) listOf(LiveControl.APERTURE) else emptyList()) + LiveControl.ZOOM
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val sliderHeight = (maxHeight - 170.dp).coerceIn(80.dp, 190.dp)
        Row(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .65f))))
            .padding(horizontal = 8.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
            controls.forEach { control ->
                val supported = when (control) {
                    LiveControl.ISO -> caps.manualSensor && caps.isoRange?.let { it.upper > it.lower } == true
                    LiveControl.SHUTTER -> caps.manualSensor && caps.shutterRange?.let { it.upper > it.lower } == true
                    LiveControl.EV -> caps.hasEv && !state.capture.manual
                    LiveControl.WB -> caps.whiteBalances.size > 1 || caps.cctRange?.let { it.upper > it.lower } == true
                    LiveControl.APERTURE -> caps.adjustableAperture
                    LiveControl.ZOOM -> state.maxZoom > state.minZoom
                }
                val enabled = allowed && supported
                val value = when (control) {
                    LiveControl.ISO -> if (state.capture.manual) state.capture.iso.toString() else "AUTO"
                    LiveControl.SHUTTER -> if (state.capture.manual) shutterLabel(state.capture.shutterNs).removeSuffix(" s") else "AUTO"
                    LiveControl.EV -> if (state.capture.manual) "M" else "%+.1f".format(Locale.US, state.capture.evIndex * caps.exposureStep)
                    LiveControl.WB -> state.capture.kelvin?.let { "$it K" } ?: caps.whiteBalances.find { it.mode == state.capture.wbMode }?.label ?: "自動"
                    LiveControl.APERTURE -> "f/%.1f".format(Locale.US, state.capture.aperture ?: caps.apertures.first())
                    LiveControl.ZOOM -> ZoomControls.label(state.capture.zoom)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    AnimatedVisibility(visible = active == control && enabled,
                        enter = expandVertically(animationSpec = tween(160), expandFrom = Alignment.Bottom) + fadeIn(tween(100)),
                        exit = shrinkVertically(animationSpec = tween(120), shrinkTowards = Alignment.Bottom) + fadeOut(tween(100))) {
                        Surface(shape = RoundedCornerShape(16.dp), color = Color.Black.copy(alpha = .82f), modifier = Modifier.padding(bottom = 8.dp).fillMaxWidth()) {
                            Column(Modifier.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(value, maxLines = 1, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                ControlSlider(control, state, model, sliderHeight, enabled && active == control)
                                TextButton(onClick = {
                                    when (control) {
                                        LiveControl.ISO, LiveControl.SHUTTER, LiveControl.APERTURE -> model.changeCapture { it.copy(manual = false, wbLocked = false) }
                                        LiveControl.EV -> model.changeCapture { it.copy(evIndex = 0) }
                                        LiveControl.WB -> model.changeCapture { it.copy(kelvin = null, wbMode = android.hardware.camera2.CaptureRequest.CONTROL_AWB_MODE_AUTO, wbLocked = false) }
                                        LiveControl.ZOOM -> model.zoom(1f)
                                    }
                                    select(null)
                                }, enabled = enabled && active == control, contentPadding = PaddingValues(0.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                                    Text(if (control in listOf(LiveControl.ISO, LiveControl.SHUTTER, LiveControl.WB, LiveControl.APERTURE)) "AUTO" else if (control == LiveControl.ZOOM) "1×" else "0", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                    Surface(onClick = {
                        if (active == control) select(null) else {
                            if (control in listOf(LiveControl.ISO, LiveControl.SHUTTER, LiveControl.APERTURE) && !state.capture.manual) {
                                engine?.enterManual()?.let { manual ->
                                    val safeManual = if (state.mode == CaptureMode.VIDEO) {
                                        val shutter = manual.shutterNs.coerceAtMost(33_333_333L).coerceAtLeast(caps.shutterRange?.lower ?: 1L)
                                        val iso = (manual.iso.toDouble() * manual.shutterNs / shutter).roundToInt()
                                        // Preserve brightness when entering manual video at the 30 fps shutter limit.
                                        manual.copy(shutterNs = shutter, iso = caps.isoRange?.clamp(iso) ?: iso)
                                    } else manual
                                    model.changeCapture { safeManual }
                                }
                            }
                            select(control)
                        }
                    }, enabled = enabled, shape = RoundedCornerShape(12.dp),
                        color = if (active == control) MaterialTheme.colorScheme.primary.copy(alpha = .2f) else Color.Black.copy(alpha = .62f),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("control-${control.name.lowercase()}")
                            .semantics { contentDescription = "${control.title} $value" }) {
                        Column(Modifier.padding(horizontal = 2.dp, vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(control.title, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = if (enabled) .7f else .3f))
                            Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold, color = if (enabled) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = .4f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlSlider(control: LiveControl, state: CameraUiState, model: CameraViewModel, height: androidx.compose.ui.unit.Dp, enabled: Boolean) {
    val caps = state.capabilities
    val capture = state.capture
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
        LiveControl.WB -> caps.cctRange?.takeIf { it.upper > it.lower }?.let { r -> SliderValue((capture.kelvin ?: state.actual.kelvin ?: 5500).coerceIn(r.lower, r.upper).toFloat(), r.lower.toFloat()..r.upper.toFloat()) { v -> model.changeCapture { it.copy(kelvin = v.roundToInt(), wbLocked = false) } } }
            ?: SliderValue(caps.whiteBalances.indexOfFirst { it.mode == capture.wbMode }.coerceAtLeast(0).toFloat(), 0f..caps.whiteBalances.lastIndex.toFloat(), (caps.whiteBalances.size - 2).coerceAtLeast(0)) { v -> model.changeCapture { it.copy(wbMode = caps.whiteBalances[v.roundToInt()].mode, kelvin = null, wbLocked = false) } }
    }
    VerticalControlSlider(slider.value, slider.onChange, slider.range, slider.steps, enabled,
        Modifier.height(height).width(48.dp).testTag("live-slider-${control.name.lowercase()}").semantics { contentDescription = control.title })
}

private data class SliderValue(val value: Float, val range: ClosedFloatingPointRange<Float> = 0f..1f, val steps: Int = 0, val onChange: (Float) -> Unit)

/** Rotate the stable Material slider, including measurement and touch coordinates. */
@Composable
private fun VerticalControlSlider(value: Float, onChange: (Float) -> Unit, range: ClosedFloatingPointRange<Float>, steps: Int, enabled: Boolean, modifier: Modifier) {
    // Up always increases, independent of the user's horizontal writing direction.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Slider(value.coerceIn(range.start, range.endInclusive), onChange, enabled = enabled, valueRange = range, steps = steps,
        modifier = modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(Constraints.fixed(constraints.maxHeight, constraints.maxWidth))
            layout(placeable.height, placeable.width) { placeable.placeWithLayer((placeable.height - placeable.width) / 2, (placeable.width - placeable.height) / 2) { rotationZ = -90f } }
        })
    }
}
