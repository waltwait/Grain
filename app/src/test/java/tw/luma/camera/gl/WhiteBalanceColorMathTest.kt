package tw.luma.camera.gl

import org.junit.Assert.*
import org.junit.Test

class WhiteBalanceColorMathTest {
    @Test fun neutralAdjustmentDoesNotAlterAnyChannel() {
        assertArrayEquals(floatArrayOf(1f, 1f, 1f), WhiteBalanceColorMath.gains(0f, 0f), 0f)
    }

    @Test fun warmthMovesGrayTowardRedAndCoolnessTowardBlue() {
        val warm = WhiteBalanceColorMath.gains(65f, 0f)
        val cool = WhiteBalanceColorMath.gains(-65f, 0f)
        assertTrue(warm[0] > warm[1] && warm[1] > warm[2])
        assertTrue(cool[2] > cool[1] && cool[1] > cool[0])
    }

    @Test fun positiveTintAddsMagentaAndNegativeTintAddsGreen() {
        val magenta = WhiteBalanceColorMath.gains(0f, 70f)
        val green = WhiteBalanceColorMath.gains(0f, -70f)
        assertTrue(magenta[0] > magenta[1] && magenta[2] > magenta[1])
        assertTrue(green[1] > green[0] && green[1] > green[2])
    }

    @Test fun allAdjustmentsKeepMidGrayLuminanceAndFinitePositiveGains() {
        for (w in -100..100 step 10) for (t in -100..100 step 10) {
            val gains = WhiteBalanceColorMath.gains(w.toFloat(), t.toFloat())
            assertTrue(gains.all { it.isFinite() && it > 0f })
            assertEquals(.18, .18 * (gains[0] * .2126 + gains[1] * .7152 + gains[2] * .0722), .000001)
        }
    }

    @Test fun valuesOutsideControlRangeCannotIncreaseTheEffect() {
        assertArrayEquals(WhiteBalanceColorMath.gains(100f, -100f), WhiteBalanceColorMath.gains(1000f, -1000f), 0f)
        assertArrayEquals(floatArrayOf(1f, 1f, 1f), WhiteBalanceColorMath.gains(Float.NaN, Float.POSITIVE_INFINITY), 0f)
    }

    @Test fun exportCannotSkipWhiteBalanceWhenLutIsOffOrStrengthIsZero() {
        assertTrue(FilterSettings().isNoOp)
        assertFalse(FilterSettings(warmth = 1f).isNoOp)
        assertFalse(FilterSettings(strength = 0f, tint = -1f).isNoOp)
        assertFalse(FilterSettings(brightnessEv = 1f).isNoOp)
    }
}
