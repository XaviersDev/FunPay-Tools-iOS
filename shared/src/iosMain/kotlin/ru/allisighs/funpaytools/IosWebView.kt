package ru.allisighs.funpaytools

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ObjCSignatureOverride
import platform.CoreGraphics.CGRectMake
import platform.Foundation.*
import platform.Foundation.NSDate
import platform.Foundation.NSError
import platform.Foundation.NSHTTPCookie
import platform.Foundation.NSHTTPCookieDomain
import platform.Foundation.NSHTTPCookieExpires
import platform.Foundation.NSHTTPCookieName
import platform.Foundation.NSHTTPCookiePath
import platform.Foundation.NSHTTPCookieSecure
import platform.Foundation.NSHTTPCookieValue
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.Foundation.distantPast
import platform.Foundation.setValue
import platform.WebKit.WKHTTPCookieStore
import platform.WebKit.WKHTTPCookieStoreObserverProtocol
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigationActionPolicy
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKUIDelegateProtocol
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.WKWebsiteDataStore
import platform.WebKit.WKWindowFeatures
import platform.darwin.NSObject

// ------------------------------------------------------------------------------------------------
// Cookies (shared WKWebsiteDataStore, mirrored into a synchronous cache like android CookieManager)
// ------------------------------------------------------------------------------------------------
private class CookieObserver : NSObject(), WKHTTPCookieStoreObserverProtocol {
    override fun cookiesDidChangeInCookieStore(cookieStore: WKHTTPCookieStore) {
        PlatformWebCookies.refresh(null)
    }
}

actual object PlatformWebCookies {
    private data class C(val name: String, val value: String, val domain: String, val path: String)

    private var cache: List<C> = emptyList()
    private var pendingOps = 0
    private val idleWaiters = ArrayList<() -> Unit>()
    private val observer = CookieObserver()
    private var observing = false

    private val store: WKHTTPCookieStore get() = WKWebsiteDataStore.defaultDataStore().httpCookieStore

    private fun ensureObserver() {
        if (observing) return
        observing = true
        store.addObserver(observer)
    }

    actual fun refresh(done: (() -> Unit)?) = PlatformBridge.runOnMain {
        ensureObserver()
        store.getAllCookies { list ->
            cache = (list ?: emptyList<Any?>()).mapNotNull { (it as? NSHTTPCookie)?.let { c -> C(c.name, c.value, c.domain, c.path) } }
            done?.invoke()
        }
    }

    actual fun getCookie(url: String): String? {
        val u = Uri.parse(url)
        val host = (u.host ?: return null).lowercase()
        val path = u.path?.ifEmpty { "/" } ?: "/"
        val matched = cache.filter { c ->
            val d = c.domain.lowercase().trimStart('.')
            (host == d || host.endsWith(".$d")) && path.startsWith(c.path.ifEmpty { "/" })
        }
        if (matched.isEmpty()) return null
        return matched.joinToString("; ") { "${it.name}=${it.value}" }
    }

    actual fun setCookie(url: String, setCookieValue: String, done: (() -> Unit)?) = PlatformBridge.runOnMain {
        val parts = setCookieValue.split(';').map { it.trim() }.filter { it.isNotEmpty() }
        val first = parts.firstOrNull() ?: return@runOnMain
        val name = first.substringBefore('=').trim()
        val value = first.substringAfter('=', "").trim()
        val attrs = parts.drop(1).associate { it.substringBefore('=').trim().lowercase() to it.substringAfter('=', "").trim() }
        val host = Uri.parse(url).host ?: "funpay.com"
        val props = HashMap<Any?, Any?>()
        props[NSHTTPCookieName] = name
        props[NSHTTPCookieValue] = value
        props[NSHTTPCookieDomain] = attrs["domain"]?.ifEmpty { null } ?: host
        props[NSHTTPCookiePath] = attrs["path"]?.ifEmpty { null } ?: "/"
        if (attrs.containsKey("secure") || url.startsWith("https")) props[NSHTTPCookieSecure] = "TRUE"
        props[NSHTTPCookieExpires] = NSDate.dateWithTimeIntervalSinceNow(60.0 * 60 * 24 * 365)
        val cookie = NSHTTPCookie.cookieWithProperties(props) ?: return@runOnMain
        pendingOps++
        ensureObserver()
        store.setCookie(cookie) {
            pendingOps--
            cache = cache.filterNot { it.name == name && it.domain.trimStart('.') == cookie.domain.trimStart('.') } + C(name, value, cookie.domain, cookie.path)
            done?.invoke()
            if (pendingOps == 0) flushWaiters()
        }
    }

    actual fun removeAll(done: (() -> Unit)?) = PlatformBridge.runOnMain {
        pendingOps++
        val ds = WKWebsiteDataStore.defaultDataStore()
        ds.removeDataOfTypes(WKWebsiteDataStore.allWebsiteDataTypes(), modifiedSince = NSDate.distantPast) {
            pendingOps--
            cache = emptyList()
            done?.invoke()
            if (pendingOps == 0) flushWaiters()
        }
    }

    /** Runs [block] on main once all cookie writes have completed (so a page load sees them). */
    fun whenIdle(block: () -> Unit) = PlatformBridge.runOnMain {
        if (pendingOps == 0) block() else idleWaiters.add(block)
    }

    private fun flushWaiters() {
        val w = idleWaiters.toList()
        idleWaiters.clear()
        w.forEach { it() }
    }
}

