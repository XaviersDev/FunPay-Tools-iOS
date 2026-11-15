package ru.allisighs.funpaytools

import kotlin.reflect.KClass

/** Spec of a local notification handed to the platform. */
data class LocalNotificationSpec(
    val id: Int,
    val title: String,
    val text: String,
    val subtitle: String? = null,
    val sound: Boolean = true,
    val timeSensitive: Boolean = false,
    val threadId: String? = null,
    /** Tap action: encoded intent extras + target name. */
    val openPayload: Map<String, String>? = null,
    /** Text-reply action: label + encoded broadcast intent. */
    val replyLabel: String? = null,
    val replyPayload: Map<String, String>? = null,
    /** Simple button actions. */
    val buttons: List<Pair<String, Map<String, String>>> = emptyList(),
)

expect object PlatformNotifications {
    fun post(spec: LocalNotificationSpec)
    fun cancel(id: Int)
    fun cancelAll()
    fun requestPermission(onResult: (Boolean) -> Unit)
    fun areNotificationsEnabled(): Boolean
}

/** Registry used to deliver notification actions back to BroadcastReceivers / the UI. */
object NotificationActionRouter {
    private val receivers = LinkedHashMap<String, () -> BroadcastReceiver>()
    var onOpenIntent: ((Intent) -> Unit)? = null

    fun registerReceiver(cls: KClass<*>, factory: () -> BroadcastReceiver) {
        receivers[cls.simpleName ?: return] = factory
    }

    fun encodeIntent(intent: Intent, kind: String): Map<String, String> {
        val m = LinkedHashMap<String, String>()
        m["__kind"] = kind
        m["__target"] = intent.component?.target?.simpleName ?: intent.component?.className ?: ""
        intent.action?.let { m["__action"] = it }
        for ((k, v) in intent.extrasBundle.map) {
            when (v) {
                null -> {}
                is Int -> m["i:$k"] = v.toString()
                is Long -> m["l:$k"] = v.toString()
                is Boolean -> m["b:$k"] = v.toString()
                is Float -> m["f:$k"] = v.toString()
                is Double -> m["d:$k"] = v.toString()
                else -> m["s:$k"] = v.toString()
            }
        }
        return m
    }

    fun decodeIntent(m: Map<String, String>): Intent {
        val i = Intent()
        i.action = m["__action"]
        for ((k, v) in m) {
            if (k.startsWith("__") || k.length < 3 || k[1] != ':') continue
            val key = k.substring(2)
            when (k[0]) {
                'i' -> i.putExtra(key, v.toIntOrNull() ?: 0)
                'l' -> i.putExtra(key, v.toLongOrNull() ?: 0L)
                'b' -> i.putExtra(key, v == "true")
                'f' -> i.putExtra(key, v.toFloatOrNull() ?: 0f)
                'd' -> i.putExtra(key, v.toDoubleOrNull() ?: 0.0)
                else -> i.putExtra(key, v)
            }
        }
        return i
    }

    /** Called by the platform when the user taps a notification or an action. */
    fun handle(payload: Map<String, String>, replyText: String?) {
        val intent = decodeIntent(payload)
        when (payload["__kind"]) {
            "broadcast" -> {
                if (replyText != null) {
                    val key = payload["__remoteInputKey"] ?: "key_text_reply"
                    intent.extrasBundle.map[RemoteInput.RESULTS_KEY] = Bundle().also { it.putCharSequence(key, replyText) }
                }
                val target = payload["__target"] ?: ""
                val f = receivers[target]
                if (f != null) {
                    try { f().onReceive(AppContext, intent) } catch (e: Exception) { println("receiver failed: ${e.message}") }
                }
            }
            "activity" -> onOpenIntent?.invoke(intent)
            else -> onOpenIntent?.invoke(intent)
        }
    }
}

