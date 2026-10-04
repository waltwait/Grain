package tw.luma.camera.gallery

data class MediaRow(val id: Long, val addedSeconds: Long, val video: Boolean)

/** Which saved photo or video the camera's corner button shows: the newest, ties broken by the higher id. */
object LatestMedia {
    fun newest(rows: List<MediaRow>): MediaRow? = rows.maxWithOrNull(compareBy<MediaRow> { it.addedSeconds }.thenBy { it.id })
}
