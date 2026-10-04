package tw.luma.camera.camera

import androidx.camera.core.ImageCapture

/** When the on-screen flash icon can be used and how it reads; the engine only fires the flash outside manual exposure. */
object FlashControl {
    fun usable(hasFlash: Boolean, manualExposure: Boolean): Boolean = hasFlash && !manualExposure

    fun enabled(usable: Boolean, busy: Boolean, recording: Boolean): Boolean = usable && !busy && !recording

    /** A requested flash that cannot fire (for example after switching to the front camera) is shown as off. */
    fun shown(requested: FlashMode, usable: Boolean): FlashMode = if (usable) requested else FlashMode.OFF

    fun description(usable: Boolean, mode: FlashMode): String = when {
        !usable -> "閃光燈無法使用"
        mode == FlashMode.AUTO -> "閃光燈自動"
        mode == FlashMode.ON -> "閃光燈已開啟"
        else -> "閃光燈已關閉"
    }

    fun imageCaptureMode(requested: FlashMode, usable: Boolean): Int = when (shown(requested, usable)) {
        FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
        FlashMode.ON -> ImageCapture.FLASH_MODE_ON
        FlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
    }
}
