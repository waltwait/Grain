package tw.luma.camera.camera

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

data class HorizonState(
    /** Roll angle in degrees relative to current orientation; 0 means level. Positive means clockwise tilt. */
    val rollDegrees: Float = 0f,
    /** Pitch angle in degrees relative to horizontal optical axis; 0 means upright perpendicular to ground. */
    val pitchDegrees: Float = 0f,
    /** Whether the roll angle is within the level tolerance (with hysteresis). */
    val isLevel: Boolean = false,
    /** Whether the pitch angle is within the level tolerance (with hysteresis). */
    val isPitchLevel: Boolean = false,
    /** 0f when device is lying nearly flat on a table (pitch > 75°), 1f when held vertical/viewable. */
    val alpha: Float = 1f,
    /** 1f when camera pitch is near horizontal (±18°), fading to 0f beyond ±28° so steep shots stay uncluttered. */
    val pitchAlpha: Float = 1f,
)

object HorizonMath {
    /** Level entry threshold: within 1.0 degree is considered level. */
    const val LEVEL_THRESHOLD_DEGREES = 1.0f

    /** Hysteresis exit threshold: stays level until tilt exceeds 1.35 degrees to prevent boundary flicker. */
    const val LEVEL_EXIT_THRESHOLD_DEGREES = 1.35f

    /** If gravity in the screen plane is below this (m/s^2), the phone is too flat to determine horizon. */
    const val MIN_PLANE_GRAVITY = 2.5f
    const val FULL_PLANE_GRAVITY = 3.8f

    /** Pitch indicator visibility range in degrees. */
    const val FULL_PITCH_VISIBLE_DEGREES = 18f
    const val MAX_PITCH_VISIBLE_DEGREES = 28f

    /** Adaptive filter parameters (m/s^2 delta thresholds and alpha bounds). */
    private const val NOISE_FLOOR_DELTA = 0.12f
    private const val FAST_MOTION_DELTA = 1.6f
    private const val MIN_SMOOTH_ALPHA = 0.08f
    private const val MAX_SMOOTH_ALPHA = 0.55f

    /**
     * Calculates the 2D device horizon state from gravity/accelerometer vector (gx, gy, gz)
     * and the device's current [CameraOrientation], applying hysteresis when [wasRollLevel] or
     * [wasPitchLevel] is provided.
     */
    fun calculate(
        gx: Float,
        gy: Float,
        gz: Float,
        orientation: CameraOrientation,
        wasRollLevel: Boolean = false,
        wasPitchLevel: Boolean = false,
    ): HorizonState {
        val planeR = sqrt(gx * gx + gy * gy)
        val alpha = when {
            planeR <= MIN_PLANE_GRAVITY -> 0f
            planeR >= FULL_PLANE_GRAVITY -> 1f
            else -> (planeR - MIN_PLANE_GRAVITY) / (FULL_PLANE_GRAVITY - MIN_PLANE_GRAVITY)
        }

        // Angle in degrees of the gravity vector in screen coordinates (-180..180)
        // gx points right, gy points up. When upright, gy > 0.
        val rawDegrees = Math.toDegrees(atan2(gx.toDouble(), gy.toDouble())).toFloat()
        val targetDegrees = orientation.sensorDegrees.toFloat()

        var rollDelta = (rawDegrees - targetDegrees) % 360f
        if (rollDelta > 180f) rollDelta -= 360f
        if (rollDelta < -180f) rollDelta += 360f

        val rollThreshold = if (wasRollLevel) LEVEL_EXIT_THRESHOLD_DEGREES else LEVEL_THRESHOLD_DEGREES
        val isRollLevel = abs(rollDelta) <= rollThreshold

        // Pitch angle: 0° when screen is vertical (gz = 0), positive when tilted forward (screen facing up),
        // negative when tilted backward (screen facing down).
        val pitchDegrees = if (planeR > 0.001f || abs(gz) > 0.001f) {
            Math.toDegrees(atan2(gz.toDouble(), planeR.toDouble())).toFloat()
        } else {
            0f
        }
        val absPitch = abs(pitchDegrees)
        val pitchThreshold = if (wasPitchLevel) LEVEL_EXIT_THRESHOLD_DEGREES else LEVEL_THRESHOLD_DEGREES
        val isPitchLevel = absPitch <= pitchThreshold

        val pitchAlpha = when {
            absPitch <= FULL_PITCH_VISIBLE_DEGREES -> 1f
            absPitch >= MAX_PITCH_VISIBLE_DEGREES -> 0f
            else -> 1f - (absPitch - FULL_PITCH_VISIBLE_DEGREES) / (MAX_PITCH_VISIBLE_DEGREES - FULL_PITCH_VISIBLE_DEGREES)
        } * alpha

        return HorizonState(
            rollDegrees = rollDelta,
            pitchDegrees = pitchDegrees,
            isLevel = isRollLevel,
            isPitchLevel = isPitchLevel,
            alpha = alpha,
            pitchAlpha = pitchAlpha,
        )
    }

    /**
     * Exponential moving average with explicit alpha.
     */
    fun smooth(current: Float, target: Float, alpha: Float = 0.25f): Float {
        return current + alpha * (target - current)
    }

    /**
     * Adaptive low-pass filter: strongly damps micro hand tremors and sensor noise when stationary,
     * while ramping up responsiveness during deliberate camera movement for zero-lag tracking.
     */
    fun adaptiveSmooth(current: Float, target: Float): Float {
        val delta = abs(target - current)
        val t = ((delta - NOISE_FLOOR_DELTA) / (FAST_MOTION_DELTA - NOISE_FLOOR_DELTA)).coerceIn(0f, 1f)
        val adaptiveAlpha = MIN_SMOOTH_ALPHA + t * (MAX_SMOOTH_ALPHA - MIN_SMOOTH_ALPHA)
        return current + adaptiveAlpha * (target - current)
    }
}
