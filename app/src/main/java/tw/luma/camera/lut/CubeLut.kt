package tw.luma.camera.lut

import java.io.Reader
import kotlin.math.floor
import kotlin.math.pow

enum class LutEncoding(val label: String) {
    SRGB("sRGB / SDR"), FLOG("F-Log（近似適配）"),
    FLOG2("F-Log2（近似適配）"), FLOG2C("F-Log2C（近似適配）")
}

/** CUBE order: red changes fastest, then green, then blue. */
class CubeLut(
    val title: String,
    val size: Int,
    val values: FloatArray,
    val domainMin: FloatArray = floatArrayOf(0f, 0f, 0f),
    val domainMax: FloatArray = floatArrayOf(1f, 1f, 1f),
    val suggestedEncoding: LutEncoding = LutEncoding.SRGB,
) {
    init {
        require(size in 2..65 && values.size == size * size * size * 3)
        require(values.all { it.isFinite() })
        require(domainMin.size == 3 && domainMax.size == 3)
        require((0..2).all { domainMin[it].isFinite() && domainMax[it].isFinite() && domainMax[it] > domainMin[it] })
    }

    fun sample(r: Float, g: Float, b: Float): FloatArray {
        val rgb = floatArrayOf(r, g, b)
        val pos = FloatArray(3) { ((rgb[it] - domainMin[it]) / (domainMax[it] - domainMin[it])).coerceIn(0f, 1f) * (size - 1) }
        val lo = IntArray(3) { floor(pos[it]).toInt() }
        val hi = IntArray(3) { (lo[it] + 1).coerceAtMost(size - 1) }
        val t = FloatArray(3) { pos[it] - lo[it] }
        val out = FloatArray(3)
        for (z in 0..1) for (y in 0..1) for (x in 0..1) {
            val index = ((if (z == 0) lo[2] else hi[2]) * size * size +
                (if (y == 0) lo[1] else hi[1]) * size + (if (x == 0) lo[0] else hi[0])) * 3
            val w = (if (x == 0) 1 - t[0] else t[0]) * (if (y == 0) 1 - t[1] else t[1]) * (if (z == 0) 1 - t[2] else t[2])
            for (c in 0..2) out[c] += values[index + c] * w
        }
        return out
    }

    companion object {
        const val MAX_FILE_BYTES = 24 * 1024 * 1024

        fun parse(reader: Reader, fallbackTitle: String = "匯入 LUT"): CubeLut {
            var title = fallbackTitle.take(80)
            var size = 0
            var data: FloatArray? = null
            var cursor = 0
            var characters = 0
            var min = floatArrayOf(0f, 0f, 0f)
            var max = floatArrayOf(1f, 1f, 1f)
            var encoding = LutEncoding.SRGB
            reader.buffered().forEachLine { raw ->
                characters += raw.length + 1
                require(characters <= MAX_FILE_BYTES) { "LUT 檔案過大（上限 24 MB）" }
                require(raw.length < 4096) { "LUT 包含過長的資料行" }
                // Only known source metadata is inferred; arbitrary filenames are not trusted.
                if (raw.trimStart().startsWith("#Gamma:", ignoreCase = true)) {
                    val gamma = raw.substringAfter(':').trim().substringBefore(" to ", raw.substringAfter(':').trim())
                    encoding = when (gamma.uppercase()) {
                        "F-LOG2C", "F-LOG2 C" -> LutEncoding.FLOG2C
                        "F-LOG2" -> LutEncoding.FLOG2
                        "F-LOG" -> LutEncoding.FLOG
                        else -> LutEncoding.SRGB
                    }
                }
                val line = raw.substringBefore('#').trim().removePrefix("\uFEFF")
                if (line.isNotEmpty()) {
                    val parts = line.split(Regex("\\s+"))
                    fun triple(): FloatArray {
                        require(parts.size == 4) { "DOMAIN 格式錯誤" }
                        return FloatArray(3) { parts[it + 1].toFloat().also { v -> require(v.isFinite()) } }
                    }
                    when (parts[0]) {
                        "TITLE" -> title = line.removePrefix("TITLE").trim().trim('"').take(80)
                        "LUT_3D_SIZE" -> {
                            require(data == null && parts.size == 2) { "重複或無效的 LUT 維度" }
                            size = parts[1].toInt()
                            require(size in 2..65) { "目前支援 2–65 格點的 3D LUT" }
                            data = FloatArray(size * size * size * 3)
                        }
                        "DOMAIN_MIN" -> min = triple()
                        "DOMAIN_MAX" -> max = triple()
                        "LUT_1D_SIZE", "LUT_1D_INPUT_RANGE", "LUT_3D_INPUT_RANGE" -> error("目前不支援 1D／shaper LUT，請使用純 3D .cube")
                        else -> {
                            val target = requireNotNull(data) { "找不到 LUT_3D_SIZE" }
                            require(parts.size == 3 && cursor + 3 <= target.size) { "LUT 資料筆數或格式錯誤" }
                            for (part in parts) target[cursor++] = part.toFloat().also { require(it.isFinite()) { "LUT 包含無效數值" } }
                        }
                    }
                }
            }
            val values = requireNotNull(data) { "不是有效的 3D CUBE 檔案" }
            require(cursor == values.size) { "LUT 資料不完整：應有 ${size * size * size} 筆 RGB" }
            return CubeLut(title.ifBlank { fallbackTitle }, size, values, min, max, encoding)
        }

        fun generate(title: String, size: Int = 17, transform: (Float, Float, Float) -> FloatArray): CubeLut {
            val values = FloatArray(size * size * size * 3)
            for (b in 0 until size) for (g in 0 until size) for (r in 0 until size) {
                val rgb = transform(r.toFloat() / (size - 1), g.toFloat() / (size - 1), b.toFloat() / (size - 1))
                val i = (b * size * size + g * size + r) * 3
                for (c in 0..2) values[i + c] = rgb[c]
            }
            return CubeLut(title, size, values)
        }

        fun builtIns(): List<CubeLut> = listOf(
            generate("暖日") { r, g, b -> floatArrayOf((r * 1.035f + .018f).coerceIn(0f, 1f), (g * .995f + .008f).coerceIn(0f, 1f), (b * .94f).coerceIn(0f, 1f)) },
            generate("柔霧") { r, g, b -> floatArrayOf(.055f + .90f * r.pow(.94f), .052f + .90f * g.pow(.96f), .06f + .88f * b) },
            generate("銀影") { r, g, b -> val l = (.2126f * r + .7152f * g + .0722f * b); floatArrayOf(l, l, l) },
        )
    }
}
