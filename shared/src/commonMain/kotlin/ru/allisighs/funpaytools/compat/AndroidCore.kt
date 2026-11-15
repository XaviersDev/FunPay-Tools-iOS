package ru.allisighs.funpaytools

import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

/** Platform services implemented in iosMain / jvmMain. */
expect object PlatformBridge {
    val filesDir: String
    val cacheDir: String
    val documentsDir: String
    fun openUrl(url: String): Boolean
    fun openAppSettings()
    fun shareText(text: String, title: String?)
    fun shareFile(path: String, mime: String?)
    fun setClipboard(text: String)
    fun getClipboard(): String?
    fun deviceId(): String
    fun vibrate(ms: Long)
    fun keepScreenOn(on: Boolean)
    fun saveImageToGallery(bytes: ByteArray): Boolean
    val deviceModel: String
    val osVersion: String
    fun runOnMain(block: () -> Unit)
    fun isNetworkAvailable(): Boolean
    fun exportFile(path: String, suggestedName: String)
    fun setClipboardImage(bytes: ByteArray): Boolean
    fun clipboardHasImage(): Boolean
    /** Writes the clipboard image to a temp PNG and returns its path. */
    fun clipboardImageToFile(): String?
    fun clipboardChangeCount(): Long
    fun setSystemBarsHidden(hidden: Boolean)
    /** Номер сборки (CFBundleVersion на iOS); 0, если неизвестен. */
    val buildNumber: Int
    /** true на iPhone/iPad. */
    val isIos: Boolean
}

val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

// ------------------------------------------------------------------------------------------------
// Context
// ------------------------------------------------------------------------------------------------
open class Context {
    companion object {
        const val MODE_PRIVATE = 0
        const val MODE_APPEND = 32768
        const val CLIPBOARD_SERVICE = "clipboard"
        const val NOTIFICATION_SERVICE = "notification"
        const val POWER_SERVICE = "power"
        const val ALARM_SERVICE = "alarm"
        const val VIBRATOR_SERVICE = "vibrator"
        const val VIBRATOR_MANAGER_SERVICE = "vibrator_manager"
        const val CONNECTIVITY_SERVICE = "connectivity"
        const val JOB_SCHEDULER_SERVICE = "jobscheduler"
        const val WINDOW_SERVICE = "window"
        const val SENSOR_SERVICE = "sensor"
        const val AUDIO_SERVICE = "audio"
        const val INPUT_METHOD_SERVICE = "input_method"
        const val ACTIVITY_SERVICE = "activity"
        const val APPWIDGET_SERVICE = "appwidget"
        const val KEYGUARD_SERVICE = "keyguard"
        const val RECEIVER_EXPORTED = 2
        const val RECEIVER_NOT_EXPORTED = 4
    }

    open val applicationContext: Context get() = AppContext
    val packageName: String get() = "ru.allisighs.funpaytools"
    val filesDir: File get() = File(PlatformBridge.filesDir).also { if (!it.exists()) it.mkdirs() }
    val cacheDir: File get() = File(PlatformBridge.cacheDir).also { if (!it.exists()) it.mkdirs() }
    val externalCacheDir: File? get() = cacheDir
    fun getExternalFilesDir(type: String?): File? = File(filesDir, type ?: "external").also { it.mkdirs() }
    fun getDir(name: String, mode: Int): File = File(filesDir, "app_$name").also { it.mkdirs() }
    fun getDatabasePath(name: String): File = File(filesDir, "databases/$name")
    val contentResolver: ContentResolver get() = ContentResolver
    val packageManager: PackageManager get() = PackageManager
    val resources: Resources get() = Resources
    val assets: AssetManager get() = AssetManager
    val mainLooper: Looper get() = Looper.getMainLooper()

    fun getSharedPreferences(name: String, mode: Int): SharedPreferences = SharedPreferencesImpl.get(name)
    fun deleteSharedPreferences(name: String): Boolean { SharedPreferencesImpl.get(name).edit().clear().commit(); return true }

    fun getString(id: Int): String = ""
    fun getColor(id: Int): Int = 0

    open fun startActivity(intent: Intent) = IntentRouter.startActivity(intent)
    open fun startActivity(intent: Intent, options: Any?) = IntentRouter.startActivity(intent)
    fun startService(intent: Intent): ComponentName? { IntentRouter.startService(intent); return null }
    fun startForegroundService(intent: Intent): ComponentName? { IntentRouter.startService(intent); return null }
    fun stopService(intent: Intent): Boolean { IntentRouter.stopService(intent); return true }
    fun sendBroadcast(intent: Intent) = IntentRouter.sendBroadcast(intent)
    fun registerReceiver(r: BroadcastReceiver?, f: IntentFilter?): Intent? = null
    fun registerReceiver(r: BroadcastReceiver?, f: IntentFilter?, flags: Int): Intent? = null
    fun unregisterReceiver(r: BroadcastReceiver?) {}
    fun checkSelfPermission(p: String): Int = PackageManager.PERMISSION_GRANTED

