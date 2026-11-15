package ru.allisighs.funpaytools

import platform.Foundation.NSArray
import platform.Foundation.NSNull
import platform.Foundation.NSNumber
import platform.JavaScriptCore.JSContext
import platform.JavaScriptCore.JSValue
import platform.JavaScriptCore.setObject
import platform.JavaScriptCore.objectForKeyedSubscript

actual class JsEngine actual constructor() {
    private val ctx = JSContext()
    private var handler: ((String, List<Any?>) -> Any?)? = null

    init {
        ctx.exceptionHandler = { _, exc ->
            LogManager.addLog("🧩 JS error: ${exc?.toString_()}")
        }
        val block: (Any?, Any?) -> Any? = { name, args ->
            val list: List<Any?> = when (args) {
                is List<*> -> args.map { normalize(it) }
                is NSArray -> (0 until args.count.toInt()).map { normalize(args.objectAtIndex(it.toULong())) }
                else -> emptyList()
            }
            val r = handler?.invoke(name?.toString() ?: "", list)
            when (r) {
                null -> NSNull()
                is Boolean -> r
                is Int -> r
                is Long -> r.toDouble()
                is Double -> r
                is Float -> r.toDouble()
                else -> r.toString()
            }
        }
        ctx.setObject(block, forKeyedSubscript = "__fptCall" as platform.Foundation.NSString)
    }

    private fun normalize(v: Any?): Any? = when (v) {
        null, is NSNull -> null
        is Boolean, is String, is Int, is Long, is Double -> v
        is NSNumber -> v.doubleValue
        else -> v.toString()
    }

    actual fun setNativeHandler(handler: (String, List<Any?>) -> Any?) { this.handler = handler }

    actual fun evaluate(js: String): String? {
        val v: JSValue = ctx.evaluateScript(js) ?: return null
        return when {
            v.isUndefined() || v.isNull() -> "null"
            v.isString() -> JSONObject.quote(v.toString_())
            v.isBoolean() -> v.toBool().toString()
            v.isNumber() -> {
                val d = v.toDouble()
                if (d == kotlin.math.floor(d) && kotlin.math.abs(d) < 1e15) d.toLong().toString() else d.toString()
            }
            else -> v.toString_()
        }
    }

    actual fun close() {
        handler = null
    }
}
