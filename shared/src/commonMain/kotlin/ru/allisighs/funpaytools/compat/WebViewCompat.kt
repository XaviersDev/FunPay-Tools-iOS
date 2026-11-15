package ru.allisighs.funpaytools

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/* android.webkit.* emulation. The actual browser is WKWebView on iOS (see PlatformWebViewHost). */

open class View {
    var parent: ViewGroup? = null
    var layoutParams: ViewGroup.LayoutParams? = null
    var visibility: Int = VISIBLE
    var tag: Any? = null
    internal var longClick: ((View) -> Boolean)? = null
    fun setOnLongClickListener(l: (View) -> Boolean) { longClick = l }
    fun setOnClickListener(l: (View) -> Unit) {}
    fun setBackgroundColor(c: Int) {}
    fun requestFocus(): Boolean = true
    fun post(r: () -> Unit): Boolean { PlatformBridge.runOnMain(r); return true }
    fun postDelayed(r: () -> Unit, ms: Long): Boolean = Handler().postDelayed(r, ms)
    val windowToken: Any? get() = null
    companion object {
        const val VISIBLE = 0
        const val INVISIBLE = 4
        const val GONE = 8
    }
}

open class ViewGroup : View() {
    fun removeView(v: View?) {}
    fun addView(v: View?) {}
    open class LayoutParams(val width: Int, val height: Int) {
        companion object {
            const val MATCH_PARENT = -1
            const val WRAP_CONTENT = -2
        }
    }
}

fun interface ValueCallback<T> {
    fun onReceiveValue(value: T)
}

class WebResourceRequest(val url: Uri, val isForMainFrame: Boolean = true, val method: String = "GET", val requestHeaders: Map<String, String> = emptyMap()) {
    val isRedirect: Boolean get() = false
    fun hasGesture(): Boolean = true
}

class WebResourceError(val errorCode: Int, val description: CharSequence)

class WebSettings {
    var javaScriptEnabled = true
    var domStorageEnabled = true
    var databaseEnabled = true
    var useWideViewPort = true
    var loadWithOverviewMode = true
    var javaScriptCanOpenWindowsAutomatically = false
    var cacheMode = LOAD_DEFAULT
    var userAgentString: String? = null
    var mixedContentMode = 0
    var allowFileAccess = false
    var allowContentAccess = false
    var allowFileAccessFromFileURLs = false
    var allowUniversalAccessFromFileURLs = false
    var builtInZoomControls = false
    var displayZoomControls = false
    var mediaPlaybackRequiresUserGesture = true
    var loadsImagesAutomatically = true
    var blockNetworkImage = false
    var textZoom = 100
    var defaultTextEncodingName = "utf-8"
    fun setSupportMultipleWindows(b: Boolean) {}
    fun setSupportZoom(b: Boolean) {}
    fun setGeolocationEnabled(b: Boolean) {}
    fun setRenderPriority(p: Any?) {}
    companion object {
        const val LOAD_DEFAULT = -1
        const val LOAD_NO_CACHE = 2
        const val LOAD_CACHE_ELSE_NETWORK = 1
        const val MIXED_CONTENT_ALWAYS_ALLOW = 0
        const val MIXED_CONTENT_NEVER_ALLOW = 1
        const val MIXED_CONTENT_COMPATIBILITY_MODE = 2
        fun getDefaultUserAgent(c: Context?): String = IOS_SAFARI_UA
    }
}

const val IOS_SAFARI_UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1"

open class WebViewClient {
    open fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = shouldOverrideUrlLoading(view, request?.url?.toString())
    open fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean = false
    open fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {}
    open fun onPageFinished(view: WebView?, url: String?) {}
    open fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {}
    open fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {}
    open fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {}
    open fun onLoadResource(view: WebView?, url: String?) {}
}

open class WebChromeClient {
    open fun onProgressChanged(view: WebView?, newProgress: Int) {}
    open fun onReceivedTitle(view: WebView?, title: String?) {}
    open fun onConsoleMessage(message: Any?): Boolean = false
}

/** Platform implementation attached when the WebView is shown on screen. */
interface PlatformWebViewImpl {
    fun loadUrl(url: String, headers: Map<String, String>)
    fun loadHtml(html: String, baseUrl: String?)
    fun goBack()
    fun goForward()
    fun canGoBack(): Boolean
    fun canGoForward(): Boolean
    fun reload()
    fun stopLoading()
    fun evaluateJavascript(js: String, callback: ((String?) -> Unit)?)
    val url: String?
    val title: String?
    val progress: Int
    fun destroy()
}

class WebView(context: Context? = null) : ViewGroup() {
    val settings = WebSettings()
    var webViewClient: WebViewClient = WebViewClient()
    var webChromeClient: WebChromeClient? = null
    internal var impl: PlatformWebViewImpl? = null
    private val pending = ArrayList<(PlatformWebViewImpl) -> Unit>()
    private var destroyed = false

