package tw.luma.camera.gallery

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class GallerySearchTest {
    private val zone = ZoneId.of("Asia/Taipei")
    private val today = LocalDate.of(2026, 10, 4)

    private fun photo(year: Int, month: Int, day: Int, name: String = "GRAIN_20261004_201530_123.jpg", video: Boolean = false) =
        SearchKey(name, video, LocalDateTime.of(year, month, day, 20, 15).atZone(zone).toEpochSecond())

    private fun hit(query: String, key: SearchKey) = GallerySearch.matches(query, key, today, zone)

    @Test fun anEmptyQueryMatchesEverything() {
        assertTrue(hit("", photo(2026, 10, 4)))
        assertTrue(hit("   ", photo(2026, 10, 4)))
    }

    @Test fun fullDatesInTheCommonSpellings() {
        val key = photo(2026, 10, 4)
        for (query in listOf("2026-10-04", "2026/10/4", "2026.10.04", "2026年10月4日")) assertTrue(query, hit(query, key))
        assertFalse(hit("2026-10-05", key))
    }

    @Test fun monthAndDayWithoutAYearMatchEveryYear() {
        assertTrue(hit("10/4", photo(2026, 10, 4)))
        assertTrue(hit("10-4", photo(2025, 10, 4)))
        assertTrue(hit("10月4日", photo(2024, 10, 4)))
        assertFalse(hit("10/4", photo(2026, 11, 4)))
        assertFalse("A day in another month", hit("10/4", photo(2026, 10, 14)))
    }

    @Test fun monthsAndYears() {
        assertTrue(hit("2026-10", photo(2026, 10, 31)))
        assertFalse(hit("2026-10", photo(2025, 10, 1)))
        assertTrue(hit("10月", photo(2024, 10, 9)))
        assertFalse(hit("10月", photo(2024, 9, 9)))
        assertTrue(hit("2026", photo(2026, 1, 1)))
        assertTrue(hit("2026年", photo(2026, 12, 31)))
        assertFalse(hit("2025", photo(2026, 1, 1)))
    }

    @Test fun todayAndYesterday() {
        assertTrue(hit("今天", photo(2026, 10, 4)))
        assertFalse(hit("今天", photo(2026, 10, 3)))
        assertTrue(hit("昨天", photo(2026, 10, 3)))
    }

    @Test fun typeWords() {
        assertTrue(hit("影片", photo(2026, 10, 4, video = true)))
        assertFalse(hit("影片", photo(2026, 10, 4)))
        assertTrue(hit("照片", photo(2026, 10, 4)))
        assertFalse(hit("照片", photo(2026, 10, 4, video = true)))
    }

    @Test fun everyWordMustMatch() {
        assertTrue(hit("10/4 影片", photo(2026, 10, 4, video = true)))
        assertFalse(hit("10/4 影片", photo(2026, 10, 4)))
        assertFalse(hit("10/5 影片", photo(2026, 10, 4, video = true)))
    }

    @Test fun anythingElseIsMatchedAgainstTheFileNameIgnoringCase() {
        assertTrue(hit("20261004", photo(2026, 10, 4)))
        assertTrue(hit("grain_edit", photo(2026, 10, 4, name = "GRAIN_EDIT_x.jpg")))
        assertFalse(hit("portra", photo(2026, 10, 4)))
    }

    @Test fun impossibleDatesFallBackToTheFileName() {
        assertFalse("There is no 13th month", hit("13/45", photo(2026, 10, 4)))
        assertFalse(hit("2026-02-30", photo(2026, 2, 28)))
    }

    @Test fun theDayIsJudgedInTheLocalTimeZone() {
        val lateEvening = SearchKey("a.jpg", false, LocalDateTime.of(2026, 10, 4, 23, 30).atZone(zone).toEpochSecond())
        assertTrue("23:30 Taipei is still the 4th although it is already the 5th in Tokyo+1", hit("10/4", lateEvening))
    }

    @Test fun aFilmNameMatchesPhotosMadeWithThatFilm() {
        val portra = photo(2026, 10, 4).copy(film = "Portra 160")
        assertTrue(hit("portra", portra))
        assertTrue(hit("160", portra))
        assertFalse(hit("velvia", portra))
        assertFalse("A photo whose film is not known yet", hit("portra", photo(2026, 10, 4)))
    }

    @Test fun filmAndDateCanBeCombined() {
        val portra = photo(2026, 10, 4).copy(film = "Portra 160")
        assertTrue(hit("10/4 portra", portra))
        assertFalse(hit("10/5 portra", portra))
    }

    @Test fun photosWithoutAFilterAreFoundByTheNoFilterLabel() {
        assertTrue(hit("無濾鏡", photo(2026, 10, 4).copy(film = "無濾鏡")))
    }

    @Test fun fullWidthCharactersFromAChineseKeyboardAreUnderstood() {
        assertTrue(hit("１０／４", photo(2026, 10, 4)))
        assertTrue("An ideographic space separates words", hit("10/4\u3000影片", photo(2026, 10, 4, video = true)))
        assertFalse(hit("10/4\u3000影片", photo(2026, 10, 4)))
    }

    @Test fun aDayMayEndWith號() {
        assertTrue(hit("10月4號", photo(2026, 10, 4)))
        assertFalse(hit("10月5號", photo(2026, 10, 4)))
    }

    @Test fun aWordThatLooksLikeADateIsAlsoSearchedAsText() {
        val film = photo(2026, 10, 4).copy(film = "Ektar 1.5")
        assertTrue("1.5 is a film number here, not January 5th", hit("1.5", film))
        assertTrue("and still January 5th for a photo of that day", hit("1.5", photo(2026, 1, 5)))
        assertFalse(hit("1.5", photo(2026, 10, 4)))
    }

    @Test fun theQueryCanBeSplitOnceAndUsedForEveryPhoto() {
        val tokens = GallerySearch.tokens("  10/4\u3000影片  portra ")
        assertEquals(listOf("10/4", "影片", "portra"), tokens)
        val key = photo(2026, 10, 4, video = true).copy(film = "Portra 160")
        assertTrue(GallerySearch.matches(tokens, key, today, zone))
        assertTrue(GallerySearch.matches(emptyList(), key, today, zone))
    }
}
