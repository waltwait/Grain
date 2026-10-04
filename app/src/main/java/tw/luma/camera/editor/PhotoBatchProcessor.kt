package tw.luma.camera.editor

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

enum class PhotoBatchStatus { PENDING, SAVED, FAILED }

data class PhotoBatchItem(val source: String, val status: PhotoBatchStatus = PhotoBatchStatus.PENDING,
    val output: String? = null, val error: String? = null)

data class PhotoBatchProgress(val items: List<PhotoBatchItem>, val running: Boolean = false,
    val currentIndex: Int? = null, val cancelRequested: Boolean = false, val stopped: Boolean = false) {
    val savedCount get() = items.count { it.status == PhotoBatchStatus.SAVED }
    val failedCount get() = items.count { it.status == PhotoBatchStatus.FAILED }
    val processedCount get() = savedCount + failedCount
    val remainingCount get() = items.size - savedCount

    companion object {
        const val MAX_PHOTOS = 20

        fun pending(sources: List<String>): PhotoBatchProgress {
            val unique = sources.distinct()
            require(unique.isNotEmpty() && unique.size <= MAX_PHOTOS) { "一次最多選 $MAX_PHOTOS 張照片" }
            require(unique.all { it.isNotBlank() }) { "照片無法讀取" }
            return PhotoBatchProgress(unique.map(::PhotoBatchItem))
        }
    }
}

/** Serial exports bound image memory. A stop finishes the current image; retries skip saved images. */
internal object PhotoBatchProcessor {
    suspend fun run(previous: PhotoBatchProgress, shouldStop: () -> Boolean,
        process: suspend (String) -> String, progress: (PhotoBatchProgress) -> Unit): PhotoBatchProgress {
        var state = PhotoBatchProgress(previous.items.map {
            if (it.status == PhotoBatchStatus.SAVED) it else PhotoBatchItem(it.source)
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
            val source = state.items[index].source
            val result = try {
                PhotoBatchItem(source, PhotoBatchStatus.SAVED, output = process(source))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                PhotoBatchItem(source, PhotoBatchStatus.FAILED, error = error.message ?: "照片未儲存")
            }
            state = state.copy(items = state.items.toMutableList().also { it[index] = result }, cancelRequested = shouldStop())
            progress(state)
        }
        return state.copy(running = false, currentIndex = null, cancelRequested = false).also(progress)
    }
}
