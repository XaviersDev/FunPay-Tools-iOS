package ru.allisighs.funpaytools

import platform.Foundation.NSData
import platform.Foundation.create
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeData
import platform.UniformTypeIdentifiers.UTTypeItem
import platform.UniformTypeIdentifiers.UTTypeJSON
import platform.UniformTypeIdentifiers.UTTypePlainText
import platform.UniformTypeIdentifiers.UTTypeImage
import platform.UniformTypeIdentifiers.UTTypeJavaScript
import platform.UniformTypeIdentifiers.UTTypeFont
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationAction
import platform.UserNotifications.UNNotificationActionOptionForeground
import platform.UserNotifications.UNNotificationActionOptions
import platform.UserNotifications.UNNotificationCategory
import platform.UserNotifications.UNNotificationCategoryOptions
import platform.UserNotifications.UNNotificationDefaultActionIdentifier
import platform.UserNotifications.UNNotificationInterruptionLevel
import platform.UserNotifications.UNNotificationPresentationOptionBanner
import platform.UserNotifications.UNNotificationPresentationOptionList
import platform.UserNotifications.UNNotificationPresentationOptionSound
import platform.UserNotifications.UNNotificationPresentationOptions
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationResponse
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTextInputNotificationAction
import platform.UserNotifications.UNTextInputNotificationResponse
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol
import platform.darwin.NSObject
import kotlin.concurrent.Volatile

// ------------------------------------------------------------------------------------------------
// Local notifications
// ------------------------------------------------------------------------------------------------
private class NotificationDelegate : NSObject(), UNUserNotificationCenterDelegateProtocol {
    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        willPresentNotification: UNNotification,
        withCompletionHandler: (UNNotificationPresentationOptions) -> Unit
    ) {
        withCompletionHandler(UNNotificationPresentationOptionBanner or UNNotificationPresentationOptionList or UNNotificationPresentationOptionSound)
    }

    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        didReceiveNotificationResponse: UNNotificationResponse,
        withCompletionHandler: () -> Unit
    ) {
        try {
            val info = didReceiveNotificationResponse.notification.request.content.userInfo
            val action = didReceiveNotificationResponse.actionIdentifier
            val key = when {
                didReceiveNotificationResponse is UNTextInputNotificationResponse -> "reply"
                action == UNNotificationDefaultActionIdentifier -> "open"
                action.startsWith("btn") -> action
                else -> "open"
            }
            @Suppress("UNCHECKED_CAST")
            val payload = (info[key] as? Map<Any?, Any?>)?.entries?.associate { it.key.toString() to it.value.toString() }
            val text = (didReceiveNotificationResponse as? UNTextInputNotificationResponse)?.userText
            if (payload != null) NotificationActionRouter.handle(payload, text)
        } catch (e: Exception) {
            println("notification response failed: ${e.message}")
        }
        withCompletionHandler()
    }
}

actual object PlatformNotifications {
    private val delegate = NotificationDelegate()
    private val categories = LinkedHashMap<String, UNNotificationCategory>()
    @Volatile private var authorized = false
    private var installed = false

    private val center: UNUserNotificationCenter get() = UNUserNotificationCenter.currentNotificationCenter()

    fun install() {
        if (installed) return
        installed = true
        center.delegate = delegate
        refreshStatus()
    }

    private fun refreshStatus() {
        center.getNotificationSettingsWithCompletionHandler { s ->
            val st = s?.authorizationStatus
            authorized = st == UNAuthorizationStatusAuthorized || st == UNAuthorizationStatusProvisional
        }
    }

    actual fun requestPermission(onResult: (Boolean) -> Unit) {
        install()
        center.requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge) { ok, _ ->
            authorized = ok
            PlatformBridge.runOnMain { onResult(ok) }
        }
    }

    actual fun areNotificationsEnabled(): Boolean { refreshStatus(); return authorized }

    private fun ensureCategory(id: String, build: () -> UNNotificationCategory) {
        synchronized(categories) {
            if (categories.containsKey(id)) return
            categories[id] = build()
            center.setNotificationCategories(categories.values.toSet())
        }
    }

    actual fun post(spec: LocalNotificationSpec) {
        install()
        val content = UNMutableNotificationContent()
        content.setTitle(spec.title)
        content.setBody(spec.text)
        spec.subtitle?.let { content.setSubtitle(it) }
        spec.threadId?.let { content.setThreadIdentifier(it) }
        if (spec.sound) content.setSound(UNNotificationSound.soundNamed("funpay_push.caf"))
        if (spec.timeSensitive) {
            try { content.setInterruptionLevel(UNNotificationInterruptionLevel.UNNotificationInterruptionLevelTimeSensitive) } catch (_: Throwable) {}
        }
        val info = HashMap<Any?, Any?>()
        spec.openPayload?.let { info["open"] = it }
        spec.replyPayload?.let { info["reply"] = it }
        spec.buttons.forEachIndexed { i, b -> info["btn$i"] = b.second }
        content.setUserInfo(info)

        val actions = ArrayList<UNNotificationAction>()
        if (spec.replyPayload != null) {
            actions.add(
                UNTextInputNotificationAction.actionWithIdentifier(
                    "reply", spec.replyLabel ?: "Ответить", 0uL, "Отправить", "Сообщение…"
                )
            )
        }
        spec.buttons.forEachIndexed { i, b ->
            actions.add(UNNotificationAction.actionWithIdentifier("btn$i", b.first, UNNotificationActionOptionForeground))
        }
        if (actions.isNotEmpty()) {
            val catId = "fpt_" + (listOf(if (spec.replyPayload != null) "r:${spec.replyLabel}" else "") + spec.buttons.map { it.first }).joinToString("|").hashCode().toUInt().toString(16)
            ensureCategory(catId) {
                UNNotificationCategory.categoryWithIdentifier(catId, actions, emptyList<String>(), 0uL)
            }
            content.setCategoryIdentifier(catId)
        }
        val req = UNNotificationRequest.requestWithIdentifier("fpt_${spec.id}", content, null)
        center.addNotificationRequest(req) { err: NSError? -> if (err != null) println("notify failed: ${err.localizedDescription}") }
    }

    actual fun cancel(id: Int) {
        center.removeDeliveredNotificationsWithIdentifiers(listOf("fpt_$id"))
        center.removePendingNotificationRequestsWithIdentifiers(listOf("fpt_$id"))
    }

    actual fun cancelAll() {
        center.removeAllDeliveredNotifications()
        center.removeAllPendingNotificationRequests()
    }
}

