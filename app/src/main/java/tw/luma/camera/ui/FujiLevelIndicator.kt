package tw.luma.camera.ui

import android.os.SystemClock
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import tw.luma.camera.camera.HorizonState

private val GrainGold = Color(0xFFFFD45B)
private const val HAPTIC_COOLDOWN_MS = 450L
private const val MAX_PITCH_DISPLAY_DEGREES = 18f

/**
 * Collects [HorizonState] inside this leaf composable so sensor updates do not trigger
 * recompositions of the parent [CameraScreen].
 */
@Composable
internal fun FujiLevelIndicator(stateFlow: StateFlow<HorizonState>, modifier: Modifier = Modifier) {
    val state by stateFlow.collectAsStateWithLifecycle()
    FujiLevelIndicator(state = state, modifier = modifier)
}

/**
 * Fujifilm X-series inspired 2D electronic level indicator.
 *
 * - Outer fixed reference brackets (`[` and `]`) with horizontal scale ticks.
 * - Split left/right dynamic roll wings that rotate with device roll and leave the center subject clear.
 * - Central micro-crosshair reticle and a 2D pitch bar that tracks forward/backward tilt when shooting
 *   near-horizontal (±18°), fading out at steep angles so downward/upward shots remain uncluttered.
 * - Smoothly snaps and illuminates in Grain brand gold (#FFD45B) with rate-limited haptic feedback.
 */
