package ru.allisighs.funpaytools

import androidx.compose.runtime.Composable
import okio.FileSystem
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.net.HttpURLConnection as JHttpURLConnection
import java.net.InetSocketAddress as JInetSocketAddress
import java.net.Proxy as JProxy
import java.net.URI
import java.net.URL as JURL

actual fun platformSleep(ms: Long) = java.lang.Thread.sleep(ms)

@Suppress("NOTHING_TO_INLINE")
actual inline fun <R> synchronized(lock: Any, block: () -> R): R = kotlin.synchronized(lock, block)

actual val platformFileSystem: FileSystem = FileSystem.SYSTEM

private val appHome: String by lazy {
    val dir = java.io.File(java.lang.System.getProperty("user.home"), ".funpaytools-ios-desktop")
    dir.mkdirs(); dir.absolutePath
}

actual fun platformHttpExecute(req: RawHttpRequest): RawHttpResponse {
    try {
        val url = JURL(req.url)
        val proxy = req.proxy?.let {
            JProxy(if (it.type == "SOCKS") JProxy.Type.SOCKS else JProxy.Type.HTTP, JInetSocketAddress(it.host, it.port))
        }
        val conn = (if (proxy != null) url.openConnection(proxy) else url.openConnection()) as JHttpURLConnection
        conn.instanceFollowRedirects = false
        conn.connectTimeout = req.timeoutMs.toInt()
        conn.readTimeout = req.timeoutMs.toInt()
        conn.useCaches = false
        if (req.method == "PATCH") {
            conn.requestMethod = "POST"
            conn.setRequestProperty("X-HTTP-Method-Override", "PATCH")
        } else conn.requestMethod = req.method
        for ((k, v) in req.headers) conn.addRequestProperty(k, v)
        req.proxy?.let { p -> if (p.user != null && p.type != "SOCKS") conn.setRequestProperty("Proxy-Authorization", Credentials.basic(p.user, p.pass ?: "")) }
        if (req.body != null) {
            conn.doOutput = true
            conn.outputStream.use { it.write(req.body) }
        }
        val code = conn.responseCode
        val headers = ArrayList<Pair<String, String>>()
        for ((k, vs) in conn.headerFields) if (k != null) for (v in vs) headers.add(k to v)
        val stream = if (code >= 400) conn.errorStream else conn.inputStream
        var bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
        if (conn.contentEncoding.equals("gzip", true) && req.headers.none { it.first.equals("Accept-Encoding", true) }) {
            bytes = java.util.zip.GZIPInputStream(bytes.inputStream()).readBytes()
        }
        return RawHttpResponse(code, conn.responseMessage ?: "", headers, bytes, req.url)
    } catch (e: java.net.SocketTimeoutException) {
        throw SocketTimeoutException(e.message)
    } catch (e: java.net.UnknownHostException) {
        throw UnknownHostException(e.message)
    } catch (e: java.io.IOException) {
        throw IOException(e.message ?: e.toString())
    }
}

actual object PlatformBridge {
    actual val filesDir: String get() = java.io.File(appHome, "files").also { it.mkdirs() }.absolutePath
    actual val cacheDir: String get() = java.io.File(appHome, "cache").also { it.mkdirs() }.absolutePath
    actual val documentsDir: String get() = java.io.File(appHome, "documents").also { it.mkdirs() }.absolutePath
    actual fun openUrl(url: String): Boolean = try { Desktop.getDesktop().browse(URI(url)); true } catch (e: Throwable) { println("openUrl $url"); false }
    actual fun openAppSettings() {}
    actual fun shareText(text: String, title: String?) { setClipboard(text); println("share: $text") }
    actual fun shareFile(path: String, mime: String?) { println("share file: $path") }
    actual fun setClipboard(text: String) { try { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) } catch (_: Throwable) {} }
    actual fun getClipboard(): String? = try { Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.stringFlavor) as? String } catch (_: Throwable) { null }
    actual fun deviceId(): String = "desktop-" + (java.lang.System.getProperty("user.name") ?: "user").hashCode().toUInt().toString(16)
    actual fun vibrate(ms: Long) {}
    actual fun keepScreenOn(on: Boolean) {}
    actual fun saveImageToGallery(bytes: ByteArray): Boolean { java.io.File(documentsDir, "img_${java.lang.System.currentTimeMillis()}.png").writeBytes(bytes); return true }
    actual val deviceModel: String get() = "Desktop"
    actual val osVersion: String get() = java.lang.System.getProperty("os.version") ?: ""
    actual fun runOnMain(block: () -> Unit) {
        if (javax.swing.SwingUtilities.isEventDispatchThread()) block() else javax.swing.SwingUtilities.invokeLater(block)
    }
    actual fun isNetworkAvailable(): Boolean = true
    actual fun setClipboardImage(bytes: ByteArray): Boolean = false
    actual fun clipboardHasImage(): Boolean = false
    actual fun clipboardImageToFile(): String? = null
    actual fun clipboardChangeCount(): Long = 0
    actual fun setSystemBarsHidden(hidden: Boolean) {}
    actual fun exportFile(path: String, suggestedName: String) {
        val target = java.io.File(documentsDir, suggestedName)
        java.io.File(path).copyTo(target, overwrite = true)
        Toast.makeText(AppContext, "Сохранено: ${target.absolutePath}", Toast.LENGTH_LONG).show()
    }
}

