package tw.luma.camera.camera

import org.junit.Assert.*
import org.junit.Test
import tw.luma.camera.gl.FilterSettings
import tw.luma.camera.lut.CubeLut

class WhiteBalanceControlsTest {
    @Test fun resetRemovesStaleTintButPreservesManualExposureAndZoom() {
        val capture = CaptureSettings(manual = true, iso = 800, shutterNs = 12_000_000, zoom = 2f,
            kelvin = 6000, tint = 23, wbLocked = true)
        val result = WhiteBalanceControls.reset(capture)
        assertNull(result.kelvin)
        assertEquals(0, result.tint)
        assertFalse(result.wbLocked)
        assertTrue(result.manual)
        assertEquals(800, result.iso)
        assertEquals(12_000_000L, result.shutterNs)
        assertEquals(2f, result.zoom)
    }

    @Test fun resetGradingPreservesTheSelectedFilmAndBrightness() {
        val lut = CubeLut.generate("Identity", 2) { r, g, b -> floatArrayOf(r, g, b) }
        val result = WhiteBalanceControls.reset(FilterSettings(lut = lut, strength = .4f, brightnessEv = 1f, warmth = 60f, tint = -20f))
        assertEquals(0f, result.warmth)
        assertEquals(0f, result.tint)
        assertSame(lut, result.lut)
        assertEquals(.4f, result.strength)
        assertEquals(1f, result.brightnessEv)
    }

    @Test fun fineKelvinStepsStayWithinNonRoundCameraLimits() {
        assertEquals(5600, WhiteBalanceControls.kelvin(5601f, 2856, 6500))
        assertEquals(2856, WhiteBalanceControls.kelvin(2800f, 2856, 6490))
        assertEquals(6490, WhiteBalanceControls.kelvin(6550f, 2856, 6490))
    }
}
