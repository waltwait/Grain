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
        private val decimalDivisors = doubleArrayOf(1.0, 10.0, 100.0, 1000.0, 10000.0, 100000.0, 1000000.0, 10000000.0, 100000000.0, 1000000000.0)
        private fun whitespace(c: Char) = c == ' ' || c in '\t'..'\r'

        /** Short decimal tokens avoid String/Float parser allocations, retaining exact float rounding. */
        private fun number(line: String, start: Int, end: Int): Float {
            var position = start
            val negative = line[position] == '-'
            if (negative || line[position] == '+') position++
            var significand = 0
            var digits = 0
            var fraction = 0
            var decimal = false
            while (position < end) {
                val c = line[position++]
                if (c in '0'..'9' && digits < 9) {
                    significand = significand * 10 + (c - '0')
                    digits++
                    if (decimal) fraction++
                } else if (c == '.' && !decimal) decimal = true
                else return line.substring(start, end).toFloat()
            }
            if (digits == 0) return line.substring(start, end).toFloat()
            // Both integer operands are exactly represented in Double. Division has at most
            // half a double ULP of error; near any float midpoint use the platform parser.
            val magnitude = significand.toDouble() / decimalDivisors[fraction]
            val candidate = if (negative) -magnitude else magnitude
            val rounded = candidate.toFloat()
            val roundedDouble = rounded.toDouble()
            if (roundedDouble != candidate) {
                val neighbor = if (roundedDouble < candidate) Math.nextUp(rounded) else Math.nextDown(rounded)
                val midpoint = (roundedDouble + neighbor.toDouble()) / 2
                if (kotlin.math.abs(candidate - midpoint) <= Math.ulp(candidate)) return line.substring(start, end).toFloat()
            }
            return rounded
        }

        private fun appendRgb(line: String, target: FloatArray, cursor: Int): Int {
            require(cursor + 3 <= target.size) { "LUT 資料筆數或格式錯誤" }
            var position = 0
            for (channel in 0..2) {
                while (position < line.length && whitespace(line[position])) position++
                val start = position
                while (position < line.length && !whitespace(line[position])) position++
                require(position > start) { "LUT 資料筆數或格式錯誤" }
                val value = number(line, start, position)
                require(value.isFinite()) { "LUT 包含無效數值" }
                target[cursor + channel] = value
            }
            while (position < line.length && whitespace(line[position])) position++
            require(position == line.length) { "LUT 資料筆數或格式錯誤" }
            return cursor + 3
        }

        // Match CUBE's ASCII whitespace without constructing a matcher/list pipeline per row.
        private fun tokens(line: String): List<String> {
            val parts = ArrayList<String>(4)
            var start = 0
            for (i in line.indices) {
                val c = line[i]
                if (c == ' ' || c in '\t'..'\r') {
                    if (i > start) parts.add(line.substring(start, i))
                    start = i + 1
                }
            }
            if (start < line.length) parts.add(line.substring(start))
            return parts
        }

        fun parse(reader: Reader, fallbackTitle: String = "匯入 LUT"): CubeLut {
            var title = fallbackTitle.take(80)
            var size = 0
            var data: FloatArray? = null
            var cursor = 0
            var characters = 0
            var min = floatArrayOf(0f, 0f, 0f)
            var max = floatArrayOf(1f, 1f, 1f)
            var encoding = LutEncoding.SRGB
            reader.use { source ->
                val lines = BoundedCubeLines(source)
                while (true) {
                    val raw = lines.next() ?: break
                    characters += raw.length + 1
                    require(characters <= MAX_FILE_BYTES) { "LUT 檔案過大（上限 24 MB）" }
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
                        if (line[0] in '0'..'9' || line[0] == '+' || line[0] == '-' || line[0] == '.') {
                            cursor = appendRgb(line, requireNotNull(data) { "找不到 LUT_3D_SIZE" }, cursor)
                        } else {
                            val parts = tokens(line)
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
                                else -> cursor = appendRgb(line, requireNotNull(data) { "找不到 LUT_3D_SIZE" }, cursor)
                            }
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

/** Bounded lines, including CRLF split across reads; never builds an arbitrarily long line. */
private class BoundedCubeLines(private val source: Reader) {
    private val buffer = CharArray(8192)
    private var position = 0
    private var limit = 0
    private var skipLf = false

    fun next(): String? {
        if (limit < 0) return null
        var continuation: StringBuilder? = null
        var length = 0
        while (true) {
            if (position == limit) {
                limit = source.read(buffer)
                position = 0
                if (limit < 0) return continuation?.toString()
                check(limit > 0) { "無法讀取 LUT 資料" }
            }
            if (skipLf) {
                skipLf = false
                if (buffer[position] == '\n') { position++; continue }
            }
            val start = position
            while (position < limit && buffer[position] != '\n' && buffer[position] != '\r') position++
            val count = position - start
            length += count
            require(length < 4096) { "LUT 包含過長的資料行" }
            if (position < limit) {
                skipLf = buffer[position++] == '\r'
                return continuation?.append(buffer, start, count)?.toString() ?: String(buffer, start, count)
            }
            if (count > 0) {
                val accumulated = continuation ?: StringBuilder(maxOf(80, length)).also { continuation = it }
                accumulated.append(buffer, start, count)
            }
        }
    }
}
