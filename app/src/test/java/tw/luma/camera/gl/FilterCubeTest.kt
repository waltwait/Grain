package tw.luma.camera.gl

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import tw.luma.camera.lut.CubeLut

class FilterCubeTest {
    private val silver = CubeLut.builtIns().first { it.title == "銀影" }

    @Test fun noFilterLeavesEveryColourAlone() {
        val cube = FilterCube.argb(FilterSettings(), size = 5)
        for (r in 0..4) for (g in 0..4) for (b in 0..4) {
            assertEquals(argb(r * 255 / 4f, g * 255 / 4f, b * 255 / 4f), cube[r][g][b])
        }
    }

    @Test fun cubeIsIndexedRedGreenBlueAndOpaque() {
        assertEquals(0xFFFF0080.toInt(), FilterCube.argb(FilterSettings(), size = 3)[2][0][1])
    }

    @Test fun fullStrengthLutMapsTheColour() {
        assertArrayEquals(floatArrayOf(.2126f, .2126f, .2126f), FilterCube.color(FilterSettings(lut = silver), 1f, 0f, 0f), 1e-4f)
    }

    @Test fun halfStrengthBlendsTheLutWithTheImage() {
        assertArrayEquals(floatArrayOf(.6063f, .1063f, .1063f),
            FilterCube.color(FilterSettings(lut = silver, strength = .5f), 1f, 0f, 0f), 1e-4f)
    }

    @Test fun zeroStrengthKeepsOnlyTheImageAdjustments() {
        assertArrayEquals(floatArrayOf(.3f, .6f, .9f), FilterCube.color(FilterSettings(lut = silver, strength = 0f), .3f, .6f, .9f), 1e-6f)
    }

    @Test fun oneStopBrighterDoublesLinearLight() {
        // sRGB .5 is .2140 linear; doubled to .4281 it encodes back to .6858.
        assertArrayEquals(floatArrayOf(.6858f, .6858f, .6858f), FilterCube.color(FilterSettings(brightnessEv = 1f), .5f, .5f, .5f), 1e-3f)
    }

    @Test fun warmthKeepsNeutralBrightnessButWarmsGrey() {
        val warm = FilterCube.color(FilterSettings(warmth = 50f), .5f, .5f, .5f)
        assertEquals(true, warm[0] > .5f && warm[2] < .5f)
    }

    private fun argb(r: Float, g: Float, b: Float) =
        (0xFF shl 24) or (Math.round(r) shl 16) or (Math.round(g) shl 8) or Math.round(b)
}
