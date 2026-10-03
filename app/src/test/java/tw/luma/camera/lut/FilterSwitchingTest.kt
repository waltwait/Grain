package tw.luma.camera.lut

import org.junit.Assert.*
import org.junit.Test
import tw.luma.camera.LutEntry
import tw.luma.camera.gl.FilterSettings

class FilterSwitchingTest {
    private val a = LutEntry("fuji-a", CubeLut.generate("富士 A", 2) { r, g, b -> floatArrayOf(r, g, b) })
    private val b = LutEntry("kodak-b", CubeLut.generate("Kodak B", 2) { r, g, b -> floatArrayOf(b, r, g) })

    @Test fun aNewFilmStartsAtFullStrengthAfterThePreviousFilmWasZero() {
        val switching = FilterSwitching()
        val next = switching.select(FilterSettings(lut = a.lut, strength = 0f), a.id, b)
        assertSame(b.lut, next.lut)
        assertEquals(1f, next.strength)
    }

    @Test fun returningToAFilmRestoresItsOwnStrength() {
        val switching = FilterSwitching()
        val first = FilterSettings(lut = a.lut, strength = .35f)
        val second = switching.select(first, a.id, b).copy(strength = .8f)
        val returned = switching.select(second, b.id, a)
        assertEquals(.35f, returned.strength)
        assertEquals(.8f, switching.select(returned, a.id, b).strength)
    }

    @Test fun rememberedStrengthSurvivesReopeningTheApp() {
        val switching = FilterSwitching { if (it == b.id) .6f else null }
        assertEquals(.6f, switching.select(FilterSettings(), null, b).strength)
    }

    @Test fun switchingToOriginalKeepsGradingAndRestoresTheFilmWhenSelectedAgain() {
        val switching = FilterSwitching()
        val first = FilterSettings(lut = a.lut, strength = .25f, warmth = 30f, tint = -10f, brightnessEv = .7f)
        val original = switching.select(first, a.id, null)
        assertNull(original.lut)
        assertEquals(30f, original.warmth)
        assertEquals(-10f, original.tint)
        assertEquals(.7f, original.brightnessEv)
        assertEquals(.25f, switching.select(original, null, a).strength)
    }

    @Test fun reselectingTheSameFilmKeepsItsCustomEncodingAndStrength() {
        val first = FilterSettings(lut = a.lut, strength = .4f, encoding = LutEncoding.FLOG2C)
        assertSame(first, FilterSwitching().select(first, a.id, a))
    }

    @Test fun importedTitlesCannotChangeTheirCategory() {
        val imported = a.copy(id = "imported-hash", imported = true)
        val entries = listOf(a, b, imported)
        assertEquals(listOf(a), FilterGroup.FUJI.entries(entries))
        assertEquals(listOf(imported), FilterGroup.GRAIN.entries(entries))
        assertEquals(FilterGroup.GRAIN, FilterGroup.of(a.copy(imported = true)))
    }

    @Test fun threeBrandGroupsExposeEveryFilmExactlyOnce() {
        val versions = listOf("160", "400", "800").map { b.copy(id = "kodak-portra-$it") }
        val imported = a.copy(id = "custom", imported = true)
        val entries = listOf(a, imported) + versions
        val visible = FilterGroup.entries.flatMap { it.entries(entries) }
        assertEquals(entries.map { it.id }.sorted(), visible.map { it.id }.sorted())
        assertEquals(versions, FilterGroup.KODAK.entries(entries))
    }

    @Test fun startingGroupTracksSelectionAndFallsBackWithoutFuji() {
        val grain = a.copy(id = "grain-daylight")
        assertEquals(FilterGroup.KODAK, FilterGroup.initial(listOf(a, b, grain), b.id))
        assertEquals(FilterGroup.FUJI, FilterGroup.initial(listOf(a, b, grain), null))
        assertEquals(FilterGroup.GRAIN, FilterGroup.initial(listOf(b, grain), null))
        assertEquals(FilterGroup.GRAIN, FilterGroup.initial(listOf(a, b, grain.copy(imported = true)), grain.id))
    }

    @Test fun browsingAnotherCategoryDoesNotSelectItsFirstFilm() {
        val cursor = FilterBrowseCursor(listOf(a.id, b.id), a.id)
        cursor.beginGesture()
        assertEquals(b.id, cursor.settledAt(1))
        val otherCategory = FilterBrowseCursor(listOf(a.id), b.id)
        assertNull(otherCategory.settledAt(0))
        otherCategory.beginGesture()
        assertEquals(a.id, otherCategory.settledAt(0))
    }

    @Test fun openingAndProgrammaticCenteringNeverChangesTheSelectedFilm() {
        val cursor = FilterBrowseCursor(listOf(a.id, b.id), b.id)
        assertEquals(1, cursor.initialPage)
        assertNull(cursor.settledAt(1))
        assertNull(cursor.settledAt(0))
    }
}
