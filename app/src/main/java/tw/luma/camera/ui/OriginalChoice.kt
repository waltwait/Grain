package tw.luma.camera.ui

/** Which edited photo is currently shown as its original. Keyed by photo, never by page, so it cannot leak to a neighbor. */
internal object OriginalChoice {
    fun isShown(shownFor: String?, uri: String): Boolean = shownFor == uri

    fun toggle(shownFor: String?, uri: String): String? = if (shownFor == uri) null else uri

    /** Moving to a different photo clears the flag; re-selecting the same photo (rotation) keeps it. */
    fun afterCurrentChanged(shownFor: String?, previous: String, current: String): String? =
        if (previous == current) shownFor else null
}
