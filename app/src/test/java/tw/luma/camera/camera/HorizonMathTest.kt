package tw.luma.camera.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class HorizonMathTest {

    @Test
    fun uprightDeviceIsLevelInBothAxes() {
        val state = HorizonMath.calculate(0f, 9.8f, 0f, CameraOrientation.UPRIGHT)
        assertEquals(0f, state.rollDegrees, 0.01f)
        assertEquals(0f, state.pitchDegrees, 0.01f)
        assertTrue("Within threshold is roll level", state.isLevel)
        assertTrue("Within threshold is pitch level", state.isPitchLevel)
        assertEquals(1f, state.alpha, 0.01f)
        assertEquals(1f, state.pitchAlpha, 0.01f)
    }

    @Test
    fun smallTiltWithinOneDegreeIsLevel() {
        val rad = Math.toRadians(0.7).toFloat()
        val gx = 9.8f * sin(rad)
        val gy = 9.8f * cos(rad)
        val state = HorizonMath.calculate(gx, gy, 0f, CameraOrientation.UPRIGHT)
        assertEquals(0.7f, state.rollDegrees, 0.05f)
        assertTrue(state.isLevel)
    }

    @Test
    fun tiltOverOneDegreeIsNotLevel() {
        val rad = Math.toRadians(3.5).toFloat()
        val gx = 9.8f * sin(rad)
        val gy = 9.8f * cos(rad)
        val state = HorizonMath.calculate(gx, gy, 0f, CameraOrientation.UPRIGHT)
        assertEquals(3.5f, state.rollDegrees, 0.05f)
        assertFalse(state.isLevel)
    }

    @Test
    fun hysteresisKeepsLevelWithinExitThresholdAndReleasesAboveIt() {
        val withinExitRad = Math.toRadians(1.2).toFloat()
        val gxWithin = 9.8f * sin(withinExitRad)
        val gyWithin = 9.8f * cos(withinExitRad)

        val notPreviouslyLevel = HorizonMath.calculate(gxWithin, gyWithin, 0f, CameraOrientation.UPRIGHT, wasRollLevel = false)
        assertFalse("1.2° should not enter level from outside", notPreviouslyLevel.isLevel)

        val previouslyLevel = HorizonMath.calculate(gxWithin, gyWithin, 0f, CameraOrientation.UPRIGHT, wasRollLevel = true)
        assertTrue("1.2° should stay level due to hysteresis", previouslyLevel.isLevel)

        val beyondExitRad = Math.toRadians(1.5).toFloat()
        val gxBeyond = 9.8f * sin(beyondExitRad)
        val gyBeyond = 9.8f * cos(beyondExitRad)
        val exited = HorizonMath.calculate(gxBeyond, gyBeyond, 0f, CameraOrientation.UPRIGHT, wasRollLevel = true)
        assertFalse("1.5° exceeds hysteresis exit threshold", exited.isLevel)
    }

    @Test
    fun pitchCalculatesForwardTiltAndFadesAtSteepAngles() {
        val pitch8Rad = Math.toRadians(8.0).toFloat()
        val state8 = HorizonMath.calculate(0f, 9.8f * cos(pitch8Rad), 9.8f * sin(pitch8Rad), CameraOrientation.UPRIGHT)
        assertEquals(8f, state8.pitchDegrees, 0.05f)
        assertFalse(state8.isPitchLevel)
        assertEquals(1f, state8.pitchAlpha, 0.01f)

        val pitch35Rad = Math.toRadians(35.0).toFloat()
        val state35 = HorizonMath.calculate(0f, 9.8f * cos(pitch35Rad), 9.8f * sin(pitch35Rad), CameraOrientation.UPRIGHT)
        assertEquals(35f, state35.pitchDegrees, 0.05f)
        assertEquals("Steep pitch hides central pitch bar to avoid clutter", 0f, state35.pitchAlpha, 0.01f)
        assertEquals("Roll indicator remains visible at 35° pitch", 1f, state35.alpha, 0.01f)
    }

    @Test
    fun landscapeLeftUpIsLevel() {
        val state = HorizonMath.calculate(9.8f, 0f, 0f, CameraOrientation.LEFT_UP)
        assertEquals(0f, state.rollDegrees, 0.01f)
        assertTrue(state.isLevel)
        assertEquals(1f, state.alpha, 0.01f)
    }

    @Test
    fun landscapeRightUpIsLevel() {
        val state = HorizonMath.calculate(-9.8f, 0f, 0f, CameraOrientation.RIGHT_UP)
        assertEquals(0f, state.rollDegrees, 0.01f)
        assertTrue(state.isLevel)
        assertEquals(1f, state.alpha, 0.01f)
    }

    @Test
    fun flatDeviceFadesToInvisible() {
        // Flat on table (only Z axis has gravity)
        val state = HorizonMath.calculate(0.2f, 0.3f, 9.8f, CameraOrientation.UPRIGHT)
        assertEquals("When plane gravity is under 2.5, alpha must be 0", 0f, state.alpha, 0.01f)
        assertEquals(0f, state.pitchAlpha, 0.01f)
    }

    @Test
    fun smoothingFollowsExponentialMovingAverageAndAdaptiveFilter() {
        val smoothed = HorizonMath.smooth(0f, 10f, 0.25f)
        assertEquals(2.5f, smoothed, 0.001f)

        // Small noise (delta = 0.05) -> heavily damped with alpha = 0.08
        val microSmoothed = HorizonMath.adaptiveSmooth(0f, 0.05f)
        assertEquals(0.05f * 0.08f, microSmoothed, 0.0005f)

        // Fast movement (delta = 2.0) -> fast tracking with alpha = 0.55
        val fastSmoothed = HorizonMath.adaptiveSmooth(0f, 2.0f)
        assertEquals(2.0f * 0.55f, fastSmoothed, 0.001f)
    }
}