class PendingIntent private constructor(val intent: Intent, val kind: String, val requestCode: Int) {
    fun send() {
        if (kind == "broadcast") NotificationActionRouter.handle(NotificationActionRouter.encodeIntent(intent, kind), null)
        else IntentRouter.startActivity(intent)
    }
    fun cancel() {}
    companion object {
        const val FLAG_ONE_SHOT = 1 shl 30
        const val FLAG_NO_CREATE = 1 shl 29
        const val FLAG_CANCEL_CURRENT = 1 shl 28
        const val FLAG_UPDATE_CURRENT = 1 shl 27
        const val FLAG_IMMUTABLE = 1 shl 26
        const val FLAG_MUTABLE = 1 shl 25
        fun getActivity(c: Context?, code: Int, intent: Intent, flags: Int): PendingIntent = PendingIntent(intent, "activity", code)
        fun getActivity(c: Context?, code: Int, intent: Intent, flags: Int, options: Bundle?): PendingIntent = PendingIntent(intent, "activity", code)
        fun getBroadcast(c: Context?, code: Int, intent: Intent, flags: Int): PendingIntent = PendingIntent(intent, "broadcast", code)
        fun getService(c: Context?, code: Int, intent: Intent, flags: Int): PendingIntent = PendingIntent(intent, "service", code)
        fun getForegroundService(c: Context?, code: Int, intent: Intent, flags: Int): PendingIntent = PendingIntent(intent, "service", code)
    }
}

class RemoteInput private constructor(val resultKey: String, val label: CharSequence?) {
    class Builder(private val key: String) {
        private var label: CharSequence? = null
        fun setLabel(l: CharSequence?) = apply { label = l }
        fun setAllowFreeFormInput(b: Boolean) = this
        fun setChoices(c: Array<CharSequence>?) = this
        fun build() = RemoteInput(key, label)
    }
    companion object {
        const val RESULTS_KEY = "android.remoteinput.results"
        fun getResultsFromIntent(intent: Intent): Bundle? = intent.extrasBundle.map[RESULTS_KEY] as? Bundle
    }
}

class NotificationChannel(val id: String, val name: CharSequence, val importance: Int) {
    var description: String? = null
    var lightColor: Int = 0
    var lockscreenVisibility: Int = 0
    var vibrationPattern: LongArray? = null
    var sound: Uri? = null
    fun setSound(uri: Uri?, attrs: AudioAttributes?) { sound = uri }
    fun enableLights(b: Boolean) {}
    fun enableVibration(b: Boolean) {}
    fun setShowBadge(b: Boolean) {}
    fun setBypassDnd(b: Boolean) {}
    fun setAllowBubbles(b: Boolean) {}
}

class Notification internal constructor(internal val b: Builder) {
    var flags: Int = 0
    var defaults: Int = 0
    var priority: Int = 0
    var category: String? = null
    var visibility: Int = 0
    var sound: Uri? = null
    var vibrate: LongArray? = null

    companion object {
        const val FLAG_ONGOING_EVENT = 2
        const val FLAG_NO_CLEAR = 32
        const val FLAG_AUTO_CANCEL = 16
        const val FLAG_INSISTENT = 4
        const val DEFAULT_ALL = -1
        const val DEFAULT_SOUND = 1
        const val DEFAULT_VIBRATE = 2
        const val PRIORITY_MAX = 2
        const val PRIORITY_HIGH = 1
        const val PRIORITY_DEFAULT = 0
        const val PRIORITY_LOW = -1
        const val PRIORITY_MIN = -2
        const val VISIBILITY_PUBLIC = 1
        const val VISIBILITY_PRIVATE = 0
        const val VISIBILITY_SECRET = -1
        const val CATEGORY_CALL = "call"
        const val CATEGORY_MESSAGE = "msg"
        const val CATEGORY_SERVICE = "service"
        const val CATEGORY_ALARM = "alarm"
        const val CATEGORY_REMINDER = "reminder"
        const val EXTRA_TEXT = "android.text"
        const val EXTRA_TITLE = "android.title"
    }

