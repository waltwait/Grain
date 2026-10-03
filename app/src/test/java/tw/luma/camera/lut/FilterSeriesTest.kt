package tw.luma.camera.lut

import org.junit.Assert.*
import org.junit.Test
import tw.luma.camera.LutEntry

class FilterSeriesTest {
    private fun entry(id: String, title: String, imported: Boolean = false) =
        LutEntry(id, CubeLut.generate(title, 2) { r, g, b -> floatArrayOf(r, g, b) }, imported)

    @Test fun portraVariantsShareOneSeriesAndADefaultOf400() {
        val entries = listOf(entry("kodak-portra-160", "Kodak Portra 160"), entry("kodak-portra-400", "Kodak Portra 400"),
            entry("kodak-portra-800", "Kodak Portra 800"), entry("kodak-tri-x-400", "Kodak Tri-X 400"))
        val series = FilterSeries.catalog(entries)
        assertEquals(2, series.size)
        assertEquals("Portra", series[0].title)
        assertEquals(listOf("160", "400", "800"), series[0].variants.map { series[0].variantLabel(it) })
        assertEquals("kodak-portra-400", series[0].preferred(null).id)
        assertEquals("kodak-portra-800", series[0].preferred("kodak-portra-800").id)
    }

    @Test fun eternaAndClassicHaveSeparateFamilies() {
        val entries = listOf(entry("fuji-CLASSIC-CHROME", "富士 CLASSIC CHROME"), entry("fuji-CLASSIC-Neg.", "富士 CLASSIC Neg."),
            entry("fuji-ETERNA", "富士 ETERNA"), entry("fuji-ETERNA-BB", "富士 ETERNA Bleach Bypass"))
        val series = FilterSeries.catalog(entries)
        assertEquals(2, series.size)
        assertEquals(2, series[0].variants.size)
        assertEquals(2, series[1].variants.size)
        assertEquals("Chrome", series[0].variantLabel(entries[0]))
        assertEquals("Neg.", series[0].variantLabel(entries[1]))
        assertEquals("Bleach Bypass", series[1].variantLabel(entries[3]))
    }

    @Test fun importedNamesCannotMergeIntoBundledSeries() {
        val entries = listOf(entry("kodak-portra-160", "Kodak Portra 160"), entry("import1", "Kodak Portra 400", true),
            entry("import2", "Kodak Portra 800", true))
        val series = FilterSeries.catalog(entries)
        assertEquals(3, series.size)
        assertEquals(FilterGroup.IMPORTED, series[1].group)
        assertEquals(FilterGroup.IMPORTED, series[2].group)
    }

    @Test fun missingRememberedOrDefaultVariantFallsBackToAnAvailableFilm() {
        val series = FilterSeries.catalog(listOf(entry("kodak-portra-160", "Kodak Portra 160"))).single()
        assertEquals("kodak-portra-160", series.preferred("deleted-filter").id)
    }
}
