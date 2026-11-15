package ru.allisighs.funpaytools

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import platform.Foundation.*
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSError
import platform.Foundation.NSHTTPCookie
import platform.Foundation.NSHTTPCookieAcceptPolicy
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSLocale
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSNumber
import platform.Foundation.NSTimeZone
import platform.Foundation.NSURL
import platform.Foundation.NSURLAuthenticationChallenge
import platform.Foundation.NSURLAuthenticationMethodServerTrust
import platform.Foundation.NSURLCredential
import platform.Foundation.NSURLCredentialPersistence
import platform.Foundation.NSURLErrorCannotConnectToHost
import platform.Foundation.NSURLErrorCannotFindHost
import platform.Foundation.NSURLErrorDNSLookupFailed
import platform.Foundation.NSURLErrorNotConnectedToInternet
import platform.Foundation.NSURLErrorTimedOut
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURLRequestReloadIgnoringLocalCacheData
import platform.Foundation.NSURLResponse
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionAuthChallengeCancelAuthenticationChallenge
import platform.Foundation.NSURLSessionAuthChallengeDisposition
import platform.Foundation.NSURLSessionAuthChallengePerformDefaultHandling
import platform.Foundation.NSURLSessionAuthChallengeUseCredential
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDataDelegateProtocol
import platform.Foundation.NSURLSessionTask
import platform.Foundation.create
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.localeWithLocaleIdentifier
import platform.Foundation.setHTTPBody
import platform.Foundation.setHTTPMethod
import platform.Foundation.setValue
import platform.Foundation.timeZoneWithAbbreviation
import platform.darwin.DISPATCH_TIME_FOREVER
import platform.darwin.NSObject
import platform.darwin.dispatch_semaphore_create
import platform.darwin.dispatch_semaphore_signal
import platform.darwin.dispatch_semaphore_wait
import platform.posix.memcpy

fun ByteArray.toNSData(): NSData = if (isEmpty()) NSData() else memScoped {
    NSData.create(bytes = allocArrayOf(this@toNSData), length = this@toNSData.size.toULong())
}

fun NSData.toByteArray(): ByteArray {
    val len = length.toInt()
    if (len == 0) return ByteArray(0)
    val out = ByteArray(len)
    out.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return out
}

private class HttpSessionDelegate(private val trustAll: Boolean, private val proxy: ProxyConfig?) : NSObject(), NSURLSessionDataDelegateProtocol {
    override fun URLSession(
        session: NSURLSession,
        task: NSURLSessionTask,
        willPerformHTTPRedirection: NSHTTPURLResponse,
        newRequest: NSURLRequest,
        completionHandler: (NSURLRequest?) -> Unit
    ) {
        // Redirects are followed by the OkHttp emulation layer so that interceptors see every hop.
        completionHandler(null)
    }

    override fun URLSession(
        session: NSURLSession,
        task: NSURLSessionTask,
        didReceiveChallenge: NSURLAuthenticationChallenge,
        completionHandler: (NSURLSessionAuthChallengeDisposition, NSURLCredential?) -> Unit
    ) {
        val space = didReceiveChallenge.protectionSpace
        if (space.isProxy() && proxy?.user != null) {
            if (didReceiveChallenge.previousFailureCount > 2) {
                completionHandler(NSURLSessionAuthChallengeCancelAuthenticationChallenge, null)
                return
            }
            val cred = NSURLCredential.credentialWithUser(proxy.user, proxy.pass ?: "", NSURLCredentialPersistence.NSURLCredentialPersistenceForSession)
            completionHandler(NSURLSessionAuthChallengeUseCredential, cred)
            return
        }
        if (trustAll && space.authenticationMethod == NSURLAuthenticationMethodServerTrust) {
            val trust = space.serverTrust
            if (trust != null) {
                completionHandler(NSURLSessionAuthChallengeUseCredential, NSURLCredential.credentialForTrust(trust))
                return
            }
        }
        completionHandler(NSURLSessionAuthChallengePerformDefaultHandling, null)
    }
}

private object SessionPool {
    private val sessions = HashMap<String, Pair<NSURLSession, HttpSessionDelegate>>()
    private val lock = Any()

    fun get(proxy: ProxyConfig?, trustAll: Boolean): NSURLSession = synchronized(lock) {
        val key = "${proxy?.type}|${proxy?.host}|${proxy?.port}|${proxy?.user}|$trustAll"
        sessions[key]?.first ?: run {
            val cfg = NSURLSessionConfiguration.ephemeralSessionConfiguration
            cfg.HTTPShouldSetCookies = false
            cfg.HTTPCookieAcceptPolicy = NSHTTPCookieAcceptPolicy.NSHTTPCookieAcceptPolicyNever
            cfg.HTTPCookieStorage = null
            cfg.URLCache = null
            cfg.requestCachePolicy = NSURLRequestReloadIgnoringLocalCacheData
            cfg.HTTPMaximumConnectionsPerHost = 6
            cfg.timeoutIntervalForRequest = 60.0
            cfg.timeoutIntervalForResource = 180.0
            cfg.waitsForConnectivity = false
            if (proxy != null) {
                val dict: Map<Any?, Any?> = if (proxy.type == "SOCKS") {
                    buildMap {
                        put("SOCKSEnable", NSNumber(int = 1))
                        put("SOCKSProxy", proxy.host)
                        put("SOCKSPort", NSNumber(int = proxy.port))
                        if (proxy.user != null) {
                            put("SOCKSUser", proxy.user)
                            put("SOCKSPassword", proxy.pass ?: "")
                            put("kCFStreamPropertySOCKSUser", proxy.user)
                            put("kCFStreamPropertySOCKSPassword", proxy.pass ?: "")
                        }
                    }
                } else {
                    mapOf(
                        "HTTPEnable" to NSNumber(int = 1),
                        "HTTPProxy" to proxy.host,
                        "HTTPPort" to NSNumber(int = proxy.port),
                        "HTTPSEnable" to NSNumber(int = 1),
                        "HTTPSProxy" to proxy.host,
                        "HTTPSPort" to NSNumber(int = proxy.port),
                    )
                }
                cfg.connectionProxyDictionary = dict
            }
            val delegate = HttpSessionDelegate(trustAll, proxy)
            val s = NSURLSession.sessionWithConfiguration(cfg, delegate, null)
            sessions[key] = s to delegate
            s
        }
    }
}