    abstract class Style
    class BigTextStyle : Style() {
        internal var big: CharSequence? = null
        internal var title: CharSequence? = null
        fun bigText(t: CharSequence?) = apply { big = t }
        fun setBigContentTitle(t: CharSequence?) = apply { title = t }
        fun setSummaryText(t: CharSequence?) = this
    }
    class InboxStyle : Style() {
        internal val lines = ArrayList<CharSequence>()
        fun addLine(l: CharSequence) = apply { lines.add(l) }
        fun setBigContentTitle(t: CharSequence?) = this
        fun setSummaryText(t: CharSequence?) = this
    }
    class BigPictureStyle : Style() {
        fun bigPicture(b: Bitmap?) = this
        fun setBigContentTitle(t: CharSequence?) = this
        fun setSummaryText(t: CharSequence?) = this
    }
    class MessagingStyle(user: Any?) : Style() {
        fun addMessage(text: CharSequence?, time: Long, sender: Any?) = this
        fun setConversationTitle(t: CharSequence?) = this
    }
    class DecoratedCustomViewStyle : Style()

    class Action internal constructor(val icon: Int, val title: CharSequence?, val intent: PendingIntent?, val remoteInputs: List<RemoteInput>) {
        class Builder(private val icon: Int, private val title: CharSequence?, private val intent: PendingIntent?) {
            constructor(icon: Any?, title: CharSequence?, intent: PendingIntent?) : this(0, title, intent)
            private val inputs = ArrayList<RemoteInput>()
            fun addRemoteInput(r: RemoteInput) = apply { inputs.add(r) }
            fun setAllowGeneratedReplies(b: Boolean) = this
            fun setSemanticAction(a: Int) = this
            fun build() = Action(icon, title, intent, inputs.toList())
        }
    }

    class Builder(context: Context?, internal val channelId: String = "") {
        internal var title: CharSequence? = null
        internal var text: CharSequence? = null
        internal var subText: CharSequence? = null
        internal var style: Style? = null
        internal var contentIntent: PendingIntent? = null
        internal val actions = ArrayList<Action>()
        internal var ongoing = false
        internal var autoCancel = false
        internal var silent = false
        internal var category: String? = null
        internal var fullScreen: PendingIntent? = null
        internal var priority = 0
        internal var group: String? = null

        fun setSmallIcon(i: Int) = this
        fun setSmallIcon(i: Any?) = this
        fun setLargeIcon(b: Bitmap?) = this
        fun setContentTitle(t: CharSequence?) = apply { title = t }
        fun setContentText(t: CharSequence?) = apply { text = t }
        fun setSubText(t: CharSequence?) = apply { subText = t }
        fun setStyle(s: Style?) = apply { style = s }
        fun setContentIntent(p: PendingIntent?) = apply { contentIntent = p }
        fun setDeleteIntent(p: PendingIntent?) = this
        fun setFullScreenIntent(p: PendingIntent?, high: Boolean) = apply { fullScreen = p }
        fun addAction(a: Action) = apply { actions.add(a) }
        fun addAction(icon: Int, title: CharSequence?, p: PendingIntent?) = apply { actions.add(Action(icon, title, p, emptyList())) }
        fun setOngoing(b: Boolean) = apply { ongoing = b }
        fun setAutoCancel(b: Boolean) = apply { autoCancel = b }
        fun setOnlyAlertOnce(b: Boolean) = this
        fun setSilent(b: Boolean) = apply { silent = b }
        fun setPriority(p: Int) = apply { priority = p }
        fun setCategory(c: String?) = apply { category = c }
        fun setVisibility(v: Int) = this
        fun setColor(c: Int) = this
        fun setColorized(b: Boolean) = this
        fun setLights(c: Int, on: Int, off: Int) = this
        fun setVibrate(p: LongArray?) = this
        fun setSound(u: Uri?) = this
        fun setSound(u: Uri?, a: Any?) = this
        fun setDefaults(d: Int) = this
        fun setWhen(w: Long) = this
        fun setShowWhen(b: Boolean) = this
        fun setUsesChronometer(b: Boolean) = this
        fun setTimeoutAfter(ms: Long) = this
        fun setGroup(g: String?) = apply { group = g }
        fun setGroupSummary(b: Boolean) = this
        fun setNumber(n: Int) = this
        fun setTicker(t: CharSequence?) = this
        fun setProgress(max: Int, p: Int, ind: Boolean) = this
        fun setCustomContentView(v: RemoteViews?) = this
        fun setForegroundServiceBehavior(b: Int) = this
        fun setChannelId(id: String) = this
        fun setExtras(b: Bundle?) = this
        fun build(): Notification = Notification(this)
        fun getNotification(): Notification = build()
    }
}

