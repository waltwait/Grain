package tw.luma.camera.gallery

import android.app.Application
import android.content.ContentUris
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import tw.luma.camera.performance.grainTrace
import tw.luma.camera.storage.SaveTarget
import tw.luma.camera.ui.NO_FILTER_LABEL
import androidx.exifinterface.media.ExifInterface
import java.io.File

data class GalleryItem(val uri: Uri, val name: String, val video: Boolean, val addedSeconds: Long, val durationMs: Long, val id: Long,
    val original: Uri? = null)
data class GalleryState(val items: List<GalleryItem> = emptyList(), val loading: Boolean = true, val error: String? = null,
    val edits: List<GalleryItem> = emptyList())

class GalleryViewModel(application: Application) : AndroidViewModel(application) {
    private val resolver = application.contentResolver
    private val _state = MutableStateFlow(GalleryState())
    val state = _state.asStateFlow()
    private var refreshJob: Job? = null
    private val thumbnails = bitmapCache(12 * 1024 * 1024)
    private val photos = bitmapCache(32 * 1024 * 1024)
    private val thumbnailSlots = Semaphore(2)
    private val photoSlots = Semaphore(1)
    private val filmFile = File(application.filesDir, "film-index.txt")
    private var filmsLoaded = false
    private var filmJob: Job? = null
    private val _films = MutableStateFlow<Map<Long, String>>(emptyMap())
    /** Photo id to the film it was made with; an empty title means no film is known. Fills in while the gallery is open. */
    val films = _films.asStateFlow()

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val (items, edits) = withContext(Dispatchers.IO) {
                    grainTrace("Grain.gallery.query") {
                        (query(false) + query(true)).sortedWith(compareByDescending<GalleryItem> { it.addedSeconds }.thenByDescending { it.id }) to queryEdits()
                    }
                }
                _state.value = GalleryState(items, loading = false, edits = edits)
                indexFilms(items)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.value = _state.value.copy(loading = false, error = "無法讀取相簿，請重試") }
        }
    }

    private fun query(video: Boolean): List<GalleryItem> {
        val collection = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val columns = listOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.DATE_ADDED) +
            if (video) listOf(MediaStore.Video.VideoColumns.DURATION) else emptyList()
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.OWNER_PACKAGE_NAME} = ? AND ${MediaStore.MediaColumns.IS_PENDING} = 0"
        return resolver.query(collection, columns.toTypedArray(), selection,
            arrayOf(if (video) "Movies/Grain/" else "Pictures/Grain/", getApplication<Application>().packageName),
            "${MediaStore.MediaColumns.DATE_ADDED} DESC, ${MediaStore.MediaColumns._ID} DESC")?.use { cursor ->
            val result = ArrayList<GalleryItem>()
            while (cursor.moveToNext()) {
                result += GalleryItem(ContentUris.withAppendedId(collection, cursor.getLong(0)), cursor.getString(1) ?: "Grain", video,
                    cursor.getLong(2), if (video) cursor.getLong(3) else 0, cursor.getLong(0))
            }
            result
        }.orEmpty()
    }

    /** Reads the film of every photo not indexed yet from its EXIF comment, in the background, and remembers it on disk. */
    private fun indexFilms(items: List<GalleryItem>) {
        filmJob?.cancel()
        filmJob = viewModelScope.launch(Dispatchers.IO) {
            if (!filmsLoaded) {
                _films.value = runCatching { FilmIndex.decode(filmFile.readText()) }.getOrDefault(emptyMap())
                filmsLoaded = true
            }
            val missing = items.filter { !it.video && it.id !in _films.value }
            if (missing.isEmpty()) return@launch
            val updated = LinkedHashMap(_films.value)
            for ((count, item) in missing.withIndex()) {
                ensureActive()
                updated[item.id] = readFilm(item.uri).orEmpty()
                if ((count + 1) % 40 == 0) _films.value = LinkedHashMap(updated)
            }
            _films.value = updated
            runCatching {
                val temporary = File(filmFile.path + ".tmp")
                temporary.writeText(FilmIndex.encode(updated))
                check(temporary.renameTo(filmFile))
            }
        }
    }

    private fun readFilm(uri: Uri): String? = try {
        resolver.openInputStream(uri)?.use { ExifInterface(it).getAttribute(ExifInterface.TAG_USER_COMMENT) }
            ?.let { FilmIndex.filmOf(it, NO_FILTER_LABEL) }
    } catch (e: CancellationException) { throw e }
    catch (_: Exception) { null }

    /** Edited photos live in their own folder; each one carries the Uri of its original copy when that still exists. */
    private fun queryEdits(): List<GalleryItem> {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.OWNER_PACKAGE_NAME} = ? AND ${MediaStore.MediaColumns.IS_PENDING} = 0"
        val rows = resolver.query(collection, arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.DATE_ADDED),
            selection, arrayOf(SaveTarget.EDIT.queryPath, getApplication<Application>().packageName),
            "${MediaStore.MediaColumns.DATE_ADDED} DESC, ${MediaStore.MediaColumns._ID} DESC")?.use { cursor ->
            val result = ArrayList<EditRow>()
            while (cursor.moveToNext()) result += EditRow(cursor.getLong(0), cursor.getString(1) ?: "Grain", cursor.getLong(2))
            result
        }.orEmpty()
        return EditPairing.pair(rows).map { pair ->
            GalleryItem(ContentUris.withAppendedId(collection, pair.edited.id), pair.edited.name, false, pair.edited.addedSeconds, 0, pair.edited.id,
                pair.original?.let { ContentUris.withAppendedId(collection, it.id) })
        }
    }

    /** Never recycle evicted bitmaps: a visible composable can still hold a reference. */
    suspend fun thumbnail(item: GalleryItem): Bitmap = thumbnails.get(item.uri.toString()) ?: thumbnailSlots.withPermit {
        withContext(Dispatchers.IO) {
            ensureActive()
            thumbnails.get(item.uri.toString()) ?: grainTrace("Grain.gallery.thumbnail") {
                resolver.loadThumbnail(item.uri, Size(256, 256), null).also { thumbnails.put(item.uri.toString(), it) }
            }
        }
    }

    suspend fun photo(item: GalleryItem): Bitmap = photos.get(item.uri.toString()) ?: photoSlots.withPermit {
        withContext(Dispatchers.IO) {
            ensureActive()
            photos.get(item.uri.toString()) ?: grainTrace("Grain.gallery.photoDecode") {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, item.uri)) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.setTargetSampleSize(PhotoViewport.sampleSize(info.size.width, info.size.height, 8_000_000))
                }.also { photos.put(item.uri.toString(), it) }
            }
        }
    }

    fun releasePhotos() { photos.evictAll() }
    override fun onCleared() { photos.evictAll(); thumbnails.evictAll(); super.onCleared() }

    private fun bitmapCache(bytes: Int) = object : LruCache<String, Bitmap>(bytes) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
}
