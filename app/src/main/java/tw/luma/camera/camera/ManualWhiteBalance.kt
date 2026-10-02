package tw.luma.camera.camera

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow

enum class WhiteBalanceBackend {
    CCT, GAINS, GRADING;
    companion object {
        fun choose(cct: Boolean, gains: Boolean) = if (cct) CCT else if (gains) GAINS else GRADING
    }
}

/** XYZ -> native sensor RGB. CameraCharacteristics calibration * color transform, row-major. */
class SensorColorCalibration(
    private val first: DoubleArray,
    private val firstKelvin: Int? = null,
    private val second: DoubleArray? = null,
    private val secondKelvin: Int? = null,
) {
    fun at(kelvin: Int): DoubleArray {
        if (second == null || firstKelvin == null || secondKelvin == null || firstKelvin == secondKelvin) return first.copyOf()
        val weight = ((1.0 / kelvin - 1.0 / firstKelvin) / (1.0 / secondKelvin - 1.0 / firstKelvin)).coerceIn(0.0, 1.0)
        return DoubleArray(9) { first[it] * (1 - weight) + second[it] * weight }
    }
}

/**
 * Public Camera2 manual WB, independent of Android 16's optional CCT control.
 * Preserve the camera's reported color matrix; temperature changes only Bayer gains.
 * K is an estimate: sensor metadata improves the mapping, but this is not Samsung's ISP calibration.
 */
class ManualWhiteBalance private constructor(
    private val baseline: FloatArray,
    val transform: DoubleArray,
    private val calibration: SensorColorCalibration?,
    private val fallbackModel: DoubleArray,
) {
    val calibrated get() = calibration != null

    fun gains(kelvin: Int, tint: Int): FloatArray {
        val temperature = kelvin.coerceIn(2000, 10000)
        val point = WhitePoint.xyz(temperature)
        val cameraWhite = calibration?.let { ColorMatrix.vector(it.at(temperature), point) }
            ?.takeIf { it.all { channel -> channel.isFinite() && channel > 1e-6 } }
        val green = (baseline[1] + baseline[2]) / 2.0
        val rgb = if (cameraWhite != null) {
            DoubleArray(3) { cameraWhite[1] / cameraWhite[it] * green }
        } else {
            // Without RAW calibration, anchor the reported AWB state to an estimated 5500 K.
            // The inverse live color matrix maps the white-point curve back into camera channels.
            val reference = ColorMatrix.vector(fallbackModel, WhitePoint.xyz(5500))
            val target = ColorMatrix.vector(fallbackModel, point)
            doubleArrayOf(baseline[0].toDouble(), green, baseline[3].toDouble()).mapIndexed { i, gain ->
                gain * (reference[i].coerceAtLeast(1e-6) / target[i].coerceAtLeast(1e-6)).coerceIn(.25, 4.0)
            }.toDoubleArray().let { values -> values.map { it * green / values[1] }.toDoubleArray() }
        }
        val shift = tint.coerceIn(-50, 50) / 50.0
        val rb = 2.0.pow(.18 * shift)
        val g = 2.0.pow(-.36 * shift)
        val result = doubleArrayOf(rgb[0] * rb, rgb[1] * baseline[1] / green * g,
            rgb[1] * baseline[2] / green * g, rgb[2] * rb)
        // Never send sub-unity gains; Android guarantees [1,3] without vendor clipping.
        val scale = maxOf(1.0, 1.0 / result.min())
        return FloatArray(4) { (result[it] * scale).coerceIn(1.0, 3.0).toFloat() }
    }

    companion object {
        fun create(gains: FloatArray, transform: DoubleArray, calibration: SensorColorCalibration? = null): ManualWhiteBalance? {
            if (gains.size != 4 || gains.any { !it.isFinite() || it <= 0 } ||
                transform.size != 9 || transform.any { !it.isFinite() }) return null
            val inverse = ColorMatrix.inverse(transform) ?: return null
            val xyzToSrgb = doubleArrayOf(3.2404542, -1.5371385, -.4985314, -.9692660, 1.8760108, .0415560,
                .0556434, -.2040259, 1.0572252)
            return ManualWhiteBalance(gains.copyOf(), transform.copyOf(), calibration, ColorMatrix.multiply(inverse, xyzToSrgb))
        }

        fun matches(expected: FloatArray, actual: FloatArray): Boolean = expected.size == 4 && actual.size == 4 &&
            expected.indices.all { actual[it].isFinite() && abs(expected[it] - actual[it]) <= maxOf(.03f, expected[it] * .05f) }
    }
}

/** Planck spectrum integrated over 380–780 nm; Y normalized to 1. Not display RGB temperature. */
internal object WhitePoint {
    // Wyman, Sloan & Shirley (JCGT 2013), equation 4 / table 1, CIE 1931 analytical fit.
    // https://research.nvidia.com/publication/2013-07_simple-analytic-approximations-cie-xyz-color-matching-functions
    private fun gaussian(wave: Double, center: Double, left: Double, right: Double): Double {
        val v = (wave - center) * if (wave < center) left else right
        return exp(-.5 * v * v)
    }
    private val observer = (380..780 step 5).map { nm ->
        val w = nm.toDouble()
        doubleArrayOf(w, .362 * gaussian(w, 442.0, .0624, .0374) + 1.056 * gaussian(w, 599.8, .0264, .0323) - .065 * gaussian(w, 501.1, .0490, .0382),
            .821 * gaussian(w, 568.8, .0213, .0247) + .286 * gaussian(w, 530.9, .0613, .0322),
            1.217 * gaussian(w, 437.0, .0845, .0278) + .681 * gaussian(w, 459.0, .0385, .0725))
    }
    fun xyz(kelvin: Int): DoubleArray {
        val point = DoubleArray(3)
        for (sample in observer) {
            val wavelength = sample[0] / 1000.0 // micrometres; common scale cancels after Y normalization
            val radiance = 1.0 / (wavelength.pow(5) * (exp(14387.76877 / (wavelength * kelvin.coerceIn(2000, 10000))) - 1))
            for (i in 0..2) point[i] += radiance * sample[i + 1]
        }
        return point.map { it / point[1] }.toDoubleArray()
    }
}

internal object ColorMatrix {
    fun vector(matrix: DoubleArray, v: DoubleArray) = DoubleArray(3) { row -> (0..2).sumOf { matrix[row * 3 + it] * v[it] } }
    fun multiply(a: DoubleArray, b: DoubleArray) = DoubleArray(9) { i -> (0..2).sumOf { a[(i / 3) * 3 + it] * b[it * 3 + i % 3] } }
    fun inverse(m: DoubleArray): DoubleArray? {
        if (m.size != 9 || m.any { !it.isFinite() }) return null
        val a = m[0]; val b = m[1]; val c = m[2]; val d = m[3]; val e = m[4]; val f = m[5]; val g = m[6]; val h = m[7]; val i = m[8]
        val determinant = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g)
        if (!determinant.isFinite() || abs(determinant) < 1e-8) return null
        return doubleArrayOf(e*i-f*h, c*h-b*i, b*f-c*e, f*g-d*i, a*i-c*g, c*d-a*f, d*h-e*g, b*g-a*h, a*e-b*d).map { it / determinant }.toDoubleArray()
    }
}
