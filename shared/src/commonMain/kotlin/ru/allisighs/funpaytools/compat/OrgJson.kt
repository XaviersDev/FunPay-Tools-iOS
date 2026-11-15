package ru.allisighs.funpaytools

/* org.json compatible JSONObject / JSONArray */

private object JsonNull {
    override fun toString() = "null"
    override fun equals(other: Any?) = other == null || other === this
    override fun hashCode() = 0
}

class JSONObject() {
    internal val map = LinkedHashMap<String, Any?>()

    constructor(json: String) : this() {
        val v = JsonTokener(json).nextValue()
        if (v !is JSONObject) throw JSONException("Value is not a JSONObject: ${json.take(80)}")
        map.putAll(v.map)
    }

    constructor(m: Map<*, *>?) : this() {
        m?.forEach { (k, v) -> if (k != null && v != null) map[k.toString()] = wrap(v) }
    }

    constructor(o: JSONObject, names: Array<String>) : this() {
        for (n in names) o.opt(n)?.let { map[n] = it }
    }

    companion object {
        val NULL: Any = JsonNull
        fun quote(s: String?): String = if (s == null) "\"\"" else buildString { appendQuoted(this, s) }
        fun wrap(o: Any?): Any? = when (o) {
            null -> NULL
            is JSONObject, is JSONArray, is String, is Boolean, is Int, is Long, is Double, is Float, is Short, is Byte -> o
            is Number -> o.toDouble()
            is Char -> o.toString()
            is Map<*, *> -> JSONObject(o)
            is Collection<*> -> JSONArray(o)
            is Array<*> -> JSONArray(o.toList())
            is IntArray -> JSONArray(o.toList())
            is LongArray -> JSONArray(o.toList())
            is DoubleArray -> JSONArray(o.toList())
            is Enum<*> -> o.name
            else -> o.toString()
        }
        fun numberToString(n: Number): String = numStr(n)
        fun getNames(o: JSONObject): Array<String>? = if (o.length() == 0) null else o.map.keys.toTypedArray()
    }

    fun length(): Int = map.size
    fun has(key: String): Boolean = map.containsKey(key)
    fun isNull(key: String): Boolean = map[key].let { it == null || it === JsonNull }
    fun keys(): MutableIterator<String> = map.keys.toMutableList().iterator()
    fun keySet(): MutableSet<String> = map.keys
    fun names(): JSONArray? = if (map.isEmpty()) null else JSONArray(map.keys.toList())
    fun remove(key: String): Any? = map.remove(key)
    fun toMap(): Map<String, Any?> = map.mapValues { (_, v) -> unwrap(v) }

    fun put(key: String, value: Any?): JSONObject {
        if (value == null) map.remove(key) else {
            if (value is Double && (value.isNaN() || value.isInfinite())) throw JSONException("Forbidden numeric value: $value")
            map[key] = wrap(value)
        }
        return this
    }
    fun put(key: String, value: Boolean): JSONObject { map[key] = value; return this }
    fun put(key: String, value: Int): JSONObject { map[key] = value; return this }
    fun put(key: String, value: Long): JSONObject { map[key] = value; return this }
    fun put(key: String, value: Double): JSONObject { map[key] = value; return this }
    fun put(key: String, value: Float): JSONObject { map[key] = value.toDouble(); return this }
    fun putOpt(key: String?, value: Any?): JSONObject { if (key != null && value != null) put(key, value); return this }
    fun accumulate(key: String, value: Any?): JSONObject {
        val cur = map[key]
        when {
            cur == null -> put(key, value)
            cur is JSONArray -> cur.put(value)
            else -> map[key] = JSONArray().put(cur).put(value)
        }
        return this
    }
    fun append(key: String, value: Any?): JSONObject {
        val cur = map[key]
        if (cur is JSONArray) cur.put(value) else map[key] = JSONArray().put(value)
        return this
    }
    fun increment(key: String): JSONObject {
        when (val cur = map[key]) {
            null -> map[key] = 1
            is Int -> map[key] = cur + 1
            is Long -> map[key] = cur + 1
            is Double -> map[key] = cur + 1
        }
        return this
    }

    fun get(key: String): Any = map[key]?.takeIf { true } ?: throw JSONException("JSONObject[\"$key\"] not found.")
    fun opt(key: String): Any? = map[key]

