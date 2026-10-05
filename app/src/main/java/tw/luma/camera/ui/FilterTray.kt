package tw.luma.camera.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import tw.luma.camera.LutEntry
import tw.luma.camera.lut.FilterBrowseCursor
import tw.luma.camera.lut.FilterGroup
import kotlin.math.roundToInt

/** A viewfinder overlay. Browsing a group or programmatically centering a card never applies a LUT. */
@Composable
internal fun FilterTray(
    entries: List<LutEntry>, selectedId: String?, strength: Float, enabled: Boolean,
    maxHeight: Dp, compact: Boolean, onSelect: (String?) -> Unit,
    onStrength: (Float) -> Unit, onStrengthFinished: () -> Unit, onImport: () -> Unit,
    onMore: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier,
) {
    val active = remember(entries, selectedId) { entries.find { it.id == selectedId } }
    var group by rememberSaveable { mutableStateOf(FilterGroup.initial(entries, selectedId)) }
    // An import can select a film outside the browsed group. Follow explicit selections only.
    LaunchedEffect(selectedId) {
        if (active != null) group = FilterGroup.of(active)
    }
    val visible = remember(entries, group) { group.entries(entries) }
    Surface(modifier.fillMaxWidth().heightIn(max = maxHeight).testTag("filter-tray")
        .pointerInput(Unit) { detectTapGestures(onTap = {}) },
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp), color = GrainSurfaces.tray) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 4.dp)) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(active?.let(FilterGroup::title) ?: NO_FILTER_LABEL, Modifier.weight(1f).testTag("filter-active-name"),
                    style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = { onSelect(null) }, enabled = enabled, contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.widthIn(min = 48.dp).heightIn(min = 48.dp).testTag("filter-original")
                        .semantics { selected = selectedId == null; contentDescription = "不使用濾鏡" }) {
                    Text(NO_FILTER_LABEL, color = if (selectedId == null) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = .65f))
                }
                TrayIcon("add", "匯入 LUT", enabled, "filter-import", onImport)
                TrayIcon("tune", "調色與濾鏡資訊", enabled, "filter-more", onMore)
                TrayIcon("close", "關閉濾鏡選擇", true, "filter-close", onClose)
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth().testTag("filter-groups")) {
                items(FilterGroup.entries, key = { it.name }) { item ->
                    FilterChip(selected = item == group, onClick = { group = item }, enabled = enabled, colors = grainChipColors(),
                        label = { Text(item.label) }, modifier = Modifier.heightIn(min = 48.dp).testTag("filter-group-${item.name}"))
                }
            }
            if (visible.isEmpty()) Box(Modifier.fillMaxWidth().heightIn(min = 72.dp), contentAlignment = Alignment.Center) {
                Text("尚無濾鏡", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = .65f))
            } else key(group, visible.map { it.id }) {
                FilmPicker(visible, selectedId, enabled, compact, onSelect)
            }
            if (active != null) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("強度", style = MaterialTheme.typography.labelMedium)
                Slider(strength.coerceIn(0f, 1f), onStrength, enabled = enabled, onValueChangeFinished = onStrengthFinished,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp).testTag("filter-strength"))
                Text("${(strength * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun TrayIcon(glyph: String, description: String, enabled: Boolean, tag: String, onClick: () -> Unit) {
    IconButton(onClick, enabled = enabled, modifier = Modifier.size(48.dp).testTag(tag).semantics { contentDescription = description }) {
        CameraGlyph(glyph, Modifier.size(21.dp), Color.White.copy(alpha = if (enabled) .8f else .35f))
    }
}

@Composable
internal fun FilmPicker(
    entries: List<LutEntry>, selectedId: String?, enabled: Boolean, compact: Boolean,
    onSelect: (String?) -> Unit, chosen: Set<String>? = null,
) {
    // With a set of checked films (the editor) taps check or uncheck and swiping only browses; without one (the camera) swiping selects.
    val multi = chosen != null
    val ids = remember(entries) { entries.map { it.id } }
    val cursor = remember(ids) { FilterBrowseCursor(ids, selectedId) }
    val pager = rememberPagerState(initialPage = cursor.initialPage) { entries.size }
    val pick by rememberUpdatedState(onSelect)
    val canPick by rememberUpdatedState(enabled)
    val scope = rememberCoroutineScope()
    LaunchedEffect(pager, cursor) {
        pager.interactionSource.interactions.collect { if (it is DragInteraction.Start) cursor.beginGesture() }
    }
    LaunchedEffect(pager, cursor) {
        snapshotFlow { pager.isScrollInProgress to pager.settledPage }.collect { (scrolling, page) ->
            if (!scrolling) cursor.settledAt(page)?.let { id ->
                if (canPick && !multi) pick(id)
            }
        }
    }
    LaunchedEffect(selectedId) {
        val page = ids.indexOf(selectedId)
        if (!multi && page >= 0 && pager.currentPage != page && !pager.isScrollInProgress) pager.animateScrollToPage(page)
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cardWidth = (maxWidth * .44f).coerceIn(136.dp, 184.dp).coerceAtMost(maxWidth)
        HorizontalPager(pager, pageSize = PageSize.Fixed(cardWidth), pageSpacing = 8.dp,
            contentPadding = PaddingValues(horizontal = ((maxWidth - cardWidth) / 2).coerceAtLeast(0.dp), vertical = 4.dp),
            userScrollEnabled = enabled, key = { ids[it] }, modifier = Modifier.fillMaxWidth().testTag("filter-pager")) { page ->
            val film = entries[page]
            FilmCard(film, if (chosen != null) film.id in chosen else film.id == selectedId, enabled, compact, multi) {
                pick(film.id)
                scope.launch { pager.animateScrollToPage(page) }
            }
        }
    }
}

@Composable
private fun FilmCard(entry: LutEntry, chosen: Boolean, enabled: Boolean, compact: Boolean, multi: Boolean, onClick: () -> Unit) {
    val group = FilterGroup.of(entry)
    val title = FilterGroup.title(entry)
    val accent = when (group) {
        FilterGroup.KODAK -> Color(0xFFD4A955)
        FilterGroup.FUJI -> Color(0xFF8CAA8D)
        FilterGroup.GRAIN -> Color(0xFFC29C85)
    }
    Surface(onClick, enabled = enabled, shape = RoundedCornerShape(10.dp), color = GrainSurfaces.raised,
        border = BorderStroke(if (chosen) 2.dp else 1.dp, if (chosen) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = .16f)),
        modifier = Modifier.fillMaxWidth().heightIn(min = if (compact) 68.dp else 88.dp).testTag("filter-${entry.id}")
            .semantics { selected = chosen; role = if (multi) Role.Checkbox else Role.RadioButton; contentDescription = "${group.label} $title" }) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            // Film packaging, not a thumbnail: the actual look is shown by the viewfinder.
            Canvas(Modifier.width(3.dp).height(if (compact) 36.dp else 48.dp)) { drawRect(accent) }
            Spacer(Modifier.width(10.dp))
            Text(title, Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (chosen) Text("✓", Modifier.padding(start = 4.dp), color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelSmall)
        }
    }
}
