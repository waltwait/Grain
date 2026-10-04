package tw.luma.camera.editor

import org.junit.Assert.*
import org.junit.Test

class FilterChoiceTest {
    @Test fun tappingAddsInOrderAndTappingAgainRemoves() {
        assertEquals(listOf("a"), FilterChoice.toggle(emptyList(), "a", sources = 1))
        assertEquals(listOf("a", "b"), FilterChoice.toggle(listOf("a"), "b", sources = 1))
        assertEquals(listOf("b"), FilterChoice.toggle(listOf("a", "b"), "a", sources = 1))
        assertEquals(emptyList<String>(), FilterChoice.toggle(listOf("a"), "a", sources = 1))
    }

    @Test fun addingStopsAtTwentyOutputs() {
        val three = listOf("a", "b", "c")
        assertEquals(listOf("a", "b", "c", "d"), FilterChoice.toggle(three, "d", sources = 5))
        val four = listOf("a", "b", "c", "d")
        assertEquals("5 photos x 5 filters would be 25 outputs", four, FilterChoice.toggle(four, "e", sources = 5))
    }

    @Test fun removingIsAlwaysAllowedEvenAtTheLimit() {
        val four = listOf("a", "b", "c", "d")
        assertEquals(listOf("a", "b", "c"), FilterChoice.toggle(four, "d", sources = 5))
    }

    @Test fun outputsAreCountedAsPhotosTimesFiltersWithAtLeastOneFilter() {
        assertEquals(1, FilterChoice.outputs(sources = 1, chosen = 0))
        assertEquals(6, FilterChoice.outputs(sources = 3, chosen = 2))
        assertEquals(4, FilterChoice.outputs(sources = 4, chosen = 0))
    }

    @Test fun thePreviewFollowsTheLastTapOrFallsBack() {
        assertEquals("b", FilterChoice.primary(listOf("a", "b"), previous = "a", tapped = "b"))
        assertEquals("Untapping another filter keeps the preview", "b", FilterChoice.primary(listOf("b", "c"), previous = "b", tapped = "a"))
        assertEquals("Removing the previewed one shows the latest left", "c", FilterChoice.primary(listOf("b", "c"), previous = "a", tapped = "a"))
        assertNull(FilterChoice.primary(emptyList(), previous = "a", tapped = "a"))
    }

    @Test fun theSummaryNamesTheChoice() {
        assertEquals("無濾鏡", FilterChoice.summary(chosenCount = 0, sources = 1, primaryTitle = null, noFilterLabel = "無濾鏡"))
        assertEquals("Portra 160", FilterChoice.summary(chosenCount = 1, sources = 1, primaryTitle = "Portra 160", noFilterLabel = "無濾鏡"))
        assertEquals("已選 3 個濾鏡 · 將輸出 6 張", FilterChoice.summary(chosenCount = 3, sources = 2, primaryTitle = "x", noFilterLabel = "無濾鏡"))
        assertEquals("已選 4 個濾鏡 · 已達上限 20 張", FilterChoice.summary(chosenCount = 4, sources = 5, primaryTitle = "x", noFilterLabel = "無濾鏡"))
    }

    @Test fun theLimitWarningOnlyShowsOnceSeveralFiltersFillTheOutputs() {
        assertTrue(FilterChoice.atLimit(chosenCount = 4, sources = 5))
        assertFalse(FilterChoice.atLimit(chosenCount = 3, sources = 5))
        assertFalse("A single filter on 20 photos is not a multi-filter limit", FilterChoice.atLimit(chosenCount = 1, sources = 20))
    }
}
