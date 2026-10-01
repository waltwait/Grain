package tw.luma.camera.camera

import org.junit.Assert.*
import org.junit.Test

class ZoomControlsTest {
    @Test fun fixedZoomDoesNotAdvertiseUnsupportedSteps() {
        assertEquals(listOf(1f), ZoomControls.presets(1f, 1f))
    }
    @Test fun narrowRangeKeepsOnlySupportedPresets() {
        assertEquals(listOf(2f, 3f), ZoomControls.presets(1.5f, 4f))
    }
    @Test fun ultraWideUsesActualMinimumRatherThanInventingHalfX() {
        assertEquals(listOf(.6f, 1f, 2f, 3f), ZoomControls.presets(.6f, 3f))
    }
    @Test fun invalidCapabilitiesProduceNoButtons() {
        assertTrue(ZoomControls.presets(Float.NaN, 8f).isEmpty())
        assertTrue(ZoomControls.presets(3f, 1f).isEmpty())
        assertTrue(ZoomControls.presets(0f, 8f).isEmpty())
    }
    @Test fun recordingClockHandlesHoursAndNegativeDurations() {
        assertEquals("00:00", ZoomControls.recordingTime(-1))
        assertEquals("01:05", ZoomControls.recordingTime(65_900_000_000L))
        assertEquals("01:01:01", ZoomControls.recordingTime(3661_000_000_000L))
    }
}