    fun getSystemService(name: String): Any? = when (name) {
        CLIPBOARD_SERVICE -> ClipboardManager
        NOTIFICATION_SERVICE -> NotificationManager
        POWER_SERVICE -> PowerManager
        ALARM_SERVICE -> AlarmManager
        VIBRATOR_SERVICE -> Vibrator
        VIBRATOR_MANAGER_SERVICE -> VibratorManager
        CONNECTIVITY_SERVICE -> ConnectivityManager
        JOB_SCHEDULER_SERVICE -> JobScheduler
        WINDOW_SERVICE -> WindowManager
        AUDIO_SERVICE -> AudioManager
        INPUT_METHOD_SERVICE -> InputMethodManager
        ACTIVITY_SERVICE -> ActivityManager
        APPWIDGET_SERVICE -> AppWidgetManager
        KEYGUARD_SERVICE -> KeyguardManager
        SENSOR_SERVICE -> SensorManager
        else -> null
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getSystemService(cls: KClass<T>): T = when (cls) {
        ClipboardManager::class -> ClipboardManager
        NotificationManager::class -> NotificationManager
        PowerManager::class -> PowerManager
        AlarmManager::class -> AlarmManager
        Vibrator::class -> Vibrator
        VibratorManager::class -> VibratorManager
        ConnectivityManager::class -> ConnectivityManager
        JobScheduler::class -> JobScheduler
        WindowManager::class -> WindowManager
        AudioManager::class -> AudioManager
        InputMethodManager::class -> InputMethodManager
        ActivityManager::class -> ActivityManager
        AppWidgetManager::class -> AppWidgetManager
        KeyguardManager::class -> KeyguardManager
        SensorManager::class -> SensorManager
        else -> throw IllegalArgumentException("Unknown service $cls")
    } as T
}

object AppContext : Activity()

open class Activity : Context() {
    var requestedOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        set(v) { field = v; OrientationController.request(v) }
    val window: Window get() = Window
    val intent: Intent get() = Intent()
    fun finish() {}
    fun finishAffinity() {}
    fun moveTaskToBack(b: Boolean): Boolean = true
    fun runOnUiThread(block: () -> Unit) = PlatformBridge.runOnMain(block)
    val isFinishing: Boolean get() = false
    val isDestroyed: Boolean get() = false
    fun recreate() {}
    fun setShowWhenLocked(b: Boolean) {}
    fun setTurnScreenOn(b: Boolean) {}
}

typealias ComponentActivity = Activity
typealias Application = Activity
typealias ContextWrapper = Context

object OrientationController {
    val requested = MutableStateFlow(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
    fun request(o: Int) { requested.value = o }
}

object Window {
    fun addFlags(f: Int) { if (f and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0) PlatformBridge.keepScreenOn(true) }
    fun clearFlags(f: Int) { if (f and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0) PlatformBridge.keepScreenOn(false) }
    fun setFlags(f: Int, mask: Int) = addFlags(f)
    var statusBarColor: Int = 0
    var navigationBarColor: Int = 0
    val decorView: Any = Unit
    val attributes: WindowManager.LayoutParams = WindowManager.LayoutParams()
    fun setAttributes(a: WindowManager.LayoutParams) {}
}

object WindowManager {
    class LayoutParams {
        var screenBrightness: Float = -1f
        var flags: Int = 0
        companion object {
            const val FLAG_KEEP_SCREEN_ON = 128
            const val FLAG_SHOW_WHEN_LOCKED = 524288
            const val FLAG_TURN_SCREEN_ON = 2097152
            const val FLAG_FULLSCREEN = 1024
            const val FLAG_DISMISS_KEYGUARD = 4194304
            const val BRIGHTNESS_OVERRIDE_NONE = -1f
        }
    }
}

object ActivityInfo {
    const val SCREEN_ORIENTATION_UNSPECIFIED = -1
    const val SCREEN_ORIENTATION_LANDSCAPE = 0
    const val SCREEN_ORIENTATION_PORTRAIT = 1
    const val SCREEN_ORIENTATION_USER = 2
    const val SCREEN_ORIENTATION_SENSOR = 4
    const val SCREEN_ORIENTATION_SENSOR_LANDSCAPE = 6
    const val SCREEN_ORIENTATION_SENSOR_PORTRAIT = 7
    const val SCREEN_ORIENTATION_FULL_SENSOR = 10
}

object Resources {
    val displayMetrics = DisplayMetrics()
    val configuration = Configuration()
    fun getIdentifier(name: String, type: String, pkg: String): Int = 0
    fun getDimensionPixelSize(id: Int): Int = 0
    fun openRawResource(id: Int): InputStream = ByteArrayInputStream(ByteArray(0))
}

class DisplayMetrics {
    var density: Float = 3f
    var widthPixels: Int = 1179
    var heightPixels: Int = 2556
    var densityDpi: Int = 460
    var scaledDensity: Float = 3f
}

object AssetManager {
    fun open(name: String): InputStream = ByteArrayInputStream(BundledAssets.get(name) ?: throw FileNotFoundException(name))
    fun list(path: String): Array<String> = BundledAssets.names().toTypedArray()
}

object PackageManager {
    const val PERMISSION_GRANTED = 0
    const val PERMISSION_DENIED = -1
    const val GET_META_DATA = 128
    const val MATCH_DEFAULT_ONLY = 65536
    fun getPackageInfo(pkg: String, flags: Int): PackageInfo = PackageInfo()
    fun getLaunchIntentForPackage(pkg: String): Intent? = null
    fun queryIntentActivities(i: Intent, flags: Int): List<Any> = emptyList()
    fun resolveActivity(i: Intent, flags: Int): Any? = Unit
    fun hasSystemFeature(f: String): Boolean = false
    fun setComponentEnabledSetting(c: ComponentName, s: Int, f: Int) {}
    class NameNotFoundException : Exception()
}

class PackageInfo {
    val versionName: String = APP_VERSION
    val versionCode: Int = 2
    val longVersionCode: Long = 2
    val firstInstallTime: Long = 0
}

object ContextCompat {
    fun checkSelfPermission(c: Context, p: String): Int = PackageManager.PERMISSION_GRANTED
    fun startForegroundService(c: Context, i: Intent) = c.startForegroundService(i)
    fun getColor(c: Context, id: Int): Int = 0
    fun getSystemService(c: Context, cls: KClass<*>): Any? = null
}

object Manifest {
    object permission {
        const val POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"
        const val READ_MEDIA_IMAGES = "android.permission.READ_MEDIA_IMAGES"
        const val READ_EXTERNAL_STORAGE = "android.permission.READ_EXTERNAL_STORAGE"
        const val WRITE_EXTERNAL_STORAGE = "android.permission.WRITE_EXTERNAL_STORAGE"
        const val CAMERA = "android.permission.CAMERA"
        const val SCHEDULE_EXACT_ALARM = "android.permission.SCHEDULE_EXACT_ALARM"
        const val INTERNET = "android.permission.INTERNET"
    }
}

object AndroidBuild {
    object VERSION {
        val SDK_INT: Int = 34
        val RELEASE: String get() = PlatformBridge.osVersion
        val CODENAME = "REL"
    }
    object VERSION_CODES {
        const val LOLLIPOP = 21
        const val M = 23
        const val N = 24
        const val N_MR1 = 25
        const val O = 26
        const val O_MR1 = 27
        const val P = 28
        const val Q = 29
        const val R = 30
        const val S = 31
        const val S_V2 = 32
        const val TIRAMISU = 33
        const val UPSIDE_DOWN_CAKE = 34
        const val VANILLA_ICE_CREAM = 35
    }
    val MANUFACTURER = "Apple"
    val BRAND = "Apple"
    val MODEL: String get() = PlatformBridge.deviceModel
    val DEVICE: String get() = PlatformBridge.deviceModel
    val PRODUCT = "iPhone"
    val HARDWARE = "apple"
    val FINGERPRINT = "apple/iphone"
    val ID = "ios"
    val DISPLAY = "iOS"
    val BOARD = "apple"
}

object R {
    object drawable {
        const val ic_dialog_alert = 1
        const val ic_funpay_arrows = 2
        const val ic_menu_close_clear_cancel = 3
        const val stat_notify_chat = 4
        const val stat_notify_sync = 5
        const val sym_action_call = 6
        const val sym_def_app_icon = 7
        const val ic_menu_send = 8
    }
    object mipmap {
        const val ic_launcher_foreground = 100
        const val ic_launcher = 101
    }
    object id {
        const val widget_avatar = 200
        const val widget_background = 201
        const val widget_balance = 202
        const val widget_balance_text = 203
        const val widget_rating = 204
        const val widget_root = 205
        const val widget_title_text = 206
        const val widget_username = 207
    }
    object layout {
        const val widget_balance = 300
        const val widget_profile = 301
    }
    object raw {
        const val funpay_push = 400
    }
}

object Log {
    fun d(tag: String?, msg: String?): Int { debugLog("D", tag, msg); return 0 }
    fun i(tag: String?, msg: String?): Int { debugLog("I", tag, msg); return 0 }
    fun w(tag: String?, msg: String?): Int { debugLog("W", tag, msg); return 0 }
    fun w(tag: String?, msg: String?, t: Throwable?): Int { debugLog("W", tag, "$msg ${t?.message}"); return 0 }
    fun e(tag: String?, msg: String?): Int { debugLog("E", tag, msg); return 0 }
    fun e(tag: String?, msg: String?, t: Throwable?): Int { debugLog("E", tag, "$msg ${t?.message}"); return 0 }
    fun v(tag: String?, msg: String?): Int { debugLog("V", tag, msg); return 0 }
    private fun debugLog(level: String, tag: String?, msg: String?) { println("$level/$tag: $msg") }
}

// ------------------------------------------------------------------------------------------------
// SharedPreferences (JSON file backed, same semantics as Android)
// ------------------------------------------------------------------------------------------------
interface SharedPreferences {
    fun getString(key: String, defValue: String?): String?
    fun getStringSet(key: String, defValues: Set<String>?): Set<String>?
    fun getInt(key: String, defValue: Int): Int
    fun getLong(key: String, defValue: Long): Long
    fun getFloat(key: String, defValue: Float): Float
    fun getBoolean(key: String, defValue: Boolean): Boolean
    fun contains(key: String): Boolean
    fun edit(): Editor
    val all: Map<String, *>
    fun registerOnSharedPreferenceChangeListener(l: OnSharedPreferenceChangeListener)
    fun unregisterOnSharedPreferenceChangeListener(l: OnSharedPreferenceChangeListener)

    interface Editor {
        fun putString(key: String, value: String?): Editor
        fun putStringSet(key: String, values: Set<String>?): Editor
        fun putInt(key: String, value: Int): Editor
        fun putLong(key: String, value: Long): Editor
        fun putFloat(key: String, value: Float): Editor
        fun putBoolean(key: String, value: Boolean): Editor
        fun remove(key: String): Editor
        fun clear(): Editor
        fun commit(): Boolean
        fun apply()
    }

    fun interface OnSharedPreferenceChangeListener {
        fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?)
    }
}

inline fun SharedPreferences.edit(commit: Boolean = false, action: SharedPreferences.Editor.() -> Unit) {
    val e = edit()
    action(e)
    if (commit) e.commit() else e.apply()
}

class SharedPreferencesImpl private constructor(private val name: String) : SharedPreferences {
    private val file = File(File(PlatformBridge.filesDir, "shared_prefs"), "$name.json")
    private val lock = Any()
    private val data = LinkedHashMap<String, Any>()
    private val listeners = ArrayList<SharedPreferences.OnSharedPreferenceChangeListener>()
    private var writeJob: Job? = null

    init { load() }

    companion object {
        private val instances = LinkedHashMap<String, SharedPreferencesImpl>()
        private val globalLock = Any()
        fun get(name: String): SharedPreferencesImpl = synchronized(globalLock) {
            instances.getOrPut(name) { SharedPreferencesImpl(name) }
        }
        fun flushAll() {
            val list = synchronized(globalLock) { instances.values.toList() }
            list.forEach { it.writeNow() }
        }
        fun allNames(): List<String> {
            val dir = File(PlatformBridge.filesDir, "shared_prefs")
            return dir.listFiles()?.filter { it.name.endsWith(".json") }?.map { it.name.removeSuffix(".json") } ?: emptyList()
        }
    }

    private fun load() {
        try {
            if (!file.exists()) return
            val o = JSONObject(file.readText())
            fun section(k: String, f: (String, Any?) -> Any?) {
                val s = o.optJSONObject(k) ?: return
                for (key in s.keys()) f(key, s.opt(key))?.let { data[key] = it }
            }
            section("s") { _, v -> v as? String }
            section("i") { _, v -> (v as? Number)?.toInt() }
            section("l") { _, v -> (v as? Number)?.toLong() }
            section("f") { _, v -> (v as? Number)?.toFloat() }
            section("b") { _, v -> v as? Boolean }
            section("ss") { _, v -> (v as? JSONArray)?.let { arr -> (0 until arr.length()).map { arr.optString(it) }.toSet() } }
        } catch (e: Exception) {
            println("prefs load failed $name: ${e.message}")
        }
    }