private val cookieDateFormatter: NSDateFormatter by lazy {
    NSDateFormatter().apply {
        locale = NSLocale.localeWithLocaleIdentifier("en_US_POSIX")
        timeZone = NSTimeZone.timeZoneWithAbbreviation("GMT")!!
        dateFormat = "EEE, dd MMM yyyy HH:mm:ss 'GMT'"
    }
}

private fun cookieToSetCookie(c: NSHTTPCookie): String {
    val sb = StringBuilder()
    sb.append(c.name).append('=').append(c.value)
    c.expiresDate?.let { d: NSDate -> sb.append("; expires=").append(cookieDateFormatter.stringFromDate(d)) }
    if (c.domain.isNotEmpty()) sb.append("; domain=").append(c.domain)
    sb.append("; path=").append(c.path.ifEmpty { "/" })
    if (c.isSecure()) sb.append("; secure")
    if (c.isHTTPOnly()) sb.append("; HttpOnly")
    return sb.toString()
}

actual fun platformHttpExecute(req: RawHttpRequest): RawHttpResponse {
    val nsUrl = NSURL.URLWithString(req.url) ?: NSURL.URLWithString(uriEncode(req.url, ":/?#[]@!$&'()*+,;=%")) ?: throw IOException("Invalid URL: ${req.url}")
    val r = NSMutableURLRequest.requestWithURL(nsUrl)
    r.setHTTPMethod(req.method)
    r.setTimeoutInterval(req.timeoutMs / 1000.0)
    r.HTTPShouldHandleCookies = false
    r.setCachePolicy(NSURLRequestReloadIgnoringLocalCacheData)
    val seen = HashSet<String>()
    for ((k, v) in req.headers) {
        if (k.equals("Content-Length", true) || k.equals("Host", true) || k.equals("Connection", true)) continue
        val lk = k.lowercase()
        if (seen.add(lk)) r.setValue(v, forHTTPHeaderField = k) else r.addValue(v, forHTTPHeaderField = k)
    }
    req.proxy?.let { p -> if (p.user != null && p.type != "SOCKS") r.setValue(Credentials.basic(p.user, p.pass ?: ""), forHTTPHeaderField = "Proxy-Authorization") }
    req.body?.let { r.setHTTPBody(it.toNSData()) }

    val session = SessionPool.get(req.proxy, req.trustAll)
    val sem = dispatch_semaphore_create(0)
    var outData: NSData? = null
    var outResp: NSURLResponse? = null
    var outErr: NSError? = null
    val task = session.dataTaskWithRequest(r) { data, resp, err ->
        outData = data
        outResp = resp
        outErr = err
        dispatch_semaphore_signal(sem)
    }
    task.resume()
    dispatch_semaphore_wait(sem, DISPATCH_TIME_FOREVER)

    val err = outErr
    if (err != null) {
        val msg = err.localizedDescription
        when (err.code) {
            NSURLErrorTimedOut -> throw SocketTimeoutException(msg)
            NSURLErrorCannotFindHost, NSURLErrorDNSLookupFailed, NSURLErrorNotConnectedToInternet -> throw UnknownHostException(msg)
            NSURLErrorCannotConnectToHost -> throw ConnectException(msg)
            else -> throw IOException(msg)
        }
    }
    val http = outResp as? NSHTTPURLResponse ?: throw IOException("No HTTP response")
    val code = http.statusCode.toInt()
    val headers = ArrayList<Pair<String, String>>()
    val all = http.allHeaderFields
    for ((k, v) in all) {
        val name = k?.toString() ?: continue
        if (name.equals("Set-Cookie", true)) continue
        headers.add(name to (v?.toString() ?: ""))
    }
    val cookies = try {
        @Suppress("UNCHECKED_CAST")
        NSHTTPCookie.cookiesWithResponseHeaderFields(all as Map<Any?, *>, nsUrl)
    } catch (e: Exception) { emptyList<Any?>() }
    for (c in cookies) (c as? NSHTTPCookie)?.let { headers.add("Set-Cookie" to cookieToSetCookie(it)) }
    val body = outData?.toByteArray() ?: ByteArray(0)
    val msg = NSHTTPURLResponse.localizedStringForStatusCode(http.statusCode)
    return RawHttpResponse(code, msg, headers, body, http.URL?.absoluteString ?: req.url)
}
