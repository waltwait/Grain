package tw.luma.camera.storage

/** An original copy keeps the source's real type, so a PNG or HEIC is never stored under a `.jpg` name. */
object ImageFormat {
    private const val FALLBACK = "image/jpeg"

    fun mimeType(decoded: String?): String = decoded?.lowercase()?.takeIf { it.startsWith("image/") } ?: FALLBACK

    fun extension(decoded: String?): String = when (val type = mimeType(decoded)) {
        "image/jpeg" -> "jpg"
        "image/heif", "image/heic" -> "heic"
        else -> type.substringAfter('/').filter { it in 'a'..'z' || it in '0'..'9' }.ifEmpty { "jpg" }
    }
}
