package tw.luma.camera.ui

import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

/** What the "no film look" choice is called everywhere; "原色" read too close to "原圖" (the original photo). */
internal const val NO_FILTER_LABEL = "無濾鏡"

/** Black scrim behind buttons that sit on a photo; at this strength gold text stays above 4.5:1 even on a white photo. */
internal const val PHOTO_OVERLAY_SCRIM_ALPHA = 0.65f

/** Dark tones that are not Material theme roles, kept in one place instead of repeated as literals. */
internal object GrainSurfaces {
    val raised = Color(0xFF242529)
    val control = Color(0xFF202124)
    val tray = Color(0xF0161719)
}

/** Selected chips use the brand gold like tabs and selected cards, not Material's default grey-lavender. */
@Composable
internal fun grainChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = .16f).compositeOver(MaterialTheme.colorScheme.surface),
    selectedLabelColor = MaterialTheme.colorScheme.primary,
)
