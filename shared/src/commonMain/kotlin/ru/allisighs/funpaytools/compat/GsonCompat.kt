package ru.allisighs.funpaytools

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.serializer
import kotlinx.serialization.serializerOrNull
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.typeOf

val gsonJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    coerceInputValues = true
    isLenient = true
    allowSpecialFloatingPointValues = true
    allowStructuredMapKeys = true
}

val gsonJsonPretty = Json(gsonJson) { prettyPrint = true }

class GsonType<T> internal constructor()

abstract class TypeToken<T> {
    val type: GsonType<T> get() = GsonType()
    val rawType: GsonType<T> get() = GsonType()
}

class JsonSyntaxException(msg: String?) : RuntimeException(msg)

class Gson internal constructor(private val pretty: Boolean) {
    constructor() : this(false)

    val json: Json get() = if (pretty) gsonJsonPretty else gsonJson

    inline fun <reified T> fromJson(text: String?, type: GsonType<T>): T = gsonDecode(json, text, typeOf<T>())
    inline fun <reified T : Any> fromJson(text: String?, cls: KClass<T>): T = gsonDecode(json, text, typeOf<T>())
    inline fun <reified T> fromJson(text: String?): T = gsonDecode(json, text, typeOf<T>())
    inline fun <reified T> fromJson(reader: Reader, type: GsonType<T>): T = gsonDecode(json, reader.readText(), typeOf<T>())
    inline fun <reified T : Any> fromJson(el: JsonElement?, cls: KClass<T>): T = gsonDecode(json, el?.toString(), typeOf<T>())

    inline fun <reified T> toJson(value: T): String = gsonEncode(json, value, typeOf<T>())
    fun toJsonTree(value: Any?): JsonElement = gsonJson.parseToJsonElement(gsonEncodeAny(json, value))
}

class GsonBuilder {
    private var pretty = false
    fun setPrettyPrinting() = apply { pretty = true }
    fun serializeNulls() = this
    fun setLenient() = this
    fun disableHtmlEscaping() = this
    fun create(): Gson = Gson(pretty)
}

@Suppress("UNCHECKED_CAST")
fun <T> gsonDecode(json: Json, text: String?, type: KType): T {
    if (text == null || text.isBlank() || text.trim() == "null") return null as T
    val ser = try { serializer(type) } catch (e: Exception) { null }
    return try {
        if (ser != null) json.decodeFromString(ser, text) as T
        else dynamicFromJson(json.parseToJsonElement(text)) as T
    } catch (e: Exception) {
        throw JsonSyntaxException(e.message)
    }
}

fun <T> gsonEncode(json: Json, value: T, type: KType): String {
    if (value == null) return "null"
    val ser = try { serializer(type) } catch (e: Exception) { null }
    if (ser != null) {
        try {
            @Suppress("UNCHECKED_CAST")
            return json.encodeToString(ser as KSerializer<Any?>, value)
        } catch (_: Exception) {}
    }
    return gsonEncodeAny(json, value)
}

@OptIn(InternalSerializationApi::class)
fun gsonEncodeAny(json: Json, value: Any?): String {
    if (value == null) return "null"
    val ser = value::class.serializerOrNull()
    if (ser != null) {
        @Suppress("UNCHECKED_CAST")
        return json.encodeToString(ser as KSerializer<Any>, value)
    }
    return json.encodeToString(JsonElement.serializer(), dynamicToJson(json, value))
}

/** Gson-like untyped decoding: objects -> LinkedHashMap, arrays -> ArrayList, numbers -> Double. */
fun dynamicFromJson(e: JsonElement): Any? = when (e) {
    is JsonNull -> null
    is JsonObject -> LinkedHashMap<String, Any?>().also { m -> e.forEach { (k, v) -> m[k] = dynamicFromJson(v) } }
    is JsonArray -> ArrayList<Any?>().also { l -> e.forEach { l.add(dynamicFromJson(it)) } }
    is JsonPrimitive -> when {
        e.isString -> e.content
        e.booleanOrNull != null -> e.booleanOrNull
        else -> e.doubleOrNull ?: e.content
    }
}

@OptIn(InternalSerializationApi::class)
fun dynamicToJson(json: Json, v: Any?): JsonElement = when (v) {
    null -> JsonNull
    is JsonElement -> v
    is String -> JsonPrimitive(v)
    is Number -> JsonPrimitive(v)
    is Boolean -> JsonPrimitive(v)
    is Char -> JsonPrimitive(v.toString())
    is Enum<*> -> JsonPrimitive(v.name)
    is Map<*, *> -> JsonObject(v.entries.associate { (k, value) -> k.toString() to dynamicToJson(json, value) })
    is Iterable<*> -> JsonArray(v.map { dynamicToJson(json, it) })
    is Array<*> -> JsonArray(v.map { dynamicToJson(json, it) })
    is JSONObject -> json.parseToJsonElement(v.toString())
    is JSONArray -> json.parseToJsonElement(v.toString())
    else -> {
        val ser = v::class.serializerOrNull()
        @Suppress("UNCHECKED_CAST")
        if (ser != null) json.encodeToJsonElement(ser as KSerializer<Any>, v) else JsonPrimitive(v.toString())
    }
}

object JsonParser {
    fun parseString(s: String): JsonElement = gsonJson.parseToJsonElement(s)
}
