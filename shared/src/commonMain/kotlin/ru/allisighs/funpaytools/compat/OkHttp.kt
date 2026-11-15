package ru.allisighs.funpaytools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

/*
 * Minimal OkHttp-compatible API implemented on top of a blocking platform transport.
 * The transport performs exactly one HTTP exchange (no redirects, no cookie handling);
 * redirects, interceptors and timeouts are emulated here like OkHttp does.
 */

class RawHttpRequest(
    val url: String,
    val method: String,
    val headers: List<Pair<String, String>>,
    val body: ByteArray?,
    val timeoutMs: Long,
    val proxy: ProxyConfig?,
    val trustAll: Boolean,
)

class RawHttpResponse(
    val code: Int,
    val message: String,
    val headers: List<Pair<String, String>>,
    val body: ByteArray,
    val finalUrl: String,
)

data class ProxyConfig(val type: String, val host: String, val port: Int, val user: String?, val pass: String?)

/** Blocking single exchange. Throws IOException subclasses on network failures. */
expect fun platformHttpExecute(req: RawHttpRequest): RawHttpResponse

// ---------------- MediaType ----------------
class MediaType private constructor(val mediaType: String) {
    val type: String get() = mediaType.substringBefore('/').trim()
    val subtype: String get() = mediaType.substringAfter('/').substringBefore(';').trim()
    fun charset(def: Charset? = null): Charset? = def
    override fun toString() = mediaType
    companion object {
        fun parse(s: String): MediaType? = if (s.contains('/')) MediaType(s) else null
        fun get(s: String): MediaType = MediaType(s)
        fun String.toMediaType(): MediaType = MediaType(this)
        fun String.toMediaTypeOrNull(): MediaType? = parse(this)
    }
}

fun String.toMediaType(): MediaType = MediaType.get(this)
fun String.toMediaTypeOrNull(): MediaType? = MediaType.parse(this)

// ---------------- Headers ----------------
class Headers internal constructor(internal val list: List<Pair<String, String>>) : Iterable<Pair<String, String>> {
    operator fun get(name: String): String? = list.lastOrNull { it.first.equals(name, true) }?.second
    fun values(name: String): List<String> = list.filter { it.first.equals(name, true) }.map { it.second }
    fun names(): Set<String> = list.map { it.first }.toCollection(LinkedHashSet())
    val size: Int get() = list.size
    fun size(): Int = list.size
    fun name(i: Int) = list[i].first
    fun value(i: Int) = list[i].second
    fun toMultimap(): Map<String, List<String>> = list.groupBy({ it.first.lowercase() }, { it.second })
    override fun iterator(): Iterator<Pair<String, String>> = list.iterator()
    fun newBuilder(): Builder = Builder().also { b -> list.forEach { b.add(it.first, it.second) } }
    override fun toString() = list.joinToString("\n") { "${it.first}: ${it.second}" }
    class Builder {
        internal val list = ArrayList<Pair<String, String>>()
        fun add(name: String, value: String) = apply { list.add(name to value) }
        fun add(line: String) = apply { list.add(line.substringBefore(':').trim() to line.substringAfter(':').trim()) }
        fun set(name: String, value: String) = apply { removeAll(name); list.add(name to value) }
        fun removeAll(name: String) = apply { list.removeAll { it.first.equals(name, true) } }
        operator fun get(name: String) = list.lastOrNull { it.first.equals(name, true) }?.second
        fun build() = Headers(list.toList())
    }
    companion object {
        fun headersOf(vararg nv: String): Headers = Headers(nv.toList().chunked(2).map { it[0] to it[1] })
        fun Map<String, String>.toHeaders(): Headers = Headers(map { it.key to it.value })
    }
}

