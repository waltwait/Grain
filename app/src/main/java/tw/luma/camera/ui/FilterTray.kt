package tw.luma.camera.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import tw.luma.camera.lut.FilterSeries
import kotlin.math.roundToInt

/** A viewfinder overlay. Browsing a group or programmatically centering a card never applies a LUT. */
@Composable
internal fun FilterTray(
    entries: List<LutEntry>, selectedId: String?, strength: Float, enabled: Boolean,
    maxHeight: Dp, compact: Boolean, onSeries: (FilterSeries) -> Unit, onVariant: (String?) -> Unit,
    onStrength: (Float) -> Unit, onStrengthFinished: () -> Unit, onImport: () -> Unit,
    onMore: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier,
) {
    val catalog = remember(entries) { FilterSeries.catalog(entries) }
    val active = remember(entries, selectedId) { entries.find { it.id == selectedId } }
    var group by rememberSaveable { mutableStateOf(FilterGroup.ALL) }
    val groups = remember(catalog) { FilterGroup.entries.filter { it == FilterGroup.ALL || catalog.any { series -> series.group == it } } }
    // An import can select a film outside the browsed group. Follow explicit selections only.
    LaunchedEffect(selectedId, groups) {
        if (active != null && group != FilterGroup.ALL && FilterGroup.of(active) != group) group = FilterGroup.of(active)
        if (group !in groups) group = FilterGroup.ALL
    }
    val visible = remember(catalog, group) { if (group == FilterGroup.ALL) catalog else catalog.filter { it.group == group } }
    Surface(modifier.fillMaxWidth().heightIn(max = maxHeight).testTag("filter-tray")
        .pointerInput(Unit) { detectTapGestures(onTap = {}) },
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp), color = Color(0xF0161719)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 4.dp)) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(active?.lut?.title?.removePrefix("富士 ") ?: "原色", Modifier.weight(1f).testTag("filter-active-name"),
                    style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = { onVariant(null) }, enabled = enabled, contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.widthIn(min = 48.dp).heightIn(min = 48.dp).testTag("filter-original")
                        .semantics { selected = selectedId == null; contentDescription = "使用原色" }) {
                    Text("原色", color = if (selectedId == null) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = .65f))
                }
                TrayIcon("add", "匯入 LUT", enabled, "filter-import", onImport)
                TrayIcon("tune", "調色與濾鏡資訊", enabled, "filter-more", onMore)
                TrayIcon("close", "關閉濾鏡選擇", true, "filter-close", onClose)
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth().testTag("filter-groups")) {
                items(groups, key = { it.name }) { item ->
                    FilterChip(selected = item == group, onClick = { group = item }, enabled = enabled,
                        label = { Text(item.label) }, modifier = Modifier.heightIn(min = 48.dp).testTag("filter-group-${item.name}"))
                }
            }
            if (visible.isEmpty()) Box(Modifier.fillMaxWidth().heightIn(min = 72.dp), contentAlignment = Alignment.Center) {
                Text("尚無濾鏡", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = .65f))
            } else key(group, visible.map { it.id }) {
                SeriesPicker(visible, selectedId, enabled, compact, onSeries, onVariant)
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
private fun SeriesPicker(
    series: List<FilterSeries>, selectedId: String?, enabled: Boolean, compact: Boolean,
    onSeries: (FilterSeries) -> Unit, onVariant: (String?) -> Unit,
) {
    val ids = remember(series) { series.map { it.id } }
    val selectedSeries = remember(series, selectedId) { series.find { it.variants.any { variant -> variant.id == selectedId } }?.id }
    val cursor = remember(ids) { FilterBrowseCursor(ids, selectedSeries) }
    val pager = rememberPagerState(initialPage = cursor.initialPage) { series.size }
    val pick by rememberUpdatedState(onSeries)
    val canPick by rememberUpdatedState(enabled)
    val scope = rememberCoroutineScope()
    LaunchedEffect(pager, cursor) {
        pager.interactionSource.interactions.collect { if (it is DragInteraction.Start) cursor.beginGesture() }
    }
    LaunchedEffect(pager, cursor) {
        snapshotFlow { pager.isScrollInProgress to pager.settledPage }.collect { (scrolling, page) ->
            if (!scrolling) cursor.settledAt(page)?.let { id ->
                if (canPick) series.find { it.id == id }?.let(pick)
            }
        }
    }
    LaunchedEffect(selectedSeries) {
        val page = ids.indexOf(selectedSeries)
        if (page >= 0 && pager.currentPage != page && !pager.isScrollInProgress) pager.animateScrollToPage(page)
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cardWidth = (maxWidth * .44f).coerceIn(136.dp, 184.dp).coerceAtMost(maxWidth)
        HorizontalPager(pager, pageSize = PageSize.Fixed(cardWidth), pageSpacing = 8.dp,
            contentPadding = PaddingValues(horizontal = ((maxWidth - cardWidth) / 2).coerceAtLeast(0.dp), vertical = 4.dp),
            userScrollEnabled = enabled, key = { ids[it] }, modifier = Modifier.fillMaxWidth().testTag("filter-series-pager")) { page ->
            val film = series[page]
            SeriesCard(film, film.id == selectedSeries, enabled, compact) {
                pick(film)
                scope.launch { pager.animateScrollToPage(page) }
            }
        }
    }
    val browsed = series[pager.currentPage.coerceIn(series.indices)]
    if (browsed.variants.size > 1) LazyRow(contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().testTag("filter-variants")) {
        items(browsed.variants, key = { it.id }) { entry ->
            FilterChip(selected = entry.id == selectedId, onClick = { onVariant(entry.id) }, enabled = enabled,
                label = { Text(browsed.variantLabel(entry)) }, modifier = Modifier.heightIn(min = 48.dp).testTag("filter-${entry.id}"))
        }
    }
}

@Composable
private fun SeriesCard(series: FilterSeries, chosen: Boolean, enabled: Boolean, compact: Boolean, onClick: () -> Unit) {
    val accent = when (series.group) {
        FilterGroup.KODAK -> Color(0xFFD4A955)
        FilterGroup.FUJI -> Color(0xFF8CAA8D)
        FilterGroup.GRAIN -> Color(0xFFC29C85)
        else -> Color(0xFFA6A8BF)
    }
    Surface(onClick, enabled = enabled, shape = RoundedCornerShape(10.dp), color = Color(0xFF242529),
        border = BorderStroke(if (chosen) 2.dp else 1.dp, if (chosen) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = .16f)),
        modifier = Modifier.fillMaxWidth().heightIn(min = if (compact) 68.dp else 88.dp).testTag("filter-series-${series.id}")
            .semantics { selected = chosen; role = Role.RadioButton; contentDescription = "${series.group.label} ${series.title}，${series.variants.size} 款" }) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            // Film packaging, not a thumbnail: the actual look is shown by the viewfinder.
            Canvas(Modifier.width(3.dp).height(if (compact) 36.dp else 48.dp)) { drawRect(accent) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(series.group.label, Modifier.weight(1f), color = accent, style = MaterialTheme.typography.labelSmall)
                    if (chosen) Text("✓", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                }
                Text(series.title, color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!compact && series.variants.size > 1) Text("${series.variants.size} 款", color = Color.White.copy(alpha = .5f),
                    style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
