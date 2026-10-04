package tw.luma.camera.ui

/** The line under the gallery title: a count on each tab, or the position inside a batch being edited. */
internal object GalleryHeader {
    fun subtitle(editing: Boolean, photoCount: Int, editCount: Int, batchPosition: Pair<Int, Int>?): String = when {
        !editing -> photoCount.toString()
        batchPosition != null -> "${batchPosition.first} / ${batchPosition.second}"
        else -> editCount.toString()
    }
}