// ---------------- HttpUrl ----------------
class HttpUrl private constructor(private val raw: String) {
    private val uri = Uri.parse(raw)
    val scheme: String get() = uri.scheme ?: "https"
    val host: String get() = uri.host ?: ""
    val port: Int get() = uri.port.takeIf { it > 0 } ?: if (scheme == "https") 443 else 80
    val encodedPath: String get() = uri.encodedPath ?: "/"
    val pathSegments: List<String> get() = uri.pathSegments
    val encodedQuery: String? get() = uri.encodedQuery
    val query: String? get() = uri.query
    val fragment: String? get() = uri.fragment
    val isHttps: Boolean get() = scheme == "https"
    fun queryParameter(name: String): String? = uri.getQueryParameter(name)
    fun queryParameterNames(): Set<String> = uri.queryParameterNames
    fun resolve(link: String): HttpUrl? = resolveUrl(raw, link)?.let { HttpUrl(it) }
    fun newBuilder(): Builder = Builder(raw)
    fun toUri(): Uri = uri
    override fun toString() = raw
    override fun equals(other: Any?) = other is HttpUrl && other.raw == raw
    override fun hashCode() = raw.hashCode()

    class Builder(private var base: String = "") {
        private val addedQuery = ArrayList<Pair<String, String?>>()
        private var scheme: String? = null
        private var host: String? = null
        private var path: String? = null
        fun scheme(s: String) = apply { scheme = s }
        fun host(h: String) = apply { host = h }
        fun addPathSegment(seg: String) = apply { path = (path ?: "") + "/" + uriEncode(seg) }
        fun addPathSegments(seg: String) = apply { path = (path ?: "") + "/" + seg.trimStart('/') }
        fun encodedPath(p: String) = apply { path = p }
        fun addQueryParameter(k: String, v: String?) = apply { addedQuery.add(urlEncodeForm(k).replace("+", "%20") to v?.let { urlEncodeForm(it).replace("+", "%20") }) }
        fun addEncodedQueryParameter(k: String, v: String?) = apply { addedQuery.add(k to v) }
        fun setQueryParameter(k: String, v: String?) = apply {
            removeAllQueryParameters(k); addQueryParameter(k, v)
        }
        fun removeAllQueryParameters(k: String) = apply {
            val enc = urlEncodeForm(k).replace("+", "%20")
            addedQuery.removeAll { it.first == enc }
            if (base.contains('?')) {
                val q = base.substringAfter('?').split('&').filter { it.isNotEmpty() && it.substringBefore('=') != enc && urlDecode(it.substringBefore('=')) != k }
                base = base.substringBefore('?') + if (q.isEmpty()) "" else "?" + q.joinToString("&")
            }
        }
        fun build(): HttpUrl {
            var u = if (base.isNotEmpty()) base else "${scheme ?: "https"}://${host ?: ""}"
            if (base.isEmpty() && path != null) u += path
            else if (path != null) {
                val q = u.substringAfter('?', "")
                val b = u.substringBefore('?')
                val origin = Regex("^[a-zA-Z]+://[^/]+").find(b)?.value ?: b
                u = origin + (if (path!!.startsWith("/")) path else "/$path") + if (q.isNotEmpty()) "?$q" else ""
            }
            if (addedQuery.isNotEmpty()) {
                val qs = addedQuery.joinToString("&") { if (it.second == null) it.first else "${it.first}=${it.second}" }
                u += if (u.contains('?')) "&$qs" else "?$qs"
            }
            return HttpUrl(u)
        }
    }

    companion object {
        fun String.toHttpUrl(): HttpUrl = parse(this) ?: throw IllegalArgumentException("Invalid URL: $this")
        fun String.toHttpUrlOrNull(): HttpUrl? = parse(this)
        fun parse(s: String): HttpUrl? {
            val t = s.trim()
            if (!(t.startsWith("http://", true) || t.startsWith("https://", true))) return null
            return HttpUrl(t)
        }
        fun get(s: String): HttpUrl = s.toHttpUrl()
    }
}

fun String.toHttpUrl(): HttpUrl = HttpUrl.parse(this) ?: throw IllegalArgumentException("Invalid URL: $this")
fun String.toHttpUrlOrNull(): HttpUrl? = HttpUrl.parse(this)

fun resolveUrl(base: String, link: String): String? {
    if (link.startsWith("http://", true) || link.startsWith("https://", true)) return link
    val b = Uri.parse(base)
    val scheme = b.scheme ?: return null
    val authority = b.encodedAuthority ?: return null
    if (link.startsWith("//")) return "$scheme:$link"
    if (link.startsWith("/")) return "$scheme://$authority$link"
    if (link.startsWith("?")) return "$scheme://$authority${b.encodedPath ?: "/"}$link"
    val basePath = (b.encodedPath ?: "/").substringBeforeLast('/') + "/"
    return "$scheme://$authority$basePath$link"
}

