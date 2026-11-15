package ru.allisighs.funpaytools

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

fun String.format(vararg args: Any?): String = javaFormat(this, args)
fun String.format(locale: Locale?, vararg args: Any?): String = javaFormat(this, args)
fun String.Companion.format(fmt: String, vararg args: Any?): String = javaFormat(fmt, args)
fun String.Companion.format(locale: Locale?, fmt: String, vararg args: Any?): String = javaFormat(fmt, args)

private val SPEC = Regex("%(\\d+\\$)?([-#+ 0,(]*)?(\\d+)?(\\.\\d+)?([a-zA-Z%])")

fun javaFormat(fmt: String, args: Array<out Any?>): String {
    val sb = StringBuilder()
    var last = 0
    var argIdx = 0
    for (m in SPEC.findAll(fmt)) {
        sb.append(fmt, last, m.range.first)
        last = m.range.last + 1
        val conv = m.groupValues[5]
        if (conv == "%") { sb.append('%'); continue }
        if (conv == "n") { sb.append('\n'); continue }
        val explicit = m.groupValues[1]
        val arg = if (explicit.isNotEmpty()) args.getOrNull(explicit.dropLast(1).toInt() - 1) else args.getOrNull(argIdx++)
        val flags = m.groupValues[2]
        val width = m.groupValues[3].toIntOrNull() ?: 0
        val precision = m.groupValues[4].drop(1).toIntOrNull()
        var body = when (conv) {
            "d" -> {
                val v = when (arg) { is Number -> arg.toLong(); is Char -> arg.code.toLong(); else -> arg.toString().toLongOrNull() ?: 0L }
                var s = abs(v).toString()
                if (flags.contains(',')) s = groupThousands(s, ' ')
                when {
                    v < 0 -> "-$s"
                    flags.contains('+') -> "+$s"
                    flags.contains(' ') -> " $s"
                    else -> s
                }
            }
            "f", "e", "g" -> {
                val v = when (arg) { is Number -> arg.toDouble(); else -> arg.toString().toDoubleOrNull() ?: 0.0 }
                var s = formatFixed(abs(v), precision ?: 6)
                if (flags.contains(',')) {
                    val dot = s.indexOf('.')
                    val ip = if (dot >= 0) s.substring(0, dot) else s
                    val fp = if (dot >= 0) s.substring(dot) else ""
                    s = groupThousands(ip, ' ') + fp
                }
                val neg = v < 0 && s.any { it in '1'..'9' }
                when {
                    neg -> "-$s"
                    flags.contains('+') -> "+$s"
                    else -> s
                }
            }
            "s", "S" -> {
                val s = arg?.toString() ?: "null"
                val r = if (precision != null && s.length > precision) s.substring(0, precision) else s
                if (conv == "S") r.uppercase() else r
            }
            "x", "X" -> {
                val s = when (arg) {
                    is Byte -> (arg.toInt() and 0xFF).toString(16)
                    is Short -> (arg.toInt() and 0xFFFF).toString(16)
                    is Int -> arg.toUInt().toString(16)
                    is Long -> arg.toULong().toString(16)
                    is Number -> arg.toLong().toString(16)
                    else -> arg.toString()
                }
                if (conv == "X") s.uppercase() else s
            }
            "o" -> (arg as? Number)?.toLong()?.toString(8) ?: arg.toString()
            "c" -> when (arg) { is Char -> arg.toString(); is Number -> arg.toInt().toChar().toString(); else -> arg.toString() }
            "b", "B" -> (arg != null && arg != false).toString()
            else -> arg.toString()
        }
        if (body.length < width) {
            body = when {
                flags.contains('-') -> body.padEnd(width)
                flags.contains('0') && conv in listOf("d", "f", "x", "X", "e", "g") -> {
                    val sign = if (body.startsWith("-") || body.startsWith("+")) body.substring(0, 1) else ""
                    sign + body.substring(sign.length).padStart(width - sign.length, '0')
                }
                else -> body.padStart(width)
            }
        }
        sb.append(body)
    }
    sb.append(fmt, last, fmt.length)
    return sb.toString()
}

private fun groupThousands(digits: String, sep: Char): String {
    val sb = StringBuilder()
    val n = digits.length
    for (i in 0 until n) {
        sb.append(digits[i])
        val rem = n - i - 1
        if (rem > 0 && rem % 3 == 0) sb.append(sep)
    }
    return sb.toString()
}

fun formatFixed(v: Double, precision: Int): String {
    if (v.isNaN()) return "NaN"
    if (v.isInfinite()) return "Infinity"
    if (precision <= 0) return v.roundHalfUp().toString()
    val factor = 10.0.pow(precision)
    val scaled = (v * factor)
    val rounded = if (scaled < 9.0E18) {
        val r = scaled.roundToLong()
        // correct for binary representation error (e.g. 1.005)
        val alt = (v * factor + 1e-9).let { kotlin.math.floor(it + 0.5).toLong() }
        if (alt != r && kotlin.math.abs(alt - scaled) <= 0.5 + 1e-7) alt else r
    } else return v.toString()
    val s = rounded.toString().padStart(precision + 1, '0')
    val ip = s.substring(0, s.length - precision)
    val fp = s.substring(s.length - precision)
    return "$ip.$fp"
}

private fun Double.roundHalfUp(): Long = kotlin.math.floor(this + 0.5).toLong()

class DecimalFormat(private val pattern: String, symbols: Any? = null) {
    fun format(v: Number): String {
        val d = v.toDouble()
        val dot = pattern.indexOf('.')
        val frac = if (dot >= 0) pattern.substring(dot + 1) else ""
        val minFrac = frac.count { it == '0' }
        val maxFrac = frac.count { it == '0' || it == '#' }
        val grouping = pattern.contains(',')
        var s = formatFixed(kotlin.math.abs(d), maxFrac)
        if (maxFrac > minFrac && s.contains('.')) {
            var end = s.length
            while (end > 0 && s[end - 1] == '0' && (end - s.indexOf('.') - 1) > minFrac) end--
            s = s.substring(0, end)
            if (s.endsWith('.')) s = s.dropLast(1)
        }
        if (grouping) {
            val p = s.indexOf('.')
            val ip = if (p >= 0) s.substring(0, p) else s
            val fp = if (p >= 0) s.substring(p) else ""
            s = groupThousands(ip, ' ') + fp
        }
        return if (d < 0) "-$s" else s
    }
    var roundingMode: Any? = null
    var isGroupingUsed: Boolean = true
}

class DecimalFormatSymbols(locale: Locale? = null) {
    var decimalSeparator: Char = '.'
    var groupingSeparator: Char = ' '
}