    private fun serialize(): String {
        val s = JSONObject(); val i = JSONObject(); val l = JSONObject(); val f = JSONObject(); val b = JSONObject(); val ss = JSONObject()
        synchronized(lock) {
            for ((k, v) in data) when (v) {
                is String -> s.put(k, v)
                is Int -> i.put(k, v)
                is Long -> l.put(k, v)
                is Float -> f.put(k, v.toDouble())
                is Boolean -> b.put(k, v)
                is Set<*> -> ss.put(k, JSONArray(v.toList()))
            }
        }
        return JSONObject().put("s", s).put("i", i).put("l", l).put("f", f).put("b", b).put("ss", ss).toString()
    }

    internal fun writeNow() {
        try {
            val text = serialize()
            val tmp = File(file.path + ".tmp")
            file.parentFile?.mkdirs()
            tmp.writeText(text)
            if (!tmp.renameTo(file)) file.writeText(text)
        } catch (e: Exception) {
            println("prefs write failed $name: ${e.message}")
        }
    }

    private fun scheduleWrite() {
        synchronized(lock) {
            writeJob?.cancel()
            writeJob = appScope.launch(Dispatchers.IO) {
                delay(250)
                writeNow()
            }
        }
    }

    override fun getString(key: String, defValue: String?): String? = synchronized(lock) { data[key] as? String ?: defValue }
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? = synchronized(lock) { (data[key] as? Set<String>)?.toSet() ?: defValues }
    override fun getInt(key: String, defValue: Int): Int = synchronized(lock) { (data[key] as? Number)?.toInt() ?: defValue }
    override fun getLong(key: String, defValue: Long): Long = synchronized(lock) { (data[key] as? Number)?.toLong() ?: defValue }
    override fun getFloat(key: String, defValue: Float): Float = synchronized(lock) { (data[key] as? Number)?.toFloat() ?: defValue }
    override fun getBoolean(key: String, defValue: Boolean): Boolean = synchronized(lock) { data[key] as? Boolean ?: defValue }
    override fun contains(key: String): Boolean = synchronized(lock) { data.containsKey(key) }
    override val all: Map<String, *> get() = synchronized(lock) { LinkedHashMap(data) }
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) { synchronized(lock) { listeners.add(l) } }
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) { synchronized(lock) { listeners.remove(l) } }

    override fun edit(): SharedPreferences.Editor = EditorImpl()

    private inner class EditorImpl : SharedPreferences.Editor {
        private val puts = LinkedHashMap<String, Any?>()
        private var clear = false
        override fun putString(key: String, value: String?) = apply { puts[key] = value }
        override fun putStringSet(key: String, values: Set<String>?) = apply { puts[key] = values?.toSet() }
        override fun putInt(key: String, value: Int) = apply { puts[key] = value }
        override fun putLong(key: String, value: Long) = apply { puts[key] = value }
        override fun putFloat(key: String, value: Float) = apply { puts[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { puts[key] = value }
        override fun remove(key: String) = apply { puts[key] = REMOVED }
        override fun clear() = apply { clear = true }

        private fun applyToMemory(): List<String> {
            val changed = ArrayList<String>()
            synchronized(lock) {
                if (clear) { changed.addAll(data.keys); data.clear() }
                for ((k, v) in puts) {
                    if (v == null || v === REMOVED) { if (data.remove(k) != null) changed.add(k) }
                    else if (data[k] != v) { data[k] = v; changed.add(k) }
                }
            }
            return changed
        }

        private fun notify(keys: List<String>) {
            if (keys.isEmpty()) return
            val ls = synchronized(lock) { listeners.toList() }
            if (ls.isEmpty()) return
            PlatformBridge.runOnMain { keys.forEach { k -> ls.forEach { it.onSharedPreferenceChanged(this@SharedPreferencesImpl, k) } } }
        }

        override fun commit(): Boolean {
            val keys = applyToMemory()
            writeNow()
            notify(keys)
            return true
        }

        override fun apply() {
            val keys = applyToMemory()
            scheduleWrite()
            notify(keys)
        }
    }
}

private val REMOVED = Any()

object PreferenceManager {
    fun getDefaultSharedPreferences(c: Context): SharedPreferences = c.getSharedPreferences("ru.allisighs.funpaytools_preferences", 0)
}

// ------------------------------------------------------------------------------------------------
// Uri
// ------------------------------------------------------------------------------------------------
class Uri private constructor(private val raw: String) : Comparable<Uri> {
    val scheme: String? get() = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*):").find(raw)?.groupValues?.get(1)
    private val afterScheme: String get() = scheme?.let { raw.substring(it.length + 1) } ?: raw
    val isHierarchical: Boolean get() = scheme == null || afterScheme.startsWith("/")
    val isOpaque: Boolean get() = !isHierarchical
    val isAbsolute: Boolean get() = scheme != null
    val isRelative: Boolean get() = scheme == null
    val schemeSpecificPart: String get() = afterScheme.substringBefore('#')
    val encodedAuthority: String? get() = if (afterScheme.startsWith("//")) afterScheme.substring(2).substringBefore('/').substringBefore('?').substringBefore('#') else null
    val authority: String? get() = encodedAuthority?.let { urlDecode(it) }
    val host: String? get() = encodedAuthority?.substringAfterLast('@')?.let { a ->
        if (a.startsWith("[")) a.substringBefore(']') + "]" else a.substringBefore(':')
    }?.ifEmpty { null }
    val port: Int get() = encodedAuthority?.substringAfterLast('@')?.substringAfterLast(':', "")?.toIntOrNull() ?: -1
    val userInfo: String? get() = encodedAuthority?.takeIf { it.contains('@') }?.substringBefore('@')
    val encodedPath: String? get() {
        val s = if (afterScheme.startsWith("//")) afterScheme.substring(2).let { a -> val i = a.indexOfFirst { it == '/' || it == '?' || it == '#' }; if (i < 0) "" else a.substring(i) } else afterScheme
        return s.substringBefore('?').substringBefore('#').let { if (scheme != null && !isHierarchical) null else it }
    }
    val path: String? get() = encodedPath?.let { urlDecode(it) }
    val encodedQuery: String? get() = raw.substringBefore('#').substringAfter('?', "").ifEmpty { null }
    val query: String? get() = encodedQuery?.let { urlDecode(it) }
    val encodedFragment: String? get() = raw.substringAfter('#', "").ifEmpty { null }
    val fragment: String? get() = encodedFragment?.let { urlDecode(it) }
    val pathSegments: List<String> get() = (path ?: "").split('/').filter { it.isNotEmpty() }
    val lastPathSegment: String? get() = pathSegments.lastOrNull()
    val queryParameterNames: Set<String> get() = (encodedQuery ?: "").split('&').filter { it.isNotEmpty() }.map { urlDecode(it.substringBefore('='), true) }.toCollection(LinkedHashSet())

    fun getQueryParameter(key: String): String? {
        val q = encodedQuery ?: return null
        for (part in q.split('&')) {
            if (part.isEmpty()) continue
            val k = urlDecode(part.substringBefore('='), true)
            if (k == key) return urlDecode(part.substringAfter('=', ""), true)
        }
        return null
    }

    fun getQueryParameters(key: String): List<String> {
        val q = encodedQuery ?: return emptyList()
        return q.split('&').filter { urlDecode(it.substringBefore('='), true) == key }.map { urlDecode(it.substringAfter('=', ""), true) }
    }

    fun getBooleanQueryParameter(key: String, def: Boolean): Boolean = getQueryParameter(key)?.let { it != "false" && it != "0" } ?: def
    @kotlin.jvm.JvmName("getSchemeFn") fun getScheme() = scheme
    @kotlin.jvm.JvmName("getHostFn") fun getHost() = host
    @kotlin.jvm.JvmName("getPathFn") fun getPath() = path

    fun buildUpon(): Builder = Builder().also { it.base = raw }
    fun normalizeScheme(): Uri = scheme?.let { s -> Uri(s.lowercase() + raw.substring(s.length)) } ?: this

    override fun toString() = raw
    override fun equals(other: Any?) = other is Uri && other.raw == raw
    override fun hashCode() = raw.hashCode()
    override fun compareTo(other: Uri) = raw.compareTo(other.raw)

    class Builder {
        internal var base = ""
        private var scheme: String? = null
        private var authority: String? = null
        private var path = StringBuilder()
        private val query = ArrayList<String>()
        private var fragment: String? = null
        fun scheme(s: String) = apply { scheme = s }
        fun authority(a: String) = apply { authority = a }
        fun encodedAuthority(a: String) = apply { authority = a }
        fun path(p: String) = apply { path = StringBuilder(uriEncode(p, "/")) }
        fun encodedPath(p: String) = apply { path = StringBuilder(p) }
        fun appendPath(p: String) = apply { if (!path.endsWith("/")) path.append('/'); path.append(uriEncode(p)) }
        fun appendEncodedPath(p: String) = apply { if (!path.endsWith("/")) path.append('/'); path.append(p.trimStart('/')) }
        fun appendQueryParameter(k: String, v: String?) = apply { query.add(uriEncode(k) + "=" + uriEncode(v ?: "")) }
        fun clearQuery() = apply { query.clear(); base = base.substringBefore('?') }
        fun fragment(f: String?) = apply { fragment = f }
        fun build(): Uri {
            var s = if (base.isNotEmpty()) base.substringBefore('#') else buildString {
                if (scheme != null) append(scheme).append(':')
                if (authority != null) append("//").append(authority)
                append(path)
            }
            if (query.isNotEmpty()) s += (if (s.contains('?')) "&" else "?") + query.joinToString("&")
            fragment?.let { s += "#" + uriEncode(it) }
            return Uri(s)
        }
    }

    companion object {
        val EMPTY = Uri("")
        fun parse(s: String?): Uri = Uri(s ?: "")
        fun fromFile(f: File): Uri = Uri("file://" + f.absolutePath)
        fun fromParts(scheme: String, ssp: String, fragment: String?): Uri = Uri("$scheme:${uriEncode(ssp, "@:/")}" + (fragment?.let { "#$it" } ?: ""))
        fun encode(s: String?): String? = s?.let { uriEncode(it) }
        fun encode(s: String?, allow: String?): String? = s?.let { uriEncode(it, allow ?: "") }
        fun decode(s: String?): String = s?.let { urlDecode(it) } ?: ""
        fun withAppendedPath(base: Uri, seg: String): Uri = base.buildUpon().appendEncodedPath(seg).build()
    }
}