// ---------------- RequestBody ----------------
abstract class RequestBody {
    abstract fun contentType(): MediaType?
    abstract fun bytes(): ByteArray
    open fun contentLength(): Long = bytes().size.toLong()

    companion object {
        fun create(contentType: MediaType?, content: String): RequestBody = content.toRequestBody(contentType)
        fun create(contentType: MediaType?, content: ByteArray): RequestBody = content.toRequestBody(contentType)
        fun create(content: String, contentType: MediaType?): RequestBody = content.toRequestBody(contentType)
        fun String.toRequestBody(contentType: MediaType? = null): RequestBody = BytesBody(encodeToByteArray(), contentType)
        fun ByteArray.toRequestBody(contentType: MediaType? = null, offset: Int = 0, byteCount: Int = size): RequestBody =
            BytesBody(copyOfRange(offset, offset + byteCount), contentType)
        fun File.asRequestBody(contentType: MediaType? = null): RequestBody = BytesBody(readBytes(), contentType)
    }
}

internal class BytesBody(private val data: ByteArray, private val ct: MediaType?) : RequestBody() {
    override fun contentType() = ct
    override fun bytes() = data
}

fun String.toRequestBody(contentType: MediaType? = null): RequestBody = BytesBody(encodeToByteArray(), contentType)
fun ByteArray.toRequestBody(contentType: MediaType? = null, offset: Int = 0, byteCount: Int = size): RequestBody =
    BytesBody(copyOfRange(offset, offset + byteCount), contentType)
fun File.asRequestBody(contentType: MediaType? = null): RequestBody = BytesBody(readBytes(), contentType)

class FormBody private constructor(private val names: List<String>, private val values: List<String>) : RequestBody() {
    private val encoded: ByteArray by lazy {
        names.indices.joinToString("&") { "${names[it]}=${values[it]}" }.encodeToByteArray()
    }
    override fun contentType(): MediaType = MediaType.get("application/x-www-form-urlencoded")
    override fun bytes() = encoded
    val size: Int get() = names.size
    fun name(i: Int) = urlDecode(names[i], true)
    fun value(i: Int) = urlDecode(values[i], true)
    fun encodedName(i: Int) = names[i]
    fun encodedValue(i: Int) = values[i]

    class Builder(charset: Charset? = null) {
        private val n = ArrayList<String>()
        private val v = ArrayList<String>()
        fun add(name: String, value: String) = apply { n.add(urlEncodeForm(name)); v.add(urlEncodeForm(value)) }
        fun addEncoded(name: String, value: String) = apply { n.add(name); v.add(value) }
        fun build() = FormBody(n.toList(), v.toList())
    }
}

class MultipartBody private constructor(private val boundary: String, private val mediaType: MediaType, val parts: List<Part>) : RequestBody() {
    override fun contentType(): MediaType = MediaType.get("${mediaType.mediaType}; boundary=$boundary")
    private val data: ByteArray by lazy {
        val out = ByteArrayOutputStream()
        for (p in parts) {
            out.write("--$boundary\r\n".encodeToByteArray())
            p.headers?.forEach { (k, v) -> out.write("$k: $v\r\n".encodeToByteArray()) }
            p.body.contentType()?.let { out.write("Content-Type: $it\r\n".encodeToByteArray()) }
            out.write("Content-Length: ${p.body.bytes().size}\r\n".encodeToByteArray())
            out.write("\r\n".encodeToByteArray())
            out.write(p.body.bytes())
            out.write("\r\n".encodeToByteArray())
        }
        out.write("--$boundary--\r\n".encodeToByteArray())
        out.toByteArray()
    }
    override fun bytes() = data

    class Part private constructor(val headers: Headers?, val body: RequestBody) {
        companion object {
            fun create(body: RequestBody): Part = Part(null, body)
            fun create(headers: Headers?, body: RequestBody): Part = Part(headers, body)
            fun createFormData(name: String, value: String): Part = createFormData(name, null, value.toRequestBody(null))
            fun createFormData(name: String, filename: String?, body: RequestBody): Part {
                val disp = StringBuilder("form-data; name=\"").append(escapeQ(name)).append('"')
                if (filename != null) disp.append("; filename=\"").append(escapeQ(filename)).append('"')
                return Part(Headers.headersOf("Content-Disposition", disp.toString()), body)
            }
            private fun escapeQ(s: String) = s.replace("\n", "%0A").replace("\r", "%0D").replace("\"", "%22")
        }
    }

