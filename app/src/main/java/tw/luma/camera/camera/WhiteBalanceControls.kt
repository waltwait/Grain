package tw.luma.camera.camera

import android.hardware.camera2.CaptureRequest
import tw.luma.camera.gl.FilterSettings
import kotlin.math.roundToInt

object WhiteBalanceControls {
    fun kelvin(value: Float, min: Int, max: Int): Int = ((value / 50f).roundToInt() * 50).coerceIn(min, max)
    fun reset(capture: CaptureSettings): CaptureSettings = capture.copy(
        kelvin = null, tint = 0, wbMode = CaptureRequest.CONTROL_AWB_MODE_AUTO, wbLocked = false,
    )
    fun reset(filter: FilterSettings): FilterSettings = filter.copy(warmth = 0f, tint = 0f)
}
