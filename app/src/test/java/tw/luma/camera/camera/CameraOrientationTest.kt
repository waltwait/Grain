package tw.luma.camera.camera

import org.junit.Assert.*
import org.junit.Test

class CameraOrientationTest {
    @Test fun allFourPhysicalHoldsSetCameraXRotationIndependentlyOfTheWindow() {
        val tracker = CameraOrientationTracker()
        assertEquals(0, tracker.update(0).targetRotation)
        assertEquals(3, tracker.update(90).targetRotation)
        assertEquals(2, tracker.update(180).targetRotation)
        assertEquals(1, tracker.update(270).targetRotation)
        assertEquals(0, tracker.update(359).targetRotation)
    }

    @Test fun flatOrUnavailableSensorKeepsTheLastKnownDirection() {
        val tracker = CameraOrientationTracker()
        assertEquals(CameraOrientation.LEFT_UP, tracker.update(90))
        assertEquals(CameraOrientation.LEFT_UP, tracker.update(-1))
        assertEquals(CameraOrientation.LEFT_UP, tracker.update(360))
    }

    @Test fun diagonalJitterDoesNotRepeatedlyRotateControls() {
        val tracker = CameraOrientationTracker()
        for (degree in listOf(44, 45, 48, 51, 54)) assertEquals(CameraOrientation.UPRIGHT, tracker.update(degree))
        assertEquals(CameraOrientation.LEFT_UP, tracker.update(55))
        for (degree in listOf(51, 46, 40, 36)) assertEquals(CameraOrientation.LEFT_UP, tracker.update(degree))
        assertEquals(CameraOrientation.UPRIGHT, tracker.update(35))
    }

    @Test fun jitterAcrossZeroHasTheSameHysteresis() {
        val tracker = CameraOrientationTracker(CameraOrientation.RIGHT_UP)
        assertEquals(CameraOrientation.RIGHT_UP, tracker.update(324))
        assertEquals(CameraOrientation.UPRIGHT, tracker.update(325))
        for (degree in listOf(329, 355, 0, 5, 306)) assertEquals(CameraOrientation.UPRIGHT, tracker.update(degree))
        assertEquals(CameraOrientation.RIGHT_UP, tracker.update(305))
    }

    @Test fun largePhysicalTurnsCanSkipIntermediateDirections() {
        val tracker = CameraOrientationTracker()
        assertEquals(CameraOrientation.UPSIDE_DOWN, tracker.update(180))
        assertEquals(CameraOrientation.UPRIGHT, tracker.update(0))
    }

    @Test fun controlsCompensateForTheActualDisplayIncludingLandscapeNaturalDevices() {
        assertEquals(270, CameraOrientation.LEFT_UP.controlDegrees(0))
        assertEquals(180, CameraOrientation.UPSIDE_DOWN.controlDegrees(0))
        assertEquals(90, CameraOrientation.RIGHT_UP.controlDegrees(0))
        for (orientation in CameraOrientation.entries) assertEquals(0, orientation.controlDegrees(orientation.targetRotation))
        assertEquals(0, CameraOrientation.LEFT_UP.controlDegrees(3))
        assertEquals(90, CameraOrientation.UPRIGHT.controlDegrees(3))
    }

    @Test fun missingSensorStartsFromTheDisplayDirection() {
        for (rotation in 0..3) assertEquals(rotation, CameraOrientation.fromDisplayRotation(rotation).targetRotation)
        assertEquals(CameraOrientation.UPRIGHT, CameraOrientation.fromDisplayRotation(-1))
    }

    @Test fun controlAnimationAlwaysTakesTheShortPathAcrossZero() {
        assertEquals(-90f, CameraOrientation.nearestControlAngle(0f, 270), .001f)
        assertEquals(360f, CameraOrientation.nearestControlAngle(270f, 0), .001f)
        assertEquals(-360f, CameraOrientation.nearestControlAngle(-270f, 0), .001f)
        assertEquals(-180f, CameraOrientation.nearestControlAngle(0f, 180), .001f)
        assertEquals(370f, CameraOrientation.nearestControlAngle(350f, 10), .001f)
    }

    @Test fun onlyTheActiveFullScreenPhoneCameraKeepsItsLayout() {
        assertTrue(CameraOrientation.keepCameraLayout(true, 411, false))
        assertFalse(CameraOrientation.keepCameraLayout(false, 411, false))
        assertFalse(CameraOrientation.keepCameraLayout(true, 411, true))
        assertFalse(CameraOrientation.keepCameraLayout(true, 600, false))
        assertFalse(CameraOrientation.keepCameraLayout(true, 840, false))
    }
}
