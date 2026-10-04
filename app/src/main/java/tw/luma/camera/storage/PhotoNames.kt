package tw.luma.camera.storage

/** Where a saved photo goes. Edits live apart from camera shots and always keep a copy of the original. */
enum class SaveTarget(val relativePath: String, val prefix: String, val alwaysSaveOriginal: Boolean) {
    CAMERA("Pictures/Grain", "GRAIN_", false),
    EDIT("Pictures/Grain Edits", "GRAIN_EDIT_", true);

    /** MediaStore stores RELATIVE_PATH with a trailing slash. */
    val queryPath: String get() = "$relativePath/"
}

/** A photo and its original copy are paired only by sharing one base file name; the original keeps its own extension. */
object PhotoNames {
    private const val ORIGINAL_SUFFIX = "_original"
    private const val EDITED_EXTENSION = ".jpg"

    fun edited(target: SaveTarget, time: String): String = target.prefix + time + EDITED_EXTENSION

    fun original(target: SaveTarget, time: String, extension: String = "jpg"): String =
        target.prefix + time + ORIGINAL_SUFFIX + "." + extension

    fun isOriginal(name: String): Boolean = name.substringBeforeLast('.').endsWith(ORIGINAL_SUFFIX)

    /** The original's file name without extension for an edited photo's name, or null when [editedName] is not an edited photo. */
    fun originalBase(editedName: String): String? =
        if (!editedName.endsWith(EDITED_EXTENSION) || isOriginal(editedName)) null
        else editedName.removeSuffix(EDITED_EXTENSION) + ORIGINAL_SUFFIX
}
