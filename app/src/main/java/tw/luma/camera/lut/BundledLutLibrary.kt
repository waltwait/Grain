package tw.luma.camera.lut

import android.content.res.AssetManager

/** Optional local debug assets. Official CUBE files are kept byte-for-byte unchanged. */
object BundledLutLibrary {
    private const val DIRECTORY = "luts/fujifilm"
    private val names = listOf(
        "CLASSIC-CHROME" to "CLASSIC CHROME",
        "CLASSIC-Neg." to "CLASSIC Neg.",
        "REALA-ACE" to "REALA ACE",
        "PROVIA" to "PROVIA",
        "Velvia" to "Velvia",
        "ASTIA" to "ASTIA",
        "PRO-Neg.Std" to "PRO Neg. Std",
        "ETERNA" to "ETERNA",
        "ETERNA-BB" to "ETERNA Bleach Bypass",
        "ACROS" to "ACROS",
    )

    fun load(assets: AssetManager): List<Pair<String, CubeLut>> {
        val available = assets.list(DIRECTORY)?.toSet().orEmpty()
        if (available.isEmpty()) return emptyList()
        return names.map { (name, label) ->
            val file = "FLog2_to_${name}_33grid_V.1.00.cube"
            require(file in available) { "內建富士 LUT 不完整：$label" }
            val lut = assets.open("$DIRECTORY/$file").bufferedReader().use { CubeLut.parse(it, "富士 $label") }
            require(lut.size == 33 && lut.suggestedEncoding == LutEncoding.FLOG2) { "內建富士 LUT 色彩格式不符：$label" }
            "fuji-$name" to lut
        }
    }
}
