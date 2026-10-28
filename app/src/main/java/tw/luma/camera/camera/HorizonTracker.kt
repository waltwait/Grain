package tw.luma.camera.camera

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

class HorizonTracker(context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
        ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var smoothedX = 0f
    private var smoothedY = 9.81f
    private var smoothedZ = 0f
    private var initialized = false

    var orientation: CameraOrientation = CameraOrientation.UPRIGHT
        set(value) {
            if (field != value) {
                field = value
                updateState(force = true)
            }
        }

    private val _state = MutableStateFlow(HorizonState())
    val state: StateFlow<HorizonState> = _state.asStateFlow()

    fun enable(): Boolean {
        if (sensorManager == null || sensor == null) return false
        return sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    fun disable() {
        sensorManager?.unregisterListener(this)
        initialized = false
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val values = event?.values ?: return
        val rawX = values[0]
        val rawY = values[1]
        val rawZ = values.getOrElse(2) { 0f }

        if (!initialized) {
            smoothedX = rawX
            smoothedY = rawY
            smoothedZ = rawZ
            initialized = true
            updateState(force = true)
        } else {
            smoothedX = HorizonMath.adaptiveSmooth(smoothedX, rawX)
            smoothedY = HorizonMath.adaptiveSmooth(smoothedY, rawY)
            smoothedZ = HorizonMath.adaptiveSmooth(smoothedZ, rawZ)
            updateState(force = false)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun updateState(force: Boolean) {
        val current = _state.value
        val next = HorizonMath.calculate(
            gx = smoothedX,
            gy = smoothedY,
            gz = smoothedZ,
            orientation = orientation,
            wasRollLevel = current.isLevel,
            wasPitchLevel = current.isPitchLevel,
        )
        if (force || shouldEmit(current, next)) {
            _state.value = next
        }
    }

    private fun shouldEmit(old: HorizonState, next: HorizonState): Boolean {
        if (old.isLevel != next.isLevel || old.isPitchLevel != next.isPitchLevel) return true
        if (abs(old.alpha - next.alpha) >= 0.01f || abs(old.pitchAlpha - next.pitchAlpha) >= 0.01f) return true
        if (abs(old.rollDegrees - next.rollDegrees) >= 0.04f) return true
        if (abs(old.pitchDegrees - next.pitchDegrees) >= 0.04f) return true
        return false
    }
}
