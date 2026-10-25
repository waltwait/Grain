package tw.luma.camera.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class HorizonMathTest {

    @Test
    fun uprightDeviceIsLevel() {
        val state = HorizonMath.calculate(0f, 9.8f, 0f, CameraOrientation.UPRIGHT)
        assertEquals(0f, state.rollDegrees, 0.01f)
        assertTrue("Within threshold is level", state.isLevel)
        assertEquals(1f, state.alpha, 0.01f)
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
    }

    @Test
    fun smoothingFollowsExponentialMovingAverage() {
        val smoothed = HorizonMath.smooth(0f, 10f, 0.25f)
        assertEquals(2.5f, smoothed, 0.001f)
    }
}
