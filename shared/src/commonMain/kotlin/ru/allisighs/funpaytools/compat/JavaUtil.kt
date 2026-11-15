package ru.allisighs.funpaytools

import co.touchlab.stately.collections.ConcurrentMutableList
import co.touchlab.stately.collections.ConcurrentMutableMap
import co.touchlab.stately.collections.ConcurrentMutableSet
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicLong as KAtomicLong
import kotlin.concurrent.atomics.AtomicReference as KAtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// ---------- UUID ----------
class UUID private constructor(private val value: String) {
    companion object {
        @OptIn(ExperimentalUuidApi::class)
        fun randomUUID(): UUID = UUID(Uuid.random().toString())
        fun fromString(s: String): UUID = UUID(s.lowercase())
        @OptIn(ExperimentalUuidApi::class)
        fun nameUUIDFromBytes(bytes: ByteArray): UUID {
            val md5 = Md5.digest(bytes)
            md5[6] = (md5[6].toInt() and 0x0f or 0x30).toByte()
            md5[8] = (md5[8].toInt() and 0x3f or 0x80).toByte()
            return UUID(Uuid.fromByteArray(md5).toString())
        }
    }
    override fun toString() = value
    override fun equals(other: Any?) = other is UUID && other.value == value
    override fun hashCode() = value.hashCode()
}

// ---------- Locale ----------
class Locale(val language: String, val country: String = "") {
    companion object {
        val US = Locale("en", "US")
        val ENGLISH = Locale("en")
        val ROOT = Locale("")
        val UK = Locale("en", "GB")
        val GERMANY = Locale("de", "DE")
        val FRANCE = Locale("fr", "FR")
        private var def = Locale("ru", "RU")
        fun getDefault(): Locale = def
        fun setDefault(l: Locale) { def = l }
        fun forLanguageTag(tag: String) = Locale(tag.substringBefore('-'), tag.substringAfter('-', ""))
    }
    fun toLanguageTag() = if (country.isEmpty()) language else "$language-$country"
    val displayLanguage: String get() = language
    override fun toString() = if (country.isEmpty()) language else "${language}_$country"
    override fun equals(other: Any?) = other is Locale && other.language == language && other.country == country
    override fun hashCode() = language.hashCode() * 31 + country.hashCode()
}

// ---------- TimeZone ----------
class TimeZone private constructor(val id: String) {
    internal val kz: kotlinx.datetime.TimeZone = try {
        when {
            id == "UTC" || id == "GMT" -> kotlinx.datetime.TimeZone.UTC
            id.startsWith("GMT") && id.length > 3 -> kotlinx.datetime.TimeZone.of("UTC" + id.substring(3))
            else -> kotlinx.datetime.TimeZone.of(id)
        }
    } catch (e: Exception) { kotlinx.datetime.TimeZone.UTC }

    companion object {
        fun getTimeZone(id: String) = TimeZone(id)
        fun getDefault(): TimeZone = TimeZone(kotlinx.datetime.TimeZone.currentSystemDefault().id)
    }

    fun getOffset(ms: Long): Int {
        val inst = Instant.fromEpochMilliseconds(ms)
        val ldt = inst.toLocalDateTime(kz)
        val utc = ldt.toInstant(kotlinx.datetime.TimeZone.UTC)
        return (utc.toEpochMilliseconds() - ms).toInt()
    }

    val rawOffset: Int get() = getOffset(System.currentTimeMillis())
    override fun toString() = id
}

// ---------- Date ----------
open class Date(var time: Long = System.currentTimeMillis()) : Comparable<Date> {
    @kotlin.jvm.JvmName("getTimeFn") fun getTime(): Long = time
    fun before(other: Date) = time < other.time
    fun after(other: Date) = time > other.time
    override fun compareTo(other: Date) = time.compareTo(other.time)
    override fun equals(other: Any?) = other is Date && other.time == time
    override fun hashCode() = time.hashCode()
    override fun toString(): String = SimpleDateFormat("EEE MMM dd HH:mm:ss yyyy", Locale.US).format(this)
    private fun field(f: Int) = Calendar.getInstance().also { it.timeInMillis = time }.get(f)
    val year: Int get() = field(Calendar.YEAR) - 1900
    val month: Int get() = field(Calendar.MONTH)
    val date: Int get() = field(Calendar.DAY_OF_MONTH)
    val day: Int get() = field(Calendar.DAY_OF_WEEK) - 1
    val hours: Int get() = field(Calendar.HOUR_OF_DAY)
    val minutes: Int get() = field(Calendar.MINUTE)
    val seconds: Int get() = field(Calendar.SECOND)
}

// ---------- Calendar ----------
class Calendar private constructor(var timeZone: TimeZone) {
    var timeInMillis: Long = System.currentTimeMillis()

