package tw.luma.camera.camera

/** When the on-screen flash icon can be used and how it reads; the engine only fires the flash outside manual exposure. */
object FlashControl {
    fun usable(hasFlash: Boolean, manualExposure: Boolean): Boolean = hasFlash && !manualExposure

    fun enabled(usable: Boolean, busy: Boolean, recording: Boolean): Boolean = usable && !busy && !recording

    /** A requested flash that cannot fire (for example after switching to the front camera) is not shown as on. */
    fun shownOn(requested: Boolean, usable: Boolean): Boolean = requested && usable

    fun description(usable: Boolean, on: Boolean): String = when {
        !usable -> "閃光燈無法使用"
        on -> "閃光燈已開啟"
        else -> "閃光燈已關閉"
    }
}
