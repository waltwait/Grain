package tw.luma.camera.editor

/**
 * The time part of the names of one photo's outputs, remembered per source photo so that a retry, or a session restored after
 * the system killed the app, keeps naming outputs that pair with the photo's one original. A new stem is saved before it is used.
 */
class StemBook(initial: Map<String, String>, private val persist: (Map<String, String>) -> Unit) {
    private val stems = LinkedHashMap(initial)

    fun stemFor(source: String, newStamp: () -> String): String =
        stems[source] ?: newStamp().also { stems[source] = it; persist(stems.toMap()) }

    fun clear() = stems.clear()

    companion object {
        fun encode(stems: Map<String, String>): List<String> = stems.map { (source, stem) -> "$source\t$stem" }

        fun decode(lines: List<String>): Map<String, String> = lines.mapNotNull { line ->
            line.split('\t', limit = 2).takeIf { it.size == 2 && it[0].isNotEmpty() && it[1].isNotEmpty() }?.let { it[0] to it[1] }
        }.toMap()
    }
}
