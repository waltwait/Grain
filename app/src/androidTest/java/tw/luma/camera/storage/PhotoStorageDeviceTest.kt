package tw.luma.camera.storage

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import tw.luma.camera.gl.FilterSettings

/** Exercises the real MediaStore: edits go to their own folder with a byte-identical original beside them. */
@RunWith(AndroidJUnit4::class)
class PhotoStorageDeviceTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val resolver = context.contentResolver
    private val images = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    private val written = mutableListOf<Uri>()
    private lateinit var source: File

    @Before fun createSource() {
        source = File.createTempFile("storage-test-", ".jpg", context.cacheDir)
        val bitmap = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(90, 120, 170)) }
        try { source.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)) } }
        finally { bitmap.recycle() }
    }

    @After fun cleanUp() {
        written.forEach { resolver.delete(it, null, null) }
        source.delete()
    }

    @Test fun editTargetWritesAPairToTheEditFolder() {
        val cameraBefore = count(SaveTarget.CAMERA)
        val uri = PhotoStorage.processAndSave(context, source, FilterSettings(), SaveTarget.EDIT).also { written += it }
        val (name, path) = requireNotNull(nameAndPath(uri))
        assertTrue(name, name.startsWith("GRAIN_EDIT_"))
        assertEquals("Pictures/Grain Edits/", path)
        val original = requireNotNull(find(SaveTarget.EDIT, requireNotNull(PhotoNames.originalOf(name)))).also { written += it }
        assertArrayEquals(source.readBytes(), requireNotNull(resolver.openInputStream(original)).use { it.readBytes() })
        assertEquals("Edits must not touch the camera folder", cameraBefore, count(SaveTarget.CAMERA))
    }

    @Test fun cameraTargetKeepsTheCameraFolderAndOptionalOriginal() {
        val before = count(SaveTarget.CAMERA)
        val single = PhotoStorage.processAndSave(context, source, FilterSettings(), SaveTarget.CAMERA, saveOriginal = false).also { written += it }
        assertEquals(before + 1, count(SaveTarget.CAMERA))
        val singleName = requireNotNull(nameAndPath(single)).first
        assertTrue(singleName, singleName.startsWith("GRAIN_") && !singleName.startsWith("GRAIN_EDIT_"))
        assertNull(find(SaveTarget.CAMERA, requireNotNull(PhotoNames.originalOf(singleName))))

        val pair = PhotoStorage.processAndSave(context, source, FilterSettings(), SaveTarget.CAMERA, saveOriginal = true).also { written += it }
        val pairName = requireNotNull(nameAndPath(pair)).first
        written += requireNotNull(find(SaveTarget.CAMERA, requireNotNull(PhotoNames.originalOf(pairName))))
        assertEquals(before + 3, count(SaveTarget.CAMERA))
    }

    private fun nameAndPath(uri: Uri): Pair<String, String>? =
        resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.RELATIVE_PATH), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) to it.getString(1) else null
        }

    private fun find(target: SaveTarget, name: String): Uri? = resolver.query(images, arrayOf(MediaStore.MediaColumns._ID),
        "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.OWNER_PACKAGE_NAME} = ?",
        arrayOf(name, target.queryPath, context.packageName), null)?.use {
        if (it.moveToFirst()) ContentUris.withAppendedId(images, it.getLong(0)) else null
    }

    private fun count(target: SaveTarget): Int = resolver.query(images, arrayOf(MediaStore.MediaColumns._ID),
        "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.OWNER_PACKAGE_NAME} = ? AND ${MediaStore.MediaColumns.IS_PENDING} = 0",
        arrayOf(target.queryPath, context.packageName), null)?.use { it.count } ?: 0
}
