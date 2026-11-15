package ru.allisighs.funpaytools

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext

/** Bare JavaScript VM (JavaScriptCore on iOS). All calls must happen on [PluginJsRuntime.thread]. */
expect class JsEngine() {
    /** Native function exposed to JS as __fptCall(name, argsArray). */
    fun setNativeHandler(handler: (String, List<Any?>) -> Any?)
    /** Evaluates script, returns JSON-ish string result like WebView.evaluateJavascript. */
    fun evaluate(js: String): String?
    fun close()
}

/** Plugin JS runtime: single thread, timers, console, fptNative proxy. */
@OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.DelicateCoroutinesApi::class)
class PluginJsRuntime(private val bridgeCall: (String, List<Any?>) -> Any?) {
    companion object {
        val thread = newSingleThreadContext("fpt-plugins-js")

        private const val POLYFILL = """
            var window = this; var self = this; var globalThis = this;
            var console = {
              log: function(){ __fptCall('__console', [Array.prototype.slice.call(arguments).map(String).join(' ')]); },
              info: function(){ console.log.apply(null, arguments); },
              warn: function(){ console.log.apply(null, arguments); },
              error: function(){ console.log.apply(null, arguments); },
              debug: function(){}
            };
            var __timers = {}; var __timerSeq = 1;
            function setTimeout(fn, ms){ var id = __timerSeq++; __timers[id] = {fn: fn, args: Array.prototype.slice.call(arguments, 2), repeat: false, ms: +ms || 0}; __fptCall('__timer', [id, +ms || 0]); return id; }
            function setInterval(fn, ms){ var id = __timerSeq++; __timers[id] = {fn: fn, args: Array.prototype.slice.call(arguments, 2), repeat: true, ms: Math.max(+ms || 0, 10)}; __fptCall('__timer', [id, Math.max(+ms || 0, 10)]); return id; }
            function clearTimeout(id){ delete __timers[id]; }
            function clearInterval(id){ delete __timers[id]; }
            function queueMicrotask(fn){ setTimeout(fn, 0); }
            function __fireTimer(id){
              var t = __timers[id]; if (!t) return;
              if (!t.repeat) delete __timers[id];
              try { if (typeof t.fn === 'function') t.fn.apply(null, t.args); else eval(String(t.fn)); }
              catch(e) { console.log('Timer error: ' + (e && e.message ? e.message : e)); }
              if (t.repeat && __timers[id]) __fptCall('__timer', [id, t.ms]);
            }
            var fptNative = new Proxy({}, { get: function(_, name){ return function(){ return __fptCall(String(name), Array.prototype.slice.call(arguments)); }; } });
            var document = { addEventListener: function(){}, createElement: function(){ return {}; }, body: {}, title: 'FPT JS Engine' };
            var navigator = { userAgent: 'FunPayTools-iOS' };
            var location = { href: 'https://funpay.tools/plugins' };
        """
    }

    private val scope = CoroutineScope(SupervisorJob() + thread)
    private var engine: JsEngine? = null
    @Volatile private var closed = false

    fun start(onReady: (PluginJsRuntime) -> Unit) {
        scope.launch {
            val e = JsEngine()
            e.setNativeHandler { name, args -> handle(name, args) }
            e.evaluate(POLYFILL)
            engine = e
            onReady(this@PluginJsRuntime)
        }
    }

    private fun handle(name: String, args: List<Any?>): Any? = when (name) {
        "__console" -> { LogManager.addLogDebug("🧩 JS: ${args.firstOrNull()}"); null }
        "__timer" -> {
            val id = (args.getOrNull(0) as? Number)?.toInt() ?: 0
            val ms = (args.getOrNull(1) as? Number)?.toLong() ?: 0L
            scope.launch {
                delay(ms)
                if (!closed) engine?.evaluate("__fireTimer($id)")
            }
            null
        }
        else -> try { bridgeCall(name, args) } catch (e: Exception) {
            LogManager.addLog("⚠️ Plugin bridge: ${e::class.simpleName}: ${e.message}"); null
        }
    }

    /** Must be called from the runtime thread (inside [post]). */
    fun evalNow(js: String): String? = if (closed) null else engine?.evaluate(js)

    fun post(js: String, callback: ((String?) -> Unit)? = null) {
        if (closed) return
        scope.launch {
            val r = try { engine?.evaluate(js) } catch (e: Throwable) { null }
            callback?.invoke(r)
        }
    }

    fun close() {
        closed = true
        scope.launch {
            try { engine?.close() } catch (_: Throwable) {}
            engine = null
        }
    }
}
