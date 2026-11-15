package ru.allisighs.funpaytools

import platform.Foundation.NSNull
import platform.Foundation.NSNumber

/** Native-call callback handed to the Swift JavaScriptCore backend. */
interface JsNativeHandler {
    fun call(name: String, args: List<Any?>): Any?
}

/** Implemented in Swift (JSContext + @convention(block) bridge), registered at launch. */
interface JsEngineBackend {
    fun install(handler: JsNativeHandler)
    fun evaluate(js: String): String?
    fun close()
}

object JsEngineRegistry {
    var factory: (() -> JsEngineBackend)? = null
}

fun jsLog(message: String) {
    LogManager.addLog("🧩 JS: $message")
}

actual class JsEngine actual constructor() {
    private val backend: JsEngineBackend? = JsEngineRegistry.factory?.invoke()
    private var handler: ((String, List<Any?>) -> Any?)? = null

    init {
        if (backend == null) LogManager.addLog("⚠️ JS-движок недоступен: плагины не будут работать")
        backend?.install(object : JsNativeHandler {
            override fun call(name: String, args: List<Any?>): Any? =
                handler?.invoke(name, args.map { normalize(it) })
        })
    }

    private fun normalize(v: Any?): Any? = when (v) {
        null, is NSNull -> null
        is Boolean, is String, is Int, is Long, is Double -> v
        is NSNumber -> v.doubleValue
        else -> v.toString()
    }

    actual fun setNativeHandler(handler: (String, List<Any?>) -> Any?) { this.handler = handler }
    actual fun evaluate(js: String): String? = backend?.evaluate(js)
    actual fun close() { handler = null; backend?.close() }
}
