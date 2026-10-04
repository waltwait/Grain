package tw.luma.camera.storage

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.graphics.Matrix
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import tw.luma.camera.gl.FilterSettings
import tw.luma.camera.gl.LutRenderer
import tw.luma.camera.performance.grainTrace
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PhotoStorage {
    private val exifTags = listOf(ExifInterface.TAG_DATETIME, ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL, ExifInterface.TAG_EXPOSURE_TIME, ExifInterface.TAG_F_NUMBER,
        ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, ExifInterface.TAG_FOCAL_LENGTH, ExifInterface.TAG_WHITE_BALANCE,
        ExifInterface.TAG_EXPOSURE_BIAS_VALUE, ExifInterface.TAG_FLASH, ExifInterface.TAG_EXPOSURE_PROGRAM, ExifInterface.TAG_EXPOSURE_MODE)

    /** Returns the edited photo's Uri; an edit target also publishes a byte copy of [source] next to it. */
    fun processAndSave(context: Context, source: File, filter: FilterSettings, target: SaveTarget,
        saveOriginal: Boolean = target.alwaysSaveOriginal): Uri {
        val exif = ExifInterface(source)
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
            inSampleSize = 1
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
        }
        BitmapFactory.decodeFile(source.absolutePath, options)
        require(options.outWidth > 0 && options.outHeight > 0) { "照片無法解碼" }
        // Bound CPU bitmap memory too; tiling alone only bounds GPU allocations.
        while ((options.outWidth.toLong() / options.inSampleSize) * (options.outHeight / options.inSampleSize) > 12_000_000L) {
            options.inSampleSize *= 2
        }
        options.inJustDecodeBounds = false
        val decoded = grainTrace("Grain.photo.decode") { BitmapFactory.decodeFile(source.absolutePath, options) } ?: error("照片無法解碼")
        var oriented: Bitmap? = null
        var filtered: Bitmap? = null
        val encoded = File.createTempFile("luma-output-", ".jpg", context.cacheDir)
        try {
            val rotation = exif.rotationDegrees
            val matrix = Matrix().apply { if (exif.isFlipped) postScale(-1f, 1f); postRotate(rotation.toFloat()) }
            oriented = if (rotation != 0 || exif.isFlipped) Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true) else decoded
            if (oriented !== decoded) decoded.recycle()
            filtered = grainTrace("Grain.photo.filter") { LutRenderer.apply(oriented, filter) }
            grainTrace("Grain.photo.jpeg") { encoded.outputStream().use { check(filtered.compress(Bitmap.CompressFormat.JPEG, 95, it)) { "照片編碼失敗" } } }
            ExifInterface(encoded).apply {
                for (tag in exifTags) exif.getAttribute(tag)?.let { setAttribute(tag, it) }
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
                setAttribute(ExifInterface.TAG_COLOR_SPACE, "1")
                setAttribute(ExifInterface.TAG_SOFTWARE, "Grain")
                setAttribute(ExifInterface.TAG_USER_COMMENT, "LUT=${filter.lut?.title ?: "Original"}; strength=${filter.strength}; input=${filter.encoding.name}; imageEV=${filter.brightnessEv}")
                saveAttributes()
            }
            val time = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
            // Publish the pair only after both files have been written successfully.
            val pending = mutableListOf<Uri>()
            try {
                val finalUri = writePending(context, encoded, PhotoNames.edited(target, time), target.relativePath).also { pending += it }
                if (saveOriginal) pending += writePending(context, source, PhotoNames.original(target, time), target.relativePath)
                for (uri in pending) context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
                return finalUri
            } catch (e: Throwable) { pending.forEach { runCatching { context.contentResolver.delete(it, null, null) } }; throw e }
        } finally {
            encoded.delete()
            if (filtered != null && filtered !== oriented && filtered !== decoded) filtered.recycle()
            if (oriented != null && oriented !== decoded) oriented.recycle()
            if (!decoded.isRecycled) decoded.recycle()
        }
    }

    private fun writePending(context: Context, file: File, name: String, relativePath: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("無法建立相簿照片")
        try { grainTrace("Grain.photo.mediaStore") { requireNotNull(resolver.openOutputStream(uri)).use { output -> file.inputStream().use { it.copyTo(output) } } } }
        catch (e: Throwable) { resolver.delete(uri, null, null); throw e }
        return uri
    }
}
