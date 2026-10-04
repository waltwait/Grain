package tw.luma.camera.camera

import org.junit.Assert.*
import org.junit.Test

class FlashControlTest {
    @Test fun flashNeedsAFlashUnitAndAutomaticExposure() {
        assertTrue(FlashControl.usable(hasFlash = true, manualExposure = false))
        assertFalse("Front cameras have no flash unit", FlashControl.usable(hasFlash = false, manualExposure = false))
        assertFalse("The engine never fires the flash in manual exposure", FlashControl.usable(hasFlash = true, manualExposure = true))
    }

    @Test fun theIconIsTappableOnlyWhenIdleAndUsable() {
        assertTrue(FlashControl.enabled(usable = true, busy = false, recording = false))
        assertFalse(FlashControl.enabled(usable = false, busy = false, recording = false))
        assertFalse("Saving a photo", FlashControl.enabled(usable = true, busy = true, recording = false))
        assertFalse("Recording", FlashControl.enabled(usable = true, busy = false, recording = true))
    }

    @Test fun anUnusableFlashIsNeverShownAsOn() {
        assertTrue(FlashControl.shownOn(requested = true, usable = true))
        assertFalse("Requested but the front camera has none", FlashControl.shownOn(requested = true, usable = false))
        assertFalse(FlashControl.shownOn(requested = false, usable = true))
    }

    @Test fun descriptionsNameTheState() {
        assertEquals("閃光燈已開啟", FlashControl.description(usable = true, on = true))
        assertEquals("閃光燈已關閉", FlashControl.description(usable = true, on = false))
        assertEquals("閃光燈無法使用", FlashControl.description(usable = false, on = false))
    }
}
