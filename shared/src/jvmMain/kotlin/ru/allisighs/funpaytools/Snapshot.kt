package ru.allisighs.funpaytools

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.EncodedImageFormat

/** Headless renderer used to eyeball screens on CI/Linux: renders composable into PNG frames. */
object Snapshot {
    fun render(name: String, frames: Int = 120, stepMs: Long = 50, content: @Composable () -> Unit) {
        val scene = ImageComposeScene(width = 1170, height = 2532, density = Density(3f), content = content)
        var t = 0L
        var img = scene.render(t)
        repeat(frames) {
            t += stepMs * 1_000_000
            java.lang.Thread.sleep(stepMs / 5)
            img = scene.render(t)
        }
        val data = img.encodeToData(EncodedImageFormat.PNG)!!
        val out = java.io.File(java.lang.System.getProperty("snap.dir", "/tmp/snaps"), "$name.png")
        out.parentFile.mkdirs()
        out.writeBytes(data.bytes)
        println("saved ${out.absolutePath}")
        scene.close()
    }
}

fun selfTest() {
    fun check(name: String, ok: Boolean, info: Any? = "") = println((if (ok) "PASS " else "FAIL ") + name + " " + info)
    // JSON
    val o = JSONObject("{\"a\":1,\"b\":\"x\\\"y\",\"c\":[1,2.5,true,null],\"d\":{\"e\":12345678901}}")
    check("json.int", o.getInt("a") == 1)
    check("json.str", o.getString("b") == "x\"y")
    check("json.arr", o.getJSONArray("c").length() == 4 && o.getJSONArray("c").getDouble(1) == 2.5)
    check("json.long", o.getJSONObject("d").getLong("e") == 12345678901L)
    check("json.roundtrip", JSONObject(o.toString()).toString() == o.toString(), o.toString())
    // Gson compat
    val gson = Gson()
    val t = AutoResponseCommand(trigger = "!привет", response = "Здравствуйте", exactMatch = false)
    val js = gson.toJson(listOf(t))
    val back: List<AutoResponseCommand> = gson.fromJson(js, object : TypeToken<List<AutoResponseCommand>>() {}.type)
    check("gson.list", back.firstOrNull() == t, js)
    val m: Map<String, Any> = gson.fromJson("{\"x\":1,\"y\":[1,2],\"z\":{\"q\":\"w\"}}", object : TypeToken<Map<String, Any>>() {}.type)
    check("gson.dynamic", m["x"] == 1.0 && m["z"] is Map<*, *>, m)
    // format / dates
    check("format", "%.2f ₽ %d %s %,d".format(3.14159, 42, "ok", 1234567) == "3.14 ₽ 42 ok 1 234 567", "%.2f ₽ %d %s %,d".format(3.14159, 42, "ok", 1234567))
    val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.US); sdf.timeZone = TimeZone.getTimeZone("UTC")
    check("date.format", sdf.format(Date(0)) == "01.01.1970 00:00", sdf.format(Date(0)))
    check("date.parse", sdf.parse("15.11.2026 13:45")?.time == 1794750300000L, sdf.parse("15.11.2026 13:45")?.time)
    check("sha1", Sha1.digest("abc".encodeToByteArray()).joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') } == "a9993e364706816aba3e25717850c26c9cd0d89d")
    check("md5", Md5.digest("abc".encodeToByteArray()).joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') } == "900150983cd24fb0d6963f7d28e17f72")
    // prefs
    val prefs = AppContext.getSharedPreferences("selftest", 0)
    prefs.edit().putString("s", "v").putInt("i", 5).putStringSet("ss", setOf("a", "b")).putBoolean("b", true).commit()
    check("prefs", prefs.getString("s", null) == "v" && prefs.getInt("i", 0) == 5 && prefs.getStringSet("ss", null) == setOf("a", "b"))
    // network + interceptors + cookies + ksoup
    var hops = 0
    val client = OkHttpClient.Builder().addNetworkInterceptor { chain -> hops++; chain.proceed(chain.request()) }.build()
    val resp = client.newCall(Request.Builder().url("http://funpay.com/").header("User-Agent", "Mozilla/5.0").build()).execute()
    val html = resp.body?.string() ?: ""
    check("http.redirect", resp.code == 200 && hops >= 2, "code=${resp.code} hops=$hops")
    check("http.setcookie", resp.headers("Set-Cookie").isNotEmpty() || (resp.priorResponse?.headers("Set-Cookie")?.isNotEmpty() == true), resp.headers("Set-Cookie").map { it.substringBefore(';') })
    val doc = com.fleeksoft.ksoup.Ksoup.parse(html)
    check("ksoup", doc.select("a").size > 10, "title=" + doc.title() + " links=" + doc.select("a").size)
    val post = client.newCall(Request.Builder().url("https://httpbin.org/post").post(FormBody.Builder().add("k", "в о").build()).build()).execute()
    check("http.form", post.body?.string()?.contains("\"k\": \"\u0432 \u043e\"") == true || post.code != 200, post.code)
}

fun main(args: Array<String>) {
    val which = args.firstOrNull() ?: "root"
    if (which == "selftest") { try { selfTest() } catch (e: Throwable) { e.printStackTrace() }; kotlin.system.exitProcess(0) }
    javax.swing.SwingUtilities.invokeAndWait {
        AppBootstrap.init()
        for (w in which.split("+")) {
            if (w == "root") { Snapshot.render("root", frames = 160) { FunPayToolsRoot() }; continue }
            try { Snapshot.render(w.replace("/", "_"), frames = 60) {
                val theme = androidx.compose.runtime.remember { ThemeManager.loadTheme(AppContext) }
                DesignTokens.theme = theme
                androidx.compose.material3.MaterialTheme(colorScheme = colorSchemeFor(theme)) {
                    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
                        FunPayToolsApp(w, AppBootstrap.repository, theme, pendingChatIdState) { }
                        ToastOverlay()
                    }
                }
            } } catch (t: Throwable) {
                println("CRASH $w: $t")
                t.stackTrace.take(4).forEach { println("    at $it") }
            }
        }
    }
    SharedPreferencesImpl.flushAll()
    kotlin.system.exitProcess(0)
}

