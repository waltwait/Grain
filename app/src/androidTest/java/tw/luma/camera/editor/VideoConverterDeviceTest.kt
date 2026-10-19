package tw.luma.camera.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import tw.luma.camera.gl.FilterSettings
import tw.luma.camera.lut.CubeLut
import tw.luma.camera.storage.VideoStorage

/** Runs the real encoder: a red clip converted with the grey film comes out as a grey H.264 MP4 in Movies/Grain Edits. */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@RunWith(AndroidJUnit4::class)
class VideoConverterDeviceTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val files = mutableListOf<File>()
    private val written = mutableListOf<Uri>()

    @After fun cleanUp() {
        files.forEach { it.delete() }
        written.forEach { context.contentResolver.delete(it, null, null) }
    }

    @Test fun filmIsBakedIntoAnH264Mp4() = runBlocking {
        val source = redClip()
        val output = File.createTempFile("converted-", ".mp4", context.cacheDir).also { files += it }
        val grey = CubeLut.builtIns().first { it.title == "銀影" }
        val progress = mutableListOf<Float>()
        assertEquals(false, VideoConverter.hasAudio(context, Uri.fromFile(source)))
        VideoConverter.convert(context, Uri.fromFile(source), hasAudio = false, FilterSettings(lut = grey), output) { progress += it }
        assertTrue("Progress stays within 0–1", progress.all { it in 0f..1f })
        val tracks = MediaExtractor().run {
            try { setDataSource(output.absolutePath); (0 until trackCount).map { getTrackFormat(it).getString(MediaFormat.KEY_MIME) } }
            finally { release() }
        }
        assertEquals(listOf(MimeTypes.VIDEO_H264), tracks)
        val frame = requireNotNull(MediaMetadataRetriever().use { it.setDataSource(output.absolutePath); it.getFrameAtTime(500_000) })
        val pixel = frame.getPixel(frame.width / 2, frame.height / 2)
        assertEquals("The grey film leaves no colour: $pixel", Color.red(pixel).toFloat(), Color.green(pixel).toFloat(), 12f)
        assertEquals("The grey film leaves no colour: $pixel", Color.green(pixel).toFloat(), Color.blue(pixel).toFloat(), 12f)
        val info = VideoConverter.probe(context, Uri.fromFile(output))
        assertTrue("A preview frame and the length are read back", info.poster.width > 0 && info.durationMs in 500L..1500L)
    }

    @Test fun convertedClipIsPublishedBesideTheEditedPhotos() = runBlocking {
        val clip = redClip()
        val name = VideoStorage.editName("TEST_${System.nanoTime()}")
        val uri = VideoStorage.saveEdit(context, clip, name).also { written += it }
        context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.MIME_TYPE, MediaStore.MediaColumns.IS_PENDING), null, null, null).use { cursor ->
            assertTrue(requireNotNull(cursor).moveToFirst())
            assertEquals(name, cursor.getString(0))
            assertEquals(VideoStorage.EDIT_QUERY_PATH, cursor.getString(1))
            assertEquals("video/mp4", cursor.getString(2))
            assertEquals(0, cursor.getInt(3))
        }
    }

    /** A one-second 320×240 red clip without sound, made from a still image. */
    private suspend fun redClip(): File {
        val png = File.createTempFile("red-", ".png", context.cacheDir).also { files += it }
        val bitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(220, 30, 30)) }
        try { png.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } } finally { bitmap.recycle() }
        val clip = File.createTempFile("red-", ".mp4", context.cacheDir).also { files += it }
        withContext(Dispatchers.Main) {
            val done = CompletableDeferred<Unit>()
            val item = EditedMediaItem.Builder(MediaItem.Builder().setUri(Uri.fromFile(png)).setImageDurationMs(1000).build())
                .setFrameRate(30).build()
            Transformer.Builder(context).setVideoMimeType(MimeTypes.VIDEO_H264).addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) { done.complete(Unit) }
                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    done.completeExceptionally(exportException)
                }
            }).build().start(item, clip.absolutePath)
            done.await()
        }
        return clip
    }
}
