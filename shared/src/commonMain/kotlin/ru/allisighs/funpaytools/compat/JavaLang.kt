package ru.allisighs.funpaytools

import kotlin.math.PI
import kotlin.math.pow
import kotlin.reflect.KClass
import kotlin.time.Clock
import kotlin.time.TimeSource

object System {
    private val start = TimeSource.Monotonic.markNow()
    fun currentTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()
    fun nanoTime(): Long = start.elapsedNow().inWholeNanoseconds
    fun lineSeparator(): String = "\n"
    fun getProperty(key: String): String? = null
    fun getProperty(key: String, def: String): String = def
    fun gc() {}
    fun exit(code: Int) {}
    val out = PrintStreamCompat()
    val err = PrintStreamCompat()
    fun arraycopy(src: ByteArray, srcPos: Int, dest: ByteArray, destPos: Int, length: Int) {
        src.copyInto(dest, destPos, srcPos, srcPos + length)
    }
}

class PrintStreamCompat {
    fun println(s: Any?) = kotlin.io.println(s)
    fun print(s: Any?) = kotlin.io.print(s)
}

object Math {
    const val PI: Double = kotlin.math.PI
    const val E: Double = kotlin.math.E
    fun toRadians(d: Double): Double = d / 180.0 * PI
    fun toDegrees(r: Double): Double = r * 180.0 / PI
    fun sin(x: Double) = kotlin.math.sin(x)
    fun cos(x: Double) = kotlin.math.cos(x)
    fun tan(x: Double) = kotlin.math.tan(x)
    fun atan(x: Double) = kotlin.math.atan(x)
    fun atan2(y: Double, x: Double) = kotlin.math.atan2(y, x)
    fun sqrt(x: Double) = kotlin.math.sqrt(x)
    fun pow(a: Double, b: Double) = a.pow(b)
    fun abs(x: Double) = kotlin.math.abs(x)
    fun abs(x: Float) = kotlin.math.abs(x)
    fun abs(x: Int) = kotlin.math.abs(x)
    fun abs(x: Long) = kotlin.math.abs(x)
    fun max(a: Int, b: Int) = kotlin.math.max(a, b)
    fun max(a: Long, b: Long) = kotlin.math.max(a, b)
    fun max(a: Double, b: Double) = kotlin.math.max(a, b)
    fun max(a: Float, b: Float) = kotlin.math.max(a, b)
    fun min(a: Int, b: Int) = kotlin.math.min(a, b)
    fun min(a: Long, b: Long) = kotlin.math.min(a, b)
    fun min(a: Double, b: Double) = kotlin.math.min(a, b)
    fun min(a: Float, b: Float) = kotlin.math.min(a, b)
    fun round(x: Double): Long = kotlin.math.round(x).toLong()
    fun round(x: Float): Int = kotlin.math.round(x).toInt()
    fun floor(x: Double) = kotlin.math.floor(x)
    fun ceil(x: Double) = kotlin.math.ceil(x)
    fun log(x: Double) = kotlin.math.ln(x)
    fun log10(x: Double) = kotlin.math.log10(x)
    fun exp(x: Double) = kotlin.math.exp(x)
    fun hypot(x: Double, y: Double) = kotlin.math.hypot(x, y)
    fun random(): Double = kotlin.random.Random.nextDouble()
    fun signum(x: Double) = kotlin.math.sign(x)
    fun floorMod(a: Int, b: Int): Int = ((a % b) + b) % b
    fun floorMod(a: Long, b: Long): Long = ((a % b) + b) % b
}

object Thread {
    fun sleep(ms: Long) = platformSleep(ms)
    fun currentThread(): ThreadInfo = ThreadInfo
}

object ThreadInfo {
    val name: String get() = "thread"
    fun interrupt() {}
    val isInterrupted: Boolean get() = false
}

expect fun platformSleep(ms: Long)

expect inline fun <R> synchronized(lock: Any, block: () -> R): R

typealias Volatile = kotlin.concurrent.Volatile

annotation class JavascriptInterface
annotation class SuppressLint(vararg val value: String)
annotation class Keep
annotation class RequiresApi(val value: Int = 0, val api: Int = 0)
annotation class TargetApi(val value: Int)
annotation class Preview(val showBackground: Boolean = false, val name: String = "", val widthDp: Int = -1, val heightDp: Int = -1, val backgroundColor: Long = 0)
annotation class WorkerThread
annotation class MainThread
annotation class UiThread

typealias SerializedName = kotlinx.serialization.SerialName

