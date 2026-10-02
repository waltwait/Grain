package tw.luma.camera.camera

import org.junit.Assert.*
import org.junit.Test

class ManualWhiteBalanceTest {
    private val identity = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)
    private val baseline = floatArrayOf(2f, 1f, 1.1f, 1.8f)

    @Test fun android16WithoutCctStillSelectsCamera2Gains() {
        assertEquals(WhiteBalanceBackend.GAINS, WhiteBalanceBackend.choose(false, true))
        assertEquals(WhiteBalanceBackend.CCT, WhiteBalanceBackend.choose(true, true))
        assertEquals(WhiteBalanceBackend.GRADING, WhiteBalanceBackend.choose(false, false))
    }

    @Test fun uncalibratedReferenceKeepsTheReportedCameraGainsAndMatrix() {
        val control = ManualWhiteBalance.create(baseline, identity)!!
        assertArrayEquals(baseline, control.gains(5500, 0), .0001f)
        assertArrayEquals(identity, control.transform, .000001)
        assertFalse(control.calibrated)
    }

    @Test fun increasingKelvinWarmsTheCaptureRatherThanCoolingIt() {
        val control = ManualWhiteBalance.create(baseline, identity)!!
        val cold = control.gains(3000, 0)
        val warm = control.gains(8000, 0)
        assertTrue(warm[0] / warm[3] > cold[0] / cold[3])
        assertEquals(baseline[1] / baseline[2], warm[1] / warm[2], .0001f)
    }

    @Test fun positiveTintAddsMagentaAndNegativeTintAddsGreen() {
        val control = ManualWhiteBalance.create(floatArrayOf(2f, 1.5f, 1.5f, 2f), identity)!!
        val green = control.gains(5500, -50)
        val magenta = control.gains(5500, 50)
        assertTrue(magenta[0] / magenta[1] > green[0] / green[1])
        assertTrue(magenta[3] / magenta[2] > green[3] / green[2])
    }

    @Test fun requestGainsStayInsideAndroidGuaranteedRangeAtEveryControlExtreme() {
        val control = ManualWhiteBalance.create(baseline, identity)!!
        for (kelvin in listOf(100, 2000, 5500, 10000, 50000)) for (tint in listOf(-200, -50, 0, 50, 200)) {
            assertTrue(control.gains(kelvin, tint).all { it.isFinite() && it in 1f..3f })
        }
    }

    @Test fun sensorCalibrationUsesCameraChannelsInsteadOfScreenRgb() {
        val calibration = SensorColorCalibration(doubleArrayOf(.6, .2, .05, .1, .8, .1, .05, .1, .65))
        val control = ManualWhiteBalance.create(baseline, identity, calibration)!!
        val white = calibration.at(4500).let { ColorMatrix.vector(it, WhitePoint.xyz(4500)) }
        val gains = control.gains(4500, 0)
        assertTrue(control.calibrated)
        // A neutral target in native sensor space should have equal gained R/G/B.
        assertEquals(white[0] * gains[0], white[1] * (gains[1] + gains[2]) / 2, .0001)
        assertEquals(white[2] * gains[3], white[1] * (gains[1] + gains[2]) / 2, .0001)
    }

    @Test fun twoIlluminantsInterpolateInReciprocalTemperature() {
        val second = identity.map { it * 2 }.toDoubleArray()
        val calibration = SensorColorCalibration(identity, 3000, second, 6000)
        assertArrayEquals(identity, calibration.at(2000), .000001)
        assertArrayEquals(second, calibration.at(10000), .000001)
        assertArrayEquals(identity.map { it * 1.5 }.toDoubleArray(), calibration.at(4000), .000001)
    }

    @Test fun malformedOrSingularResultDoesNotEnableManualWhiteBalance() {
        assertNull(ManualWhiteBalance.create(floatArrayOf(1f, Float.NaN, 1f, 1f), identity))
        assertNull(ManualWhiteBalance.create(baseline, DoubleArray(9)))
        assertNull(ManualWhiteBalance.create(baseline, identity.copyOf(8)))
        assertNull(ManualWhiteBalance.create(floatArrayOf(0f, 1f, 1f, 1f), identity))
    }

    @Test fun gainReadbackMustMatchAllFourChannels() {
        assertTrue(ManualWhiteBalance.matches(baseline, floatArrayOf(2.01f, 1f, 1.1f, 1.81f)))
        assertFalse(ManualWhiteBalance.matches(baseline, floatArrayOf(2f, 1f, 1.1f, 2.5f)))
        assertFalse(ManualWhiteBalance.matches(baseline, floatArrayOf(2f, 1f, Float.NaN, 1.8f)))
    }

    @Test fun planckWhitePointsAgreeWithIndependentCieChromaticities() {
        // Tabulated Planckian locus: 2856 K ~(.4476, .4075), 6500 K ~(.3135, .3237).
        for ((kelvin, xy) in listOf(2856 to (.4476 to .4075), 6500 to (.3135 to .3237))) {
            val point = WhitePoint.xyz(kelvin)
            val sum = point.sum()
            assertEquals(xy.first, point[0] / sum, .003)
            assertEquals(xy.second, point[1] / sum, .003)
            assertEquals(1.0, point[1], .000001)
        }
    }
}
