package tw.luma.camera.gallery

import tw.luma.camera.storage.PhotoNames

internal data class EditRow(val id: Long, val name: String, val addedSeconds: Long = 0)

internal data class EditPair(val edited: EditRow, val original: EditRow?)

/** Pairs edited photos with their original copies by file name alone; no index is kept anywhere. */
internal object EditPairing {
    /** Keeps the input order, lists only edited photos, and ignores originals whose edit is gone. */
    fun pair(rows: List<EditRow>): List<EditPair> {
        val originals = rows.filter { PhotoNames.isOriginal(it.name) }.associateBy { it.name.substringBeforeLast('.') }
        return rows.filterNot { PhotoNames.isOriginal(it.name) }
            .map { edited -> EditPair(edited, PhotoNames.originalBase(edited.name)?.let(originals::get)) }
    }
}
