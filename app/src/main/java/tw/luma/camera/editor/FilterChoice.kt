package tw.luma.camera.editor

import kotlin.math.max

/** Rules for choosing several filters for the same photos: every tap checks or unchecks one, up to 20 outputs in all. */
object FilterChoice {
    fun outputs(sources: Int, chosen: Int): Int = sources * max(1, chosen)

    /** The list after tapping [id]; adding is refused (the list stays the same) once photos × filters would pass the limit. */
    fun toggle(chosen: List<String>, id: String, sources: Int): List<String> = when {
        id in chosen -> chosen - id
        outputs(sources, chosen.size + 1) > PhotoBatchProgress.MAX_OUTPUTS -> chosen
        else -> chosen + id
    }

    /** The filter shown in the preview: the one just checked, else the previous one while it is still checked, else the latest left. */
    fun primary(chosen: List<String>, previous: String?, tapped: String): String? = when {
        tapped in chosen -> tapped
        previous != null && previous in chosen -> previous
        else -> chosen.lastOrNull()
    }

    fun atLimit(chosenCount: Int, sources: Int): Boolean = chosenCount >= 2 && outputs(sources, chosenCount) >= PhotoBatchProgress.MAX_OUTPUTS

    fun summary(chosenCount: Int, sources: Int, primaryTitle: String?, noFilterLabel: String): String = when {
        chosenCount <= 0 -> noFilterLabel
        chosenCount == 1 -> primaryTitle ?: noFilterLabel
        atLimit(chosenCount, sources) -> "已選 $chosenCount 個濾鏡 · 已達上限 ${PhotoBatchProgress.MAX_OUTPUTS} 張"
        else -> "已選 $chosenCount 個濾鏡 · 將輸出 ${outputs(sources, chosenCount)} 張"
    }
}
