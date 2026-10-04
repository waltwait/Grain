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

    @Test fun pairsAnOriginalWhateverItsExtension() {
        val png = EditRow(6, "GRAIN_EDIT_x_original.png")
        val heic = EditRow(7, "GRAIN_EDIT_y_original.heic")
        assertEquals(listOf(EditPair(x, png), EditPair(y, heic)), EditPairing.pair(listOf(x, png, y, heic)))
        assertTrue("An original of any extension is never listed on its own", EditPairing.pair(listOf(png, heic)).isEmpty())
    }

    @Test fun emptyFolderGivesAnEmptyList() {
        assertTrue(EditPairing.pair(emptyList()).isEmpty())
    }

    @Test fun severalOutputsOfOnePhotoShareItsOriginal() {
        val second = EditRow(8, "GRAIN_EDIT_x-2.jpg")
        val third = EditRow(9, "GRAIN_EDIT_x-3.jpg")
        val pairs = EditPairing.pair(listOf(third, second, x, xOriginal))
        assertEquals(listOf(EditPair(third, xOriginal), EditPair(second, xOriginal), EditPair(x, xOriginal)), pairs)
    }

    @Test fun anOutputWhoseOriginalIsMissingHasNone() {
        assertEquals(listOf(EditPair(EditRow(8, "GRAIN_EDIT_x-2.jpg"), null)), EditPairing.pair(listOf(EditRow(8, "GRAIN_EDIT_x-2.jpg"))))
    }
}
