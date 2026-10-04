package tw.luma.camera.camera

import androidx.camera.core.ImageCapture
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

    @Test fun tappingCyclesOffAutoOnAndBackToOff() {
        assertEquals(FlashMode.AUTO, FlashMode.OFF.next())
        assertEquals(FlashMode.ON, FlashMode.AUTO.next())
        assertEquals(FlashMode.OFF, FlashMode.ON.next())
    }

    @Test fun anUnusableFlashIsShownAsOffWhateverWasRequested() {
        assertEquals(FlashMode.AUTO, FlashControl.shown(requested = FlashMode.AUTO, usable = true))
        assertEquals(FlashMode.ON, FlashControl.shown(requested = FlashMode.ON, usable = true))
        assertEquals("Requested but the front camera has none", FlashMode.OFF, FlashControl.shown(requested = FlashMode.ON, usable = false))
        assertEquals(FlashMode.OFF, FlashControl.shown(requested = FlashMode.AUTO, usable = false))
    }

    @Test fun descriptionsNameTheState() {
        assertEquals("閃光燈已關閉", FlashControl.description(usable = true, mode = FlashMode.OFF))
        assertEquals("閃光燈自動", FlashControl.description(usable = true, mode = FlashMode.AUTO))
        assertEquals("閃光燈已開啟", FlashControl.description(usable = true, mode = FlashMode.ON))
        assertEquals("閃光燈無法使用", FlashControl.description(usable = false, mode = FlashMode.OFF))
    }

    @Test fun theCameraGetsTheMatchingFlashMode() {
        assertEquals(ImageCapture.FLASH_MODE_OFF, FlashControl.imageCaptureMode(FlashMode.OFF, usable = true))
        assertEquals(ImageCapture.FLASH_MODE_AUTO, FlashControl.imageCaptureMode(FlashMode.AUTO, usable = true))
        assertEquals(ImageCapture.FLASH_MODE_ON, FlashControl.imageCaptureMode(FlashMode.ON, usable = true))
        assertEquals("Never fire when it cannot", ImageCapture.FLASH_MODE_OFF, FlashControl.imageCaptureMode(FlashMode.ON, usable = false))
        assertEquals(ImageCapture.FLASH_MODE_OFF, FlashControl.imageCaptureMode(FlashMode.AUTO, usable = false))
    }
}
