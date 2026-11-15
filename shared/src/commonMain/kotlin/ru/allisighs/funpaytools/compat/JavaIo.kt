package ru.allisighs.funpaytools

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import okio.use

expect val platformFileSystem: FileSystem

open class File(path: String) : Comparable<File> {
    val path: String = path.trimEnd('/').ifEmpty { "/" }

    constructor(parent: File?, child: String) : this(if (parent == null) child else parent.path.trimEnd('/') + "/" + child.trimStart('/'))
    constructor(parent: String?, child: String) : this(if (parent == null) child else parent.trimEnd('/') + "/" + child.trimStart('/'))

    internal val okPath: Path get() = path.toPath()

    val name: String get() = path.substringAfterLast('/')
    val absolutePath: String get() = path
    val canonicalPath: String get() = path
    val absoluteFile: File get() = this
    val parent: String? get() = path.substringBeforeLast('/', "").ifEmpty { null }
    val parentFile: File? get() = parent?.let { File(it) }
    val nameWithoutExtension: String get() = name.substringBeforeLast('.')
    val extension: String get() = name.substringAfterLast('.', "")
    val isDirectory: Boolean get() = platformFileSystem.metadataOrNull(okPath)?.isDirectory == true
    val isFile: Boolean get() = platformFileSystem.metadataOrNull(okPath)?.isRegularFile == true

    fun exists(): Boolean = platformFileSystem.exists(okPath)
    fun length(): Long = platformFileSystem.metadataOrNull(okPath)?.size ?: 0L
    fun lastModified(): Long = platformFileSystem.metadataOrNull(okPath)?.lastModifiedAtMillis ?: 0L
    fun setLastModified(t: Long): Boolean = true
    fun canRead() = exists()
    fun canWrite() = true
    fun mkdirs(): Boolean = try { platformFileSystem.createDirectories(okPath); true } catch (e: Exception) { false }
    fun mkdir(): Boolean = mkdirs()
    fun createNewFile(): Boolean {
        if (exists()) return false
        parentFile?.mkdirs()
        writeBytes(ByteArray(0)); return true
    }
    fun delete(): Boolean = try { platformFileSystem.delete(okPath, mustExist = false); true } catch (e: Exception) { false }
    fun deleteRecursively(): Boolean = try { platformFileSystem.deleteRecursively(okPath, mustExist = false); true } catch (e: Exception) { false }
    fun deleteOnExit() {}
    fun renameTo(dest: File): Boolean = try { platformFileSystem.atomicMove(okPath, dest.okPath); true } catch (e: Exception) { false }
    fun listFiles(): Array<File>? = try { platformFileSystem.list(okPath).map { File(it.toString()) }.toTypedArray() } catch (e: Exception) { null }
    fun listFiles(filter: (File) -> Boolean): Array<File>? = listFiles()?.filter(filter)?.toTypedArray()
    fun list(): Array<String>? = listFiles()?.map { it.name }?.toTypedArray()
    fun walkTopDown(): Sequence<File> = sequence {
        yield(this@File)
        if (isDirectory) listFiles()?.forEach { yieldAll(it.walkTopDown()) }
    }
    fun walk(): Sequence<File> = walkTopDown()

    fun readBytes(): ByteArray = platformFileSystem.read(okPath) { readByteArray() }
    fun readText(charset: Charset = Charsets.UTF_8): String = readBytes().decodeToString()
    fun readLines(charset: Charset = Charsets.UTF_8): List<String> = readText().lines()
    fun writeBytes(b: ByteArray) {
        parentFile?.let { if (!it.exists()) it.mkdirs() }
        platformFileSystem.write(okPath) { write(b) }
    }
    fun writeText(s: String, charset: Charset = Charsets.UTF_8) = writeBytes(s.encodeToByteArray())
    fun appendText(s: String, charset: Charset = Charsets.UTF_8) = appendBytes(s.encodeToByteArray())
    fun appendBytes(b: ByteArray) {
        parentFile?.let { if (!it.exists()) it.mkdirs() }
        platformFileSystem.appendingSink(okPath, mustExist = false).buffer().use { it.write(b) }
    }
    fun forEachLine(action: (String) -> Unit) = readLines().forEach(action)
    fun inputStream(): InputStream = ByteArrayInputStream(readBytes())
    fun outputStream(): OutputStream = FileOutputStream(this)
    fun bufferedReader(): BufferedReader = BufferedReader(InputStreamReader(inputStream()))
    fun copyTo(target: File, overwrite: Boolean = false): File {
        if (target.exists() && !overwrite) throw okio.IOException("exists")
        target.writeBytes(readBytes()); return target
    }
    fun toURI(): String = "file://$path"
    fun toPath(): Path = okPath