    var time: Date
        get() = Date(timeInMillis)
        set(v) { timeInMillis = v.time }

    @kotlin.jvm.JvmName("getTimeFn") fun getTime(): Date = Date(timeInMillis)
    @kotlin.jvm.JvmName("setTimeFn") fun setTime(d: Date) { timeInMillis = d.time }
    @kotlin.jvm.JvmName("setTimeInMillisFn") fun setTimeInMillis(ms: Long) { timeInMillis = ms }
    @kotlin.jvm.JvmName("getTimeInMillisFn") fun getTimeInMillis(): Long = timeInMillis

    private fun ldt(): LocalDateTime = Instant.fromEpochMilliseconds(timeInMillis).toLocalDateTime(timeZone.kz)
    private fun setLdt(l: LocalDateTime) { timeInMillis = l.toInstant(timeZone.kz).toEpochMilliseconds() }

    companion object {
        const val ERA = 0
        const val YEAR = 1
        const val MONTH = 2
        const val WEEK_OF_YEAR = 3
        const val WEEK_OF_MONTH = 4
        const val DATE = 5
        const val DAY_OF_MONTH = 5
        const val DAY_OF_YEAR = 6
        const val DAY_OF_WEEK = 7
        const val DAY_OF_WEEK_IN_MONTH = 8
        const val AM_PM = 9
        const val HOUR = 10
        const val HOUR_OF_DAY = 11
        const val MINUTE = 12
        const val SECOND = 13
        const val MILLISECOND = 14
        const val ZONE_OFFSET = 15

        const val SUNDAY = 1
        const val MONDAY = 2
        const val TUESDAY = 3
        const val WEDNESDAY = 4
        const val THURSDAY = 5
        const val FRIDAY = 6
        const val SATURDAY = 7

        const val JANUARY = 0
        const val FEBRUARY = 1
        const val MARCH = 2
        const val APRIL = 3
        const val MAY = 4
        const val JUNE = 5
        const val JULY = 6
        const val AUGUST = 7
        const val SEPTEMBER = 8
        const val OCTOBER = 9
        const val NOVEMBER = 10
        const val DECEMBER = 11

        const val AM = 0
        const val PM = 1

        fun getInstance(): Calendar = Calendar(TimeZone.getDefault())
        fun getInstance(tz: TimeZone): Calendar = Calendar(tz)
        fun getInstance(locale: Locale): Calendar = Calendar(TimeZone.getDefault())
        fun getInstance(tz: TimeZone, locale: Locale): Calendar = Calendar(tz)
    }

    fun get(field: Int): Int {
        val l = ldt()
        return when (field) {
            YEAR -> l.year
            MONTH -> l.month.ordinal
            DAY_OF_MONTH -> l.day
            DAY_OF_YEAR -> l.dayOfYear
            DAY_OF_WEEK -> (l.dayOfWeek.isoDayNumber % 7) + 1
            HOUR_OF_DAY -> l.hour
            HOUR -> l.hour % 12
            AM_PM -> if (l.hour < 12) AM else PM
            MINUTE -> l.minute
            SECOND -> l.second
            MILLISECOND -> l.nanosecond / 1_000_000
            WEEK_OF_YEAR -> (l.dayOfYear - 1) / 7 + 1
            WEEK_OF_MONTH -> (l.day - 1) / 7 + 1
            DAY_OF_WEEK_IN_MONTH -> (l.day - 1) / 7 + 1
            ZONE_OFFSET -> timeZone.getOffset(timeInMillis)
            ERA -> 1
            else -> 0
        }
    }

    fun set(field: Int, value: Int) {
        val l = ldt()
        when (field) {
            YEAR -> setLdt(build(value, l.month.ordinal, l.day, l.hour, l.minute, l.second, l.nanosecond))
            MONTH -> setLdt(build(l.year, value, l.day, l.hour, l.minute, l.second, l.nanosecond))
            DAY_OF_MONTH -> setLdt(build(l.year, l.month.ordinal, value, l.hour, l.minute, l.second, l.nanosecond))
            HOUR_OF_DAY, HOUR -> setLdt(build(l.year, l.month.ordinal, l.day, value, l.minute, l.second, l.nanosecond))
            MINUTE -> setLdt(build(l.year, l.month.ordinal, l.day, l.hour, value, l.second, l.nanosecond))
            SECOND -> setLdt(build(l.year, l.month.ordinal, l.day, l.hour, l.minute, value, l.nanosecond))
            MILLISECOND -> setLdt(build(l.year, l.month.ordinal, l.day, l.hour, l.minute, l.second, value * 1_000_000))
            DAY_OF_YEAR -> {
                val d = LocalDate(l.year, 1, 1).plus(DatePeriod(days = value - 1))
                setLdt(LocalDateTime(d, l.time))
            }
            DAY_OF_WEEK -> {
                val cur = get(DAY_OF_WEEK)
                add(DAY_OF_MONTH, value - cur)
            }
            else -> {}
        }
    }

