package tw.luma.camera.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.delay
import tw.luma.camera.CameraUiState
import tw.luma.camera.camera.CaptureFeedback
import tw.luma.camera.camera.PhotoPhase

@Preview(name = "Quiet capture · portrait", widthDp = 360, heightDp = 640)
@Composable private fun QuietCapturePortraitPreview() = CaptureFeedbackPreview()

@Preview(name = "Quiet capture · 320 dp", widthDp = 320, heightDp = 480)
@Composable private fun QuietCaptureSmallPreview() = CaptureFeedbackPreview()

@Preview(name = "Quiet capture · landscape", widthDp = 640, heightDp = 300)
@Composable private fun QuietCaptureLandscapePreview() = CaptureFeedbackPreview()

@Preview(name = "Inline error · large font", widthDp = 360, heightDp = 640, fontScale = 1.5f)
@Composable private fun QuietCaptureErrorPreview() = CaptureFeedbackPreview("照片未儲存，請再試一次")

// An interactive timing fixture. It uses a small solid bitmap, not a camera or a saved photo.
@Composable private fun CaptureFeedbackPreview(message: String? = null) {
    var feedback by remember { mutableStateOf(CaptureFeedback()) }
    var thumbnail by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(feedback.phase) {
        when (feedback.phase) {
            PhotoPhase.CAPTURING -> { delay(100); feedback = feedback.capturedPhoto(feedback.requestId) }
            PhotoPhase.SAVING -> {
                delay(800)
                thumbnail = createBitmap(160, 160).apply { eraseColor(0xFF748677.toInt()) }
                feedback = feedback.savedPhoto(feedback.requestId)
            }
            PhotoPhase.IDLE -> Unit
        }
    }
    val state = CameraUiState(ready = true, busy = feedback.phase != PhotoPhase.IDLE, captureFeedback = feedback, message = message)
    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFFFD45B), onPrimary = Color(0xFF231C08))) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF414A43), Color(0xFF18211C))))) {
            ViewfinderCaptureFeedback(feedback.capturedPhotoId, Modifier.fillMaxSize())
            NativeCameraToolbar(state, Modifier.align(Alignment.TopCenter), panel = {}) { Text("00:00") }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black).padding(horizontal = 22.dp, vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    SavedMediaButton(thumbnail, feedback.savedRevision, feedback.phase == PhotoPhase.SAVING, !state.busy, {})
                    CameraCaptureButton(state) { feedback = feedback.startPhoto() }
                    Spacer(Modifier.size(48.dp))
                }
                CameraStatusLine(message)
            }
        }
    }
}
