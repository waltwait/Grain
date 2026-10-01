package tw.luma.camera

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import tw.luma.camera.ui.CameraScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFFFFD45B), onPrimary = Color(0xFF231C08),
                secondary = Color(0xFFE0E0E5), background = Color.Black,
                surface = Color(0xFF18191C), surfaceVariant = Color(0xFF2D2E33),
            )) {
                CameraScreen(viewModel())
            }
        }
    }
}