    fun set(year: Int, month: Int, day: Int) {
        val l = ldt()
        setLdt(build(year, month, day, l.hour, l.minute, l.second, l.nanosecond))
    }

    fun set(year: Int, month: Int, day: Int, hour: Int, minute: Int) {
        setLdt(build(year, month, day, hour, minute, 0, 0))
    }

    fun set(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int) {
        setLdt(build(year, month, day, hour, minute, second, 0))
    }

    /** Lenient builder: normalizes overflowing fields like java.util.Calendar does. */
    private fun build(year: Int, month0: Int, day: Int, hour: Int, minute: Int, second: Int, nano: Int): LocalDateTime {
        var y = year
        var m = month0
        y += Math.floorMod(m, 12).let { (m - it) / 12 }
        m = Math.floorMod(m, 12)
        val base = LocalDate(y, m + 1, 1)
        val date = base.plus(DatePeriod(days = day - 1))
        val totalSec = hour.toLong() * 3600 + minute.toLong() * 60 + second
        val dayShift = Math.floorMod(totalSec, 86400L).let { (totalSec - it) / 86400 }
        val secOfDay = Math.floorMod(totalSec, 86400L).toInt()
        val d2 = date.plus(DatePeriod(days = dayShift.toInt()))
        return LocalDateTime(d2, LocalTime(secOfDay / 3600, (secOfDay / 60) % 60, secOfDay % 60, nano.coerceIn(0, 999_999_999)))
    }

    fun add(field: Int, amount: Int) {
        when (field) {
            MILLISECOND -> timeInMillis += amount
            SECOND -> timeInMillis += amount * 1000L
            MINUTE -> timeInMillis += amount * 60_000L
            HOUR_OF_DAY, HOUR -> timeInMillis += amount * 3_600_000L
            DAY_OF_MONTH, DAY_OF_YEAR, DAY_OF_WEEK -> {
                val l = ldt()
                setLdt(LocalDateTime(l.date.plus(amount, DateTimeUnit.DAY), l.time))
            }
            WEEK_OF_YEAR, WEEK_OF_MONTH -> add(DAY_OF_MONTH, amount * 7)
            MONTH -> {
                val l = ldt()
                setLdt(LocalDateTime(l.date.plus(amount, DateTimeUnit.MONTH), l.time))
            }
            YEAR -> {
                val l = ldt()
                setLdt(LocalDateTime(l.date.plus(amount, DateTimeUnit.YEAR), l.time))
            }
        }
    }

    fun getActualMaximum(field: Int): Int {
        val l = ldt()
        return when (field) {
            DAY_OF_MONTH -> {
                val first = LocalDate(l.year, l.month.ordinal + 1, 1)
                val next = first.plus(1, DateTimeUnit.MONTH)
                (next.toEpochDays() - first.toEpochDays()).toInt()
            }
            DAY_OF_YEAR -> if (isLeap(l.year)) 366 else 365
            MONTH -> 11
            HOUR_OF_DAY -> 23
            MINUTE, SECOND -> 59
            else -> 0
        }
    }

    private fun isLeap(y: Int) = (y % 4 == 0 && y % 100 != 0) || y % 400 == 0

    fun before(o: Any?) = o is Calendar && timeInMillis < o.timeInMillis
    fun after(o: Any?) = o is Calendar && timeInMillis > o.timeInMillis
    fun clone(): Any = Calendar(timeZone).also { it.timeInMillis = timeInMillis }
    var firstDayOfWeek: Int = MONDAY
}

// ---------- SimpleDateFormat ----------
private val RU_MONTHS_GEN = listOf("января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря")
private val RU_MONTHS_NOM = listOf("январь", "февраль", "март", "апрель", "май", "июнь", "июль", "август", "сентябрь", "октябрь", "ноябрь", "декабрь")
private val RU_MONTHS_SHORT = listOf("янв.", "февр.", "мар.", "апр.", "мая", "июн.", "июл.", "авг.", "сент.", "окт.", "нояб.", "дек.")
private val EN_MONTHS = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
private val RU_DAYS = listOf("понедельник", "вторник", "среда", "четверг", "пятница", "суббота", "воскресенье")
private val RU_DAYS_SHORT = listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс")
private val EN_DAYS = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

open class SimpleDateFormat(private val pattern: String, private val locale: Locale = Locale.getDefault()) {
    var timeZone: TimeZone = TimeZone.getDefault()
    var isLenient: Boolean = true

