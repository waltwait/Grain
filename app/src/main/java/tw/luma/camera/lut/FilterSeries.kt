package tw.luma.camera.lut

import tw.luma.camera.LutEntry

data class FilterSeries(val id: String, val title: String, val group: FilterGroup, val variants: List<LutEntry>) {
    fun preferred(rememberedId: String?): LutEntry = variants.find { it.id == rememberedId }
        ?: variants.find { it.id == "kodak-portra-400" } ?: variants.first()

    fun variantLabel(entry: LutEntry): String = when (entry.id) {
        "kodak-portra-160" -> "160"
        "kodak-portra-400" -> "400"
        "kodak-portra-800" -> "800"
        "fuji-CLASSIC-CHROME" -> "Chrome"
        "fuji-CLASSIC-Neg." -> "Neg."
        "fuji-ETERNA" -> "一般"
        "fuji-ETERNA-BB" -> "Bleach Bypass"
        else -> entry.lut.title
    }

    companion object {
        // Group known library IDs only. An imported title is not proof of its series or source.
        fun key(entry: LutEntry): String = if (entry.imported) "imported-${entry.id}" else when (entry.id) {
            "kodak-portra-160", "kodak-portra-400", "kodak-portra-800" -> "kodak-portra"
            "fuji-CLASSIC-CHROME", "fuji-CLASSIC-Neg." -> "fuji-classic"
            "fuji-ETERNA", "fuji-ETERNA-BB" -> "fuji-eterna"
            else -> entry.id
        }

        fun catalog(entries: List<LutEntry>): List<FilterSeries> = entries.groupBy(::key).map { (id, variants) ->
            val first = variants.first()
            val group = FilterGroup.of(first)
            val title = when (id) {
                "kodak-portra" -> "Portra"
                "fuji-classic" -> "CLASSIC"
                "fuji-eterna" -> "ETERNA"
                else -> when (group) {
                    FilterGroup.FUJI -> first.lut.title.removePrefix("富士 ")
                    FilterGroup.KODAK -> first.lut.title.removePrefix("Kodak ")
                    else -> first.lut.title
                }
            }
            FilterSeries(id, title, group, if (id == "kodak-portra") variants.sortedBy { it.id } else variants)
        }
    }
}
