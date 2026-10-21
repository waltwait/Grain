package tw.luma.camera.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer

/**
 * One motion for everything that opens: a fast start that settles gently, and a quicker exit. The system's
 * animation scale still applies, so "remove animations" makes all of it instant.
 */
internal object GrainMotion {
    /** Material 3 emphasized decelerate. */
    val Enter = CubicBezierEasing(.05f, .7f, .1f, 1f)
    /** Material 3 emphasized accelerate. */
    val Exit = CubicBezierEasing(.3f, 0f, .8f, .15f)

    fun <T> enter(durationMillis: Int = 300): FiniteAnimationSpec<T> = tween(durationMillis, easing = Enter)
    fun <T> exit(durationMillis: Int = 160): FiniteAnimationSpec<T> = tween(durationMillis, easing = Exit)

    /** A panel rising from the bottom: the controls panel, the film tray, a slider. */
    fun riseIn(): EnterTransition = fadeIn(enter(220)) + slideInVertically(enter()) { it / 5 } +
        scaleIn(enter(), initialScale = .96f, transformOrigin = TransformOrigin(.5f, 1f))
    fun sinkOut(): ExitTransition = fadeOut(exit()) + slideOutVertically(exit()) { it / 6 }

    /** A row that opens in place: the ratio choices, the gallery's search, the device details. */
    fun expandIn(): EnterTransition = fadeIn(enter(220)) + expandVertically(enter(), expandFrom = Alignment.Top)
    fun shrinkOut(): ExitTransition = fadeOut(exit(120)) + shrinkVertically(exit())
}

/** Fades in, growing from [from] of its size, the first time this content is shown, so a screen opens instead of popping in. */
@Composable
internal fun Modifier.openIn(from: Float = .96f): Modifier {
    val shown = remember { Animatable(0f) }
    LaunchedEffect(Unit) { shown.animateTo(1f, GrainMotion.enter()) }
    return graphicsLayer {
        alpha = shown.value
        val scale = from + (1f - from) * shown.value
        scaleX = scale
        scaleY = scale
    }
}