    @kotlin.jvm.JvmName("setTimeZoneFn") fun setTimeZone(tz: TimeZone) { timeZone = tz }
    @kotlin.jvm.JvmName("getTimeZoneFn") fun getTimeZone(): TimeZone = timeZone

    private sealed class Tok {
        class Lit(val s: String) : Tok()
        class Field(val c: Char, val n: Int) : Tok()
    }

    private val tokens: List<Tok> by lazy {
        val out = ArrayList<Tok>()
        var i = 0
        while (i < pattern.length) {
            val c = pattern[i]
            if (c == '\'') {
                val end = pattern.indexOf('\'', i + 1)
                if (end == i + 1) { out.add(Tok.Lit("'")); i += 2; continue }
                val lit = if (end < 0) pattern.substring(i + 1) else pattern.substring(i + 1, end)
                out.add(Tok.Lit(lit))
                i = if (end < 0) pattern.length else end + 1
                continue
            }
            if (c.isLetter()) {
                var j = i
                while (j < pattern.length && pattern[j] == c) j++
                out.add(Tok.Field(c, j - i))
                i = j
            } else {
                out.add(Tok.Lit(c.toString()))
                i++
            }
        }
        out
    }

    private val ru get() = locale.language == "ru" || locale.language.isEmpty() && Locale.getDefault().language == "ru"

    fun format(date: Date): String = format(date.time)
    fun format(obj: Any?): String = when (obj) {
        is Date -> format(obj.time)
        is Number -> format(obj.toLong())
        else -> ""
    }

    fun format(ms: Long): String {
        val l = Instant.fromEpochMilliseconds(ms).toLocalDateTime(timeZone.kz)
        val sb = StringBuilder()
        for (t in tokens) {
            when (t) {
                is Tok.Lit -> sb.append(t.s)
                is Tok.Field -> sb.append(
                    when (t.c) {
                        'y' -> if (t.n == 2) (l.year % 100).toString().padStart(2, '0') else l.year.toString().padStart(t.n, '0')
                        'M', 'L' -> when {
                            t.n >= 4 -> if (ru) (if (t.c == 'L') RU_MONTHS_NOM else RU_MONTHS_GEN)[l.month.ordinal] else EN_MONTHS[l.month.ordinal]
                            t.n == 3 -> if (ru) RU_MONTHS_SHORT[l.month.ordinal] else EN_MONTHS[l.month.ordinal].take(3)
                            else -> (l.month.ordinal + 1).toString().padStart(t.n, '0')
                        }
                        'd' -> l.day.toString().padStart(t.n, '0')
                        'H' -> l.hour.toString().padStart(t.n, '0')
                        'k' -> (if (l.hour == 0) 24 else l.hour).toString().padStart(t.n, '0')
                        'h' -> ((l.hour + 11) % 12 + 1).toString().padStart(t.n, '0')
                        'K' -> (l.hour % 12).toString().padStart(t.n, '0')
                        'm' -> l.minute.toString().padStart(t.n, '0')
                        's' -> l.second.toString().padStart(t.n, '0')
                        'S' -> (l.nanosecond / 1_000_000).toString().padStart(3, '0').take(t.n.coerceAtLeast(1)).padEnd(t.n, '0')
                        'E' -> {
                            val idx = l.dayOfWeek.isoDayNumber - 1
                            if (t.n >= 4) (if (ru) RU_DAYS else EN_DAYS)[idx] else (if (ru) RU_DAYS_SHORT[idx] else EN_DAYS[idx].take(3))
                        }
                        'a' -> if (l.hour < 12) "AM" else "PM"
                        'D' -> l.dayOfYear.toString().padStart(t.n, '0')
                        'Z' -> offsetString(ms, false)
                        'X', 'x' -> offsetString(ms, true)
                        'z' -> timeZone.id
                        else -> ""
                    }
                )
            }
        }
        return sb.toString()
    }

    private fun offsetString(ms: Long, iso: Boolean): String {
        val off = timeZone.getOffset(ms) / 60000
        if (iso && off == 0) return "Z"
        val sign = if (off < 0) "-" else "+"
        val a = kotlin.math.abs(off)
        val hh = (a / 60).toString().padStart(2, '0')
        val mm = (a % 60).toString().padStart(2, '0')
        return if (iso) "$sign$hh:$mm" else "$sign$hh$mm"
    }

