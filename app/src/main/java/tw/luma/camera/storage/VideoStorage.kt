package tw.luma.camera.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import java.io.File

/** Converted videos live beside the edited photos' folder, apart from the camera's own clips. */
object VideoStorage {
    const val EDIT_PATH = "Movies/Grain Edits"
    /** MediaStore stores RELATIVE_PATH with a trailing slash. */
    const val EDIT_QUERY_PATH = "$EDIT_PATH/"

    fun editName(time: String): String = "${SaveTarget.EDIT.prefix}$time.mp4"

    /** Publishes a finished MP4 only once it is fully copied, so the gallery never lists a half-written clip. */
    fun saveEdit(context: Context, file: File, name: String): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, EDIT_PATH)
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: error("無法建立相簿影片")
        try {
            requireNotNull(resolver.openOutputStream(uri)) { "無法寫入相簿影片" }.use { output -> file.inputStream().use { it.copyTo(output) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
        } catch (e: Throwable) { runCatching { resolver.delete(uri, null, null) }; throw e }
        return uri
    }
}
