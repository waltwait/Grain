package tw.luma.camera.gl

import kotlin.math.abs

/**
 * The front camera's part of a composed back-and-front frame, in the frame's texture coordinates (0–1, y up):
 * skin is softened [inside] it, or everywhere else when the front camera fills the frame and the back one is the inset.
 * [radiusX] and [radiusY] are the blur reach in the same coordinates.
 */
data class SkinArea(val left: Float, val bottom: Float, val right: Float, val top: Float, val inside: Boolean,
    val radiusX: Float, val radiusY: Float, val strength: Float = LIGHT) {

    /** The area and reach in a camera buffer's own coordinates, which [transform] (column-major 4×4) maps the frame's into. */
    fun inBuffer(transform: FloatArray): SkinPass {
        fun x(u: Float, v: Float) = transform[0] * u + transform[4] * v + transform[12]
        fun y(u: Float, v: Float) = transform[1] * u + transform[5] * v + transform[13]
        val (x0, y0) = x(left, bottom) to y(left, bottom)
        val (x1, y1) = x(right, top) to y(right, top)
        return SkinPass(floatArrayOf(minOf(x0, x1), minOf(y0, y1), maxOf(x0, x1), maxOf(y0, y1)),
            floatArrayOf(abs(transform[0] * radiusX) + abs(transform[4] * radiusY), abs(transform[1] * radiusX) + abs(transform[5] * radiusY)),
            inside, strength)
    }

    companion object {
        /** A light touch: pores and fine lines fade, the face keeps its texture. */
        const val LIGHT = .55f
    }
}

/** [SkinArea] as the shader reads it: [rect] is min x, min y, max x, max y and [radius] the reach per axis. */
class SkinPass(val rect: FloatArray, val radius: FloatArray, val inside: Boolean, val strength: Float)
