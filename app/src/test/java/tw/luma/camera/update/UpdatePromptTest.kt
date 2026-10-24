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

    @Test fun everyReleaseNoteLineBecomesOneBulletWithoutItsMarkdownMarker() {
        assertEquals(listOf("第一點", "第二點 - 含連字號", "第三點"),
            UpdatePrompt.noteLines("- 第一點\n\n* 第二點 - 含連字號\n  • 第三點  \n"))
        assertEquals(listOf("沒有符號的一行"), UpdatePrompt.noteLines("沒有符號的一行"))
        assertTrue(UpdatePrompt.noteLines(" \n ").isEmpty())
    }

    @Test fun sizeAndProgressAreShownInMegabytes() {
        assertEquals("14.7 MB", UpdatePrompt.sizeLabel(15_366_556))
        assertEquals("0.0 MB", UpdatePrompt.sizeLabel(-5))
        assertEquals("0% · 0.0 / 14.7 MB", UpdatePrompt.progressLabel(0f, 15_366_556))
        assertEquals("50% · 7.3 / 14.7 MB", UpdatePrompt.progressLabel(.5f, 15_366_556))
        assertEquals("Progress is clamped to 0–100%", "100% · 14.7 / 14.7 MB", UpdatePrompt.progressLabel(1.7f, 15_366_556))
    }
}
