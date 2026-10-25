package tw.luma.camera.camera

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

data class HorizonState(
    /** Roll angle in degrees relative to current orientation; 0 means level. Positive means clockwise tilt. */
    val rollDegrees: Float = 0f,
    /** Whether the angle is within the level tolerance (e.g. ±1°). */
    val isLevel: Boolean = false,
    /** 0f when device is lying nearly flat on a table (pitch > 75°), 1f when held vertical/viewable. */
    val alpha: Float = 1f,
)

object HorizonMath {
    /** Level threshold: within 1.0 degree is considered level. */
    const val LEVEL_THRESHOLD_DEGREES = 1.0f

    /** If gravity in the screen plane is below this (m/s^2), the phone is too flat to determine horizon. */
    const val MIN_PLANE_GRAVITY = 2.5f
    const val FULL_PLANE_GRAVITY = 3.8f

    /**
     * Calculates the device horizon state from raw gravity/accelerometer vector (gx, gy, gz)
     * and the device's current [CameraOrientation].
     */
    fun calculate(gx: Float, gy: Float, gz: Float, orientation: CameraOrientation): HorizonState {
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

        var delta = (rawDegrees - targetDegrees) % 360f
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f

        val isLevel = abs(delta) <= LEVEL_THRESHOLD_DEGREES

        return HorizonState(
            rollDegrees = delta,
            isLevel = isLevel,
            alpha = alpha,
        )
    }

    /**
     * Exponential moving average to smooth sensor inputs without lagging.
     */
    fun smooth(current: Float, target: Float, alpha: Float = 0.25f): Float {
        return current + alpha * (target - current)
    }
}