    class Builder(private val boundary: String = "----FPT" + UUID.randomUUID().toString().replace("-", "")) {
        private var type: MediaType = MIXED
        private val parts = ArrayList<Part>()
        fun setType(t: MediaType) = apply { type = t }
        fun addPart(p: Part) = apply { parts.add(p) }
        fun addPart(body: RequestBody) = apply { parts.add(Part.create(body)) }
        fun addPart(headers: Headers?, body: RequestBody) = apply { parts.add(Part.create(headers, body)) }
        fun addFormDataPart(name: String, value: String) = apply { parts.add(Part.createFormData(name, value)) }
        fun addFormDataPart(name: String, filename: String?, body: RequestBody) = apply { parts.add(Part.createFormData(name, filename, body)) }
        fun build(): MultipartBody = MultipartBody(boundary, type, parts.toList())
    }

    companion object {
        val MIXED = MediaType.get("multipart/mixed")
        val FORM = MediaType.get("multipart/form-data")
        val ALTERNATIVE = MediaType.get("multipart/alternative")
    }
}

// ---------------- Request ----------------
class Request private constructor(
    val url: HttpUrl,
    val method: String,
    val headers: Headers,
    val body: RequestBody?,
    internal val tagValue: Any?,
) {
    fun header(name: String): String? = headers[name]
    fun headers(name: String): List<String> = headers.values(name)
    fun newBuilder(): Builder = Builder().also { b ->
        b.url(url); b.method(method, body); headers.forEach { b.addHeader(it.first, it.second) }; b.tag(tagValue)
    }
    fun url(): HttpUrl = url
    fun method(): String = method
    fun tag(): Any? = tagValue
    val isHttps: Boolean get() = url.isHttps
    override fun toString() = "Request{method=$method, url=$url}"

    class Builder {
        private var url: HttpUrl? = null
        private var method = "GET"
        private var body: RequestBody? = null
        private val headers = Headers.Builder()
        private var tag: Any? = null
        fun url(u: String) = apply {
            val fixed = when {
                u.startsWith("ws:", true) -> "http:" + u.substring(3)
                u.startsWith("wss:", true) -> "https:" + u.substring(4)
                else -> u
            }
            url = HttpUrl.parse(fixed) ?: throw IllegalArgumentException("Expected URL scheme 'http' or 'https' but no scheme was found for $u")
        }
        fun url(u: HttpUrl) = apply { url = u }
        fun header(name: String, value: String) = apply { headers.set(name, value) }
        fun addHeader(name: String, value: String) = apply { headers.add(name, value) }
        fun removeHeader(name: String) = apply { headers.removeAll(name) }
        fun headers(h: Headers) = apply { headers.list.clear(); h.forEach { headers.add(it.first, it.second) } }
        fun get() = apply { method = "GET"; body = null }
        fun head() = apply { method = "HEAD"; body = null }
        fun post(b: RequestBody) = apply { method = "POST"; body = b }
        fun put(b: RequestBody) = apply { method = "PUT"; body = b }
        fun patch(b: RequestBody) = apply { method = "PATCH"; body = b }
        fun delete(b: RequestBody? = null) = apply { method = "DELETE"; body = b }
        fun method(m: String, b: RequestBody?) = apply { method = m; body = b }
        fun tag(t: Any?) = apply { tag = t }
        fun cacheControl(c: Any?) = apply { header("Cache-Control", "no-cache") }
        fun build(): Request = Request(url ?: throw IllegalStateException("url == null"), method, headers.build(), body, tag)
    }
}

// ---------------- Response ----------------
abstract class ResponseBody : AutoCloseable {
    abstract fun bytes(): ByteArray
    abstract fun contentType(): MediaType?
    open fun string(): String = bytes().decodeToString()
    open fun byteStream(): InputStream = ByteArrayInputStream(bytes())
    open fun charStream(): Reader = InputStreamReader(byteStream())
    open fun contentLength(): Long = bytes().size.toLong()
    open fun source(): ResponseBody = this
    override fun close() {}
    companion object {
        fun create(contentType: MediaType?, content: String): ResponseBody = content.toResponseBody(contentType)
        fun String.toResponseBody(contentType: MediaType? = null): ResponseBody = BytesResponseBody(encodeToByteArray(), contentType)
        fun ByteArray.toResponseBody(contentType: MediaType? = null): ResponseBody = BytesResponseBody(this, contentType)
    }
}