// ------------------------------------------------------------------------------------------------
// WKWebView-backed android WebView
// ------------------------------------------------------------------------------------------------
private class NavDelegate(private val owner: IosWebViewImpl) : NSObject(), WKNavigationDelegateProtocol, WKUIDelegateProtocol {
    override fun webView(webView: WKWebView, decidePolicyForNavigationAction: WKNavigationAction, decisionHandler: (WKNavigationActionPolicy) -> Unit) {
        val url = decidePolicyForNavigationAction.request.URL?.absoluteString
        val mainFrame = decidePolicyForNavigationAction.targetFrame?.isMainFrame() ?: true
        if (url == null) { decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyAllow); return }
        if (decidePolicyForNavigationAction.targetFrame == null) {
            // target=_blank / window.open: stay in this web view
            decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyCancel)
            webView.loadRequest(decidePolicyForNavigationAction.request)
            return
        }
        val override = if (mainFrame) try {
            owner.view.webViewClient.shouldOverrideUrlLoading(owner.view, WebResourceRequest(Uri.parse(url), true, decidePolicyForNavigationAction.request.HTTPMethod ?: "GET"))
        } catch (e: Exception) { false } else false
        val scheme = Uri.parse(url).scheme?.lowercase()
        if (override) {
            decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyCancel)
        } else if (scheme != null && scheme !in setOf("http", "https", "about", "data", "blob", "file", "javascript")) {
            decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyCancel)
            PlatformBridge.openUrl(url)
        } else {
            decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyAllow)
        }
    }

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didStartProvisionalNavigation: WKNavigation?) {
        owner.progressValue = 10
        val u = webView.URL?.absoluteString
        owner.view.webViewClient.onPageStarted(owner.view, u, null)
        owner.view.webChromeClient?.onProgressChanged(owner.view, 10)
    }

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didCommitNavigation: WKNavigation?) {
        owner.progressValue = 60
        val u = webView.URL?.absoluteString
        owner.view.webViewClient.doUpdateVisitedHistory(owner.view, u, false)
        owner.view.webChromeClient?.onProgressChanged(owner.view, 60)
    }

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
        owner.finished(webView.URL?.absoluteString)
    }

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didFailNavigation: WKNavigation?, withError: NSError) {
        owner.failed(webView.URL?.absoluteString, withError)
    }

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didFailProvisionalNavigation: WKNavigation?, withError: NSError) {
        owner.failed(webView.URL?.absoluteString, withError)
    }

    override fun webView(webView: WKWebView, createWebViewWithConfiguration: WKWebViewConfiguration, forNavigationAction: WKNavigationAction, windowFeatures: WKWindowFeatures): WKWebView? {
        webView.loadRequest(forNavigationAction.request)
        return null
    }
}

