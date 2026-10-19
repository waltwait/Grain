package tw.luma.camera.editor

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.SingleColorLut
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tw.luma.camera.gl.FilterCube
import tw.luma.camera.gl.FilterSettings

/** What the editor needs to know about a picked video before converting it. */
internal data class VideoInfo(val poster: Bitmap, val durationMs: Long, val hasAudio: Boolean)

/** Turns one video (an iPhone .mov, say) into an H.264 + AAC MP4 that plays everywhere, with the film baked in. */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
internal object VideoConverter {
    /** A frame from the middle of the clip, upright and at most [maxSide] px, to preview the film on. */
    fun probe(context: Context, source: Uri, maxSide: Int = 1440): VideoInfo = MediaMetadataRetriever().use { retriever ->
        retriever.setDataSource(context, source)
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        val hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes"
        require(hasVideo) { "這個檔案沒有影像" }
        val poster = retriever.getScaledFrameAtTime(durationMs * 500, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, maxSide, maxSide)
            ?: retriever.getFrameAtTime(0) ?: error("影片無法讀取")
        VideoInfo(poster, durationMs, retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes")
    }

    fun hasAudio(context: Context, source: Uri): Boolean = MediaMetadataRetriever().use { retriever ->
        retriever.setDataSource(context, source)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
    }

    /** Writes [source] to [output]; an H.264 + AAC clip without a filter is only repackaged, which takes seconds. */
    suspend fun convert(context: Context, source: Uri, hasAudio: Boolean, filter: FilterSettings, output: File, progress: (Float) -> Unit) {
        val effects: List<Effect> = if (filter.isNoOp) emptyList()
            else withContext(Dispatchers.Default) { listOf(SingleColorLut.createFromCube(FilterCube.argb(filter))) }
        val item = EditedMediaItem.Builder(MediaItem.fromUri(source)).setEffects(Effects(emptyList(), effects)).build()
        val tracks = if (hasAudio) setOf(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_VIDEO) else setOf(C.TRACK_TYPE_VIDEO)
        // This H.264 is 8-bit SDR, so iPhone HDR is tone-mapped first and the film then sees SDR colours, as photos do.
        val composition = Composition.Builder(EditedMediaItemSequence.Builder(tracks).addItem(item).build())
            .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL).build()
        // Transformer must be driven from the thread it was built on.
        withContext(Dispatchers.Main) {
            val done = CompletableDeferred<Unit>()
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) { done.complete(Unit) }
                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        done.completeExceptionally(IllegalStateException("影片無法轉檔（${exportException.errorCodeName}）", exportException))
                    }
                }).build()
            transformer.start(composition, output.absolutePath)
            val holder = ProgressHolder()
            val poll = launch {
                while (true) {
                    if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) progress(holder.progress / 100f)
                    delay(250)
                }
            }
            try { done.await() }
            catch (cancelled: CancellationException) { transformer.cancel(); throw cancelled }
            finally { poll.cancel() }
        }
    }
}
