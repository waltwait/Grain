package tw.luma.camera.editor

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

enum class PhotoBatchStatus { PENDING, SAVED, FAILED }

/** One output: [source] photo rendered with the filter [filterId] (null when no filter is chosen). */
data class PhotoBatchItem(val source: String, val status: PhotoBatchStatus = PhotoBatchStatus.PENDING,
    val output: String? = null, val error: String? = null, val filterId: String? = null) {
    val id: String get() = if (filterId == null) source else "$source|$filterId"
}

data class PhotoBatchProgress(val items: List<PhotoBatchItem>, val running: Boolean = false,
    val currentIndex: Int? = null, val cancelRequested: Boolean = false, val stopped: Boolean = false) {
    val savedCount get() = items.count { it.status == PhotoBatchStatus.SAVED }
    val failedCount get() = items.count { it.status == PhotoBatchStatus.FAILED }
    val processedCount get() = savedCount + failedCount
    val remainingCount get() = items.size - savedCount

    companion object {
        const val MAX_PHOTOS = 20
        const val MAX_OUTPUTS = 20

        /** Jobs are ordered photo by photo, every chosen filter of a photo before the next photo. */
        fun pending(sources: List<String>, filterIds: List<String?> = listOf(null)): PhotoBatchProgress {
            val unique = sources.distinct()
            val filters = filterIds.distinct().ifEmpty { listOf(null) }
            require(unique.isNotEmpty() && unique.size <= MAX_PHOTOS) { "一次最多選 $MAX_PHOTOS 張照片" }
            require(unique.all { it.isNotBlank() }) { "照片無法讀取" }
            require(unique.size * filters.size <= MAX_OUTPUTS) { "一次最多輸出 $MAX_OUTPUTS 張" }
            return PhotoBatchProgress(unique.flatMap { source -> filters.map { PhotoBatchItem(source, filterId = it) } })
        }
    }
}

/** Serial exports bound image memory. A stop finishes the current image; retries skip saved images. */
internal object PhotoBatchProcessor {
    suspend fun run(previous: PhotoBatchProgress, shouldStop: () -> Boolean,
        process: suspend (String) -> String, progress: (PhotoBatchProgress) -> Unit): PhotoBatchProgress {
        var state = PhotoBatchProgress(previous.items.map {
            if (it.status == PhotoBatchStatus.SAVED) it else PhotoBatchItem(it.source, filterId = it.filterId)
        }, running = true)
        progress(state)
        for (index in state.items.indices) {
            coroutineContext.ensureActive()
            if (state.items[index].status == PhotoBatchStatus.SAVED) continue
            if (shouldStop()) {
                state = state.copy(stopped = true)
                break
            }
            state = state.copy(currentIndex = index)
            progress(state)
            val job = state.items[index]
            val result = try {
                job.copy(status = PhotoBatchStatus.SAVED, output = process(job.id))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                job.copy(status = PhotoBatchStatus.FAILED, error = error.message ?: "照片未儲存")
            }
            state = state.copy(items = state.items.toMutableList().also { it[index] = result }, cancelRequested = shouldStop())
            progress(state)
        }
        return state.copy(running = false, currentIndex = null, cancelRequested = false).also(progress)
    }
}
