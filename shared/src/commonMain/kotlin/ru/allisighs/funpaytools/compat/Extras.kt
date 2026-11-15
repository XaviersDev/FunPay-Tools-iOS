package ru.allisighs.funpaytools

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader

// ---------- String helpers that exist only on the JVM ----------
fun String.lowercase(locale: Locale): String = lowercase()
fun String.uppercase(locale: Locale): String = uppercase()
fun Char.lowercase(locale: Locale): String = lowercase()
fun Char.uppercase(locale: Locale): String = uppercase()
fun String.toLowerCase(locale: Locale): String = lowercase()
fun String.toUpperCase(locale: Locale): String = uppercase()
fun String.capitalize(locale: Locale): String = replaceFirstChar { it.uppercase() }
fun String.decapitalize(locale: Locale): String = replaceFirstChar { it.lowercase() }
fun String.toByteArray(): ByteArray = encodeToByteArray()
fun String.intern(): String = this
fun String.regionMatches(thisOffset: Int, other: String, otherOffset: Int, length: Int, ignoreCase: Boolean = false): Boolean =
    regionMatches(thisOffset, other, otherOffset, length, ignoreCase)
fun StringBuilder.appendCodePoint(cp: Int): StringBuilder { append(codePointToString(cp)); return this }

// ---------- java.net.URI ----------
class URI(private val spec: String) {
    private val u = Uri.parse(spec)
    init { if (spec.contains(' ')) throw IllegalArgumentException("Illegal character in URI: $spec") }
    val host: String? get() = u.host
    val scheme: String? get() = u.scheme
    val path: String? get() = u.path
    val rawPath: String? get() = u.encodedPath
    val query: String? get() = u.query
    val rawQuery: String? get() = u.encodedQuery
    val port: Int get() = u.port
    val fragment: String? get() = u.fragment
    val authority: String? get() = u.authority
    fun toURL(): URL = URL(spec)
    fun resolve(s: String): URI = URI(resolveUrl(spec, s) ?: s)
    override fun toString() = spec
    companion object { fun create(s: String) = URI(s) }
}

// ---------- SHA-1 ----------
object Sha1 {
    fun digest(msg: ByteArray): ByteArray {
        var h0 = 0x67452301; var h1 = 0xEFCDAB89.toInt(); var h2 = 0x98BADCFE.toInt(); var h3 = 0x10325476; var h4 = 0xC3D2E1F0.toInt()
        val bitLen = msg.size.toLong() * 8
        val padLen = ((msg.size + 9 + 63) / 64) * 64
        val p = ByteArray(padLen)
        msg.copyInto(p); p[msg.size] = 0x80.toByte()
        for (i in 0 until 8) p[padLen - 1 - i] = (bitLen ushr (8 * i)).toByte()
        val w = IntArray(80)
        for (blk in 0 until padLen / 64) {
            for (i in 0 until 16) {
                val o = blk * 64 + i * 4
                w[i] = ((p[o].toInt() and 0xff) shl 24) or ((p[o + 1].toInt() and 0xff) shl 16) or ((p[o + 2].toInt() and 0xff) shl 8) or (p[o + 3].toInt() and 0xff)
            }
            for (i in 16 until 80) w[i] = (w[i - 3] xor w[i - 8] xor w[i - 14] xor w[i - 16]).rotateLeft(1)
            var a = h0; var b = h1; var c = h2; var d = h3; var e = h4
            for (i in 0 until 80) {
                val (f, k) = when (i / 20) {
                    0 -> ((b and c) or (b.inv() and d)) to 0x5A827999
                    1 -> (b xor c xor d) to 0x6ED9EBA1
                    2 -> ((b and c) or (b and d) or (c and d)) to 0x8F1BBCDC.toInt()
                    else -> (b xor c xor d) to 0xCA62C1D6.toInt()
                }
                val t = a.rotateLeft(5) + f + e + k + w[i]
                e = d; d = c; c = b.rotateLeft(30); b = a; a = t
            }
            h0 += a; h1 += b; h2 += c; h3 += d; h4 += e
        }
        val out = ByteArray(20)
        for ((i, v) in intArrayOf(h0, h1, h2, h3, h4).withIndex()) for (j in 0 until 4) out[i * 4 + j] = (v ushr (24 - 8 * j)).toByte()
        return out
    }
}

