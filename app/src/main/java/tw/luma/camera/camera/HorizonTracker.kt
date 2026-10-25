package tw.luma.camera.camera

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
            field = value
            updateState()
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
        } else {
            smoothedX = HorizonMath.smooth(smoothedX, rawX, 0.25f)
            smoothedY = HorizonMath.smooth(smoothedY, rawY, 0.25f)
            smoothedZ = HorizonMath.smooth(smoothedZ, rawZ, 0.25f)
        }

        updateState()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun updateState() {
        _state.value = HorizonMath.calculate(smoothedX, smoothedY, smoothedZ, orientation)
    }
}