    fun getString(key: String): String {
        val v = get(key)
        if (v === JsonNull) return "null"
        return if (v is String) v else asString(v)
    }
    fun optString(key: String): String = optString(key, "")
    fun optString(key: String, fallback: String?): String {
        val v = map[key]
        return if (v == null || v === JsonNull) (fallback ?: "") else asString(v)
    }
    fun getInt(key: String): Int = toInt(get(key)) ?: throw JSONException("JSONObject[\"$key\"] is not an int.")
    fun optInt(key: String): Int = optInt(key, 0)
    fun optInt(key: String, fallback: Int): Int = toInt(map[key]) ?: fallback
    fun getLong(key: String): Long = toLong(get(key)) ?: throw JSONException("JSONObject[\"$key\"] is not a long.")
    fun optLong(key: String): Long = optLong(key, 0L)
    fun optLong(key: String, fallback: Long): Long = toLong(map[key]) ?: fallback
    fun getDouble(key: String): Double = toDouble(get(key)) ?: throw JSONException("JSONObject[\"$key\"] is not a number.")
    fun optDouble(key: String): Double = optDouble(key, Double.NaN)
    fun optDouble(key: String, fallback: Double): Double = toDouble(map[key]) ?: fallback
    fun optFloat(key: String, fallback: Float = Float.NaN): Float = toDouble(map[key])?.toFloat() ?: fallback
    fun getBoolean(key: String): Boolean = toBool(get(key)) ?: throw JSONException("JSONObject[\"$key\"] is not a Boolean.")
    fun optBoolean(key: String): Boolean = optBoolean(key, false)
    fun optBoolean(key: String, fallback: Boolean): Boolean = toBool(map[key]) ?: fallback
    fun getJSONObject(key: String): JSONObject = get(key) as? JSONObject ?: throw JSONException("JSONObject[\"$key\"] is not a JSONObject.")
    fun optJSONObject(key: String): JSONObject? = map[key] as? JSONObject
    fun getJSONArray(key: String): JSONArray = get(key) as? JSONArray ?: throw JSONException("JSONObject[\"$key\"] is not a JSONArray.")
    fun optJSONArray(key: String): JSONArray? = map[key] as? JSONArray
    fun optNumber(key: String): Number? = map[key] as? Number

    override fun toString(): String = buildString { writeValue(this, this@JSONObject, -1, 0) }
    fun toString(indent: Int): String = buildString { writeValue(this, this@JSONObject, indent, 0) }
    override fun equals(other: Any?): Boolean = other is JSONObject && other.map == map
    override fun hashCode(): Int = map.hashCode()
}

class JSONArray() : Iterable<Any?> {
    internal val list = ArrayList<Any?>()

    constructor(json: String) : this() {
        val v = JsonTokener(json).nextValue()
        if (v !is JSONArray) throw JSONException("Value is not a JSONArray: ${json.take(80)}")
        list.addAll(v.list)
    }

    constructor(c: Collection<*>?) : this() {
        c?.forEach { list.add(JSONObject.wrap(it)) }
    }

    constructor(arr: Array<*>) : this(arr.toList())

    fun length(): Int = list.size
    val size: Int get() = list.size
    override fun iterator(): Iterator<Any?> = list.iterator()
    fun isNull(i: Int): Boolean = list.getOrNull(i).let { it == null || it === JsonNull }

    fun put(value: Any?): JSONArray { list.add(JSONObject.wrap(value)); return this }
    fun put(value: Int): JSONArray { list.add(value); return this }
    fun put(value: Long): JSONArray { list.add(value); return this }
    fun put(value: Double): JSONArray { list.add(value); return this }
    fun put(value: Boolean): JSONArray { list.add(value); return this }
    fun put(index: Int, value: Any?): JSONArray {
        while (list.size <= index) list.add(JsonNull)
        list[index] = JSONObject.wrap(value)
        return this
    }
    fun putAll(c: Collection<*>): JSONArray { c.forEach { put(it) }; return this }
    fun remove(i: Int): Any? = if (i in list.indices) list.removeAt(i) else null

