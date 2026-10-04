package tw.luma.camera.gallery

import org.junit.Assert.*
import org.junit.Test

class EditPairingTest {
    private val x = EditRow(1, "GRAIN_EDIT_x.jpg")
    private val xOriginal = EditRow(2, "GRAIN_EDIT_x_original.jpg")
    private val y = EditRow(3, "GRAIN_EDIT_y.jpg")
    private val yOriginal = EditRow(4, "GRAIN_EDIT_y_original.jpg")

    @Test fun pairsEachEditedPhotoWithItsOriginal() {
        val pairs = EditPairing.pair(listOf(xOriginal, x, yOriginal, y))
        assertEquals(listOf(EditPair(x, xOriginal), EditPair(y, yOriginal)), pairs)
    }

    @Test fun editedWithoutOriginalHasNoOriginal() {
        assertEquals(listOf(EditPair(x, null)), EditPairing.pair(listOf(x)))
    }

    @Test fun orphanOriginalIsNotListed() {
        assertTrue(EditPairing.pair(listOf(xOriginal)).isEmpty())
    }

    @Test fun renamedDuplicateDoesNotPairWithAnotherEdit() {
        val duplicate = EditRow(5, "GRAIN_EDIT_x (1).jpg")
        val pairs = EditPairing.pair(listOf(x, duplicate, xOriginal))
        assertEquals(listOf(EditPair(x, xOriginal), EditPair(duplicate, null)), pairs)
    }

    @Test fun emptyFolderGivesAnEmptyList() {
        assertTrue(EditPairing.pair(emptyList()).isEmpty())
    }
}