    override fun compareTo(other: File) = path.compareTo(other.path)
    override fun equals(other: Any?) = other is File && other.path == path
    override fun hashCode() = path.hashCode()
    override fun toString() = path

    companion object {
        val separator = "/"
        fun createTempFile(prefix: String, suffix: String?, dir: File? = null): File {
            val d = dir ?: File(AppContext.cacheDir.path)
            d.mkdirs()
            return File(d, prefix + UUID.randomUUID().toString().take(8) + (suffix ?: ".tmp")).also { it.writeBytes(ByteArray(0)) }
        }
    }
}

// ---------------- Streams ----------------
abstract class InputStream : AutoCloseable {
    abstract fun read(): Int
    open fun read(b: ByteArray): Int = read(b, 0, b.size)
    open fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        var c = read()
        if (c == -1) return -1
        b[off] = c.toByte()
        var i = 1
        while (i < len) {
            c = read()
            if (c == -1) break
            b[off + i] = c.toByte()
            i++
        }
        return i
    }
    open fun readBytes(): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (true) {
            val n = read(buf)
            if (n <= 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
    open fun readAllBytes(): ByteArray = readBytes()
    open fun available(): Int = 0
    open fun skip(n: Long): Long { var i = 0L; while (i < n && read() != -1) i++; return i }
    override fun close() {}
    fun bufferedReader(charset: Charset = Charsets.UTF_8): BufferedReader = BufferedReader(InputStreamReader(this))
    fun reader(charset: Charset = Charsets.UTF_8): InputStreamReader = InputStreamReader(this)
    fun copyTo(out: OutputStream, bufferSize: Int = 8192): Long {
        val b = readBytes(); out.write(b); return b.size.toLong()
    }
    fun buffered(size: Int = 8192): InputStream = this
}

open class ByteArrayInputStream(private val data: ByteArray) : InputStream() {
    private var pos = 0
    override fun read(): Int = if (pos < data.size) data[pos++].toInt() and 0xff else -1
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (pos >= data.size) return -1
        val n = minOf(len, data.size - pos)
        data.copyInto(b, off, pos, pos + n)
        pos += n
        return n
    }
    override fun readBytes(): ByteArray {
        val r = data.copyOfRange(pos, data.size); pos = data.size; return r
    }
    override fun available(): Int = data.size - pos
}

class FileInputStream(file: File) : ByteArrayInputStream(file.readBytes()) {
    constructor(path: String) : this(File(path))
}

abstract class OutputStream : AutoCloseable {
    abstract fun write(b: Int)
    open fun write(b: ByteArray) = write(b, 0, b.size)
    open fun write(b: ByteArray, off: Int, len: Int) { for (i in off until off + len) write(b[i].toInt()) }
    open fun flush() {}
    override fun close() {}
    fun bufferedWriter(charset: Charset = Charsets.UTF_8): BufferedWriter = BufferedWriter(OutputStreamWriter(this))
    fun writer(charset: Charset = Charsets.UTF_8): OutputStreamWriter = OutputStreamWriter(this)
    fun buffered(size: Int = 8192): OutputStream = this
}

open class ByteArrayOutputStream(size: Int = 32) : OutputStream() {
    private var buf = ByteArray(maxOf(size, 16))
    private var count = 0
    private fun ensure(n: Int) {
        if (count + n > buf.size) buf = buf.copyOf(maxOf(buf.size * 2, count + n))
    }
    override fun write(b: Int) { ensure(1); buf[count++] = b.toByte() }
    override fun write(b: ByteArray, off: Int, len: Int) { ensure(len); b.copyInto(buf, count, off, off + len); count += len }
    fun toByteArray(): ByteArray = buf.copyOf(count)
    fun size(): Int = count
    fun reset() { count = 0 }
    override fun toString(): String = buf.decodeToString(0, count)
    fun toString(charset: String): String = toString()
    fun writeTo(out: OutputStream) = out.write(buf, 0, count)
}

open class FileOutputStream(private val file: File, private val append: Boolean = false) : ByteArrayOutputStream() {
    constructor(path: String) : this(File(path))
    private var closed = false
    override fun flush() {}
    override fun close() {
        if (closed) return
        closed = true
        if (append) file.appendBytes(toByteArray()) else file.writeBytes(toByteArray())
        onCloseHook?.invoke(file)
    }
    var onCloseHook: ((File) -> Unit)? = null
}

open class Reader : AutoCloseable {
    open fun readText(): String = ""
    override fun close() {}
}

open class InputStreamReader(private val input: InputStream?, charset: Any? = null) : Reader() {
    override fun readText(): String = (input ?: throw NullPointerException("InputStream is null")).readBytes().decodeToString()
    override fun close() { input?.close() }
}

class StringReader(private val s: String) : Reader() {
    override fun readText() = s
}