internal class BytesResponseBody(private val data: ByteArray, private val ct: MediaType?) : ResponseBody() {
    override fun bytes() = data
    override fun contentType() = ct
}

fun String.toResponseBody(contentType: MediaType? = null): ResponseBody = BytesResponseBody(encodeToByteArray(), contentType)
fun ByteArray.toResponseBody(contentType: MediaType? = null): ResponseBody = BytesResponseBody(this, contentType)

/** Serves both as okhttp3.Response and retrofit2.Response<ResponseBody>. */
class Response internal constructor(
    val request: Request,
    val code: Int,
    val message: String,
    val headers: Headers,
    val body: ResponseBody?,
    val priorResponse: Response?,
) : AutoCloseable {
    val isSuccessful: Boolean get() = code in 200..299
    val isRedirect: Boolean get() = code in listOf(300, 301, 302, 303, 307, 308)
    val networkResponse: Response? get() = this
    val protocol: String get() = "http/1.1"
    val sentRequestAtMillis: Long = System.currentTimeMillis()
    val receivedResponseAtMillis: Long = System.currentTimeMillis()

    fun header(name: String): String? = headers[name]
    fun header(name: String, def: String?): String? = headers[name] ?: def
    fun headers(name: String): List<String> = headers.values(name)
    fun peekBody(max: Long): ResponseBody = body ?: "".toResponseBody()

    // retrofit-style accessors
    fun body(): ResponseBody? = if (isSuccessful) body else null
    fun errorBody(): ResponseBody? = if (!isSuccessful) body else null
    fun code(): Int = code
    fun message(): String = message
    fun headers(): Headers = headers
    fun raw(): Response = this

    fun newBuilder(): Builder = Builder().request(request).code(code).message(message).headers(headers).body(body)

    override fun close() {}

    class Builder {
        private var request: Request? = null
        private var code = 200
        private var message = ""
        private var headers = Headers(emptyList())
        private var body: ResponseBody? = null
        private var prior: Response? = null
        fun request(r: Request) = apply { request = r }
        fun code(c: Int) = apply { code = c }
        fun message(m: String) = apply { message = m }
        fun headers(h: Headers) = apply { headers = h }
        fun header(n: String, v: String) = apply { headers = headers.newBuilder().set(n, v).build() }
        fun addHeader(n: String, v: String) = apply { headers = headers.newBuilder().add(n, v).build() }
        fun removeHeader(n: String) = apply { headers = headers.newBuilder().removeAll(n).build() }
        fun body(b: ResponseBody?) = apply { body = b }
        fun protocol(p: Any?) = this
        fun priorResponse(p: Response?) = apply { prior = p }
        fun build() = Response(request!!, code, message, headers, body, prior)
    }
}

// ---------------- Interceptors / Calls ----------------
fun interface Interceptor {
    fun intercept(chain: Chain): Response
    interface Chain {
        fun request(): Request
        fun proceed(request: Request): Response
        fun call(): Call
        fun connectTimeoutMillis(): Int = 60000
        fun readTimeoutMillis(): Int = 60000
    }
    companion object {
        inline operator fun invoke(crossinline block: (Chain) -> Response): Interceptor = Interceptor { block(it) }
    }
}

interface Callback {
    fun onFailure(call: Call, e: IOException)
    fun onResponse(call: Call, response: Response)
}

fun interface Authenticator {
    fun authenticate(route: Any?, response: Response): Request?
    companion object {
        val NONE = Authenticator { _, _ -> null }
    }
}

object Credentials {
    fun basic(user: String, pass: String, charset: Charset? = null): String =
        "Basic " + Base64.encodeToString("$user:$pass".encodeToByteArray(), Base64.NO_WRAP)
}

class Proxy(val type: Type, val address: InetSocketAddress?) {
    enum class Type { DIRECT, HTTP, SOCKS }
    companion object { val NO_PROXY = Proxy(Type.DIRECT, null) }
}

