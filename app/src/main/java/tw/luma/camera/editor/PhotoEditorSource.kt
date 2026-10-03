package tw.luma.camera.editor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File
import tw.luma.camera.storage.PhotoImportIo

internal object PhotoEditorSource {
    /** Decode once, with the same sRGB and EXIF treatment as the full-size photo exporter. */
    fun preview(file: File): Bitmap {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
        }
        BitmapFactory.decodeFile(file.absolutePath, options)
        options.inSampleSize = PhotoImportIo.previewSampleSize(options.outWidth, options.outHeight)
        options.inJustDecodeBounds = false
        val exif = ExifInterface(file)
        val decoded = BitmapFactory.decodeFile(file.absolutePath, options) ?: error("照片無法解碼")
        if (exif.rotationDegrees == 0 && !exif.isFlipped) return decoded
        try {
            val matrix = Matrix().apply {
                if (exif.isFlipped) postScale(-1f, 1f)
                postRotate(exif.rotationDegrees.toFloat())
            }
            return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                .also { if (it !== decoded) decoded.recycle() }
        } catch (error: Throwable) { decoded.recycle(); throw error }
    }
}
