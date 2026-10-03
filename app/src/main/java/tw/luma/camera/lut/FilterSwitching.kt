package tw.luma.camera.lut

import tw.luma.camera.LutEntry
import tw.luma.camera.gl.FilterSettings

class FilterSwitching(private val loadStrength: (String) -> Float? = { null }) {
    private val strengths = mutableMapOf<String, Float>()

    fun select(previous: FilterSettings, previousId: String?, next: LutEntry?): FilterSettings {
        if (previousId == next?.id && previous.lut === next?.lut) return previous
        previousId?.let { strengths[it] = validStrength(previous.strength) }
        val strength = next?.let { strengths[it.id] ?: loadStrength(it.id)?.let(::validStrength) } ?: 1f
        return previous.copy(lut = next?.lut, strength = strength,
            encoding = next?.lut?.suggestedEncoding ?: LutEncoding.SRGB)
    }

    private fun validStrength(value: Float) = if (value.isFinite()) value.coerceIn(0f, 1f) else 1f
}

enum class FilterGroup(val label: String) {
    FUJI("FUJIFILM"), KODAK("KODAK"), GRAIN("GRAIN");
    fun entries(all: List<LutEntry>): List<LutEntry> = all.filter { of(it) == this }

    companion object {
        fun initial(entries: List<LutEntry>, selectedId: String?): FilterGroup {
            entries.find { it.id == selectedId }?.let { return of(it) }
            return listOf(FUJI, GRAIN, KODAK).firstOrNull { it.entries(entries).isNotEmpty() } ?: FUJI
        }

        fun of(entry: LutEntry) = when {
            entry.imported -> GRAIN
            entry.id.startsWith("fuji-") -> FUJI
            entry.id.startsWith("kodak-") -> KODAK
            else -> GRAIN
        }

        fun title(entry: LutEntry) = when (of(entry)) {
            FUJI -> entry.lut.title.removePrefix("富士 ")
            KODAK -> entry.lut.title.removePrefix("Kodak ")
            GRAIN -> entry.lut.title
        }
    }
}

class FilterBrowseCursor(private val ids: List<String>, selectedId: String?) {
    val initialPage = ids.indexOf(selectedId).coerceAtLeast(0)
    private var userGesture = false
    fun beginGesture() { userGesture = true }
    fun settledAt(page: Int): String? {
        if (!userGesture) return null
        userGesture = false
        return ids.getOrNull(page)
    }
}
