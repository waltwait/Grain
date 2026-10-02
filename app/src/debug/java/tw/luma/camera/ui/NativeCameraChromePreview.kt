package tw.luma.camera.ui

import android.hardware.camera2.CaptureRequest
import android.util.Range
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import tw.luma.camera.CameraUiState
import tw.luma.camera.camera.CameraCapabilities
import tw.luma.camera.camera.CaptureSettings
import tw.luma.camera.camera.WhiteBalance

// Design fixtures only: previews do not start the camera or claim device capabilities.
@Preview(name = "320 dp · ISO", widthDp = 320, heightDp = 480)
@Composable
private fun SmallCameraChromePreview() = CameraChromePreview(LiveControl.ISO)

@Preview(name = "Landscape · zoom", widthDp = 640, heightDp = 300)
@Composable
private fun LandscapeCameraChromePreview() = CameraChromePreview(LiveControl.ZOOM)

@Preview(name = "Large type · white balance", widthDp = 360, heightDp = 640, fontScale = 1.3f)
@Composable
private fun LargeTypeCameraChromePreview() = CameraChromePreview(LiveControl.WB)

@Composable
private fun CameraChromePreview(initialControl: LiveControl) {
    var active by remember { mutableStateOf<LiveControl?>(initialControl) }
    val state = remember(initialControl) {
        CameraUiState(
            ready = true, minZoom = .5f, maxZoom = 8f,
            capture = CaptureSettings(manual = initialControl == LiveControl.ISO, iso = 400, shutterNs = 8_000_000),
            capabilities = CameraCapabilities(
                manualSensor = true, isoRange = Range(100, 6400), shutterRange = Range(100_000L, 1_000_000_000L),
                exposureRange = Range(-6, 6), exposureStep = 1f / 3f,
                whiteBalances = listOf(WhiteBalance(CaptureRequest.CONTROL_AWB_MODE_AUTO, "自動"),
                    WhiteBalance(CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT, "日光")),
            ),
        )
    }
    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFFFD45B), onPrimary = Color(0xFF231C08))) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF393E3A), Color(0xFF151918))))) {
            NativeCameraToolbar(state, Modifier.align(Alignment.TopCenter), panel = {}) { Text("00:00", color = Color.White) }
            NativeCameraControls(state, active, select = { active = it }, reset = { active = null },
                slider = { _, width, enabled ->
                    HorizontalControlSlider(.5f, {}, 0f..1f, 0, enabled, Modifier.width(width))
                }, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}