    fun get(i: Int): Any = list.getOrNull(i) ?: throw JSONException("JSONArray[$i] not found.")
    fun opt(i: Int): Any? = list.getOrNull(i)
    fun getString(i: Int): String = get(i).let { if (it is String) it else asString(it) }
    fun optString(i: Int): String = optString(i, "")
    fun optString(i: Int, fallback: String?): String = list.getOrNull(i).let { if (it == null || it === JsonNull) fallback ?: "" else asString(it) }
    fun getInt(i: Int): Int = toInt(get(i)) ?: throw JSONException("JSONArray[$i] is not a number.")
    fun optInt(i: Int, fallback: Int = 0): Int = toInt(list.getOrNull(i)) ?: fallback
    fun getLong(i: Int): Long = toLong(get(i)) ?: throw JSONException("JSONArray[$i] is not a number.")
    fun optLong(i: Int, fallback: Long = 0L): Long = toLong(list.getOrNull(i)) ?: fallback
    fun getDouble(i: Int): Double = toDouble(get(i)) ?: throw JSONException("JSONArray[$i] is not a number.")
    fun optDouble(i: Int, fallback: Double = Double.NaN): Double = toDouble(list.getOrNull(i)) ?: fallback
    fun getBoolean(i: Int): Boolean = toBool(get(i)) ?: throw JSONException("JSONArray[$i] is not a boolean.")
    fun optBoolean(i: Int, fallback: Boolean = false): Boolean = toBool(list.getOrNull(i)) ?: fallback
    fun getJSONObject(i: Int): JSONObject = get(i) as? JSONObject ?: throw JSONException("JSONArray[$i] is not a JSONObject.")
    fun optJSONObject(i: Int): JSONObject? = list.getOrNull(i) as? JSONObject
    fun getJSONArray(i: Int): JSONArray = get(i) as? JSONArray ?: throw JSONException("JSONArray[$i] is not a JSONArray.")
    fun optJSONArray(i: Int): JSONArray? = list.getOrNull(i) as? JSONArray
    fun join(sep: String): String = list.joinToString(sep) { if (it is String) JSONObject.quote(it) else stringify(it) }
    fun toList(): List<Any?> = list.map { unwrap(it) }

    override fun toString(): String = buildString { writeValue(this, this@JSONArray, -1, 0) }
    fun toString(indent: Int): String = buildString { writeValue(this, this@JSONArray, indent, 0) }
    override fun equals(other: Any?): Boolean = other is JSONArray && other.list == list
    override fun hashCode(): Int = list.hashCode()
}

class JSONTokener(private val s: String) {
    fun nextValue(): Any? = JsonTokener(s).nextValue()
}

private fun unwrap(v: Any?): Any? = when (v) {
    JsonNull -> null
    is JSONObject -> v.toMap()
    is JSONArray -> v.toList()
    else -> v
}

private fun numStr(n: Number): String = when (n) {
    is Double -> if (n == kotlin.math.floor(n) && !n.isInfinite() && kotlin.math.abs(n) < 1e15) n.toLong().toString() else n.toString()
    is Float -> numStr(n.toDouble())
    else -> n.toString()
}

private fun asString(v: Any?): String = when (v) {
    null, JsonNull -> "null"
    is String -> v
    is Number -> numStr(v)
    else -> v.toString()
}

private fun stringify(v: Any?): String = buildString { writeValue(this, v, -1, 0) }

private fun toInt(v: Any?): Int? = when (v) {
    is Number -> v.toInt()
    is String -> v.trim().toIntOrNull() ?: v.trim().toDoubleOrNull()?.toInt()
    else -> null
}

private fun toLong(v: Any?): Long? = when (v) {
    is Number -> v.toLong()
    is String -> v.trim().toLongOrNull() ?: v.trim().toDoubleOrNull()?.toLong()
    else -> null
}

private fun toDouble(v: Any?): Double? = when (v) {
    is Number -> v.toDouble()
    is String -> v.trim().toDoubleOrNull()
    else -> null
}

private fun toBool(v: Any?): Boolean? = when (v) {
    is Boolean -> v
    is String -> when (v.lowercase()) { "true" -> true; "false" -> false; else -> null }
    else -> null
}

internal fun appendQuoted(sb: StringBuilder, s: String) {
    sb.append('"')
    var prev = '\u0000'
    for (c in s) {
        when (c) {
            '"' -> sb.append("\\\"")
            '\\' -> sb.append("\\\\")
            '/' -> if (prev == '<') sb.append("\\/") else sb.append('/')
            '\b' -> sb.append("\\b")
            '\u000C' -> sb.append("\\f")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            else -> if (c < ' ' || c in '\u0080'..'\u009f' || c in ' '..'⃿') {
                sb.append("\\u").append(c.code.toString(16).padStart(4, '0'))
            } else sb.append(c)
        }
        prev = c
    }
    sb.append('"')
}

private fun writeValue(sb: StringBuilder, v: Any?, indent: Int, level: Int) {
    when (v) {
        null, JsonNull -> sb.append("null")
        is String -> appendQuoted(sb, v)
        is Number -> sb.append(numStr(v))
        is Boolean -> sb.append(v.toString())
        is JSONObject -> {
            if (v.map.isEmpty()) { sb.append("{}"); return }
            sb.append('{')
            var first = true
            for ((k, value) in v.map) {
                if (!first) sb.append(',')
                first = false
                if (indent > 0) { sb.append('\n'); repeat((level + 1) * indent) { sb.append(' ') } }
                appendQuoted(sb, k)
                sb.append(':')
                if (indent > 0) sb.append(' ')
                writeValue(sb, value, indent, level + 1)
            }
            if (indent > 0) { sb.append('\n'); repeat(level * indent) { sb.append(' ') } }
            sb.append('}')
        }
        is JSONArray -> {
            if (v.list.isEmpty()) { sb.append("[]"); return }
            sb.append('[')
            var first = true
            for (value in v.list) {
                if (!first) sb.append(',')
                first = false
                if (indent > 0) { sb.append('\n'); repeat((level + 1) * indent) { sb.append(' ') } }
                writeValue(sb, value, indent, level + 1)
            }
            if (indent > 0) { sb.append('\n'); repeat(level * indent) { sb.append(' ') } }
            sb.append(']')
        }
        is Map<*, *> -> writeValue(sb, JSONObject(v), indent, level)
        is Collection<*> -> writeValue(sb, JSONArray(v), indent, level)
        else -> appendQuoted(sb, v.toString())
    }
}

