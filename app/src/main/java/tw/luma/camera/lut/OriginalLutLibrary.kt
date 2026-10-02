package tw.luma.camera.lut

import java.io.InputStream
data class FilmLook(val id: String, val lut: CubeLut, val description: String,
    val sourceUrl: String? = null, val licenseUrl: String? = null)

object OriginalLutLibrary {
    private data class Definition(val name: String, val title: String, val description: String)
    private val definitions = listOf(
        Definition("daylight", "Daylight", "自然日系 · 柔和對比，適合日常與風景"),
        Definition("warm-portrait", "Warm Portrait", "溫暖人像 · 柔和亮部，保留自然膚色"),
        Definition("chrome-street", "Chrome Street", "冷調街拍 · 低飽和、橄欖綠與冷色陰影"),
        Definition("golden-hour", "Golden Hour", "金色時刻 · 溫暖陽光與柔和暗部"),
        Definition("night-cinema", "Night Cinema", "電影夜色 · 藍綠陰影，收斂鮮豔霓虹"),
        Definition("silver", "Silver", "黑白底片 · 清晰對比，保留灰階層次"),
    )

    /** Prebaked sRGB -> sRGB tables: no text parsing or grading math on the camera path. */
    fun load(openAsset: (String) -> InputStream): List<FilmLook> = definitions.map { definition ->
        val lut = PackedLut.read(openAsset("luts/grain/${definition.name}.glut"), definition.title)
        require(lut.size == 33) { "Grain Originals 格點錯誤" }
        FilmLook("grain-${definition.name}", lut, "Grain Originals · ${definition.description}")
    }
}