fun String.toUri(): Uri = Uri.parse(this)

class ComponentName(val packageName: String, val className: String) {
    internal var target: KClass<*>? = null
    constructor(context: Context, cls: KClass<*>) : this("ru.allisighs.funpaytools", cls.simpleName ?: "") { target = cls }
    fun flattenToString() = "$packageName/$className"
}

// ------------------------------------------------------------------------------------------------
// Bundle / Intent
// ------------------------------------------------------------------------------------------------
open class Bundle(internal val map: MutableMap<String, Any?> = LinkedHashMap()) {
    fun putString(k: String, v: String?) { map[k] = v }
    fun putInt(k: String, v: Int) { map[k] = v }
    fun putLong(k: String, v: Long) { map[k] = v }
    fun putBoolean(k: String, v: Boolean) { map[k] = v }
    fun putFloat(k: String, v: Float) { map[k] = v }
    fun putDouble(k: String, v: Double) { map[k] = v }
    fun putCharSequence(k: String, v: CharSequence?) { map[k] = v }
    fun putStringArrayList(k: String, v: ArrayList<String>?) { map[k] = v }
    fun putBundle(k: String, v: Bundle?) { map[k] = v }
    fun putAll(b: Bundle) { map.putAll(b.map) }
    fun getString(k: String): String? = map[k]?.toString()
    fun getString(k: String, def: String): String = map[k]?.toString() ?: def
    fun getInt(k: String, def: Int = 0): Int = (map[k] as? Number)?.toInt() ?: def
    fun getLong(k: String, def: Long = 0L): Long = (map[k] as? Number)?.toLong() ?: def
    fun getBoolean(k: String, def: Boolean = false): Boolean = map[k] as? Boolean ?: def
    fun getFloat(k: String, def: Float = 0f): Float = (map[k] as? Number)?.toFloat() ?: def
    fun getDouble(k: String, def: Double = 0.0): Double = (map[k] as? Number)?.toDouble() ?: def
    fun getCharSequence(k: String): CharSequence? = map[k] as? CharSequence
    @Suppress("UNCHECKED_CAST")
    fun getStringArrayList(k: String): ArrayList<String>? = map[k] as? ArrayList<String>
    fun getBundle(k: String): Bundle? = map[k] as? Bundle
    fun containsKey(k: String) = map.containsKey(k)
    fun keySet(): Set<String> = map.keys
    fun get(k: String): Any? = map[k]
    fun remove(k: String) { map.remove(k) }
    fun isEmpty() = map.isEmpty()
    fun size() = map.size
    fun clear() = map.clear()
}

class IntentFilter(vararg actions: String) {
    val actions = actions.toMutableList()
    fun addAction(a: String) { actions.add(a) }
    var priority: Int = 0
}

open class Intent() {
    var action: String? = null
    var data: Uri? = null
    var type: String? = null
    var flags: Int = 0
    var `package`: String? = null
    var component: ComponentName? = null
    val categories = LinkedHashSet<String>()
    val extrasBundle = Bundle()
    val extras: Bundle? get() = extrasBundle
    var clipData: ClipData? = null
    var selector: Intent? = null

    constructor(action: String?) : this() { this.action = action }
    constructor(action: String?, uri: Uri?) : this() { this.action = action; this.data = uri }
    constructor(context: Context?, cls: KClass<*>) : this() { component = ComponentName(AppContext, cls) }
    constructor(action: String?, uri: Uri?, context: Context?, cls: KClass<*>) : this() { this.action = action; data = uri; component = ComponentName(AppContext, cls) }
    constructor(other: Intent) : this() {
        action = other.action; data = other.data; type = other.type; flags = other.flags; component = other.component
        extrasBundle.putAll(other.extrasBundle)
    }

    fun setAction(a: String?): Intent { action = a; return this }
    fun setData(u: Uri?): Intent { data = u; return this }
    fun setType(t: String?): Intent { type = t; return this }
    fun setDataAndType(u: Uri?, t: String?): Intent { data = u; type = t; return this }
    fun setFlags(f: Int): Intent { flags = f; return this }
    fun addFlags(f: Int): Intent { flags = flags or f; return this }
    fun setPackage(p: String?): Intent { `package` = p; return this }
    val dataString: String? get() = data?.toString()
    fun setComponent(c: ComponentName?): Intent { component = c; return this }
    fun setClassName(pkg: String, cls: String): Intent { component = ComponentName(pkg, cls); return this }
    fun setClassName(ctx: Context, cls: String): Intent { component = ComponentName("ru.allisighs.funpaytools", cls); return this }
    fun setClass(ctx: Context, cls: KClass<*>): Intent { component = ComponentName(ctx, cls); return this }
    fun addCategory(c: String): Intent { categories.add(c); return this }
    @kotlin.jvm.JvmName("getPackageFn") fun getPackage(): String? = `package`

    fun putExtra(k: String, v: String?): Intent { extrasBundle.map[k] = v; return this }
    fun putExtra(k: String, v: CharSequence?): Intent { extrasBundle.map[k] = v; return this }
    fun putExtra(k: String, v: Int): Intent { extrasBundle.map[k] = v; return this }
    fun putExtra(k: String, v: Long): Intent { extrasBundle.map[k] = v; return this }
    fun putExtra(k: String, v: Boolean): Intent { extrasBundle.map[k] = v; return this }
    fun putExtra(k: String, v: Float): Intent { extrasBundle.map[k] = v; return this }
    fun putExtra(k: String, v: Double): Intent { extrasBundle.map[k] = v; return this }
    fun putExtra(k: String, v: Uri?): Intent { extrasBundle.map[k] = v; return this }
    fun putExtra(k: String, v: Intent?): Intent { extrasBundle.map[k] = v; return this }
    fun putExtra(k: String, v: Bundle?): Intent { extrasBundle.map[k] = v; return this }
    fun putExtra(k: String, v: Array<String>?): Intent { extrasBundle.map[k] = v; return this }
    fun putExtras(b: Bundle): Intent { extrasBundle.putAll(b); return this }
    fun putStringArrayListExtra(k: String, v: ArrayList<String>?): Intent { extrasBundle.map[k] = v; return this }
    fun putParcelableArrayListExtra(k: String, v: ArrayList<*>?): Intent { extrasBundle.map[k] = v; return this }

    fun getStringExtra(k: String): String? = extrasBundle.map[k]?.toString()
    fun getCharSequenceExtra(k: String): CharSequence? = extrasBundle.map[k] as? CharSequence
    fun getIntExtra(k: String, def: Int): Int = (extrasBundle.map[k] as? Number)?.toInt() ?: def
    fun getLongExtra(k: String, def: Long): Long = (extrasBundle.map[k] as? Number)?.toLong() ?: def
    fun getBooleanExtra(k: String, def: Boolean): Boolean = extrasBundle.map[k] as? Boolean ?: def
    fun getFloatExtra(k: String, def: Float): Float = (extrasBundle.map[k] as? Number)?.toFloat() ?: def
    fun getDoubleExtra(k: String, def: Double): Double = (extrasBundle.map[k] as? Number)?.toDouble() ?: def
    @Suppress("UNCHECKED_CAST")
    fun <T> getParcelableExtra(k: String): T? = extrasBundle.map[k] as? T
    @Suppress("UNCHECKED_CAST")
    fun getStringArrayListExtra(k: String): ArrayList<String>? = extrasBundle.map[k] as? ArrayList<String>
    fun hasExtra(k: String) = extrasBundle.containsKey(k)
    fun removeExtra(k: String) { extrasBundle.remove(k) }
    fun resolveActivity(pm: PackageManager): ComponentName? = ComponentName("ios", "ios")
    fun toUri(flags: Int): String = data?.toString() ?: ""