    internal fun attach(i: PlatformWebViewImpl) {
        impl = i
        val p = pending.toList()
        pending.clear()
        p.forEach { it(i) }
    }

    private fun run(op: (PlatformWebViewImpl) -> Unit) {
        if (destroyed) return
        val i = impl
        if (i != null) op(i) else pending.add(op)
    }

    fun loadUrl(url: String) = run { it.loadUrl(url, emptyMap()) }
    fun loadUrl(url: String, headers: Map<String, String>) = run { it.loadUrl(url, headers) }
    fun loadData(data: String, mime: String?, enc: String?) = run { it.loadHtml(data, null) }
    fun loadDataWithBaseURL(baseUrl: String?, data: String, mime: String?, enc: String?, historyUrl: String?) = run { it.loadHtml(data, baseUrl) }
    fun goBack() = run { it.goBack() }
    fun goForward() = run { it.goForward() }
    fun canGoBack(): Boolean = impl?.canGoBack() == true
    fun canGoForward(): Boolean = impl?.canGoForward() == true
    fun reload() = run { it.reload() }
    fun stopLoading() = run { it.stopLoading() }
    fun clearHistory() {}
    fun clearCache(includeDisk: Boolean) {}
    fun clearFormData() {}
    fun onPause() {}
    fun onResume() {}
    fun addJavascriptInterface(obj: Any, name: String) {}
    fun removeJavascriptInterface(name: String) {}
    fun evaluateJavascript(js: String, callback: ValueCallback<String>?) = run { i -> i.evaluateJavascript(js) { r -> callback?.onReceiveValue(r ?: "null") } }
    val url: String? get() = impl?.url
    val originalUrl: String? get() = impl?.url
    val title: String? get() = impl?.title
    val progress: Int get() = impl?.progress ?: 0
    val hitTestResult: HitTestResult get() = HitTestResult()
    fun destroy() {
        destroyed = true
        impl?.destroy()
        impl = null
        pending.clear()
    }

    class HitTestResult {
        val type: Int = UNKNOWN_TYPE
        val extra: String? = null
        companion object {
            const val UNKNOWN_TYPE = 0
            const val SRC_ANCHOR_TYPE = 7
            const val SRC_IMAGE_ANCHOR_TYPE = 8
            const val IMAGE_TYPE = 5
        }
    }

    companion object {
        fun setWebContentsDebuggingEnabled(b: Boolean) {}
    }
}

/** android.webkit.CookieManager over the platform web cookie store (WKHTTPCookieStore on iOS). */
expect object PlatformWebCookies {
    /** Cached "name=value; name2=value2" for the url (kept in sync with the web view store). */
    fun getCookie(url: String): String?
    fun setCookie(url: String, setCookieValue: String, done: (() -> Unit)?)
    fun removeAll(done: (() -> Unit)?)
    fun refresh(done: (() -> Unit)?)
}

class CookieManager private constructor() {
    companion object {
        private val inst = CookieManager()
        fun getInstance(): CookieManager = inst
    }
    fun setAcceptCookie(b: Boolean) {}
    fun setAcceptThirdPartyCookies(v: WebView?, b: Boolean) {}
    fun acceptCookie(): Boolean = true
    fun getCookie(url: String?): String? = url?.let { PlatformWebCookies.getCookie(it) }
    fun setCookie(url: String, value: String) = PlatformWebCookies.setCookie(url, value, null)
    fun setCookie(url: String, value: String, cb: ValueCallback<Boolean>?) = PlatformWebCookies.setCookie(url, value) { cb?.onReceiveValue(true) }
    fun removeAllCookies(cb: ValueCallback<Boolean>?) = PlatformWebCookies.removeAll { cb?.onReceiveValue(true) }
    fun removeAllCookie() = PlatformWebCookies.removeAll(null)
    fun removeSessionCookies(cb: ValueCallback<Boolean>?) { cb?.onReceiveValue(true) }
    fun hasCookies(): Boolean = true
    fun flush() {}
}

class WebStorage private constructor() {
    companion object {
        private val inst = WebStorage()
        fun getInstance(): WebStorage = inst
    }
    fun deleteAllData() = PlatformWebCookies.removeAll(null)
}

@Composable
expect fun PlatformWebViewHost(webView: WebView, modifier: Modifier)

/** androidx.compose.ui.viewinterop.AndroidView: only WebView content is supported on iOS. */
@Composable
fun <T : View> AndroidView(factory: (Context) -> T, modifier: Modifier = Modifier, update: (T) -> Unit = {}) {
    val view = remember { factory(AppContext) }
    if (view is WebView) PlatformWebViewHost(view, modifier)
    update(view)
}

@Composable
fun <T : View> AndroidView(factory: (Context) -> T, modifier: Modifier = Modifier, onRelease: (T) -> Unit, update: (T) -> Unit = {}) =
    AndroidView(factory, modifier, update)
