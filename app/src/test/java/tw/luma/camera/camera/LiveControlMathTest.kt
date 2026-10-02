package tw.luma.camera.camera

import org.junit.Assert.*
import org.junit.Test

class LiveControlMathTest {
    @Test fun zoomAndSensorScalesRoundTripAcrossLargeRanges() {
        for ((min, max) in listOf(.6 to 30.0, 50.0 to 6400.0, 100_000.0 to 1_000_000_000.0)) {
            for (position in listOf(0f, .01f, .25f, .5f, .9f, 1f)) {
                val value = LiveControlMath.value(position, min, max)
                assertTrue(value in min..max)
                assertEquals(position, LiveControlMath.position(value, min, max), .0001f)
            }
        }
    }
    @Test fun endpointsClampRatherThanExceedDeviceZoom() {
        assertEquals(.6, LiveControlMath.value(-1f, .6, 30.0), .00001)
        assertEquals(30.0, LiveControlMath.value(2f, .6, 30.0), .00001)
        assertEquals(1f, LiveControlMath.position(80.0, .6, 30.0), 0f)
    }
    @Test fun fixedLensDoesNotDivideByZero() {
        assertEquals(0f, LiveControlMath.position(1.0, 1.0, 1.0), 0f)
        assertEquals(1.0, LiveControlMath.value(.5f, 1.0, 1.0), 0.0)
    }
    @Test fun swipeDirectionMatchesBrightnessAndScreenDensity() {
        assertEquals(3, LiveControlMath.exposureIndex(0, -160f, 2f, 1f/3f, -12, 12))
        assertEquals(-3, LiveControlMath.exposureIndex(0, 160f, 2f, 1f/3f, -12, 12))
        assertEquals(3, LiveControlMath.exposureIndex(0, -80f, 1f, 1f/3f, -12, 12))
    }
    @Test fun swipeStartsAtCurrentEvAndQuantizesToSensorSteps() {
        assertEquals(5, LiveControlMath.exposureIndex(4, -30f, 1f, .5f, -6, 6))
        assertEquals(4, LiveControlMath.exposureIndex(4, -5f, 1f, .5f, -6, 6))
        assertEquals(-6, LiveControlMath.exposureIndex(4, 10000f, 1f, .5f, -6, 6))
        assertEquals(6, LiveControlMath.exposureIndex(4, -10000f, 1f, .5f, -6, 6))
    }
    @Test fun unavailableExposureStepLeavesExposureUnchanged() {
        assertEquals(2, LiveControlMath.exposureIndex(2, -100f, 1f, 0f, -6, 6))
    }
}
