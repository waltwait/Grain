package tw.luma.camera.storage

/** Where a saved photo goes. Edits live apart from camera shots and always keep a copy of the original. */
enum class SaveTarget(val relativePath: String, val prefix: String, val alwaysSaveOriginal: Boolean) {
    CAMERA("Pictures/Grain", "GRAIN_", false),
    EDIT("Pictures/Grain Edits", "GRAIN_EDIT_", true);

    /** MediaStore stores RELATIVE_PATH with a trailing slash. */
    val queryPath: String get() = "$relativePath/"
}

/** A photo and its original copy are paired only by sharing one base file name. */
object PhotoNames {
    private const val ORIGINAL_SUFFIX = "_original"
    private const val EXTENSION = ".jpg"

    fun edited(target: SaveTarget, time: String): String = target.prefix + time + EXTENSION

    fun original(target: SaveTarget, time: String): String = target.prefix + time + ORIGINAL_SUFFIX + EXTENSION

    fun isOriginal(name: String): Boolean = name.endsWith(ORIGINAL_SUFFIX + EXTENSION)

    /** The original's file name for an edited photo's name, or null when [editedName] is not an edited photo. */
    fun originalOf(editedName: String): String? =
        if (!editedName.endsWith(EXTENSION) || isOriginal(editedName)) null
        else editedName.removeSuffix(EXTENSION) + ORIGINAL_SUFFIX + EXTENSION
}
