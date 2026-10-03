package tw.luma.camera.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import tw.luma.camera.camera.CameraOrientation

internal val LocalCameraControlRotation = compositionLocalOf { 0 }

/** Rotate only a button's contents. Its placement, touch target and semantics stay fixed. */
@Composable
internal fun Modifier.cameraControlRotation(): Modifier {
    val degrees = LocalCameraControlRotation.current
    val rotation = remember { Animatable(degrees.toFloat()) }
    LaunchedEffect(degrees) {
        rotation.animateTo(CameraOrientation.nearestControlAngle(rotation.value, degrees), tween(160, easing = FastOutSlowInEasing))
    }
    return graphicsLayer { rotationZ = rotation.value }
}
