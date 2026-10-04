package tw.luma.camera.update

/** Quiet update prompting: no dialogs or notifications, only a dot and a button label once a new version is known. */
object UpdatePrompt {
    fun shouldCheckOnLaunch(feedUrl: String, alreadyChecked: Boolean): Boolean = feedUrl.isNotBlank() && !alreadyChecked

    fun entryLabel(newVersion: String?): String = if (newVersion.isNullOrBlank()) "檢查更新" else "有新版 $newVersion"

    /** The release notes belong to the version on offer, so an up-to-date app shows none. */
    fun notesToShow(available: Boolean, notes: String): String? = notes.trim().takeIf { available && it.isNotEmpty() }
}
