package tw.luma.camera.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.os.Build
import android.util.Range
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.CameraInfo

data class WhiteBalance(val mode: Int, val label: String)

@androidx.annotation.OptIn(markerClass = [androidx.camera.camera2.interop.ExperimentalCamera2Interop::class])
data class CameraCapabilities(
    val id: String = "",
    val manualSensor: Boolean = false,
    val isoRange: Range<Int>? = null,
    val shutterRange: Range<Long>? = null,
    val maxFrameDuration: Long = 0,
    val apertures: List<Float> = emptyList(),
    val whiteBalances: List<WhiteBalance> = emptyList(),
    val awbLock: Boolean = false,
    val cctRange: Range<Int>? = null,
    val exposureRange: Range<Int> = Range(0, 0),
    val exposureStep: Float = 0f,
    val aeLock: Boolean = false,
) {
    val adjustableAperture get() = manualSensor && apertures.size > 1
    val hasEv get() = exposureRange.lower != exposureRange.upper

    companion object {
        fun read(info: CameraInfo, context: android.content.Context): CameraCapabilities {
            val camera = Camera2CameraInfo.from(info)
            fun <T> get(key: CameraCharacteristics.Key<T>): T? = camera.getCameraCharacteristic(key)
            val manager = context.getSystemService(android.hardware.camera2.CameraManager::class.java)
            val requestKeys = manager.getCameraCharacteristics(camera.cameraId).availableCaptureRequestKeys.orEmpty()
            val sensor = get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR) == true &&
                get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES)?.contains(CaptureRequest.CONTROL_AE_MODE_OFF) == true &&
                CaptureRequest.SENSOR_EXPOSURE_TIME in requestKeys && CaptureRequest.SENSOR_SENSITIVITY in requestKeys
            val modes = get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES) ?: intArrayOf()
            val allWb = listOf(WhiteBalance(CaptureRequest.CONTROL_AWB_MODE_AUTO, "自動"),
                WhiteBalance(CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT, "日光"), WhiteBalance(CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT, "陰天"),
                WhiteBalance(CaptureRequest.CONTROL_AWB_MODE_SHADE, "陰影"), WhiteBalance(CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT, "鎢絲燈"),
                WhiteBalance(CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT, "螢光燈"))
            val cct = if (Build.VERSION.SDK_INT >= 36 && modes.contains(CaptureRequest.CONTROL_AWB_MODE_OFF) &&
                get(CameraCharacteristics.COLOR_CORRECTION_AVAILABLE_MODES)?.contains(CameraMetadata.COLOR_CORRECTION_MODE_CCT) == true &&
                CaptureRequest.COLOR_CORRECTION_COLOR_TEMPERATURE in requestKeys && CaptureRequest.COLOR_CORRECTION_COLOR_TINT in requestKeys)
                get(CameraCharacteristics.COLOR_CORRECTION_COLOR_TEMPERATURE_RANGE) else null
            val exposure = info.exposureState
            return CameraCapabilities(camera.cameraId, sensor,
                get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE), get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE),
                get(CameraCharacteristics.SENSOR_INFO_MAX_FRAME_DURATION) ?: 0,
                if (CaptureRequest.LENS_APERTURE in requestKeys) get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)?.toList().orEmpty() else emptyList(),
                allWb.filter { it.mode in modes }, get(CameraCharacteristics.CONTROL_AWB_LOCK_AVAILABLE) == true, cct,
                exposure.exposureCompensationRange, exposure.exposureCompensationStep.toFloat(), get(CameraCharacteristics.CONTROL_AE_LOCK_AVAILABLE) == true)
        }
    }
}

data class CaptureSettings(
    val manual: Boolean = false,
    val iso: Int = 100,
    val shutterNs: Long = 8_000_000,
    val aperture: Float? = null,
    val evIndex: Int = 0,
    val wbMode: Int = CaptureRequest.CONTROL_AWB_MODE_AUTO,
    val wbLocked: Boolean = false,
    val kelvin: Int? = null,
    val tint: Int = 0,
    val zoom: Float = 1f,
    val flash: Boolean = false,
)

data class ActualCapture(val iso: Int? = null, val shutterNs: Long? = null, val aperture: Float? = null, val kelvin: Int? = null, val tint: Int? = null)

fun shutterLabel(ns: Long?): String {
    if (ns == null || ns <= 0) return "—"
    val seconds = ns / 1_000_000_000.0
    return if (seconds >= .5) "%.1f s".format(java.util.Locale.US, seconds) else "1/${(1.0 / seconds).toInt()} s"
}