    companion object {
        const val ACTION_VIEW = "android.intent.action.VIEW"
        const val ACTION_SEND = "android.intent.action.SEND"
        const val ACTION_SEND_MULTIPLE = "android.intent.action.SEND_MULTIPLE"
        const val ACTION_SENDTO = "android.intent.action.SENDTO"
        const val ACTION_DIAL = "android.intent.action.DIAL"
        const val ACTION_MAIN = "android.intent.action.MAIN"
        const val ACTION_PICK = "android.intent.action.PICK"
        const val ACTION_GET_CONTENT = "android.intent.action.GET_CONTENT"
        const val ACTION_OPEN_DOCUMENT = "android.intent.action.OPEN_DOCUMENT"
        const val ACTION_CREATE_DOCUMENT = "android.intent.action.CREATE_DOCUMENT"
        const val ACTION_BOOT_COMPLETED = "android.intent.action.BOOT_COMPLETED"
        const val ACTION_SCREEN_ON = "android.intent.action.SCREEN_ON"
        const val ACTION_SCREEN_OFF = "android.intent.action.SCREEN_OFF"
        const val ACTION_USER_PRESENT = "android.intent.action.USER_PRESENT"
        const val ACTION_BATTERY_CHANGED = "android.intent.action.BATTERY_CHANGED"
        const val ACTION_POWER_CONNECTED = "android.intent.action.ACTION_POWER_CONNECTED"
        const val ACTION_POWER_DISCONNECTED = "android.intent.action.ACTION_POWER_DISCONNECTED"
        const val ACTION_APPLICATION_DETAILS_SETTINGS = "android.settings.APPLICATION_DETAILS_SETTINGS"
        const val EXTRA_TEXT = "android.intent.extra.TEXT"
        const val EXTRA_SUBJECT = "android.intent.extra.SUBJECT"
        const val EXTRA_STREAM = "android.intent.extra.STREAM"
        const val EXTRA_TITLE = "android.intent.extra.TITLE"
        const val EXTRA_EMAIL = "android.intent.extra.EMAIL"
        const val EXTRA_MIME_TYPES = "android.intent.extra.MIME_TYPES"
        const val EXTRA_ALLOW_MULTIPLE = "android.intent.extra.ALLOW_MULTIPLE"
        const val CATEGORY_DEFAULT = "android.intent.category.DEFAULT"
        const val CATEGORY_LAUNCHER = "android.intent.category.LAUNCHER"
        const val CATEGORY_OPENABLE = "android.intent.category.OPENABLE"
        const val CATEGORY_BROWSABLE = "android.intent.category.BROWSABLE"
        const val FLAG_ACTIVITY_NEW_TASK = 0x10000000
        const val FLAG_ACTIVITY_CLEAR_TOP = 0x04000000
        const val FLAG_ACTIVITY_SINGLE_TOP = 0x20000000
        const val FLAG_ACTIVITY_CLEAR_TASK = 0x00008000
        const val FLAG_ACTIVITY_NO_HISTORY = 0x40000000
        const val FLAG_ACTIVITY_REORDER_TO_FRONT = 0x00020000
        const val FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS = 0x00800000
        const val FLAG_GRANT_READ_URI_PERMISSION = 1
        const val FLAG_GRANT_WRITE_URI_PERMISSION = 2
        const val FLAG_GRANT_PERSISTABLE_URI_PERMISSION = 64
        const val FLAG_INCLUDE_STOPPED_PACKAGES = 32
        const val FLAG_RECEIVER_FOREGROUND = 0x10000000
        const val URI_INTENT_SCHEME = 1
        fun createChooser(target: Intent, title: CharSequence?): Intent = Intent(target).also { it.extrasBundle.map[EXTRA_TITLE] = title }
        fun parseUri(uri: String, flags: Int): Intent {
            if (uri.startsWith("intent:")) {
                val fallback = Regex("S\\.browser_fallback_url=([^;]+)").find(uri)?.groupValues?.get(1)?.let { urlDecode(it) }
                val schemeName = Regex(";scheme=([^;]+)").find(uri)?.groupValues?.get(1)
                val pathPart = uri.removePrefix("intent:").substringBefore("#Intent")
                val i = Intent(ACTION_VIEW, Uri.parse(if (schemeName != null) "$schemeName:$pathPart" else pathPart))
                if (fallback != null) i.putExtra("browser_fallback_url", fallback)
                i.`package` = Regex(";package=([^;]+)").find(uri)?.groupValues?.get(1)
                return i
            }
            return Intent(ACTION_VIEW, Uri.parse(uri))
        }
    }
}

/** Routes Android intents to iOS equivalents. */
object IntentRouter {
    var serviceStarter: ((KClass<*>) -> Unit)? = null
    var serviceStopper: ((KClass<*>) -> Unit)? = null
    var activityOpener: ((Intent) -> Boolean)? = null

    fun startActivity(intent: Intent) {
        try {
            if (activityOpener?.invoke(intent) == true) return
            val action = intent.action
            val data = intent.data?.toString()
            when {
                action == Intent.ACTION_SEND || action == Intent.ACTION_SEND_MULTIPLE -> {
                    val stream = intent.extrasBundle.map[Intent.EXTRA_STREAM] as? Uri
                    if (stream != null) {
                        val path = ContentResolver.resolvePath(stream)
                        if (path != null) { PlatformBridge.shareFile(path, intent.type); return }
                    }
                    val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
                    PlatformBridge.shareText(text, intent.getStringExtra(Intent.EXTRA_TITLE))
                }
                action == Intent.ACTION_SENDTO || action == Intent.ACTION_DIAL -> data?.let { PlatformBridge.openUrl(it) }
                data != null && (data.startsWith("http") || data.contains(":")) && !data.startsWith("package:") && !data.startsWith("content:") && !data.startsWith("file:") -> {
                    if (!PlatformBridge.openUrl(data)) {
                        intent.getStringExtra("browser_fallback_url")?.let { PlatformBridge.openUrl(it) }
                    }
                }
                action != null && (action.startsWith("android.settings") || action == Intent.ACTION_APPLICATION_DETAILS_SETTINGS) -> PlatformBridge.openAppSettings()
                data != null && data.startsWith("package:") -> PlatformBridge.openAppSettings()
                data != null && (data.startsWith("file:") || data.startsWith("content:")) -> ContentResolver.resolvePath(Uri.parse(data))?.let { PlatformBridge.shareFile(it, intent.type) }
                else -> {}
            }
        } catch (e: Exception) {
            println("startActivity failed: ${e.message}")
        }
    }

    fun startService(intent: Intent) { intent.component?.target?.let { serviceStarter?.invoke(it) } }
    fun stopService(intent: Intent) { intent.component?.target?.let { serviceStopper?.invoke(it) } }
    fun sendBroadcast(intent: Intent) {}
}

// ------------------------------------------------------------------------------------------------
// ContentResolver / ContentValues / MediaStore / Environment
// ------------------------------------------------------------------------------------------------
object ContentResolver {
    const val SCHEME_ANDROID_RESOURCE = "android.resource"
    const val SCHEME_CONTENT = "content"
    const val SCHEME_FILE = "file"

    fun resolvePath(uri: Uri): String? {
        val s = uri.toString()
        return when {
            s.startsWith("file://") -> urlDecode(s.removePrefix("file://"))
            s.startsWith("/") -> s
            s.startsWith("content://fpt/") -> File(PlatformBridge.filesDir, "content/" + s.removePrefix("content://fpt/")).path
            else -> null
        }
    }

    fun openInputStream(uri: Uri): InputStream {
        val p = resolvePath(uri) ?: throw FileNotFoundException("No content provider: $uri")
        val f = File(p)
        if (!f.exists()) throw FileNotFoundException(p)
        return ByteArrayInputStream(f.readBytes())
    }

    fun openOutputStream(uri: Uri): OutputStream? = openOutputStream(uri, "w")
    fun openOutputStream(uri: Uri, mode: String): OutputStream? {
        val p = resolvePath(uri) ?: return null
        val f = File(p)
        f.parentFile?.mkdirs()
        val out = FileOutputStream(f, mode.contains("a"))
        PendingExports.hookFor(f)?.let { hook -> out.onCloseHook = hook }
        return out
    }

    fun openFileDescriptor(uri: Uri, mode: String): Any? = null
    fun getType(uri: Uri): String? {
        val n = uri.toString().lowercase()
        return when {
            n.endsWith(".png") -> "image/png"
            n.endsWith(".jpg") || n.endsWith(".jpeg") -> "image/jpeg"
            n.endsWith(".gif") -> "image/gif"
            n.endsWith(".webp") -> "image/webp"
            n.endsWith(".json") -> "application/json"
            n.endsWith(".js") -> "application/javascript"
            n.endsWith(".txt") -> "text/plain"
            n.endsWith(".ttf") || n.endsWith(".otf") -> "font/ttf"
            else -> "application/octet-stream"
        }
    }
    fun takePersistableUriPermission(uri: Uri, flags: Int) {}
    fun releasePersistableUriPermission(uri: Uri, flags: Int) {}
    fun query(uri: Uri, projection: Array<String>?, selection: String?, args: Array<String>?, sort: String?): Cursor? =
        Cursor(resolvePath(uri)?.let { File(it) })
    fun insert(uri: Uri, values: ContentValues): Uri? {
        val name = values.getAsString("_display_name") ?: ("file_" + System.currentTimeMillis())
        val f = File(File(PlatformBridge.cacheDir, "exports"), name)
        f.parentFile?.mkdirs()
        val mime = values.getAsString("mime_type") ?: ""
        PendingExports.register(f, if (mime.startsWith("image/")) ExportKind.GALLERY else ExportKind.FILE)
        return Uri.fromFile(f)
    }
    fun delete(uri: Uri, where: String?, args: Array<String>?): Int = 0
    fun update(uri: Uri, values: ContentValues, where: String?, args: Array<String>?): Int = 0
}

