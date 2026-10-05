package tw.luma.camera.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import tw.luma.camera.gallery.GalleryItem
import tw.luma.camera.gallery.GalleryViewModel

/** Edit tab home: pick a new photo, or reopen a past edit to compare it with its original. */
@Composable
internal fun EditedHome(edits: List<GalleryItem>, model: GalleryViewModel, choosePhoto: () -> Unit, open: (GalleryItem) -> Unit,
    error: String? = null, retry: () -> Unit = {}) {
    Column(Modifier.fillMaxSize().testTag("photo-editor"), horizontalAlignment = Alignment.CenterHorizontally) {
        FilledTonalButton(onClick = choosePhoto, modifier = Modifier.padding(16.dp).heightIn(min = 48.dp).testTag("editor-choose")) {
            CameraGlyph("add", Modifier.size(20.dp), LocalContentColor.current)
            Spacer(Modifier.width(8.dp))
            Text("選擇照片")
        }
        // A failed gallery read would otherwise look like "no edits yet".
        if (error != null) {
            Text(error, Modifier.padding(horizontal = 16.dp).testTag("edited-error"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = retry, modifier = Modifier.heightIn(min = 48.dp).testTag("edited-retry")) { Text("重試") }
        }
        if (edits.isNotEmpty()) {
            Text("編輯成品", Modifier.align(Alignment.Start).padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge)
            LazyVerticalGrid(columns = GridCells.Adaptive(112.dp), contentPadding = PaddingValues(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxSize().testTag("edited-grid")) {
                items(edits, key = { it.uri.toString() }, contentType = { "media" }) { item ->
                    Surface(onClick = { open(item) }, shape = RoundedCornerShape(2.dp), color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.aspectRatio(1f).testTag("edited-item").semantics { contentDescription = "照片 ${item.name}" }) {
                        GalleryThumbnail(item, model, Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}