typealias NotificationCompat = Notification

object NotificationManager {
    const val IMPORTANCE_NONE = 0
    const val IMPORTANCE_MIN = 1
    const val IMPORTANCE_LOW = 2
    const val IMPORTANCE_DEFAULT = 3
    const val IMPORTANCE_HIGH = 4
    const val IMPORTANCE_MAX = 5

    private val channels = LinkedHashMap<String, NotificationChannel>()

    fun createNotificationChannel(c: NotificationChannel) { channels[c.id] = c }
    fun createNotificationChannels(list: List<NotificationChannel>) = list.forEach { createNotificationChannel(it) }
    fun deleteNotificationChannel(id: String) { channels.remove(id) }
    fun getNotificationChannel(id: String): NotificationChannel? = channels[id]
    fun areNotificationsEnabled(): Boolean = PlatformNotifications.areNotificationsEnabled()
    fun cancel(id: Int) = PlatformNotifications.cancel(id)
    fun cancel(tag: String?, id: Int) = PlatformNotifications.cancel(id)
    fun cancelAll() = PlatformNotifications.cancelAll()
    val activeNotifications: Array<Any> get() = emptyArray()
    fun canUseFullScreenIntent(): Boolean = false

    fun notify(id: Int, n: Notification) {
        val b = n.b
        // Persistent "service is running" notifications have no iOS equivalent.
        if ((b.ongoing || (n.flags and Notification.FLAG_ONGOING_EVENT) != 0) && b.fullScreen == null) return
        val channel = channels[b.channelId]
        val important = (channel?.importance ?: IMPORTANCE_DEFAULT) >= IMPORTANCE_DEFAULT
        val bigText = (b.style as? Notification.BigTextStyle)?.big
        val inbox = (b.style as? Notification.InboxStyle)?.lines?.joinToString("\n")
        val body = (bigText ?: inbox ?: b.text ?: "").toString()
        var replyLabel: String? = null
        var replyPayload: Map<String, String>? = null
        val buttons = ArrayList<Pair<String, Map<String, String>>>()
        for (a in b.actions) {
            val pi = a.intent ?: continue
            if (a.remoteInputs.isNotEmpty() && replyPayload == null) {
                replyLabel = a.title?.toString() ?: "Ответить"
                replyPayload = NotificationActionRouter.encodeIntent(pi.intent, pi.kind) + ("__remoteInputKey" to a.remoteInputs.first().resultKey)
            } else {
                buttons.add((a.title?.toString() ?: "") to NotificationActionRouter.encodeIntent(pi.intent, pi.kind))
            }
        }
        val open = (b.contentIntent ?: b.fullScreen)?.let { NotificationActionRouter.encodeIntent(it.intent, it.kind) }
        PlatformNotifications.post(
            LocalNotificationSpec(
                id = id,
                title = (b.title ?: "FunPay Tools").toString(),
                text = body,
                subtitle = b.subText?.toString(),
                sound = !b.silent && important,
                timeSensitive = b.category == Notification.CATEGORY_CALL || b.priority >= Notification.PRIORITY_HIGH || (channel?.importance ?: 0) >= IMPORTANCE_HIGH,
                threadId = b.group ?: b.channelId.ifEmpty { null },
                openPayload = open,
                replyLabel = replyLabel,
                replyPayload = replyPayload,
                buttons = buttons,
            )
        )
    }

    fun notify(tag: String?, id: Int, n: Notification) = notify(id, n)
}

object NotificationManagerCompat {
    fun from(c: Context): NotificationManager = NotificationManager
}
