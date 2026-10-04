package tw.luma.camera.gallery

import org.junit.Assert.*
import org.junit.Test

class FilmIndexTest {
    private val none = "無濾鏡"

    @Test fun theFilmIsReadFromTheUserComment() {
        assertEquals("Portra 160", FilmIndex.filmOf("LUT=Portra 160; strength=1.0; input=SRGB; imageEV=0.0", none))
        assertEquals("富士 PROVIA", FilmIndex.filmOf("LUT=富士 PROVIA; strength=0.5", none))
    }

    @Test fun anExifCharsetPrefixDoesNotGetInTheWay() {
        assertEquals("Tri-X 400", FilmIndex.filmOf("ASCII\u0000\u0000\u0000LUT=Tri-X 400; strength=1.0", none))
    }

    @Test fun aPhotoWithoutAFilterIsCalledTheNoFilterLabel() {
        assertEquals(none, FilmIndex.filmOf("LUT=Original; strength=1.0", none))
    }

    @Test fun commentsWithoutAFilmGiveNothing() {
        assertNull(FilmIndex.filmOf(null, none))
        assertNull(FilmIndex.filmOf("", none))
        assertNull(FilmIndex.filmOf("shot on a phone", none))
        assertNull(FilmIndex.filmOf("LUT=; strength=1.0", none))
    }

    @Test fun theIndexSurvivesAWriteAndARead() {
        val films = mapOf(1L to "Portra 160", 22L to "富士 PROVIA", 303L to "")
        assertEquals(films, FilmIndex.decode(FilmIndex.encode(films)))
    }

    @Test fun anUnknownFilmIsRememberedAsEmptySoItIsNotReadAgain() {
        assertEquals("", FilmIndex.decode("7\t")[7L])
        assertTrue(FilmIndex.decode("7\t").containsKey(7L))
    }

    @Test fun malformedLinesAreSkipped() {
        assertEquals(mapOf(2L to "Velvia"), FilmIndex.decode("junk\nx\ty\n2\tVelvia\n\n"))
    }

    @Test fun tabsAndNewlinesInATitleCannotBreakTheFile() {
        val decoded = FilmIndex.decode(FilmIndex.encode(mapOf(5L to "A\tB\nC")))
        assertEquals("A B C", decoded[5L])
    }

    @Test fun filmsAreListedMostUsedFirst() {
        val films = mapOf(1L to "Portra 160", 2L to "Velvia", 3L to "Portra 160", 4L to "", 5L to "Velvia", 6L to "Acros", 7L to "Portra 160")
        assertEquals(listOf("Portra 160", "Velvia", "Acros"), FilmIndex.usedFilms(films))
    }
}