enum class ExportKind { FILE, GALLERY }

/** Files that should be handed to the user (share/export or Photos) once written. */
object PendingExports {
    private val pending = LinkedHashMap<String, ExportKind>()
    fun register(f: File, kind: ExportKind) { synchronized(pending) { pending[f.path] = kind } }
    fun hookFor(f: File): ((File) -> Unit)? {
        val kind = synchronized(pending) { pending.remove(f.path) } ?: return null
        return { file ->
            PlatformBridge.runOnMain {
                if (kind == ExportKind.GALLERY) {
                    if (!PlatformBridge.saveImageToGallery(file.readBytes())) PlatformBridge.shareFile(file.path, null)
                    else Toast.makeText(AppContext, "Сохранено в Фото", Toast.LENGTH_SHORT).show()
                } else PlatformBridge.exportFile(file.path, file.name)
            }
        }
    }
}

class Cursor(private val file: File?) : AutoCloseable {
    private var moved = false
    fun moveToFirst(): Boolean { moved = file != null; return moved }
    fun moveToNext(): Boolean = false
    fun getColumnIndex(name: String): Int = when (name) { "_display_name" -> 0; "_size" -> 1; else -> -1 }
    fun getColumnIndexOrThrow(name: String): Int = getColumnIndex(name)
    fun getString(i: Int): String? = if (i == 0) file?.name else if (i == 1) file?.length()?.toString() else null
    fun getLong(i: Int): Long = if (i == 1) file?.length() ?: 0L else 0L
    fun getInt(i: Int): Int = getLong(i).toInt()
    val count: Int get() = if (file != null) 1 else 0
    override fun close() {}
}

object OpenableColumns {
    const val DISPLAY_NAME = "_display_name"
    const val SIZE = "_size"
}

class ContentValues {
    private val map = LinkedHashMap<String, Any?>()
    fun put(k: String, v: String?) { map[k] = v }
    fun put(k: String, v: Int?) { map[k] = v }
    fun put(k: String, v: Long?) { map[k] = v }
    fun put(k: String, v: Boolean?) { map[k] = v }
    fun getAsString(k: String): String? = map[k]?.toString()
    fun clear() = map.clear()
}

object MediaStore {
    object MediaColumns {
        const val DISPLAY_NAME = "_display_name"
        const val MIME_TYPE = "mime_type"
        const val RELATIVE_PATH = "relative_path"
        const val IS_PENDING = "is_pending"
        const val SIZE = "_size"
    }
    object Images {
        object Media {
            const val DISPLAY_NAME = "_display_name"
            const val MIME_TYPE = "mime_type"
            const val RELATIVE_PATH = "relative_path"
            const val IS_PENDING = "is_pending"
            val EXTERNAL_CONTENT_URI: Uri = Uri.parse("content://media/external/images/media")
            fun getBitmap(cr: ContentResolver, uri: Uri): Bitmap? = cr.openInputStream(uri)?.let { BitmapFactory.decodeStream(it) }
        }
    }
    object Downloads {
        val EXTERNAL_CONTENT_URI: Uri = Uri.parse("content://media/external/downloads")
    }
    object Files {
        fun getContentUri(v: String): Uri = Uri.parse("content://media/$v/file")
    }
    const val ACTION_IMAGE_CAPTURE = "android.media.action.IMAGE_CAPTURE"
}

object Environment {
    const val DIRECTORY_DOWNLOADS = "Download"
    const val DIRECTORY_PICTURES = "Pictures"
    const val DIRECTORY_DOCUMENTS = "Documents"
    const val DIRECTORY_DCIM = "DCIM"
    fun getExternalStoragePublicDirectory(type: String): File = File(PlatformBridge.documentsDir, type).also { it.mkdirs() }
    fun getExternalStorageDirectory(): File = File(PlatformBridge.documentsDir)
    fun getExternalStorageState(): String = "mounted"
    const val MEDIA_MOUNTED = "mounted"
}

object MediaScannerConnection {
    fun scanFile(c: Context, paths: Array<String>, mimes: Array<String>?, cb: Any?) {}
}

// ------------------------------------------------------------------------------------------------
// Toast
// ------------------------------------------------------------------------------------------------
class Toast private constructor(val text: String, val duration: Int) {
    fun show() { ToastHost.show(text, duration) }
    fun cancel() {}
    fun setGravity(g: Int, x: Int, y: Int) {}
    companion object {
        const val LENGTH_SHORT = 0
        const val LENGTH_LONG = 1
        fun makeText(context: Context?, text: CharSequence?, duration: Int): Toast = Toast(text?.toString() ?: "", duration)
        fun makeText(context: Context?, resId: Int, duration: Int): Toast = Toast("", duration)
    }
}