open class BufferedReader(private val reader: Reader, size: Int = 8192) : Reader() {
    private var lines: ArrayDeque<String>? = null
    private fun ensure(): ArrayDeque<String> {
        lines?.let { return it }
        val text = reader.readText()
        val l = ArrayDeque(if (text.isEmpty()) emptyList() else text.split("\n").let { if (text.endsWith("\n")) it.dropLast(1) else it }.map { it.trimEnd('\r') })
        lines = l
        return l
    }
    private var consumedAll = false
    override fun readText(): String {
        val l = lines
        return if (l == null) reader.readText() else l.joinToString("\n").also { l.clear() }
    }
    fun readLine(): String? = ensure().removeFirstOrNull()
    fun readLines(): List<String> = ensure().toList().also { lines?.clear() }
    fun lineSequence(): Sequence<String> = readLines().asSequence()
    fun <T> useLines(block: (Sequence<String>) -> T): T = use { block(readLines().asSequence()) }
    fun forEachLine(action: (String) -> Unit) = readLines().forEach(action)
    override fun close() = reader.close()
}

open class Writer : AutoCloseable {
    open fun write(s: String) {}
    open fun flush() {}
    override fun close() {}
    fun append(s: CharSequence): Writer { write(s.toString()); return this }
}

open class OutputStreamWriter(private val out: OutputStream, charset: Any? = null) : Writer() {
    override fun write(s: String) = out.write(s.encodeToByteArray())
    override fun flush() = out.flush()
    override fun close() = out.close()
}

class BufferedWriter(private val w: Writer, size: Int = 8192) : Writer() {
    override fun write(s: String) = w.write(s)
    fun newLine() = w.write("\n")
    override fun flush() = w.flush()
    override fun close() = w.close()
}

class FileWriter(file: File, append: Boolean = false) : OutputStreamWriter(FileOutputStream(file, append))
class PrintWriter(private val w: Writer) : Writer() {
    override fun write(s: String) = w.write(s)
    fun print(s: Any?) = w.write(s.toString())
    fun println(s: Any?) = w.write(s.toString() + "\n")
    override fun flush() = w.flush()
    override fun close() = w.close()
}

class StringWriter : Writer() {
    private val sb = StringBuilder()
    override fun write(s: String) { sb.append(s) }
    override fun toString() = sb.toString()
}

// ---------------- URL encoding ----------------
object URLEncoder {
    fun encode(s: String, enc: String = "UTF-8"): String = urlEncodeForm(s)
    fun encode(s: String, charset: Charset): String = urlEncodeForm(s)
}

object URLDecoder {
    fun decode(s: String, enc: String = "UTF-8"): String = urlDecode(s, plusAsSpace = true)
    fun decode(s: String, charset: Charset): String = urlDecode(s, plusAsSpace = true)
}

fun urlEncodeForm(s: String): String {
    val sb = StringBuilder()
    for (b in s.encodeToByteArray()) {
        val c = b.toInt() and 0xff
        when {
            c in 'a'.code..'z'.code || c in 'A'.code..'Z'.code || c in '0'.code..'9'.code || c == '.'.code || c == '-'.code || c == '*'.code || c == '_'.code -> sb.append(c.toChar())
            c == ' '.code -> sb.append('+')
            else -> { sb.append('%'); sb.append(HEX[c shr 4]); sb.append(HEX[c and 0xf]) }
        }
    }
    return sb.toString()
}

fun uriEncode(s: String, allow: String = ""): String {
    val sb = StringBuilder()
    for (b in s.encodeToByteArray()) {
        val c = b.toInt() and 0xff
        val ch = c.toChar()
        when {
            ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' || ch in "-_.!~*'()" || (c < 128 && allow.contains(ch)) -> sb.append(ch)
            else -> { sb.append('%'); sb.append(HEX[c shr 4]); sb.append(HEX[c and 0xf]) }
        }
    }
    return sb.toString()
}

private val HEX = "0123456789ABCDEF".toCharArray()

fun urlDecode(s: String, plusAsSpace: Boolean = false): String {
    val out = ByteArrayOutputStream()
    var i = 0
    while (i < s.length) {
        val c = s[i]
        when {
            c == '%' && i + 2 < s.length -> {
                val h = s.substring(i + 1, i + 3).toIntOrNull(16)
                if (h != null) { out.write(h); i += 3 } else { out.write(c.toString().encodeToByteArray()); i++ }
            }
            c == '+' && plusAsSpace -> { out.write(' '.code); i++ }
            else -> { out.write(c.toString().encodeToByteArray()); i++ }
        }
    }
    return out.toByteArray().decodeToString()
}

