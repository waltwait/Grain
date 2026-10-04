package tw.luma.camera.gallery

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** What the gallery search looks at for one photo or video. [film] is null while it is not known yet. */
data class SearchKey(val name: String, val video: Boolean, val addedSeconds: Long, val film: String? = null)

/**
 * Words are separated by spaces and every word must match. A word is a date (2026-10-04, 10/4, 10月4日, 2026-10, 10月, 2026),
 * 今天 or 昨天, 照片 or 影片, or otherwise a piece of the file name or of the film name.
 */
object GallerySearch {
    private val fullDate = Regex("""(\d{4})[-/.年](\d{1,2})[-/.月](\d{1,2})日?""")
    private val yearMonth = Regex("""(\d{4})[-/.年](\d{1,2})月?""")
    private val monthDay = Regex("""(\d{1,2})[-/.月](\d{1,2})日?""")
    private val monthOnly = Regex("""(\d{1,2})月""")
    private val yearOnly = Regex("""(\d{4})年?""")
    private val videoWords = setOf("影片", "video", "videos")
    private val photoWords = setOf("照片", "photo", "photos")

    fun matches(query: String, key: SearchKey, today: LocalDate, zone: ZoneId): Boolean {
        val tokens = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return true
        val date = Instant.ofEpochSecond(key.addedSeconds).atZone(zone).toLocalDate()
        return tokens.all { matchesToken(it, key, date, today) }
    }

    private fun matchesToken(token: String, key: SearchKey, date: LocalDate, today: LocalDate): Boolean {
        val word = token.lowercase()
        return when {
            word in videoWords -> key.video
            word in photoWords -> !key.video
            word == "今天" || word == "today" -> date == today
            word == "昨天" || word == "yesterday" -> date == today.minusDays(1)
            else -> dateMatch(word, date) ?: (key.name.contains(token, ignoreCase = true) || key.film?.contains(token, ignoreCase = true) == true)
        }
    }

    /** True or false when [word] is a valid date expression, null when it is not one (so it is searched as text). */
    private fun dateMatch(word: String, date: LocalDate): Boolean? {
        fullDate.matchEntire(word)?.let { m ->
            val (y, mo, d) = m.destructured
            return runCatching { LocalDate.of(y.toInt(), mo.toInt(), d.toInt()) }.getOrNull()?.let { it == date }
        }
        yearMonth.matchEntire(word)?.let { m ->
            val (y, mo) = m.destructured
            return if (mo.toInt() in 1..12) date.year == y.toInt() && date.monthValue == mo.toInt() else null
        }
        monthDay.matchEntire(word)?.let { m ->
            val (mo, d) = m.destructured
            val valid = mo.toInt() in 1..12 && d.toInt() in 1..YearMonth.of(2024, mo.toInt()).lengthOfMonth()
            return if (valid) date.monthValue == mo.toInt() && date.dayOfMonth == d.toInt() else null
        }
        monthOnly.matchEntire(word)?.let { m ->
            return if (m.groupValues[1].toInt() in 1..12) date.monthValue == m.groupValues[1].toInt() else null
        }
        yearOnly.matchEntire(word)?.let { m ->
            return if (m.groupValues[1].toInt() in 1900..2200) date.year == m.groupValues[1].toInt() else null
        }
        return null
    }
}