    fun parse(text: String): Date? {
        var pos = 0
        var year = 1970; var month = 0; var day = 1; var hour = 0; var minute = 0; var second = 0; var milli = 0
        var pm: Boolean? = null
        var offsetMin: Int? = null
        fun readNum(maxLen: Int): Int {
            val start = pos
            while (pos < text.length && text[pos].isDigit() && pos - start < maxLen) pos++
            if (start == pos) throw ParseException("Unparseable date: \"$text\"", pos)
            return text.substring(start, pos).toInt()
        }
        for ((idx, t) in tokens.withIndex()) {
            when (t) {
                is Tok.Lit -> {
                    for (ch in t.s) {
                        if (pos < text.length && text[pos] == ch) pos++
                        else if (ch == ' ' ) { while (pos < text.length && text[pos].isWhitespace()) pos++ }
                        else throw ParseException("Unparseable date: \"$text\"", pos)
                    }
                }
                is Tok.Field -> {
                    val next = tokens.getOrNull(idx + 1)
                    val maxLen = if (next is Tok.Field) t.n else 9
                    when (t.c) {
                        'y' -> { val v = readNum(if (t.n == 2 && next is Tok.Field) 2 else maxLen); year = if (t.n == 2 && v < 100) 2000 + v else v }
                        'M', 'L' -> if (t.n >= 3) {
                            val rest = text.substring(pos).lowercase()
                            val lists = listOf(RU_MONTHS_GEN, RU_MONTHS_NOM, EN_MONTHS.map { it.lowercase() }, RU_MONTHS_SHORT, EN_MONTHS.map { it.lowercase().take(3) })
                            var found = false
                            for (lst in lists) {
                                val i = lst.indexOfFirst { rest.startsWith(it) }
                                if (i >= 0) { month = i; pos += lst[i].length; found = true; break }
                            }
                            if (!found) throw ParseException("Unparseable date: \"$text\"", pos)
                        } else month = readNum(maxLen) - 1
                        'd' -> day = readNum(maxLen)
                        'H', 'k' -> hour = readNum(maxLen)
                        'h', 'K' -> hour = readNum(maxLen)
                        'm' -> minute = readNum(maxLen)
                        's' -> second = readNum(maxLen)
                        'S' -> milli = readNum(maxLen)
                        'a' -> {
                            val r = text.substring(pos).uppercase()
                            if (r.startsWith("AM")) { pm = false; pos += 2 } else if (r.startsWith("PM")) { pm = true; pos += 2 }
                        }
                        'E' -> { while (pos < text.length && text[pos].isLetter()) pos++; if (pos < text.length && text[pos] == '.') pos++ }
                        'Z', 'X', 'x' -> {
                            if (pos < text.length && text[pos] == 'Z') { offsetMin = 0; pos++ }
                            else if (pos < text.length && (text[pos] == '+' || text[pos] == '-')) {
                                val sign = if (text[pos] == '-') -1 else 1
                                pos++
                                val hh = readNum(2)
                                if (pos < text.length && text[pos] == ':') pos++
                                val mm = if (pos < text.length && text[pos].isDigit()) readNum(2) else 0
                                offsetMin = sign * (hh * 60 + mm)
                            }
                        }
                        'z' -> { while (pos < text.length && !text[pos].isWhitespace()) pos++ }
                        else -> {}
                    }
                }
            }
        }
        if (pm == true && hour < 12) hour += 12
        if (pm == false && hour == 12) hour = 0
        val cal = if (offsetMin != null) Calendar.getInstance(TimeZone.getTimeZone("UTC")) else Calendar.getInstance(timeZone)
        cal.set(year, month, day, hour, minute, second)
        cal.set(Calendar.MILLISECOND, milli)
        var ms = cal.timeInMillis
        if (offsetMin != null) ms -= offsetMin!! * 60_000L
        return Date(ms)
    }

    fun toPattern() = pattern
}

class ParseException(message: String, val errorOffset: Int = 0) : Exception(message)

// ---------- TimeUnit ----------
enum class TimeUnit(private val ms: Long) {
    NANOSECONDS(0), MICROSECONDS(0), MILLISECONDS(1), SECONDS(1000), MINUTES(60_000), HOURS(3_600_000), DAYS(86_400_000);

    fun toMillis(v: Long): Long = when (this) {
        NANOSECONDS -> v / 1_000_000
        MICROSECONDS -> v / 1000
        else -> v * ms
    }
    fun toSeconds(v: Long): Long = toMillis(v) / 1000
    fun toMinutes(v: Long): Long = toMillis(v) / 60_000
    fun toHours(v: Long): Long = toMillis(v) / 3_600_000
    fun toDays(v: Long): Long = toMillis(v) / 86_400_000
    fun toNanos(v: Long): Long = when (this) {
        NANOSECONDS -> v
        MICROSECONDS -> v * 1000
        else -> v * ms * 1_000_000
    }
}

// ---------- Concurrent collections ----------
typealias ConcurrentHashMap<K, V> = ConcurrentMutableMap<K, V>
typealias ConcurrentLinkedQueue<E> = ConcurrentMutableList<E>
typealias CopyOnWriteArrayList<E> = ConcurrentMutableList<E>