// ---------- coil (Android Context -> coil3 PlatformContext) ----------
val Context.imageLoader: ImageLoader get() = SingletonImageLoader.get(PlatformContext.INSTANCE)
val Context.platformContext: PlatformContext get() = PlatformContext.INSTANCE

// ---------- android.webkit.MimeTypeMap ----------
class MimeTypeMap private constructor() {
    private val map = mapOf(
        "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "png" to "image/png", "gif" to "image/gif", "webp" to "image/webp",
        "heic" to "image/heic", "pdf" to "application/pdf", "txt" to "text/plain", "json" to "application/json",
        "zip" to "application/zip", "mp4" to "video/mp4", "mov" to "video/quicktime", "js" to "application/javascript",
        "html" to "text/html", "ttf" to "font/ttf", "otf" to "font/otf",
    )
    fun getExtensionFromMimeType(mime: String?): String? = map.entries.firstOrNull { it.value == mime }?.key
    fun getMimeTypeFromExtension(ext: String?): String? = ext?.lowercase()?.let { map[it] }
    fun hasExtension(ext: String?) = ext != null && map.containsKey(ext.lowercase())
    companion object {
        private val inst = MimeTypeMap()
        fun getSingleton(): MimeTypeMap = inst
        fun getFileExtensionFromUrl(url: String?): String = url?.substringBefore('?')?.substringAfterLast('/')?.substringAfterLast('.', "") ?: ""
    }
}

// ---------- Sensors (accelerometer via CoreMotion on iOS) ----------
expect object PlatformMotion {
    /** Callback receives Android-convention accelerometer values in m/s². */
    fun start(cb: (FloatArray) -> Unit)
    fun stop()
}

class Sensor internal constructor(val type: Int) {
    val name: String get() = "iOS sensor $type"
    companion object {
        const val TYPE_ACCELEROMETER = 1
        const val TYPE_MAGNETIC_FIELD = 2
        const val TYPE_GYROSCOPE = 4
        const val TYPE_LIGHT = 5
        const val TYPE_PROXIMITY = 8
        const val TYPE_GRAVITY = 9
        const val TYPE_LINEAR_ACCELERATION = 10
        const val TYPE_ROTATION_VECTOR = 11
    }
}

class SensorEvent(val sensor: Sensor, val values: FloatArray, val timestamp: Long = System.nanoTime(), val accuracy: Int = 3)

interface SensorEventListener {
    fun onSensorChanged(event: SensorEvent)
    fun onAccuracyChanged(sensor: Sensor?, accuracy: Int)
}

object SensorManagerImpl {
    private val listeners = LinkedHashMap<SensorEventListener, Sensor>()
    fun getDefaultSensor(type: Int): Sensor? = if (type == Sensor.TYPE_ACCELEROMETER || type == Sensor.TYPE_GRAVITY) Sensor(type) else null
    fun registerListener(l: SensorEventListener, s: Sensor?, delay: Int): Boolean {
        if (s == null) return false
        val start = listeners.isEmpty()
        listeners[l] = s
        if (start) PlatformMotion.start { v ->
            PlatformBridge.runOnMain { listeners.toList().forEach { (ls, sensor) -> ls.onSensorChanged(SensorEvent(sensor, v.copyOf())) } }
        }
        return true
    }
    fun unregisterListener(l: SensorEventListener) {
        listeners.remove(l)
        if (listeners.isEmpty()) PlatformMotion.stop()
    }
    fun unregisterListener(l: SensorEventListener, s: Sensor?) = unregisterListener(l)
}
