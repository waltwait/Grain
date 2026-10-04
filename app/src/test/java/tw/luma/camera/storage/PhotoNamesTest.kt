package tw.luma.camera.storage

import org.junit.Assert.*
import org.junit.Test

class PhotoNamesTest {
    private val time = "20261004_201530_123"

    @Test fun editedAndOriginalNamesUseTheTargetPrefix() {
        assertEquals("GRAIN_EDIT_20261004_201530_123.jpg", PhotoNames.edited(SaveTarget.EDIT, time))
        assertEquals("GRAIN_EDIT_20261004_201530_123_original.jpg", PhotoNames.original(SaveTarget.EDIT, time))
        assertEquals("GRAIN_20261004_201530_123.jpg", PhotoNames.edited(SaveTarget.CAMERA, time))
        assertEquals("GRAIN_20261004_201530_123_original.jpg", PhotoNames.original(SaveTarget.CAMERA, time))
    }

    @Test fun queryPathsEndWithASlash() {
        assertEquals("Pictures/Grain Edits/", SaveTarget.EDIT.queryPath)
        assertEquals("Pictures/Grain/", SaveTarget.CAMERA.queryPath)
        assertTrue(SaveTarget.EDIT.alwaysSaveOriginal)
        assertFalse(SaveTarget.CAMERA.alwaysSaveOriginal)
    }

    @Test fun originalBaseIsTheEditedNameWithoutExtensionPlusSuffix() {
        assertEquals("GRAIN_EDIT_x_original", PhotoNames.originalBase("GRAIN_EDIT_x.jpg"))
        assertNull(PhotoNames.originalBase("GRAIN_EDIT_x_original.jpg"))
        assertNull(PhotoNames.originalBase("GRAIN_EDIT_x_original.png"))
        assertNull(PhotoNames.originalBase("note.png"))
    }

    @Test fun originalKeepsTheSourceExtension() {
        assertEquals("GRAIN_EDIT_20261004_201530_123_original.png", PhotoNames.original(SaveTarget.EDIT, time, "png"))
        assertEquals("GRAIN_EDIT_20261004_201530_123_original.heic", PhotoNames.original(SaveTarget.EDIT, time, "heic"))
    }

    @Test fun isOriginalRecognizesTheSuffixOnly() {
        assertTrue(PhotoNames.isOriginal("GRAIN_EDIT_x_original.jpg"))
        assertFalse(PhotoNames.isOriginal("GRAIN_EDIT_x.jpg"))
        assertFalse("A renamed duplicate is not a recognized original", PhotoNames.isOriginal("GRAIN_EDIT_x_original (1).jpg"))
    }

    @Test fun isOriginalIgnoresTheExtension() {
        assertTrue(PhotoNames.isOriginal("GRAIN_EDIT_x_original.png"))
        assertTrue(PhotoNames.isOriginal("GRAIN_EDIT_x_original.heic"))
    }
}