fun <K, V> ConcurrentMutableMap<K, V>.putIfAbsent(key: K, value: V): V? = block { m ->
    val cur = m[key]
    if (cur == null) m[key] = value
    cur
}

fun <K, V> ConcurrentMutableMap<K, V>.computeIfAbsent(key: K, f: (K) -> V): V = block { m ->
    m[key] ?: f(key).also { m[key] = it }
}

fun <K, V> MutableMap<K, V>.getOrDefaultCompat(key: K, def: V): V = this[key] ?: def
fun <K, V> MutableMap<K, V>.putIfAbsent(key: K, value: V): V? {
    val cur = this[key]
    if (cur == null) this[key] = value
    return cur
}

fun <E> ConcurrentMutableList<E>.offer(e: E): Boolean = add(e)
fun <E> ConcurrentMutableList<E>.poll(): E? = block { l -> if (l.isEmpty()) null else l.removeAt(0) }
fun <E> ConcurrentMutableList<E>.peek(): E? = block { l -> l.firstOrNull() }

object ConcurrentHashMapCompat {
    fun <E> newKeySet(): MutableSet<E> = ConcurrentMutableSet()
}

object Collections {
    fun <T> swap(list: MutableList<T>, i: Int, j: Int) {
        val t = list[i]; list[i] = list[j]; list[j] = t
    }
    fun <T> synchronizedList(list: MutableList<T>): MutableList<T> = ConcurrentMutableList<T>().also { it.addAll(list) }
    fun <K, V> synchronizedMap(map: MutableMap<K, V>): MutableMap<K, V> = ConcurrentMutableMap<K, V>().also { it.putAll(map) }
    fun <T> synchronizedSet(set: MutableSet<T>): MutableSet<T> = ConcurrentMutableSet<T>().also { it.addAll(set) }
    fun <T> emptyList(): List<T> = kotlin.collections.emptyList()
    fun <K, V> emptyMap(): Map<K, V> = kotlin.collections.emptyMap()
    fun <T> unmodifiableList(l: List<T>): List<T> = l
    fun <T> shuffle(l: MutableList<T>) = l.shuffle()
    fun <T : Comparable<T>> sort(l: MutableList<T>) = l.sort()
    fun <T> reverse(l: MutableList<T>) = l.reverse()
    fun <T> singletonList(t: T): List<T> = listOf(t)
    fun <T> newSetFromMap(map: MutableMap<T, Boolean>): MutableSet<T> = ConcurrentMutableSet()
}

// ---------- Atomics ----------
@OptIn(ExperimentalAtomicApi::class)
class AtomicBoolean(initial: Boolean = false) {
    private val v = AtomicInt(if (initial) 1 else 0)
    fun get(): Boolean = v.load() == 1
    fun set(b: Boolean) = v.store(if (b) 1 else 0)
    fun compareAndSet(expect: Boolean, update: Boolean): Boolean = v.compareAndSet(if (expect) 1 else 0, if (update) 1 else 0)
    fun getAndSet(b: Boolean): Boolean = v.exchange(if (b) 1 else 0) == 1
    fun lazySet(b: Boolean) = set(b)
    override fun toString() = get().toString()
}

@OptIn(ExperimentalAtomicApi::class)
class AtomicInteger(initial: Int = 0) {
    private val v = AtomicInt(initial)
    fun get(): Int = v.load()
    fun set(i: Int) = v.store(i)
    fun incrementAndGet(): Int = v.addAndFetch(1)
    fun decrementAndGet(): Int = v.addAndFetch(-1)
    fun getAndIncrement(): Int = v.fetchAndAdd(1)
    fun getAndDecrement(): Int = v.fetchAndAdd(-1)
    fun addAndGet(d: Int): Int = v.addAndFetch(d)
    fun getAndAdd(d: Int): Int = v.fetchAndAdd(d)
    fun getAndSet(i: Int): Int = v.exchange(i)
    fun compareAndSet(e: Int, u: Int) = v.compareAndSet(e, u)
    fun toInt() = get()
    override fun toString() = get().toString()
}

@OptIn(ExperimentalAtomicApi::class)
class AtomicLong(initial: Long = 0L) {
    private val v = KAtomicLong(initial)
    fun get(): Long = v.load()
    fun set(i: Long) = v.store(i)
    fun incrementAndGet(): Long = v.addAndFetch(1)
    fun decrementAndGet(): Long = v.addAndFetch(-1)
    fun getAndIncrement(): Long = v.fetchAndAdd(1)
    fun addAndGet(d: Long): Long = v.addAndFetch(d)
    fun getAndSet(i: Long): Long = v.exchange(i)
    fun compareAndSet(e: Long, u: Long) = v.compareAndSet(e, u)
    override fun toString() = get().toString()
}

