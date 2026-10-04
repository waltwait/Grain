package tw.luma.camera.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun BackIconButton(onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, description: String = "返回") {
    IconButton(onClick = onClick, enabled = enabled,
        modifier = modifier.size(48.dp).semantics { contentDescription = description }) {
        CameraGlyph("back", Modifier.size(24.dp), LocalContentColor.current)
    }
}
