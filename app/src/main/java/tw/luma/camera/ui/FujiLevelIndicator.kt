package tw.luma.camera.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import tw.luma.camera.camera.HorizonState

/**
 * Fujifilm-inspired electronic level indicator.
 * Displays fixed horizontal reference ticks and a dynamic rotating horizon line that snaps
 * and turns Grain brand gold (#FFD45B) when level within ±1°.
 */
@Composable
internal fun FujiLevelIndicator(state: HorizonState, modifier: Modifier = Modifier) {
    if (state.alpha <= 0.01f) return

    val view = LocalView.current
    LaunchedEffect(state.isLevel) {
        if (state.isLevel && state.alpha > 0.5f) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    val animatedRoll by animateFloatAsState(
        targetValue = if (state.isLevel) 0f else state.rollDegrees,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "horizon-roll"
    )

    val targetColor = if (state.isLevel) Color(0xFFFFD45B) else Color.White.copy(alpha = 0.72f)
    val color by animateColorAsState(targetColor, tween(120), label = "horizon-color")

    val density = LocalDensity.current
    val tickStartPx = with(density) { 34.dp.toPx() }
    val tickEndPx = with(density) { 54.dp.toPx() }
    val dynamicHalfLenPx = with(density) { 26.dp.toPx() }
    val strokePx = with(density) { 1.8.dp.toPx() }
    val shadowStrokePx = with(density) { 3.4.dp.toPx() }

    Box(modifier.fillMaxSize().cameraControlRotation().testTag("fuji-level-indicator"), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val center = this.center
            val effectiveAlpha = state.alpha
            val shadowColor = Color.Black.copy(alpha = 0.45f * effectiveAlpha)
            val strokeColor = color.copy(alpha = color.alpha * effectiveAlpha)

            // 1. Fixed reference ticks on left and right
            // Shadow behind reference ticks
            drawLine(shadowColor, Offset(center.x - tickEndPx, center.y), Offset(center.x - tickStartPx, center.y), shadowStrokePx, StrokeCap.Round)
            drawLine(shadowColor, Offset(center.x + tickStartPx, center.y), Offset(center.x + tickEndPx, center.y), shadowStrokePx, StrokeCap.Round)
            // Foreground reference ticks
            drawLine(strokeColor, Offset(center.x - tickEndPx, center.y), Offset(center.x - tickStartPx, center.y), strokePx, StrokeCap.Round)
            drawLine(strokeColor, Offset(center.x + tickStartPx, center.y), Offset(center.x + tickEndPx, center.y), strokePx, StrokeCap.Round)

            // 2. Dynamic horizon bar rotating around center
            rotate(degrees = -animatedRoll, pivot = center) {
                val start = Offset(center.x - dynamicHalfLenPx, center.y)
                val end = Offset(center.x + dynamicHalfLenPx, center.y)
                // Shadow
                drawLine(shadowColor, start, end, shadowStrokePx, StrokeCap.Round)
                // Foreground dynamic bar
                drawLine(strokeColor, start, end, strokePx, StrokeCap.Round)
            }
        }
    }
}
