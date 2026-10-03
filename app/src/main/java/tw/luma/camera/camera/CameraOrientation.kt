package tw.luma.camera.camera

import kotlin.math.abs

/** Sensor orientation is clockwise; CameraX Surface rotation is counter-clockwise. */
enum class CameraOrientation(val sensorDegrees: Int) {
    UPRIGHT(0), LEFT_UP(90), UPSIDE_DOWN(180), RIGHT_UP(270);

    val targetRotation: Int get() = (4 - sensorDegrees / 90) % 4

    fun controlDegrees(displayRotation: Int): Int = ((targetRotation - displayRotation + 4) % 4) * 90

    companion object {
        fun fromDisplayRotation(rotation: Int): CameraOrientation = entries.firstOrNull { it.targetRotation == rotation } ?: UPRIGHT

        /** Unwrap around the current angle so 270 → 0 takes 90°, rather than spinning 270°. */
        fun nearestControlAngle(current: Float, target: Int): Float =
            current + ((target - current) % 360f + 540f) % 360f - 180f

        fun keepCameraLayout(cameraActive: Boolean, smallestWidthDp: Int, multiWindow: Boolean): Boolean =
            cameraActive && smallestWidthDp in 1 until 600 && !multiWindow
    }
}

/** Keep the last direction when flat; 10° hysteresis avoids flutter near diagonal holds. */
class CameraOrientationTracker(initial: CameraOrientation = CameraOrientation.UPRIGHT) {
    var orientation: CameraOrientation = initial
        private set

    fun update(degrees: Int): CameraOrientation {
        if (degrees !in 0..359) return orientation
        val distance = abs(degrees - orientation.sensorDegrees).let { minOf(it, 360 - it) }
        if (distance >= 55) orientation = CameraOrientation.entries[((degrees + 45) / 90) % 4]
        return orientation
    }
}