// ---------------- java.net.URL / HttpURLConnection (on top of the OkHttp shim) ----------------
class URL(private val spec: String) {
    fun openConnection(): HttpURLConnection = HttpURLConnection(spec)
    fun openConnection(proxy: Any?): HttpURLConnection = HttpURLConnection(spec)
    override fun toString() = spec
    val host: String get() = Uri.parse(spec).host ?: ""
    val protocol: String get() = Uri.parse(spec).scheme ?: ""
    val path: String get() = Uri.parse(spec).path ?: ""
    val query: String? get() = spec.substringAfter('?', "").ifEmpty { null }
    fun readText(): String = openConnection().inputStream.readBytes().decodeToString()
    fun openStream(): InputStream = openConnection().inputStream
}

open class HttpURLConnection(private val url: String) {
    companion object {
        const val HTTP_OK = 200
        const val HTTP_CREATED = 201
        const val HTTP_NO_CONTENT = 204
        const val HTTP_MOVED_PERM = 301
        const val HTTP_MOVED_TEMP = 302
        const val HTTP_NOT_MODIFIED = 304
        const val HTTP_BAD_REQUEST = 400
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val HTTP_NOT_FOUND = 404
        const val HTTP_INTERNAL_ERROR = 500
    }
    var requestMethod: String = "GET"
    var connectTimeout: Int = 30000
    var readTimeout: Int = 30000
    var doOutput: Boolean = false
    var doInput: Boolean = true
    var useCaches: Boolean = false
    var instanceFollowRedirects: Boolean = true
    private val headers = LinkedHashMap<String, String>()
    private val body = ByteArrayOutputStream()
    private var response: Response? = null

    fun setRequestProperty(k: String, v: String) { headers[k] = v }
    fun addRequestProperty(k: String, v: String) { headers[k] = v }
    fun getRequestProperty(k: String): String? = headers[k]
    val outputStream: OutputStream get() = body
    fun connect() { ensure() }

    private fun ensure(): Response {
        response?.let { return it }
        val ct = headers.entries.firstOrNull { it.key.equals("Content-Type", true) }?.value
        val b = if (requestMethod == "GET" || requestMethod == "HEAD") null else body.toByteArray().toRequestBody(ct?.toMediaTypeOrNull())
        val rb = Request.Builder().url(url).method(requestMethod, b)
        headers.forEach { (k, v) -> rb.header(k, v) }
        val client = OkHttpClient.Builder()
            .connectTimeout(connectTimeout.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(readTimeout.toLong(), TimeUnit.MILLISECONDS)
            .followRedirects(instanceFollowRedirects)
            .build()
        val r = client.newCall(rb.build()).execute()
        response = r
        return r
    }

    private var bodyBytes: ByteArray? = null
    private fun bytes(): ByteArray = bodyBytes ?: (ensure().body?.bytes() ?: ByteArray(0)).also { bodyBytes = it }

    val responseCode: Int get() = ensure().code
    val responseMessage: String get() = ensure().message
    val inputStream: InputStream get() {
        val r = ensure()
        if (r.code >= 400) throw okio.IOException("Server returned HTTP response code: ${r.code} for URL: $url")
        return ByteArrayInputStream(bytes())
    }
    val errorStream: InputStream get() = ByteArrayInputStream(if (ensure().code >= 400) bytes() else ByteArray(0))
    val contentLength: Int get() = bytes().size
    val contentType: String? get() = ensure().header("Content-Type")
    fun getHeaderField(name: String): String? = ensure().header(name)
    val headerFields: Map<String, List<String>> get() = ensure().headers.toMultimap()
    fun disconnect() {}
}

// ---------------- Base64 (android.util.Base64) ----------------
object Base64 {
    const val DEFAULT = 0
    const val NO_PADDING = 1
    const val NO_WRAP = 2
    const val CRLF = 4
    const val URL_SAFE = 8
    const val NO_CLOSE = 16

    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    fun encodeToString(input: ByteArray, flags: Int): String {
        var s = if (flags and URL_SAFE != 0) kotlin.io.encoding.Base64.UrlSafe.encode(input) else kotlin.io.encoding.Base64.Default.encode(input)
        if (flags and NO_PADDING != 0) s = s.trimEnd('=')
        if (flags and NO_WRAP == 0) s = s.chunked(76).joinToString("\n", postfix = "\n")
        return s
    }

    fun encode(input: ByteArray, flags: Int): ByteArray = encodeToString(input, flags).encodeToByteArray()

    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    fun decode(str: String, flags: Int): ByteArray {
        var s = str.filterNot { it.isWhitespace() }
        if (flags and URL_SAFE != 0 || s.contains('-') || s.contains('_')) s = s.replace('-', '+').replace('_', '/')
        val pad = (4 - s.length % 4) % 4
        if (pad in 1..2) s += "=".repeat(pad)
        return kotlin.io.encoding.Base64.Default.decode(s)
    }

    fun decode(input: ByteArray, flags: Int): ByteArray = decode(input.decodeToString(), flags)
}
