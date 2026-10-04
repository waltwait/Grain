package tw.luma.camera.ui

import android.hardware.camera2.CaptureRequest
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tw.luma.camera.camera.ActualCapture
import tw.luma.camera.camera.CameraCapabilities
import tw.luma.camera.camera.CaptureSettings
import tw.luma.camera.camera.WhiteBalanceControls
import tw.luma.camera.gl.FilterSettings
import java.util.Locale
import kotlin.math.roundToInt

/** Two adjustments at a time; software grading remains available without camera CCT. */
@Composable
internal fun WhiteBalancePanel(
    capture: CaptureSettings,
    filter: FilterSettings,
    caps: CameraCapabilities,
    actual: ActualCapture,
    enabled: Boolean,
    onCapture: ((CaptureSettings) -> CaptureSettings) -> Unit,
    onFilter: ((FilterSettings) -> FilterSettings) -> Unit,
) {
    val range = caps.kelvinRange
    var cameraTemperature by remember(caps.id, range) { mutableStateOf(range != null) }
    if (range != null) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(cameraTemperature, { cameraTemperature = true }, { Text("色溫 K") }, enabled = enabled, colors = grainChipColors(),
                modifier = Modifier.testTag("wb-mode-camera"))
            FilterChip(!cameraTemperature, { cameraTemperature = false }, { Text("冷暖調色") }, enabled = enabled, colors = grainChipColors(),
                modifier = Modifier.testTag("wb-mode-grading"))
        }
    }
    if (caps.whiteBalances.isNotEmpty()) {
        var open by remember { mutableStateOf(false) }
        val label = if (capture.kelvin != null) "手動色溫" else caps.whiteBalances.find { it.mode == capture.wbMode }?.label ?: "自動"
        Box {
            TextButton({ open = true }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("wb-presets")) {
                Text("相機白平衡：$label ▾", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            DropdownMenu(open && enabled, { open = false }) {
                caps.whiteBalances.forEach { wb ->
                    DropdownMenuItem(text = { Text(wb.label) }, onClick = {
                        onCapture { it.copy(wbMode = wb.mode, kelvin = null, tint = 0, wbLocked = false) }
                        open = false
                    }, modifier = Modifier.testTag("wb-preset-${wb.mode}"))
                }
            }
        }
    }
    if (cameraTemperature && range != null) {
        val kelvin = (capture.kelvin ?: actual.kelvin?.takeIf { caps.whiteBalanceBackend == tw.luma.camera.camera.WhiteBalanceBackend.CCT }
            ?: 5500).coerceIn(range.lower, range.upper)
        fun setKelvin(value: Float) = onCapture {
            it.copy(kelvin = WhiteBalanceControls.kelvin(value, range.lower, range.upper), wbLocked = false)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (capture.kelvin != null) "色溫 · ${capture.kelvin} K"
                else if (capture.wbMode == CaptureRequest.CONTROL_AWB_MODE_AUTO) "色溫 · 自動" else "色溫 · 預設",
                Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
            TextButton({ setKelvin(kelvin - 50f) }, enabled = enabled && kelvin > range.lower,
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag("wb-kelvin-minus")
                    .semantics { contentDescription = "色溫減少 50 K" }) { Text("−") }
            TextButton({ setKelvin(kelvin + 50f) }, enabled = enabled && kelvin < range.upper,
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag("wb-kelvin-plus")
                    .semantics { contentDescription = "色溫增加 50 K" }) { Text("+") }
        }
        HorizontalControlSlider(kelvin.toFloat(), { setKelvin(it) }, range.lower.toFloat()..range.upper.toFloat(), 0, enabled,
            Modifier.fillMaxWidth().testTag("live-slider-wb").semantics { contentDescription = "色溫，左右滑動調整" })
        AdjustmentLabel("色偏", capture.tint.toFloat())
        HorizontalControlSlider(capture.tint.toFloat(), { value -> onCapture { it.copy(tint = value.roundToInt()) } },
            -50f..50f, 99, enabled && capture.kelvin != null,
            Modifier.fillMaxWidth().testTag("wb-camera-tint").semantics { contentDescription = "相機白平衡色偏，左右滑動調整" })
    } else {
        SoftwareWhiteBalanceSliders(filter, enabled, onFilter)
    }
    if (caps.awbLock && capture.kelvin == null && capture.wbMode == CaptureRequest.CONTROL_AWB_MODE_AUTO) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("鎖定自動白平衡", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
            Switch(capture.wbLocked || capture.manual, { lock -> onCapture { it.copy(wbLocked = lock) } }, enabled = enabled && !capture.manual,
                modifier = Modifier.testTag("wb-lock").semantics { contentDescription = "鎖定自動白平衡" })
        }
    }
}

@Composable
internal fun SoftwareWhiteBalanceSliders(
    filter: FilterSettings,
    enabled: Boolean,
    onFilter: ((FilterSettings) -> FilterSettings) -> Unit,
) {
    AdjustmentLabel("冷暖 · 冷 ↔ 暖", filter.warmth)
    HorizontalControlSlider(filter.warmth, { value -> onFilter { it.copy(warmth = value) } }, -100f..100f, 199, enabled,
        Modifier.fillMaxWidth().testTag("wb-warmth").semantics { contentDescription = "冷暖調色，往左偏冷，往右偏暖" })
    AdjustmentLabel("色偏 · 綠 ↔ 洋紅", filter.tint)
    HorizontalControlSlider(filter.tint, { value -> onFilter { it.copy(tint = value) } }, -100f..100f, 199, enabled,
        Modifier.fillMaxWidth().testTag("wb-grading-tint").semantics { contentDescription = "調色色偏，往左偏綠，往右偏洋紅" })
}

@Composable
private fun AdjustmentLabel(label: String, value: Float) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
        Text("%+.0f".format(Locale.US, value), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
    }
}
