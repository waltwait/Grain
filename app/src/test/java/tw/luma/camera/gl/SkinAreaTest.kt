package tw.luma.camera.gl

import org.junit.Assert.*
import org.junit.Test

class SkinAreaTest {
    private val area = SkinArea(.6f, .7f, .9f, .95f, inside = true, radiusX = .003f, radiusY = .002f)

    private fun assertArray(expected: FloatArray, actual: FloatArray) = assertArrayEquals(expected, actual, 1e-6f)

    @Test fun identityKeepsTheArea() {
        val identity = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
        val pass = area.inBuffer(identity)
        assertArray(floatArrayOf(.6f, .7f, .9f, .95f), pass.rect)
        assertArray(floatArrayOf(.003f, .002f), pass.radius)
        assertTrue(pass.inside)
        assertEquals(SkinArea.LIGHT, pass.strength, 0f)
    }
    @Test fun aFlippedBufferFlipsTheAreaButNotItsReach() {
        // SurfaceTexture's usual transform: y' = 1 − y.
        val flip = floatArrayOf(1f, 0f, 0f, 0f, 0f, -1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 1f)
        val pass = area.inBuffer(flip)
        assertArray(floatArrayOf(.6f, .05f, .9f, .3f), pass.rect)
        assertArray(floatArrayOf(.003f, .002f), pass.radius)
    }
    @Test fun aQuarterTurnSwapsTheAxes() {
        // x' = y, y' = 1 − x.
        val turn = floatArrayOf(0f, -1f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 1f)
        val pass = area.inBuffer(turn)
        assertArray(floatArrayOf(.7f, .1f, .95f, .4f), pass.rect)
        assertArray(floatArrayOf(.002f, .003f), pass.radius)
    }
}
