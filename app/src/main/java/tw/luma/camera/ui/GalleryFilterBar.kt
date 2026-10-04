package tw.luma.camera.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/** The photos tab's search and filters; collapsed by default so the grid starts right under the title. */
@Composable
internal fun GalleryFilterBar(query: String, onQuery: (String) -> Unit, filter: Int, onFilter: (Int) -> Unit,
    films: List<String>, film: String?, onFilm: (String?) -> Unit) {
    val focus: FocusManager = LocalFocusManager.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("gallery-filter-bar"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(query, onQuery, Modifier.fillMaxWidth().testTag("gallery-search"), singleLine = true,
            placeholder = { Text("搜尋日期、膠片，例如 10/4") },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }, modifier = Modifier.semantics { contentDescription = "清除搜尋" }) {
                    CameraGlyph("close", Modifier.size(18.dp), LocalContentColor.current)
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("全部", "照片", "影片").forEachIndexed { index, text ->
                FilterChip(selected = filter == index, onClick = { onFilter(index) }, label = { Text(text) }, colors = grainChipColors(),
                    modifier = Modifier.testTag("gallery-filter-$index"))
            }
        }
        if (films.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(films) { title ->
                FilterChip(selected = film == title, onClick = { onFilm(if (film == title) null else title) }, label = { Text(title) },
                    colors = grainChipColors(), modifier = Modifier.testTag("gallery-film-chip"))
            }
        }
    }
}
