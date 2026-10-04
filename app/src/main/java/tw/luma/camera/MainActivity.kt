package tw.luma.camera

import android.os.Bundle
import android.os.Build
import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.OrientationEventListener
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import tw.luma.camera.ui.CameraScreen
import tw.luma.camera.ui.LocalCameraControlRotation
import tw.luma.camera.camera.CameraOrientation
import tw.luma.camera.camera.CameraOrientationTracker

class MainActivity : ComponentActivity() {
    private var cameraActive = true
    private var resumed = false
    private var orientation by mutableStateOf(CameraOrientation.UPRIGHT)
    private lateinit var orientationTracker: CameraOrientationTracker
    private val orientationListener by lazy {
        object : OrientationEventListener(this) {
            override fun onOrientationChanged(degrees: Int) { orientation = orientationTracker.update(degrees) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraActive = savedInstanceState?.getBoolean("camera-active", true) ?: true
        updateWindowOrientation()
        orientation = CameraOrientation.fromDisplayRotation(savedInstanceState?.getInt("camera-rotation") ?: displayRotation())
        orientationTracker = CameraOrientationTracker(orientation)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFFFFD45B), onPrimary = Color(0xFF231C08),
                secondary = Color(0xFFE0E0E5), background = Color.Black,
                surface = Color(0xFF18191C), surfaceVariant = Color(0xFF2D2E33),
            )) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground) {
                    CompositionLocalProvider(LocalCameraControlRotation provides orientation.controlDegrees(displayRotation())) {
                        CameraScreen(viewModel(), orientation, ::setCameraActive)
                    }
                }
            }
        }
    }

    private fun setCameraActive(active: Boolean) {
        if (cameraActive == active) return
        cameraActive = active
        updateWindowOrientation()
        updateOrientationListener()
    }

    // Intentional camera behavior requested by the user; gallery and large/resizable windows remain adaptive.
    @SuppressLint("SourceLockedOrientationActivity")
    private fun updateWindowOrientation() {
        val requested = if (CameraOrientation.keepCameraLayout(cameraActive, resources.configuration.smallestScreenWidthDp, isInMultiWindowMode))
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        if (requestedOrientation != requested) requestedOrientation = requested
    }

    private fun updateOrientationListener() {
        if (cameraActive && resumed && orientationListener.canDetectOrientation())
            orientationListener.enable() else orientationListener.disable()
    }

    @Suppress("DEPRECATION")
    private fun displayRotation(): Int = if (Build.VERSION.SDK_INT >= 30) display?.rotation ?: 0 else windowManager.defaultDisplay.rotation

    override fun onResume() { super.onResume(); resumed = true; updateOrientationListener() }
    override fun onPause() { resumed = false; orientationListener.disable(); super.onPause() }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("camera-active", cameraActive)
        outState.putInt("camera-rotation", orientation.targetRotation)
        super.onSaveInstanceState(outState)
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        updateWindowOrientation()
    }
}