@Composable
internal fun FujiLevelIndicator(state: HorizonState, modifier: Modifier = Modifier) {
    if (state.alpha <= 0.01f) return

    val view = LocalView.current
    var prevRollLevel by remember { mutableStateOf(state.isLevel) }
    var prevPitchLevel by remember { mutableStateOf(state.isPitchLevel) }
    var lastHapticTimeMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(state.isLevel, state.isPitchLevel) {
        val enteredRollLevel = !prevRollLevel && state.isLevel
        val enteredFull2DLevel = state.isLevel && !prevPitchLevel && state.isPitchLevel
        prevRollLevel = state.isLevel
        prevPitchLevel = state.isPitchLevel

        if ((enteredRollLevel || enteredFull2DLevel) && state.alpha > 0.5f) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastHapticTimeMs >= HAPTIC_COOLDOWN_MS) {
                lastHapticTimeMs = now
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
        }
    }

    val animatedRoll by animateFloatAsState(
        targetValue = if (state.isLevel) 0f else state.rollDegrees,
        animationSpec = spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "horizon-roll",
    )

    val clampedPitch = if (state.isPitchLevel) {
        0f
    } else {
        state.pitchDegrees.coerceIn(-MAX_PITCH_DISPLAY_DEGREES, MAX_PITCH_DISPLAY_DEGREES)
    }
    val animatedPitch by animateFloatAsState(
        targetValue = clampedPitch,
        animationSpec = spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "horizon-pitch",
    )

    val animatedWingGapDp by animateFloatAsState(
        targetValue = if (state.isLevel) 8.5f else 11.5f,
        animationSpec = tween(140),
        label = "horizon-wing-gap",
    )

    val rollColor by animateColorAsState(
        targetValue = if (state.isLevel) GrainGold else Color.White.copy(alpha = 0.82f),
        animationSpec = tween(130),
        label = "horizon-roll-color",
    )
    val refTickColor by animateColorAsState(
        targetValue = if (state.isLevel) GrainGold.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.58f),
        animationSpec = tween(130),
        label = "horizon-ref-color",
    )
    val pitchColor by animateColorAsState(
        targetValue = if (state.isPitchLevel) GrainGold else Color.White.copy(alpha = 0.76f),
        animationSpec = tween(130),
        label = "horizon-pitch-color",
    )
    val reticleColor by animateColorAsState(
        targetValue = when {
            state.isLevel && state.isPitchLevel -> GrainGold
            state.isLevel -> GrainGold.copy(alpha = 0.72f)
            else -> Color.White.copy(alpha = 0.45f)
        },
        animationSpec = tween(130),
        label = "horizon-reticle-color",
    )

    val density = LocalDensity.current
    val refInnerPx = with(density) { 65.dp.toPx() }
    val refOuterPx = with(density) { 86.dp.toPx() }
    val refCapHalfHeightPx = with(density) { 2.6.dp.toPx() }
    val wingOuterPx = with(density) { 58.dp.toPx() }
    val reticleInnerPx = with(density) { 1.8.dp.toPx() }
    val reticleOuterPx = with(density) { 4.6.dp.toPx() }
    val pitchHalfWidthPx = with(density) { 6.5.dp.toPx() }
    val maxPitchOffsetPx = with(density) { 14.dp.toPx() }
    val strokePx = with(density) { 1.5.dp.toPx() }
    val thinStrokePx = with(density) { 1.2.dp.toPx() }
    val shadowStrokePx = with(density) { 2.9.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .cameraControlRotation()
            .testTag("fuji-level-indicator"),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val center = this.center
            val effectiveAlpha = state.alpha
            val shadowColor = Color.Black.copy(alpha = 0.42f * effectiveAlpha)
            val wingInnerPx = with(density) { animatedWingGapDp.dp.toPx() }

            val activeRefColor = refTickColor.copy(alpha = refTickColor.alpha * effectiveAlpha)
            val activeRollColor = rollColor.copy(alpha = rollColor.alpha * effectiveAlpha)
            val activeReticleColor = reticleColor.copy(alpha = reticleColor.alpha * effectiveAlpha)

            // 1. Center Fujifilm micro-crosshair reticle (+)
            drawShadowedLine(
                shadowColor = shadowColor,
                fgColor = activeReticleColor,
                start = Offset(center.x - reticleOuterPx, center.y),
                end = Offset(center.x - reticleInnerPx, center.y),
                fgStroke = thinStrokePx,
                shadowStroke = shadowStrokePx * 0.85f,
            )
            drawShadowedLine(
                shadowColor = shadowColor,
                fgColor = activeReticleColor,
                start = Offset(center.x + reticleInnerPx, center.y),
                end = Offset(center.x + reticleOuterPx, center.y),
                fgStroke = thinStrokePx,
                shadowStroke = shadowStrokePx * 0.85f,
            )
            drawShadowedLine(
                shadowColor = shadowColor,
                fgColor = activeReticleColor,
                start = Offset(center.x, center.y - reticleOuterPx),
                end = Offset(center.x, center.y - reticleInnerPx),
                fgStroke = thinStrokePx,
                shadowStroke = shadowStrokePx * 0.85f,
            )
            drawShadowedLine(
                shadowColor = shadowColor,
                fgColor = activeReticleColor,
                start = Offset(center.x, center.y + reticleInnerPx),
                end = Offset(center.x, center.y + reticleOuterPx),
                fgStroke = thinStrokePx,
                shadowStroke = shadowStrokePx * 0.85f,
            )

            // 2. Fixed outer reference brackets (left and right)
            // Horizontal reference ticks
            drawShadowedLine(
                shadowColor = shadowColor,
                fgColor = activeRefColor,
                start = Offset(center.x - refOuterPx, center.y),
                end = Offset(center.x - refInnerPx, center.y),
                fgStroke = strokePx,
                shadowStroke = shadowStrokePx,
            )
            drawShadowedLine(
                shadowColor = shadowColor,
                fgColor = activeRefColor,
                start = Offset(center.x + refInnerPx, center.y),
                end = Offset(center.x + refOuterPx, center.y),
                fgStroke = strokePx,
                shadowStroke = shadowStrokePx,
            )
            // Vertical inner bracket caps
            drawShadowedLine(
                shadowColor = shadowColor,
                fgColor = activeRefColor,
                start = Offset(center.x - refInnerPx, center.y - refCapHalfHeightPx),
                end = Offset(center.x - refInnerPx, center.y + refCapHalfHeightPx),
                fgStroke = thinStrokePx,
                shadowStroke = shadowStrokePx * 0.85f,
            )
            drawShadowedLine(
                shadowColor = shadowColor,
                fgColor = activeRefColor,
                start = Offset(center.x + refInnerPx, center.y - refCapHalfHeightPx),
                end = Offset(center.x + refInnerPx, center.y + refCapHalfHeightPx),
                fgStroke = thinStrokePx,
                shadowStroke = shadowStrokePx * 0.85f,
            )

            // 3. Dynamic 2D Pitch bar inside the central window (when near-horizontal)
            if (state.pitchAlpha > 0.01f) {
                val pAlpha = state.pitchAlpha
                val pitchShadow = Color.Black.copy(alpha = 0.42f * pAlpha)
                val activePitchColor = pitchColor.copy(alpha = pitchColor.alpha * pAlpha)
                val pitchY = center.y + (animatedPitch / MAX_PITCH_DISPLAY_DEGREES) * maxPitchOffsetPx

                drawShadowedLine(
                    shadowColor = pitchShadow,
                    fgColor = activePitchColor,
                    start = Offset(center.x - pitchHalfWidthPx, pitchY),
                    end = Offset(center.x + pitchHalfWidthPx, pitchY),
                    fgStroke = strokePx,
                    shadowStroke = shadowStrokePx,
                )
            }

            // 4. Dynamic Roll wings rotating around center pivot
            rotate(degrees = -animatedRoll, pivot = center) {
                drawShadowedLine(
                    shadowColor = shadowColor,
                    fgColor = activeRollColor,
                    start = Offset(center.x - wingOuterPx, center.y),
                    end = Offset(center.x - wingInnerPx, center.y),
                    fgStroke = strokePx,
                    shadowStroke = shadowStrokePx,
                )
                drawShadowedLine(
                    shadowColor = shadowColor,
                    fgColor = activeRollColor,
                    start = Offset(center.x + wingInnerPx, center.y),
                    end = Offset(center.x + wingOuterPx, center.y),
                    fgStroke = strokePx,
                    shadowStroke = shadowStrokePx,
                )
            }
        }
    }
}

private fun DrawScope.drawShadowedLine(
    shadowColor: Color,
    fgColor: Color,
    start: Offset,
    end: Offset,
    fgStroke: Float,
    shadowStroke: Float,
) {
    drawLine(shadowColor, start, end, shadowStroke, StrokeCap.Round)
    drawLine(fgColor, start, end, fgStroke, StrokeCap.Round)
}