class InetSocketAddress(val hostName: String, val port: Int) {
    val hostString: String get() = hostName
}

class Dispatcher {
    var maxRequests = 64
    var maxRequestsPerHost = 5
    fun cancelAll() {}
}

class ConnectionPool(maxIdle: Int = 5, keepAlive: Long = 5, unit: TimeUnit = TimeUnit.MINUTES) {
    fun evictAll() {}
}

interface CookieJar {
    fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>)
    fun loadForRequest(url: HttpUrl): List<Cookie>
    companion object {
        val NO_COOKIES: CookieJar = object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {}
            override fun loadForRequest(url: HttpUrl): List<Cookie> = emptyList()
        }
    }
}

class Cookie(val name: String, val value: String, val domain: String = "", val path: String = "/", val expiresAt: Long = Long.MAX_VALUE, val secure: Boolean = false, val httpOnly: Boolean = false, val persistent: Boolean = false) {
    fun name() = name
    fun value() = value
    override fun toString() = "$name=$value"
    companion object {
        fun parse(url: HttpUrl, setCookie: String): Cookie? {
            val first = setCookie.substringBefore(';')
            if (!first.contains('=')) return null
            val attrs = setCookie.split(';').drop(1).associate { it.substringBefore('=').trim().lowercase() to it.substringAfter('=', "").trim() }
            val now = System.currentTimeMillis()
            val maxAge = attrs["max-age"]?.toLongOrNull()
            val expires = when {
                maxAge != null -> if (maxAge <= 0) Long.MIN_VALUE else now + maxAge * 1000
                attrs["expires"] != null -> parseHttpDate(attrs["expires"]!!) ?: Long.MAX_VALUE
                else -> Long.MAX_VALUE
            }
            val persistent = maxAge != null || attrs.containsKey("expires")
            return Cookie(first.substringBefore('=').trim(), first.substringAfter('=').trim(), (attrs["domain"] ?: url.host).trimStart('.'), attrs["path"] ?: "/",
                expires, attrs.containsKey("secure"), attrs.containsKey("httponly"), persistent)
        }
        fun parseAll(url: HttpUrl, headers: Headers): List<Cookie> = headers.values("Set-Cookie").mapNotNull { parse(url, it) }
    }
}

class OkHttpClient private constructor(internal val b: Builder) {
    constructor() : this(Builder())

    val interceptors: List<Interceptor> get() = b.interceptors.toList()
    val networkInterceptors: List<Interceptor> get() = b.networkInterceptors.toList()
    val followRedirects: Boolean get() = b.followRedirects
    val dispatcher: Dispatcher = Dispatcher()
    val connectionPool: ConnectionPool = ConnectionPool()

    fun newBuilder(): Builder = b.copy()
    fun newCall(request: Request): Call = RealCall(this, request)

    class Builder {
        internal var connectTimeoutMs = 10_000L
        internal var readTimeoutMs = 10_000L
        internal var writeTimeoutMs = 10_000L
        internal var callTimeoutMs = 0L
        internal var followRedirects = true
        internal var followSslRedirects = true
        internal val interceptors = ArrayList<Interceptor>()
        internal val networkInterceptors = ArrayList<Interceptor>()
        internal var proxy: Proxy? = null
        internal var proxyAuth: Authenticator? = null
        internal var trustAll = false
        internal var cookieJar: CookieJar = CookieJar.NO_COOKIES
        internal var retryOnFailure = true

        internal fun copy(): Builder = Builder().also {
            it.connectTimeoutMs = connectTimeoutMs; it.readTimeoutMs = readTimeoutMs; it.writeTimeoutMs = writeTimeoutMs
            it.callTimeoutMs = callTimeoutMs; it.followRedirects = followRedirects; it.followSslRedirects = followSslRedirects
            it.interceptors.addAll(interceptors); it.networkInterceptors.addAll(networkInterceptors)
            it.proxy = proxy; it.proxyAuth = proxyAuth; it.trustAll = trustAll; it.cookieJar = cookieJar
        }