class IosWebViewImpl(val view: WebView) : PlatformWebViewImpl {
    val wk: WKWebView
    private val delegate = NavDelegate(this)
    var progressValue = 0

    init {
        val cfg = WKWebViewConfiguration()
        cfg.websiteDataStore = WKWebsiteDataStore.defaultDataStore()
        cfg.preferences.javaScriptCanOpenWindowsAutomatically = view.settings.javaScriptCanOpenWindowsAutomatically
        wk = WKWebView(frame = CGRectMake(0.0, 0.0, 390.0, 700.0), configuration = cfg)
        wk.navigationDelegate = delegate
        wk.UIDelegate = delegate
        wk.allowsBackForwardNavigationGestures = true
        val ua = view.settings.userAgentString
        wk.customUserAgent = if (ua == null || ua.contains("Android", ignoreCase = true)) IOS_SAFARI_UA else ua
        PlatformWebCookies.refresh(null)
    }

    internal fun finished(url: String?) {
        progressValue = 100
        view.webViewClient.doUpdateVisitedHistory(view, url, false)
        PlatformWebCookies.refresh {
            view.webChromeClient?.onReceivedTitle(view, wk.title)
            view.webChromeClient?.onProgressChanged(view, 100)
            view.webViewClient.onPageFinished(view, url)
        }
    }

    internal fun failed(url: String?, err: NSError) {
        progressValue = 100
        if (err.code != -999L) {
            view.webViewClient.onReceivedError(view, url?.let { WebResourceRequest(Uri.parse(it)) }, WebResourceError(err.code.toInt(), err.localizedDescription))
            view.webViewClient.onReceivedError(view, err.code.toInt(), err.localizedDescription, url)
        }
        view.webChromeClient?.onProgressChanged(view, 100)
        view.webViewClient.onPageFinished(view, url)
    }

    override fun loadUrl(url: String, headers: Map<String, String>) {
        PlatformWebCookies.whenIdle {
            if (url.startsWith("javascript:")) {
                wk.evaluateJavaScript(urlDecode(url.removePrefix("javascript:")), null)
                return@whenIdle
            }
            val nsUrl = NSURL.URLWithString(url) ?: NSURL.URLWithString(uriEncode(url, ":/?#[]@!$&'()*+,;=%")) ?: return@whenIdle
            val req = NSMutableURLRequest.requestWithURL(nsUrl)
            headers.forEach { (k, v) -> req.setValue(v, forHTTPHeaderField = k) }
            wk.loadRequest(req)
        }
    }

    override fun loadHtml(html: String, baseUrl: String?) {
        wk.loadHTMLString(html, baseURL = baseUrl?.let { NSURL.URLWithString(it) })
    }

    override fun goBack() { if (wk.canGoBack) wk.goBack() }
    override fun goForward() { if (wk.canGoForward) wk.goForward() }
    override fun canGoBack(): Boolean = wk.canGoBack
    override fun canGoForward(): Boolean = wk.canGoForward
    override fun reload() { wk.reload() }
    override fun stopLoading() { wk.stopLoading() }
    override fun evaluateJavascript(js: String, callback: ((String?) -> Unit)?) {
        wk.evaluateJavaScript(js) { result, _ ->
            callback?.invoke(when (result) {
                null -> "null"
                is String -> JSONObject.quote(result)
                else -> result.toString()
            })
        }
    }
    override val url: String? get() = wk.URL?.absoluteString
    override val title: String? get() = wk.title
    override val progress: Int get() = (wk.estimatedProgress * 100).toInt()
    override fun destroy() {
        wk.stopLoading()
        wk.navigationDelegate = null
        wk.UIDelegate = null
    }
}

@Composable
actual fun PlatformWebViewHost(webView: WebView, modifier: Modifier) {
    val impl = remember(webView) { IosWebViewImpl(webView).also { webView.attach(it) } }
    DisposableEffect(impl) { onDispose { } }
    UIKitView(
        factory = { impl.wk },
        modifier = modifier,
        properties = UIKitInteropProperties(interactionMode = UIKitInteropInteractionMode.NonCooperative, isNativeAccessibilityEnabled = true)
    )
}