typealias IOException = okio.IOException
typealias FileNotFoundException = okio.FileNotFoundException
open class SocketTimeoutException(message: String? = null) : okio.IOException(message)
open class UnknownHostException(message: String? = null) : okio.IOException(message)
open class ConnectException(message: String? = null) : okio.IOException(message)
open class SSLException(message: String? = null) : okio.IOException(message)
open class InterruptedException(message: String? = null) : Exception(message)
open class JSONException(message: String? = null) : RuntimeException(message)
open class SecurityException(message: String? = null) : RuntimeException(message)
open class NoSuchFieldException(message: String? = null) : Exception(message)
open class ClassNotFoundException(message: String? = null) : Exception(message)
typealias CancellationExceptionCompat = kotlinx.coroutines.CancellationException

val <T : Any> KClass<T>.java: KClass<T> get() = this
val Any.javaClass: KClass<out Any> get() = this::class

object Charsets {
    val UTF_8 = Charset("UTF-8")
    val US_ASCII = Charset("US-ASCII")
    val ISO_8859_1 = Charset("ISO-8859-1")
}

class Charset(val name: String) {
    companion object {
        fun forName(n: String) = Charset(n)
        fun defaultCharset() = Charsets.UTF_8
    }
    override fun toString() = name
}

object StandardCharsets {
    val UTF_8 = Charsets.UTF_8
    val US_ASCII = Charsets.US_ASCII
    val ISO_8859_1 = Charsets.ISO_8859_1
}

fun String.toByteArray(charset: Charset): ByteArray = encodeToByteArray()
fun String.toByteArray(charset: String): ByteArray = encodeToByteArray()
fun String.getBytes(): ByteArray = encodeToByteArray()
fun String(bytes: ByteArray, charset: Charset): String = bytes.decodeToString()
fun String(bytes: ByteArray, charset: String): String = bytes.decodeToString()
fun String(bytes: ByteArray): String = bytes.decodeToString()
fun String(bytes: ByteArray, offset: Int, length: Int, charset: Charset = Charsets.UTF_8): String =
    bytes.decodeToString(offset, offset + length)
fun String(chars: CharArray): String = chars.concatToString()
fun ByteArray.toString(charset: Charset): String = decodeToString()

object Integer {
    const val MAX_VALUE = Int.MAX_VALUE
    const val MIN_VALUE = Int.MIN_VALUE
    fun parseInt(s: String): Int = s.trim().toInt()
    fun parseInt(s: String, radix: Int): Int = s.trim().toInt(radix)
    fun valueOf(s: String): Int = s.trim().toInt()
    fun toHexString(i: Int): String = i.toUInt().toString(16)
    fun toString(i: Int): String = i.toString()
    fun compare(a: Int, b: Int) = a.compareTo(b)
}

object JLong {
    fun parseLong(s: String): Long = s.trim().toLong()
}

object JDouble {
    fun parseDouble(s: String): Double = s.trim().toDouble()
}

object Character {
    fun isDigit(c: Char) = c.isDigit()
    fun isLetter(c: Char) = c.isLetter()
    fun isLetterOrDigit(c: Char) = c.isLetterOrDigit()
    fun isWhitespace(c: Char) = c.isWhitespace()
    fun isUpperCase(c: Char) = c.isUpperCase()
    fun isLowerCase(c: Char) = c.isLowerCase()
    fun toUpperCase(c: Char) = c.uppercaseChar()
    fun toLowerCase(c: Char) = c.lowercaseChar()
    fun isSurrogate(c: Char) = c.isSurrogate()
    fun isHighSurrogate(c: Char) = c.isHighSurrogate()
    fun charCount(cp: Int) = if (cp >= 0x10000) 2 else 1
    fun toChars(cp: Int): CharArray = codePointToString(cp).toCharArray()
}

fun codePointToString(cp: Int): String {
    return if (cp < 0x10000) cp.toChar().toString() else {
        val v = cp - 0x10000
        val hi = ((v shr 10) + 0xD800).toChar()
        val lo = ((v and 0x3FF) + 0xDC00).toChar()
        "$hi$lo"
    }
}

fun String.codePointAt(index: Int): Int {
    val c = this[index]
    if (c.isHighSurrogate() && index + 1 < length) {
        val d = this[index + 1]
        if (d.isLowSurrogate()) return ((c.code - 0xD800) shl 10) + (d.code - 0xDC00) + 0x10000
    }
    return c.code
}

fun String.codePointsList(): List<Int> {
    val out = ArrayList<Int>()
    var i = 0
    while (i < length) {
        val cp = codePointAt(i)
        out.add(cp)
        i += if (cp >= 0x10000) 2 else 1
    }
    return out
}

fun String.codePointCount(begin: Int, end: Int): Int = substring(begin, end).codePointsList().size

fun String.offsetByCodePoints(index: Int, offset: Int): Int {
    var i = index
    var n = 0
    while (n < offset && i < length) {
        val cp = codePointAt(i)
        i += if (cp >= 0x10000) 2 else 1
        n++
    }
    return i
}

fun Throwable.printStackTraceCompat() = printStackTrace()

fun Any.hashCodeCompat(): Int = hashCode()