actual object PlatformNotifications {
    actual fun post(spec: LocalNotificationSpec) { println("NOTIFY #${spec.id}: ${spec.title} — ${spec.text}") }
    actual fun cancel(id: Int) {}
    actual fun cancelAll() {}
    actual fun requestPermission(onResult: (Boolean) -> Unit) = onResult(true)
    actual fun areNotificationsEnabled(): Boolean = true
}

actual object PlatformPickers {
    actual fun pickImages(multiple: Boolean, onResult: (List<String>) -> Unit) = pick(onResult = { onResult(listOfNotNull(it)) })
    actual fun pickDocument(mimeTypes: List<String>, onResult: (String?) -> Unit) = pick(onResult)
    private fun pick(onResult: (String?) -> Unit) {
        javax.swing.SwingUtilities.invokeLater {
            val fc = javax.swing.JFileChooser()
            val r = fc.showOpenDialog(null)
            if (r == javax.swing.JFileChooser.APPROVE_OPTION) {
                val src = fc.selectedFile
                val dst = java.io.File(java.io.File(PlatformBridge.cacheDir, "picked").also { it.mkdirs() }, "${java.lang.System.currentTimeMillis()}_${src.name}")
                src.copyTo(dst, overwrite = true)
                onResult("file://" + dst.absolutePath)
            } else onResult(null)
        }
    }
}

actual object PlatformUi {
    actual fun setStatusBarDarkIcons(dark: Boolean) {}
}

actual object PlatformBackground {
    actual fun setKeepAlive(enabled: Boolean) {}
    actual val isKeepAliveActive: Boolean get() = false
}

actual class JsEngine actual constructor() {
    actual fun setNativeHandler(handler: (String, List<Any?>) -> Any?) {}
    actual fun evaluate(js: String): String? = null
    actual fun close() {}
}

actual object PlatformWebCookies {
    private val jar = LinkedHashMap<String, String>()
    actual fun getCookie(url: String): String? = jar.entries.joinToString("; ") { "${it.key}=${it.value}" }.ifEmpty { null }
    actual fun setCookie(url: String, setCookieValue: String, done: (() -> Unit)?) {
        val first = setCookieValue.substringBefore(';')
        jar[first.substringBefore('=').trim()] = first.substringAfter('=', "").trim()
        done?.invoke()
    }
    actual fun removeAll(done: (() -> Unit)?) { jar.clear(); done?.invoke() }
    actual fun refresh(done: (() -> Unit)?) { done?.invoke() }
}

@androidx.compose.runtime.Composable
actual fun PlatformWebViewHost(webView: WebView, modifier: androidx.compose.ui.Modifier) {
    androidx.compose.foundation.layout.Box(modifier, contentAlignment = androidx.compose.ui.Alignment.Center) {
        androidx.compose.material3.Text("WebView (только на iOS)")
    }
}

actual object PlatformMotion {
    actual fun start(cb: (FloatArray) -> Unit) {}
    actual fun stop() {}
}
