package tw.luma.camera.gallery

/**
 * Which film (LUT) each photo was made with. The truth is the "LUT=…" part of the photo's EXIF user comment; this index
 * only remembers what was read so the gallery does not open every photo again. An empty title means "read, no film known".
 */
object FilmIndex {
    private val lut = Regex("""LUT=([^;]*)""")
    private val escape = Regex("""\\u([0-9a-fA-F]{4})""")

    /** EXIF comments hold ASCII only, so anything else (and the separator ';') is written as \uXXXX. */
    fun encodeTitle(title: String): String = buildString {
        for (c in title) if (c.code in 32..126 && c != '\\' && c != ';') append(c) else append("\\u%04x".format(c.code))
    }

    fun decodeTitle(text: String): String = escape.replace(text) { it.groupValues[1].toInt(16).toChar().toString() }

    fun filmOf(userComment: String?, noFilterLabel: String): String? {
        val raw = userComment?.let { lut.find(it)?.groupValues?.get(1)?.trim() }?.takeIf { it.isNotEmpty() } ?: return null
        if ('?' in raw) return null // Older versions stored non-ASCII titles as "?", which cannot be recovered.
        val title = decodeTitle(raw)
        return if (title == "Original") noFilterLabel else title
    }

    fun encode(films: Map<Long, String>): String =
        films.entries.joinToString("\n") { (id, title) -> "$id\t${title.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')}" }

    fun decode(text: String): Map<Long, String> {
        val films = LinkedHashMap<Long, String>()
        for (line in text.lineSequence()) {
            val tab = line.indexOf('\t')
            val id = if (tab > 0) line.substring(0, tab).toLongOrNull() else null
            if (id != null) films[id] = line.substring(tab + 1)
        }
        return films
    }

    /** Films that have at least one photo, the most used first (ties by name). */
    fun usedFilms(films: Map<Long, String>): List<String> =
        films.values.filter { it.isNotEmpty() }.groupingBy { it }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).map { it.key }
}