// ------------------------------------------------------------------------------------------------
// Pickers
// ------------------------------------------------------------------------------------------------
private fun pickedDir(): String {
    val d = PlatformBridge.cacheDir + "/picked"
    NSFileManager.defaultManager.createDirectoryAtPath(d, true, null, null)
    return d
}

private class PhotoPickerDelegate(private val onDone: (List<String>) -> Unit) : NSObject(), PHPickerViewControllerDelegateProtocol {
    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, null)
        val results = didFinishPicking.filterIsInstance<PHPickerResult>()
        if (results.isEmpty()) { finish(emptyList()); return }
        val out = arrayOfNulls<String>(results.size)
        var remaining = results.size
        val lock = Any()
        results.forEachIndexed { idx, r ->
            loadImage(r) { path ->
                val done = synchronized(lock) {
                    out[idx] = path
                    remaining--
                    remaining == 0
                }
                if (done) finish(out.filterNotNull())
            }
        }
    }

    private fun finish(list: List<String>) {
        PlatformBridge.runOnMain { onDone(list); PlatformPickers.release(this) }
    }

    private fun loadImage(r: PHPickerResult, cb: (String?) -> Unit) {
        val p = r.itemProvider
        val direct = listOf("public.png" to "png", "com.compuserve.gif" to "gif", "public.jpeg" to "jpg", "org.webmproject.webp" to "webp")
        val match = direct.firstOrNull { p.hasItemConformingToTypeIdentifier(it.first) }
        if (match != null) {
            p.loadDataRepresentationForTypeIdentifier(match.first) { data: NSData?, _: NSError? ->
                if (data != null) cb(writePicked(data.toByteArray(), match.second)) else convertViaUIImage(p, cb)
            }
        } else convertViaUIImage(p, cb)
    }

    private fun convertViaUIImage(p: platform.Foundation.NSItemProvider, cb: (String?) -> Unit) {
        p.loadDataRepresentationForTypeIdentifier("public.image") { data: NSData?, _: NSError? ->
            if (data == null) { cb(null); return@loadDataRepresentationForTypeIdentifier }
            val img = UIImage.imageWithData(data)
            val jpeg = img?.let { UIImageJPEGRepresentation(it, 0.92) }
            cb(if (jpeg != null) writePicked(jpeg.toByteArray(), "jpg") else writePicked(data.toByteArray(), "img"))
        }
    }
}

private fun writePicked(bytes: ByteArray, ext: String): String {
    val path = pickedDir() + "/" + NSUUID().UUIDString + "." + ext
    File(path).writeBytes(bytes)
    return "file://$path"
}

private class DocPickerDelegate(private val onDone: (String?) -> Unit) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
        var result: String? = null
        if (url != null) {
            val access = url.startAccessingSecurityScopedResource()
            try {
                val data = NSData.create(contentsOfURL = url)
                if (data != null) {
                    val name: String = url.lastPathComponent ?: "file"
                    val path = pickedDir() + "/" + NSUUID().UUIDString.take(8) + "_" + name
                    File(path).writeBytes(data.toByteArray())
                    result = "file://$path"
                }
            } finally {
                if (access) url.stopAccessingSecurityScopedResource()
            }
        }
        PlatformBridge.runOnMain { onDone(result); PlatformPickers.release(this) }
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        PlatformBridge.runOnMain { onDone(null); PlatformPickers.release(this) }
    }
}

actual object PlatformPickers {
    private val retained = ArrayList<Any>()
    internal fun release(o: Any) { retained.remove(o) }

    actual fun pickImages(multiple: Boolean, onResult: (List<String>) -> Unit) = PlatformBridge.runOnMain {
        val cfg = PHPickerConfiguration()
        cfg.filter = PHPickerFilter.imagesFilter
        cfg.selectionLimit = if (multiple) 20 else 1
        val picker = PHPickerViewController(cfg)
        val d = PhotoPickerDelegate(onResult)
        retained.add(d)
        picker.delegate = d
        presentOnTop(picker)
    }

    actual fun pickDocument(mimeTypes: List<String>, onResult: (String?) -> Unit) = PlatformBridge.runOnMain {
        val types = mimeTypes.mapNotNull { m ->
            when {
                m == "*/*" || m.isBlank() -> UTTypeItem
                m.startsWith("image/") && m.endsWith("*") -> UTTypeImage
                m == "application/json" -> UTTypeJSON
                m.startsWith("text/") && m.endsWith("*") -> UTTypePlainText
                m.contains("javascript") -> UTTypeJavaScript
                m.startsWith("font/") || m.contains("ttf") || m.contains("otf") -> UTTypeFont
                m == "application/octet-stream" -> UTTypeData
                else -> UTType.typeWithMIMEType(m) ?: UTTypeItem
            }
        }.ifEmpty { listOf(UTTypeItem) }
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = types, asCopy = true)
        val d = DocPickerDelegate(onResult)
        retained.add(d)
        picker.delegate = d
        presentOnTop(picker)
    }
}