@OptIn(ExperimentalAtomicApi::class)
class AtomicReference<T>(initial: T) {
    private val v = KAtomicReference(initial)
    fun get(): T = v.load()
    fun set(t: T) = v.store(t)
    fun getAndSet(t: T): T = v.exchange(t)
    fun compareAndSet(e: T, u: T): Boolean {
        while (true) {
            val cur = v.load()
            if (cur != e && cur !== e) return false
            if (v.compareAndSet(cur, u)) return true
        }
    }
    fun updateAndGet(f: (T) -> T): T {
        while (true) {
            val cur = v.load()
            val nxt = f(cur)
            if (v.compareAndSet(cur, nxt)) return nxt
        }
    }
    override fun toString() = get().toString()
}

fun <T> AtomicReference(): AtomicReference<T?> = AtomicReference(null)

// ---------- regex Pattern ----------
class Pattern private constructor(private val regex: Regex) {
    companion object {
        const val CASE_INSENSITIVE = 2
        const val MULTILINE = 8
        const val DOTALL = 32
        const val UNICODE_CASE = 64
        fun compile(p: String): Pattern = Pattern(Regex(p))
        fun compile(p: String, flags: Int): Pattern {
            val opts = mutableSetOf<RegexOption>()
            if (flags and CASE_INSENSITIVE != 0) opts.add(RegexOption.IGNORE_CASE)
            if (flags and MULTILINE != 0) opts.add(RegexOption.MULTILINE)
            if (flags and DOTALL != 0) return Pattern(Regex("(?s)$p", opts))
            return Pattern(Regex(p, opts))
        }
        fun quote(s: String) = Regex.escape(s)
        fun matches(p: String, s: CharSequence) = Regex(p).matches(s)
    }
    fun matcher(s: CharSequence) = Matcher(regex, s)
    fun toRegex() = regex
    fun pattern() = regex.pattern
}

class Matcher(private val regex: Regex, private val input: CharSequence) {
    private var current: MatchResult? = null
    private var nextStart = 0
    fun find(): Boolean {
        if (nextStart > input.length) { current = null; return false }
        current = regex.find(input, nextStart)
        val c = current ?: return false
        nextStart = if (c.range.isEmpty()) c.range.first + 1 else c.range.last + 1
        return true
    }
    fun matches(): Boolean {
        current = regex.matchEntire(input)
        return current != null
    }
    fun lookingAt(): Boolean {
        val m = regex.find(input)
        current = if (m != null && m.range.first == 0) m else null
        return current != null
    }
    fun group(): String = current?.value ?: throw IllegalStateException("No match")
    fun group(i: Int): String? = current?.groups?.get(i)?.value
    fun group(name: String): String? = current?.groups?.let { g -> try { (g as? MatchNamedGroupCollection)?.get(name)?.value } catch (e: Exception) { null } }
    fun groupCount(): Int = (current?.groupValues?.size ?: 1) - 1
    fun start(): Int = current?.range?.first ?: -1
    fun end(): Int = (current?.range?.last ?: -2) + 1
    fun replaceAll(rep: String): String = regex.replace(input, rep.replace("\\$", "\u0000").replace(Regex("\\$(\\d)"), "\\$$1").replace("\u0000", "\\$"))
}

// ---------- MD5 (for UUID.nameUUIDFromBytes / hashing) ----------
object Md5 {
    private val S = intArrayOf(7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21)
    private val K = IntArray(64) { i -> (kotlin.math.abs(kotlin.math.sin((i + 1).toDouble())) * 4294967296.0).toLong().toInt() }

    fun digest(message: ByteArray): ByteArray {
        val msgLenBits = message.size.toLong() * 8
        val numBlocks = ((message.size + 8) ushr 6) + 1
        val totalLen = numBlocks shl 6
        val padded = ByteArray(totalLen)
        message.copyInto(padded)
        padded[message.size] = 0x80.toByte()
        for (i in 0 until 8) padded[totalLen - 8 + i] = (msgLenBits ushr (8 * i)).toByte()
        var a0 = 0x67452301; var b0 = 0xefcdab89.toInt(); var c0 = 0x98badcfe.toInt(); var d0 = 0x10325476
        val m = IntArray(16)
        for (blk in 0 until numBlocks) {
            for (i in 0 until 16) {
                val o = (blk shl 6) + i * 4
                m[i] = (padded[o].toInt() and 0xff) or ((padded[o + 1].toInt() and 0xff) shl 8) or ((padded[o + 2].toInt() and 0xff) shl 16) or ((padded[o + 3].toInt() and 0xff) shl 24)
            }
            var a = a0; var b = b0; var c = c0; var d = d0
            for (i in 0 until 64) {
                var f: Int; val g: Int
                when (i / 16) {
                    0 -> { f = (b and c) or (b.inv() and d); g = i }
                    1 -> { f = (d and b) or (d.inv() and c); g = (5 * i + 1) % 16 }
                    2 -> { f = b xor c xor d; g = (3 * i + 5) % 16 }
                    else -> { f = c xor (b or d.inv()); g = (7 * i) % 16 }
                }
                f = f + a + K[i] + m[g]
                a = d; d = c; c = b
                b += (f shl S[i]) or (f ushr (32 - S[i]))
            }
            a0 += a; b0 += b; c0 += c; d0 += d
        }
        val out = ByteArray(16)
        for ((idx, v) in intArrayOf(a0, b0, c0, d0).withIndex()) for (j in 0 until 4) out[idx * 4 + j] = (v ushr (8 * j)).toByte()
        return out
    }
}

