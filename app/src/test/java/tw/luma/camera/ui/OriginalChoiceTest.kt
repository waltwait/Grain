package tw.luma.camera.ui

import org.junit.Assert.*
import org.junit.Test

class OriginalChoiceTest {
    @Test fun aSwipeToAnotherPhotoNeverInheritsTheFlag() {
        val shown = OriginalChoice.toggle(null, "a")
        assertTrue(OriginalChoice.isShown(shown, "a"))
        assertFalse("The next page must start on its edited photo", OriginalChoice.isShown(shown, "b"))
    }

    @Test fun leavingThePhotoClearsTheFlag() {
        assertNull(OriginalChoice.afterCurrentChanged("a", previous = "a", current = "b"))
    }

    @Test fun reassigningTheSamePhotoKeepsTheFlag() {
        assertEquals("Rotation re-selects the same photo", "a", OriginalChoice.afterCurrentChanged("a", previous = "a", current = "a"))
    }

    @Test fun togglingTwiceReturnsToTheEditedPhoto() {
        assertNull(OriginalChoice.toggle("a", "a"))
        assertEquals("a", OriginalChoice.toggle(null, "a"))
    }
}