internal class JsonTokener(private val s: String) {
    private var i = 0

    private fun skipWs() {
        while (i < s.length) {
            val c = s[i]
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t' || c == '﻿') i++
            else if (c == '/' && i + 1 < s.length && s[i + 1] == '/') { while (i < s.length && s[i] != '\n') i++ }
            else if (c == '/' && i + 1 < s.length && s[i + 1] == '*') { val e = s.indexOf("*/", i + 2); i = if (e < 0) s.length else e + 2 }
            else break
        }
    }

    fun nextValue(): Any? {
        skipWs()
        if (i >= s.length) throw JSONException("End of input at character $i")
        return when (val c = s[i]) {
            '{' -> readObject()
            '[' -> readArray()
            '"', '\'' -> readString(c)
            else -> readLiteral()
        }
    }

    private fun readObject(): JSONObject {
        val o = JSONObject()
        i++
        skipWs()
        if (i < s.length && s[i] == '}') { i++; return o }
        while (true) {
            skipWs()
            if (i >= s.length) throw JSONException("Unterminated object")
            val key = when (s[i]) {
                '"', '\'' -> readString(s[i])
                else -> readLiteral().toString()
            }
            skipWs()
            if (i >= s.length || (s[i] != ':' && s[i] != '=')) throw JSONException("Expected ':' after $key at $i")
            i++
            if (i < s.length && s[i] == '>') i++
            val v = nextValue()
            o.map[key] = v ?: JSONObject.NULL
            skipWs()
            if (i >= s.length) throw JSONException("Unterminated object")
            when (s[i]) {
                ',', ';' -> { i++; skipWs(); if (i < s.length && s[i] == '}') { i++; return o } }
                '}' -> { i++; return o }
                else -> throw JSONException("Expected ',' or '}' at $i")
            }
        }
    }

    private fun readArray(): JSONArray {
        val a = JSONArray()
        i++
        skipWs()
        if (i < s.length && s[i] == ']') { i++; return a }
        while (true) {
            skipWs()
            if (i < s.length && s[i] == ',') { a.list.add(JSONObject.NULL); i++; continue }
            a.list.add(nextValue() ?: JSONObject.NULL)
            skipWs()
            if (i >= s.length) throw JSONException("Unterminated array")
            when (s[i]) {
                ',' -> { i++; skipWs(); if (i < s.length && s[i] == ']') { i++; return a } }
                ']' -> { i++; return a }
                else -> throw JSONException("Expected ',' or ']' at $i")
            }
        }
    }

    private fun readString(q: Char): String {
        i++
        val sb = StringBuilder()
        while (i < s.length) {
            val c = s[i++]
            when (c) {
                q -> return sb.toString()
                '\\' -> {
                    if (i >= s.length) break
                    when (val e = s[i++]) {
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'u' -> {
                            val hex = s.substring(i, minOf(i + 4, s.length))
                            sb.append(hex.toInt(16).toChar())
                            i += 4
                        }
                        else -> sb.append(e)
                    }
                }
                else -> sb.append(c)
            }
        }
        throw JSONException("Unterminated string")
    }

    private fun readLiteral(): Any {
        val start = i
        while (i < s.length && s[i] !in ",:]}/\\\"[{;=#" && !s[i].isWhitespace()) i++
        val tok = s.substring(start, i)
        if (tok.isEmpty()) throw JSONException("Expected literal value at character $start")
        return when (tok) {
            "true" -> true
            "false" -> false
            "null" -> JSONObject.NULL
            else -> {
                if (tok[0].isDigit() || tok[0] == '-' || tok[0] == '.') {
                    if (tok.contains('.') || tok.contains('e') || tok.contains('E')) tok.toDoubleOrNull() ?: tok
                    else tok.toIntOrNull() ?: tok.toLongOrNull() ?: tok.toDoubleOrNull() ?: tok
                } else tok
            }
        }
    }
}
