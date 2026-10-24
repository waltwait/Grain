package tw.luma.camera.update

/** Quiet update prompting: no dialogs or notifications, only a dot and a button label once a new version is known. */
object UpdatePrompt {
    fun shouldCheckOnLaunch(feedUrl: String, alreadyChecked: Boolean): Boolean = feedUrl.isNotBlank() && !alreadyChecked

    fun entryLabel(newVersion: String?): String = if (newVersion.isNullOrBlank()) "檢查更新" else "有新版 $newVersion"

    /** The release notes belong to the version on offer, so an up-to-date app shows none. */
    fun notesToShow(available: Boolean, notes: String): String? = notes.trim().takeIf { available && it.isNotEmpty() }

    /** One entry per release-note line, without the Markdown bullet, so the page can lay them out as a list. */
    fun noteLines(notes: String): List<String> = notes.lines()
        .map { it.trim().removePrefix("-").removePrefix("*").removePrefix("•").trim() }
        .filter { it.isNotEmpty() }

    fun sizeLabel(bytes: Long): String = String.format(java.util.Locale.US, "%.1f MB", bytes.coerceAtLeast(0) / 1_048_576.0)

    /** Download progress such as "42% · 6.2 / 14.7 MB". */
    fun progressLabel(progress: Float, totalBytes: Long): String {
        val done = progress.coerceIn(0f, 1f)
        return String.format(java.util.Locale.US, "%d%% · %.1f / %s", (done * 100).toInt(),
            totalBytes.coerceAtLeast(0) * done / 1_048_576.0, sizeLabel(totalBytes))
    }
}