object ToastHost {
    data class Item(val id: Long, val text: String, val long: Boolean)
    val items = mutableStateListOf<Item>()
    private var counter = 0L
    fun show(text: String, duration: Int) {
        if (text.isBlank()) return
        PlatformBridge.runOnMain {
            val item = Item(++counter, text, duration == Toast.LENGTH_LONG)
            items.add(item)
            while (items.size > 3) items.removeAt(0)
            appScope.launch(Dispatchers.Main) {
                delay(if (item.long) 3500 else 2000)
                items.remove(item)
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// Clipboard
// ------------------------------------------------------------------------------------------------
class ClipData private constructor(val label: CharSequence?, val text: CharSequence?, val uri: Uri? = null) {
    class Item(val text: CharSequence?, val uri: Uri? = null) {
        fun coerceToText(c: Context?): CharSequence = text ?: ""
    }
    fun getItemAt(i: Int): Item = Item(if (uri != null) null else text, uri)
    val itemCount: Int get() = 1
    val description: Any? get() = null
    companion object {
        fun newPlainText(label: CharSequence?, text: CharSequence?): ClipData = ClipData(label, text)
        fun newUri(cr: Any?, label: CharSequence?, uri: Uri): ClipData = ClipData(label, null, uri)
        fun newRawUri(label: CharSequence?, uri: Uri): ClipData = ClipData(label, uri.toString(), uri)
    }
}

object ClipboardManager {
    fun interface OnPrimaryClipChangedListener { fun onPrimaryClipChanged() }
    private val clipListeners = ArrayList<OnPrimaryClipChangedListener>()
    fun addPrimaryClipChangedListener(l: OnPrimaryClipChangedListener) { clipListeners.add(l) }
    fun removePrimaryClipChangedListener(l: OnPrimaryClipChangedListener) { clipListeners.remove(l) }
    fun notifyChanged() = clipListeners.toList().forEach { it.onPrimaryClipChanged() }

    class ClipDescription(private val mimes: List<String>, val timestamp: Long) {
        val mimeTypeCount: Int get() = mimes.size
        fun getMimeType(i: Int): String = mimes[i]
        fun hasMimeType(m: String): Boolean = mimes.any { it == m || (m.endsWith("/*") && it.startsWith(m.dropLast(1))) }
    }
    val primaryClipDescription: ClipDescription? get() {
        val mimes = ArrayList<String>()
        if (PlatformBridge.clipboardHasImage()) mimes.add("image/png")
        if (mimes.isEmpty()) mimes.add("text/plain")
        return ClipDescription(mimes, PlatformBridge.clipboardChangeCount())
    }

    fun setPrimaryClip(clip: ClipData) {
        val u = clip.uri
        val path = u?.let { ContentResolver.resolvePath(it) }
        if (path != null && File(path).exists()) PlatformBridge.setClipboardImage(File(path).readBytes())
        else PlatformBridge.setClipboard(clip.text?.toString() ?: "")
    }
    val primaryClip: ClipData? get() {
        if (PlatformBridge.clipboardHasImage()) {
            val p = PlatformBridge.clipboardImageToFile()
            if (p != null) return ClipData.newUri(null, "image", Uri.fromFile(File(p)))
        }
        return PlatformBridge.getClipboard()?.let { ClipData.newPlainText("", it) }
    }
    fun hasPrimaryClip(): Boolean = PlatformBridge.getClipboard() != null
    @kotlin.jvm.JvmName("getPrimaryClipFn") fun getPrimaryClip(): ClipData? = primaryClip
    fun clearPrimaryClip() { PlatformBridge.setClipboard("") }
    var text: CharSequence?
        get() = PlatformBridge.getClipboard()
        set(v) { PlatformBridge.setClipboard(v?.toString() ?: "") }
}

// ------------------------------------------------------------------------------------------------
// Misc services
// ------------------------------------------------------------------------------------------------
object PowerManager {
    const val PARTIAL_WAKE_LOCK = 1
    const val SCREEN_BRIGHT_WAKE_LOCK = 10
    const val FULL_WAKE_LOCK = 26
    const val ACQUIRE_CAUSES_WAKEUP = 0x10000000
    const val ON_AFTER_RELEASE = 0x20000000
    fun isIgnoringBatteryOptimizations(pkg: String): Boolean = true
    fun newWakeLock(flags: Int, tag: String): WakeLock = WakeLock()
    val isInteractive: Boolean get() = true
    val isPowerSaveMode: Boolean get() = false
    fun isScreenOn(): Boolean = true
    class WakeLock {
        var isHeld = false
            private set
        fun acquire() { isHeld = true }
        fun acquire(timeout: Long) { isHeld = true }
        fun release() { isHeld = false }
        fun setReferenceCounted(b: Boolean) {}
    }
}

object AlarmManager {
    const val RTC_WAKEUP = 0
    const val RTC = 1
    const val ELAPSED_REALTIME_WAKEUP = 2
    const val ELAPSED_REALTIME = 3
    const val INTERVAL_FIFTEEN_MINUTES = 900_000L
    fun set(type: Int, at: Long, pi: PendingIntent?) {}
    fun setExact(type: Int, at: Long, pi: PendingIntent?) {}
    fun setExactAndAllowWhileIdle(type: Int, at: Long, pi: PendingIntent?) {}
    fun setAndAllowWhileIdle(type: Int, at: Long, pi: PendingIntent?) {}
    fun setRepeating(type: Int, at: Long, interval: Long, pi: PendingIntent?) {}
    fun setInexactRepeating(type: Int, at: Long, interval: Long, pi: PendingIntent?) {}
    fun setAlarmClock(info: Any?, pi: PendingIntent?) {}
    fun cancel(pi: PendingIntent?) {}
    fun canScheduleExactAlarms(): Boolean = true
}

object SystemClock {
    fun elapsedRealtime(): Long = System.nanoTime() / 1_000_000
    fun uptimeMillis(): Long = System.nanoTime() / 1_000_000
    fun sleep(ms: Long) = Thread.sleep(ms)
}

object Vibrator {
    fun vibrate(ms: Long) = PlatformBridge.vibrate(ms)
    fun vibrate(effect: VibrationEffect?) = PlatformBridge.vibrate(50)
    fun vibrate(pattern: LongArray, repeat: Int) = PlatformBridge.vibrate(pattern.sum().coerceAtMost(400))
    fun hasVibrator(): Boolean = true
    fun cancel() {}
}

object VibratorManager {
    val defaultVibrator: Vibrator get() = Vibrator
}

class VibrationEffect private constructor() {
    companion object {
        const val DEFAULT_AMPLITUDE = -1
        fun createOneShot(ms: Long, amp: Int) = VibrationEffect()
        fun createWaveform(timings: LongArray, repeat: Int) = VibrationEffect()
        fun createWaveform(timings: LongArray, amps: IntArray, repeat: Int) = VibrationEffect()
        fun createPredefined(id: Int) = VibrationEffect()
        const val EFFECT_CLICK = 0
        const val EFFECT_TICK = 2
    }
}

object ConnectivityManager {
    val activeNetwork: Any? get() = if (PlatformBridge.isNetworkAvailable()) Unit else null
    fun getNetworkCapabilities(n: Any?): NetworkCapabilities? = if (n != null) NetworkCapabilities() else null
    val activeNetworkInfo: NetworkInfo? get() = if (PlatformBridge.isNetworkAvailable()) NetworkInfo() else null
}

class NetworkCapabilities {
    fun hasCapability(c: Int): Boolean = true
    fun hasTransport(t: Int): Boolean = true
    companion object {
        const val NET_CAPABILITY_INTERNET = 12
        const val NET_CAPABILITY_VALIDATED = 16
        const val TRANSPORT_WIFI = 1
        const val TRANSPORT_CELLULAR = 0
    }
}

class NetworkInfo {
    val isConnected = true
    val isConnectedOrConnecting = true
}

object JobScheduler {
    fun schedule(job: Any?): Int = 1
    fun cancel(id: Int) {}
}

object AudioManager {
    const val STREAM_RING = 2
    const val STREAM_MUSIC = 3
    const val STREAM_NOTIFICATION = 5
    const val RINGER_MODE_NORMAL = 2
    val ringerMode: Int get() = RINGER_MODE_NORMAL
}

object InputMethodManager {
    fun hideSoftInputFromWindow(token: Any?, flags: Int): Boolean = true
    fun showSoftInput(v: Any?, flags: Int): Boolean = true
}

object ActivityManager {
    fun isBackgroundRestricted(): Boolean = false
}

object KeyguardManager {
    val isKeyguardLocked: Boolean get() = false
    fun requestDismissKeyguard(a: Activity, cb: Any?) {}
}

object SensorManager {
    const val SENSOR_DELAY_FASTEST = 0
    const val SENSOR_DELAY_GAME = 1
    const val SENSOR_DELAY_UI = 2
    const val SENSOR_DELAY_NORMAL = 3
    const val SENSOR_STATUS_ACCURACY_HIGH = 3
    const val GRAVITY_EARTH = 9.80665f
    fun getDefaultSensor(type: Int): Sensor? = SensorManagerImpl.getDefaultSensor(type)
    fun registerListener(l: SensorEventListener, s: Sensor?, delay: Int): Boolean = SensorManagerImpl.registerListener(l, s, delay)
    fun unregisterListener(l: SensorEventListener) = SensorManagerImpl.unregisterListener(l)
    fun unregisterListener(l: SensorEventListener, s: Sensor?) = SensorManagerImpl.unregisterListener(l)
}

object AppWidgetManager {
    const val ACTION_APPWIDGET_UPDATE = "android.appwidget.action.APPWIDGET_UPDATE"
    const val EXTRA_APPWIDGET_IDS = "appWidgetIds"
    fun getInstance(c: Context): AppWidgetManager = this
    fun getAppWidgetIds(c: ComponentName): IntArray = IntArray(0)
    val isRequestPinAppWidgetSupported: Boolean get() = false
    fun requestPinAppWidget(c: ComponentName, extras: Bundle?, cb: PendingIntent?): Boolean = false
    fun updateAppWidget(id: Int, views: RemoteViews) {}
    fun updateAppWidget(ids: IntArray, views: RemoteViews) {}
}

class RemoteViews(pkg: String, layout: Int) {
    fun setTextViewText(id: Int, text: CharSequence?) {}
    fun setTextColor(id: Int, color: Int) {}
    fun setImageViewBitmap(id: Int, b: Bitmap?) {}
    fun setImageViewResource(id: Int, res: Int) {}
    fun setInt(id: Int, method: String, v: Int) {}
    fun setFloat(id: Int, method: String, v: Float) {}
    fun setOnClickPendingIntent(id: Int, pi: PendingIntent?) {}
    fun setViewVisibility(id: Int, v: Int) {}
    fun setTextViewTextSize(id: Int, unit: Int, size: Float) {}
}

open class AppWidgetProvider : BroadcastReceiver() {
    open fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {}
    open fun onEnabled(context: Context) {}
    open fun onDisabled(context: Context) {}
    override fun onReceive(context: Context, intent: Intent) {}
}

abstract class BroadcastReceiver {
    abstract fun onReceive(context: Context, intent: Intent)
    fun goAsync(): PendingResult = PendingResult()
    class PendingResult { fun finish() {} }
}

object Looper {
    fun getMainLooper(): Looper = this
    fun myLooper(): Looper = this
    fun prepare() {}
}

open class Handler(looper: Looper? = null) {
    fun post(r: () -> Unit): Boolean { PlatformBridge.runOnMain(r); return true }
    fun postDelayed(r: () -> Unit, delayMs: Long): Boolean {
        appScope.launch(Dispatchers.Main) { delay(delayMs); r() }
        return true
    }
    fun removeCallbacksAndMessages(token: Any?) {}
    fun removeCallbacks(r: Any?) {}
}

object Settings {
    const val ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS = "android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"
    const val ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS = "android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS"
    const val ACTION_APPLICATION_DETAILS_SETTINGS = "android.settings.APPLICATION_DETAILS_SETTINGS"
    const val ACTION_SETTINGS = "android.settings.SETTINGS"
    const val ACTION_APP_NOTIFICATION_SETTINGS = "android.settings.APP_NOTIFICATION_SETTINGS"
    const val ACTION_REQUEST_SCHEDULE_EXACT_ALARM = "android.settings.REQUEST_SCHEDULE_EXACT_ALARM"
    const val ACTION_MANAGE_OVERLAY_PERMISSION = "android.settings.action.MANAGE_OVERLAY_PERMISSION"
    const val EXTRA_APP_PACKAGE = "android.provider.extra.APP_PACKAGE"
    object Secure {
        const val ANDROID_ID = "android_id"
        fun getString(cr: ContentResolver?, key: String): String? = if (key == ANDROID_ID) PlatformBridge.deviceId() else null
    }
    object System {
        fun canWrite(c: Context): Boolean = false
    }
    fun canDrawOverlays(c: Context): Boolean = false
}

object RingtoneManager {
    const val TYPE_RINGTONE = 1
    const val TYPE_NOTIFICATION = 2
    const val TYPE_ALARM = 4
    fun getDefaultUri(type: Int): Uri = Uri.parse("ios-sound://default")
    fun getRingtone(c: Context, uri: Uri): Ringtone = Ringtone()
}

class Ringtone {
    fun play() = PlatformBridge.vibrate(300)
    fun stop() {}
    val isPlaying: Boolean get() = false
    var isLooping: Boolean = false
}

class AudioAttributes private constructor() {
    class Builder {
        fun setContentType(t: Int) = this
        fun setUsage(u: Int) = this
        fun build() = AudioAttributes()
    }
    companion object {
        const val CONTENT_TYPE_SONIFICATION = 4
        const val USAGE_NOTIFICATION = 5
        const val USAGE_NOTIFICATION_RINGTONE = 6
        const val USAGE_ALARM = 4
    }
}

/** androidx.core.graphics.ColorUtils */
object ColorUtils {
    fun blendARGB(c1: Int, c2: Int, ratio: Float): Int {
        val inv = 1f - ratio
        val a = AndroidColor.alpha(c1) * inv + AndroidColor.alpha(c2) * ratio
        val r = AndroidColor.red(c1) * inv + AndroidColor.red(c2) * ratio
        val g = AndroidColor.green(c1) * inv + AndroidColor.green(c2) * ratio
        val b = AndroidColor.blue(c1) * inv + AndroidColor.blue(c2) * ratio
        return AndroidColor.argb(a.toInt(), r.toInt(), g.toInt(), b.toInt())
    }
    fun calculateLuminance(color: Int): Double {
        fun ch(c: Int): Double { val v = c / 255.0; return if (v < 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4) }
        return 0.2126 * ch(AndroidColor.red(color)) + 0.7152 * ch(AndroidColor.green(color)) + 0.0722 * ch(AndroidColor.blue(color))
    }
    fun setAlphaComponent(color: Int, alpha: Int): Int = (color and 0x00ffffff) or (alpha shl 24)
    fun colorToHSL(color: Int, out: FloatArray) {
        val r = AndroidColor.red(color) / 255f; val g = AndroidColor.green(color) / 255f; val b = AndroidColor.blue(color) / 255f
        val max = maxOf(r, g, b); val min = minOf(r, g, b); val d = max - min
        val l = (max + min) / 2f
        var h: Float; val s: Float
        if (max == min) { h = 0f; s = 0f } else {
            s = d / (1f - kotlin.math.abs(2f * l - 1f))
            h = when (max) { r -> ((g - b) / d) % 6f; g -> (b - r) / d + 2f; else -> (r - g) / d + 4f }
        }
        h = (h * 60f) % 360f; if (h < 0) h += 360f
        out[0] = h; out[1] = s.coerceIn(0f, 1f); out[2] = l.coerceIn(0f, 1f)
    }
    fun HSLToColor(hsl: FloatArray): Int {
        val h = hsl[0]; val s = hsl[1]; val l = hsl[2]
        val c = (1f - kotlin.math.abs(2 * l - 1f)) * s
        val m = l - 0.5f * c
        val x = c * (1f - kotlin.math.abs((h / 60f % 2f) - 1f))
        val hi = (h.toInt() / 60) % 6
        val (r, g, b) = when (hi) {
            0 -> Triple(c, x, 0f); 1 -> Triple(x, c, 0f); 2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c); 4 -> Triple(x, 0f, c); else -> Triple(c, 0f, x)
        }
        return AndroidColor.rgb(((r + m) * 255).toInt().coerceIn(0, 255), ((g + m) * 255).toInt().coerceIn(0, 255), ((b + m) * 255).toInt().coerceIn(0, 255))
    }
    fun calculateContrast(fg: Int, bg: Int): Double {
        val l1 = calculateLuminance(fg) + 0.05; val l2 = calculateLuminance(bg) + 0.05
        return maxOf(l1, l2) / minOf(l1, l2)
    }
}

/** android.graphics.Color static helpers (ARGB Int based). */
object AndroidColor {
    const val BLACK = -0x1000000
    const val WHITE = -0x1
    const val TRANSPARENT = 0
    const val RED = -0x10000
    const val GREEN = -0xff0100
    const val BLUE = -0xffff01
    const val GRAY = -0x777778
    const val DKGRAY = -0xbbbbbc
    const val LTGRAY = -0x333334
    const val YELLOW = -0x100
    const val CYAN = -0xff0001
    const val MAGENTA = -0xff01

    fun alpha(c: Int) = (c ushr 24) and 0xff
    fun red(c: Int) = (c shr 16) and 0xff
    fun green(c: Int) = (c shr 8) and 0xff
    fun blue(c: Int) = c and 0xff
    fun rgb(r: Int, g: Int, b: Int): Int = (0xff shl 24) or ((r and 0xff) shl 16) or ((g and 0xff) shl 8) or (b and 0xff)
    fun argb(a: Int, r: Int, g: Int, b: Int): Int = ((a and 0xff) shl 24) or ((r and 0xff) shl 16) or ((g and 0xff) shl 8) or (b and 0xff)
    fun rgb(r: Float, g: Float, b: Float): Int = rgb((r * 255).toInt(), (g * 255).toInt(), (b * 255).toInt())
    fun argb(a: Float, r: Float, g: Float, b: Float): Int = argb((a * 255).toInt(), (r * 255).toInt(), (g * 255).toInt(), (b * 255).toInt())

    private val NAMED = mapOf(
        "black" to BLACK, "white" to WHITE, "red" to RED, "green" to GREEN, "blue" to BLUE, "gray" to GRAY, "grey" to GRAY,
        "yellow" to YELLOW, "cyan" to CYAN, "magenta" to MAGENTA, "transparent" to TRANSPARENT, "darkgray" to DKGRAY,
        "lightgray" to LTGRAY, "aqua" to CYAN, "fuchsia" to MAGENTA, "lime" to GREEN, "maroon" to -0x800000,
        "navy" to -0xffff80, "olive" to -0x7f8000, "purple" to -0x7fff80, "silver" to -0x3f3f40, "teal" to -0xff7f80,
    )

    fun parseColor(s: String): Int {
        val t = s.trim()
        if (t.startsWith("#")) {
            val hex = t.substring(1)
            val v = hex.toLongOrNull(16) ?: throw IllegalArgumentException("Unknown color")
            return when (hex.length) {
                6 -> (v or 0xff000000L).toInt()
                8 -> v.toInt()
                3 -> {
                    val r = (v shr 8) and 0xf; val g = (v shr 4) and 0xf; val b = v and 0xf
                    rgb((r * 17).toInt(), (g * 17).toInt(), (b * 17).toInt())
                }
                else -> throw IllegalArgumentException("Unknown color")
            }
        }
        return NAMED[t.lowercase()] ?: throw IllegalArgumentException("Unknown color")
    }

    fun colorToHSV(color: Int, hsv: FloatArray) {
        val r = red(color) / 255f; val g = green(color) / 255f; val b = blue(color) / 255f
        val max = maxOf(r, g, b); val min = minOf(r, g, b); val d = max - min
        var h = when {
            d == 0f -> 0f
            max == r -> 60f * (((g - b) / d) % 6f)
            max == g -> 60f * (((b - r) / d) + 2f)
            else -> 60f * (((r - g) / d) + 4f)
        }
        if (h < 0) h += 360f
        hsv[0] = h
        hsv[1] = if (max == 0f) 0f else d / max
        hsv[2] = max
    }

    fun RGBToHSV(r: Int, g: Int, b: Int, hsv: FloatArray) = colorToHSV(rgb(r, g, b), hsv)

    fun HSVToColor(hsv: FloatArray): Int = HSVToColor(255, hsv)

    fun HSVToColor(alpha: Int, hsv: FloatArray): Int {
        val h = ((hsv[0] % 360f) + 360f) % 360f
        val s = hsv[1].coerceIn(0f, 1f); val v = hsv[2].coerceIn(0f, 1f)
        val c = v * s
        val x = c * (1 - kotlin.math.abs((h / 60f) % 2f - 1))
        val m = v - c
        val (r, g, b) = when ((h / 60f).toInt()) {
            0 -> Triple(c, x, 0f); 1 -> Triple(x, c, 0f); 2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c); 4 -> Triple(x, 0f, c); else -> Triple(c, 0f, x)
        }
        return argb(alpha, ((r + m) * 255).toInt().coerceIn(0, 255), ((g + m) * 255).toInt().coerceIn(0, 255), ((b + m) * 255).toInt().coerceIn(0, 255))
    }

    fun luminance(c: Int): Float = ColorUtils.calculateLuminance(c).toFloat()
    fun valueOf(c: Int) = c
}

/** Assets bundled into the binary (Android assets/ folder). */
object BundledAssets {
    fun get(name: String): ByteArray? = when (name) {
        "funpay_chats.json" -> Base64.decode(FUNPAY_CHATS_JSON_B64, Base64.DEFAULT)
        else -> null
    }
    fun names(): List<String> = listOf("funpay_chats.json")
}