class MessageDigest private constructor(private val algo: String) {
    private val buf = ArrayList<Byte>()
    companion object {
        fun getInstance(algo: String) = MessageDigest(algo.uppercase())
    }
    fun update(b: ByteArray) { buf.addAll(b.toList()) }
    fun digest(): ByteArray {
        val data = buf.toByteArray(); buf.clear()
        return when (algo) {
            "MD5" -> Md5.digest(data)
            "SHA-256", "SHA256" -> Sha256.digest(data)
            "SHA-1", "SHA1", "SHA" -> Sha1.digest(data)
            else -> Sha256.digest(data)
        }
    }
    fun digest(b: ByteArray): ByteArray { update(b); return digest() }
}

object Sha256 {
    private val K = intArrayOf(
        0x428a2f98, 0x71374491, -0x4a3f0431, -0x164a245b, 0x3956c25b, 0x59f111f1, -0x6dc07d5c, -0x54e3a12b,
        -0x27f85568, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, -0x7f214e02, -0x6423f959, -0x3e640e8c,
        -0x1b64963f, -0x1041b87a, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        -0x67c1aeae, -0x57ce3993, -0x4ffcd838, -0x40a68039, -0x391ff40d, -0x2a586eb9, 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, -0x7e3d36d2, -0x6d8dd37b,
        -0x5d40175f, -0x57e599b5, -0x3db47490, -0x3893ae5d, -0x2e6d17e7, -0x2966f9dc, -0xbf1ca7b, 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, -0x7b3787ec, -0x7338fdf8, -0x6f410006, -0x5baf9315, -0x41065c09, -0x398e870e
    )
    fun digest(msg: ByteArray): ByteArray {
        val h = intArrayOf(0x6a09e667, -0x4498517b, 0x3c6ef372, -0x5ab00ac6, 0x510e527f, -0x64fa9774, 0x1f83d9ab, 0x5be0cd19)
        val bitLen = msg.size.toLong() * 8
        val padLen = ((msg.size + 9 + 63) / 64) * 64
        val p = ByteArray(padLen)
        msg.copyInto(p); p[msg.size] = 0x80.toByte()
        for (i in 0 until 8) p[padLen - 1 - i] = (bitLen ushr (8 * i)).toByte()
        val w = IntArray(64)
        for (blk in 0 until padLen / 64) {
            for (i in 0 until 16) {
                val o = blk * 64 + i * 4
                w[i] = ((p[o].toInt() and 0xff) shl 24) or ((p[o + 1].toInt() and 0xff) shl 16) or ((p[o + 2].toInt() and 0xff) shl 8) or (p[o + 3].toInt() and 0xff)
            }
            for (i in 16 until 64) {
                val s0 = w[i - 15].rotateRight(7) xor w[i - 15].rotateRight(18) xor (w[i - 15] ushr 3)
                val s1 = w[i - 2].rotateRight(17) xor w[i - 2].rotateRight(19) xor (w[i - 2] ushr 10)
                w[i] = w[i - 16] + s0 + w[i - 7] + s1
            }
            var a = h[0]; var b = h[1]; var c = h[2]; var d = h[3]; var e = h[4]; var f = h[5]; var g = h[6]; var hh = h[7]
            for (i in 0 until 64) {
                val s1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
                val ch = (e and f) xor (e.inv() and g)
                val t1 = hh + s1 + ch + K[i] + w[i]
                val s0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
                val maj = (a and b) xor (a and c) xor (b and c)
                val t2 = s0 + maj
                hh = g; g = f; f = e; e = d + t1; d = c; c = b; b = a; a = t1 + t2
            }
            h[0] += a; h[1] += b; h[2] += c; h[3] += d; h[4] += e; h[5] += f; h[6] += g; h[7] += hh
        }
        val out = ByteArray(32)
        for (i in 0 until 8) for (j in 0 until 4) out[i * 4 + j] = (h[i] ushr (24 - 8 * j)).toByte()
        return out
    }
}