        fun connectTimeout(t: Long, unit: TimeUnit) = apply { connectTimeoutMs = unit.toMillis(t) }
        fun readTimeout(t: Long, unit: TimeUnit) = apply { readTimeoutMs = unit.toMillis(t) }
        fun writeTimeout(t: Long, unit: TimeUnit) = apply { writeTimeoutMs = unit.toMillis(t) }
        fun callTimeout(t: Long, unit: TimeUnit) = apply { callTimeoutMs = unit.toMillis(t) }
        fun connectTimeout(d: kotlin.time.Duration) = apply { connectTimeoutMs = d.inWholeMilliseconds }
        fun readTimeout(d: kotlin.time.Duration) = apply { readTimeoutMs = d.inWholeMilliseconds }
        fun writeTimeout(d: kotlin.time.Duration) = apply { writeTimeoutMs = d.inWholeMilliseconds }
        fun callTimeout(d: kotlin.time.Duration) = apply { callTimeoutMs = d.inWholeMilliseconds }
        fun followRedirects(f: Boolean) = apply { followRedirects = f }
        fun followSslRedirects(f: Boolean) = apply { followSslRedirects = f }
        fun addInterceptor(i: Interceptor) = apply { interceptors.add(i) }
        fun addNetworkInterceptor(i: Interceptor) = apply { networkInterceptors.add(i) }
        fun proxy(p: Proxy?) = apply { proxy = p }
        fun proxyAuthenticator(a: Authenticator) = apply { proxyAuth = a }
        fun hostnameVerifier(v: (String, Any?) -> Boolean) = apply { trustAll = true }
        fun sslSocketFactory(f: Any?, tm: Any?) = apply { trustAll = true }
        fun cookieJar(j: CookieJar) = apply { cookieJar = j }
        fun retryOnConnectionFailure(r: Boolean) = apply { retryOnFailure = r }
        fun dispatcher(d: Dispatcher) = this
        fun connectionPool(p: ConnectionPool) = this
        fun cache(c: Any?) = this
        fun pingInterval(t: Long, unit: TimeUnit) = this
        fun protocols(p: List<Any>) = this
        fun dns(d: Any?) = this
        fun build(): OkHttpClient = OkHttpClient(copy())
    }
}

interface Call {
    fun execute(): Response
    fun enqueue(callback: Callback)
    fun cancel()
    fun isCanceled(): Boolean
    fun request(): Request
    fun isExecuted(): Boolean
}

internal class RealCall(private val client: OkHttpClient, private val originalRequest: Request) : Call {
    private var canceled = false
    private var executed = false

    override fun request() = originalRequest
    override fun isCanceled() = canceled
    override fun isExecuted() = executed
    override fun cancel() { canceled = true }

    override fun enqueue(callback: Callback) {
        httpBackgroundScope.launchIo {
            try {
                val r = execute()
                callback.onResponse(this@RealCall, r)
            } catch (e: IOException) {
                callback.onFailure(this@RealCall, e)
            } catch (e: Exception) {
                callback.onFailure(this@RealCall, IOException(e.message ?: e.toString()))
            }
        }
    }

    override fun execute(): Response {
        executed = true
        val chain = AppChain(0, originalRequest)
        return chain.proceed(originalRequest)
    }

    private inner class AppChain(private val index: Int, private val req: Request) : Interceptor.Chain {
        override fun request() = req
        override fun call(): Call = this@RealCall
        override fun proceed(request: Request): Response {
            if (canceled) throw IOException("Canceled")
            val list = client.b.interceptors
            return if (index < list.size) list[index].intercept(AppChain(index + 1, request))
            else followUps(request)
        }
    }

    private fun followUps(first: Request): Response {
        var req = first
        var prior: Response? = null
        var count = 0
        while (true) {
            if (canceled) throw IOException("Canceled")
            var resp = NetChain(0, req).proceed(req)
            if (prior != null) resp = resp.newBuilder().priorResponse(prior.newBuilder().body(null).build()).build()

            if (resp.code == 407 && client.b.proxyAuth != null) {
                val next = client.b.proxyAuth!!.authenticate(null, resp)
                if (next != null && count < 3) { prior = resp; req = next; count++; continue }
            }
            if (!client.b.followRedirects || !resp.isRedirect) return resp
            val loc = resp.header("Location") ?: return resp
            val target = resolveUrl(req.url.toString(), loc) ?: return resp
            if (++count > 20) throw IOException("Too many follow-up requests: $count")
            val builder = req.newBuilder().url(target)
            val code = resp.code
            if (code == 303 || ((code == 301 || code == 302) && req.method == "POST")) {
                builder.method("GET", null).removeHeader("Content-Type").removeHeader("Content-Length").removeHeader("Transfer-Encoding")
            }
            val newUrl = HttpUrl.parse(target)
            if (newUrl != null && newUrl.host != req.url.host) builder.removeHeader("Authorization")
            prior = resp
            req = builder.build()
        }
    }

