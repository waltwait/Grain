package tw.luma.camera.update

import org.junit.Assert.*
import org.junit.Test

class UpdatePromptTest {
    @Test fun launchChecksOnceAndOnlyWhenAFeedIsConfigured() {
        assertTrue(UpdatePrompt.shouldCheckOnLaunch("https://example.test/latest.json", alreadyChecked = false))
        assertFalse("Once per launch", UpdatePrompt.shouldCheckOnLaunch("https://example.test/latest.json", alreadyChecked = true))
        assertFalse("No feed configured", UpdatePrompt.shouldCheckOnLaunch("", alreadyChecked = false))
        assertFalse(UpdatePrompt.shouldCheckOnLaunch("  ", alreadyChecked = false))
    }

    @Test fun settingsButtonAnnouncesANewVersionOnly() {
        assertEquals("檢查更新", UpdatePrompt.entryLabel(null))
        assertEquals("檢查更新", UpdatePrompt.entryLabel(" "))
        assertEquals("有新版 0.7.2", UpdatePrompt.entryLabel("0.7.2"))
    }

    @Test fun notesAreShownOnlyWhenThereIsSomethingToInstall() {
        assertEquals("編輯成品另存。", UpdatePrompt.notesToShow(available = true, notes = " 編輯成品另存。\n"))
        assertNull("Up to date shows no description", UpdatePrompt.notesToShow(available = false, notes = "編輯成品另存。"))
        assertNull(UpdatePrompt.notesToShow(available = true, notes = "  "))
    }
}
