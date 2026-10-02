package tw.luma.camera.lut

import java.io.InputStream

object KodakLutLibrary {
    private val definitions = listOf(
        Triple("portra-160", "Portra 160", "柔和人像與日常"),
        Triple("portra-400", "Portra 400", "自然色彩與街拍"),
        Triple("portra-800", "Portra 800", "較濃郁的負片色調"),
        Triple("ektachrome-100-vs", "Ektachrome 100 VS", "鮮明正片色彩與風景"),
        Triple("tri-x-400", "Tri-X 400", "黑白街拍與灰階層次"),
    )

    fun load(openAsset: (String) -> InputStream): List<FilmLook> = definitions.map { (slug, title, description) ->
        val lut = PackedLut.read(openAsset("luts/kodak/$slug.glut"), "Kodak $title")
        require(lut.size == 64) { "Kodak 模擬 LUT 格點錯誤" }
        FilmLook("kodak-$slug", lut, "社群 Kodak 模擬 · $description\nPat David · CC BY-SA 4.0",
            "https://github.com/NatronGitHub/clut", "https://creativecommons.org/licenses/by-sa/4.0/")
    }
}