    private inner class NetChain(private val index: Int, private val req: Request) : Interceptor.Chain {
        override fun request() = req
        override fun call(): Call = this@RealCall
        override fun proceed(request: Request): Response {
            val list = client.b.networkInterceptors
            return if (index < list.size) list[index].intercept(NetChain(index + 1, request))
            else transport(request)
        }
    }

    private fun transport(request: Request): Response {
        val headers = ArrayList<Pair<String, String>>()
        request.headers.forEach { headers.add(it) }
        val body = request.body
        if (body != null && headers.none { it.first.equals("Content-Type", true) }) {
            body.contentType()?.let { headers.add("Content-Type" to it.toString()) }
        }
        if (headers.none { it.first.equals("User-Agent", true) }) headers.add("User-Agent" to "okhttp/4.12.0")
        val jarCookies = client.b.cookieJar.loadForRequest(request.url)
        if (jarCookies.isNotEmpty() && headers.none { it.first.equals("Cookie", true) }) {
            headers.add("Cookie" to jarCookies.joinToString("; ") { "${it.name}=${it.value}" })
        }
        val p = client.b.proxy
        val proxy = if (p != null && p.type != Proxy.Type.DIRECT && p.address != null) {
            val creds = proxyCredentialsFor(p.address.hostName, p.address.port)
            ProxyConfig(if (p.type == Proxy.Type.SOCKS) "SOCKS" else "HTTP", p.address.hostName, p.address.port, creds?.first, creds?.second)
        } else null
        val timeout = maxOf(client.b.connectTimeoutMs + client.b.readTimeoutMs, client.b.callTimeoutMs).coerceAtLeast(5_000L)
        val raw = try {
            platformHttpExecute(RawHttpRequest(request.url.toString(), request.method, headers, body?.bytes(), timeout, proxy, client.b.trustAll))
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException(e.message ?: e.toString())
        }
        val rh = Headers(raw.headers)
        val ct = rh["Content-Type"]?.let { MediaType.parse(it) }
        val resp = Response(request, raw.code, raw.message, rh, BytesResponseBody(raw.body, ct), null)
        if (client.b.cookieJar !== CookieJar.NO_COOKIES) {
            val cookies = Cookie.parseAll(request.url, rh)
            if (cookies.isNotEmpty()) client.b.cookieJar.saveFromResponse(request.url, cookies)
        }
        return resp
    }
}

/** SOCKS proxy credentials registered via java.net.Authenticator emulation. */
private val proxyCreds = co.touchlab.stately.collections.ConcurrentMutableMap<String, Pair<String, String>>()
fun registerProxyCredentials(host: String, port: Int, user: String, pass: String) { proxyCreds["$host:$port"] = user to pass }
fun proxyCredentialsFor(host: String, port: Int): Pair<String, String>? = proxyCreds["$host:$port"]

internal val httpBackgroundScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
internal fun kotlinx.coroutines.CoroutineScope.launchIo(block: suspend () -> Unit) {
    this.launch(Dispatchers.IO) { block() }
}

/** Parses RFC 1123 / RFC 850 / asctime cookie dates. */
fun parseHttpDate(s: String): Long? {
    val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    val m = Regex("(\\d{1,2})[ -]([A-Za-z]{3})[a-z]*[ -](\\d{2,4})\\s+(\\d{1,2}):(\\d{2}):(\\d{2})").find(s) ?: return null
    val day = m.groupValues[1].toInt()
    val mon = months.indexOf(m.groupValues[2].lowercase()).takeIf { it >= 0 } ?: return null
    var year = m.groupValues[3].toInt()
    if (year < 70) year += 2000 else if (year < 100) year += 1900
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    cal.set(year, mon, day, m.groupValues[4].toInt(), m.groupValues[5].toInt(), m.groupValues[6].toInt())
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}
