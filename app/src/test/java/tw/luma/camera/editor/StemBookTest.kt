package tw.luma.camera.editor

import org.junit.Assert.*
import org.junit.Test

class StemBookTest {
    @Test fun aNewStemIsSavedBeforeItIsHandedOut() {
        val saved = mutableListOf<Map<String, String>>()
        val book = StemBook(emptyMap()) { saved += it }
        assertEquals("T1", book.stemFor("photo-a") { "T1" })
        assertEquals("The first photo's stem must already be in the saved copy", mapOf("photo-a" to "T1"), saved.last())
    }

    @Test fun thePhotoThatIsBeingExportedIsNeverMissingFromTheSavedCopy() {
        val saved = mutableListOf<Map<String, String>>()
        val book = StemBook(emptyMap()) { saved += it }
        book.stemFor("a") { "T1" }
        book.stemFor("b") { "T2" }
        assertEquals(mapOf("a" to "T1", "b" to "T2"), saved.last())
    }

    @Test fun theSameSourceKeepsItsStemAndSavesNothingNew() {
        var saves = 0
        val book = StemBook(emptyMap()) { saves++ }
        assertEquals("T1", book.stemFor("a") { "T1" })
        assertEquals("T1", book.stemFor("a") { "T9" })
        assertEquals(1, saves)
    }

    @Test fun restoredStemsAreReusedSoNamesStayPaired() {
        val book = StemBook(mapOf("a" to "T1")) { fail("Nothing new to save") }
        assertEquals("T1", book.stemFor("a") { "T9" })
    }

    @Test fun clearingForgetsEveryStem() {
        val book = StemBook(mapOf("a" to "T1")) { }
        book.clear()
        assertEquals("T2", book.stemFor("a") { "T2" })
    }

    @Test fun theSavedFormSurvivesAWriteAndARead() {
        val stems = mapOf("content://media/external/images/media/42" to "20261004_201530_123", "x y" to "T")
        assertEquals(stems, StemBook.decode(StemBook.encode(stems)))
    }

    @Test fun malformedSavedLinesAreSkipped() {
        assertEquals(mapOf("a" to "T1"), StemBook.decode(listOf("junk", "a\tT1", "")))
    }
}
