package tw.luma.camera.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import tw.luma.camera.CameraUiState
import tw.luma.camera.LutEntry
import tw.luma.camera.gl.FilterSettings
import tw.luma.camera.lut.CubeLut
import tw.luma.camera.lut.FilterSwitching

@Preview(name = "Bottom film tray · Portra", widthDp = 360, heightDp = 620)
@Composable private fun PortraitFilterTrayPreview() = FilterTrayPreview()

@Preview(name = "Film tray · 320 dp", widthDp = 320, heightDp = 480)
@Composable private fun SmallFilterTrayPreview() = FilterTrayPreview()

@Preview(name = "Film tray · landscape", widthDp = 640, heightDp = 300)
@Composable private fun LandscapeFilterTrayPreview() = FilterTrayPreview()

@Preview(name = "Film tray · large type", widthDp = 360, heightDp = 640, fontScale = 1.5f)
@Composable private fun LargeTypeFilterTrayPreview() = FilterTrayPreview()

// Layout fixtures use identity LUTs, not camera output or measured film previews.
@Composable private fun FilterTrayPreview() {
    val entries = remember {
        listOf("kodak-portra-160" to "Kodak Portra 160", "kodak-portra-400" to "Kodak Portra 400",
            "kodak-portra-800" to "Kodak Portra 800", "kodak-ektachrome-100-vs" to "Kodak Ektachrome 100 VS",
            "grain-daylight" to "Daylight", "fuji-CLASSIC-CHROME" to "富士 CLASSIC CHROME",
            "fuji-CLASSIC-Neg." to "富士 CLASSIC Neg.", "fuji-ETERNA" to "富士 ETERNA",
            "fuji-ETERNA-BB" to "富士 ETERNA Bleach Bypass").map { (id, title) ->
            LutEntry(id, CubeLut.generate(title, 2) { r, g, b -> floatArrayOf(r, g, b) })
        }
    }
    var state by remember { mutableStateOf(CameraUiState(luts = entries, selectedLut = entries[1].id,
        filter = FilterSettings(lut = entries[1].lut, strength = .75f))) }
    val switch = remember { FilterSwitching() }
    val select: (String?) -> Unit = { id ->
        val next = entries.find { it.id == id }
        state = state.copy(selectedLut = next?.id, filter = switch.select(state.filter, state.selectedLut, next))
    }
    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFFFD45B), onPrimary = Color(0xFF231C08))) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF464D40), Color(0xFF1B2120))))) {
            NativeCameraToolbar(state, Modifier.align(Alignment.TopCenter), panel = {}) { Text("00:00") }
            FilterTray(entries, state.selectedLut, state.filter.strength, true, (maxHeight - 56.dp).coerceAtLeast(48.dp),
                compact = maxHeight < 360.dp, onSelect = select,
                onStrength = { state = state.copy(filter = state.filter.copy(strength = it)) }, onStrengthFinished = {},
                onImport = {}, onMore = {}, onClose = {}, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}
