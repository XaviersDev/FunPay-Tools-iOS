package ru.allisighs.funpaytools

import androidx.compose.runtime.Composable
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import okio.FileSystem
import kotlin.native.identityHashCode
import platform.AudioToolbox.AudioServicesPlaySystemSound
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSThread
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlock
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UIKit.UIDevice
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIImage
import platform.UIKit.UIImageWriteToSavedPhotosAlbum
import platform.UIKit.UIPasteboard
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.popoverPresentationController
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.posix.uname
import platform.posix.utsname
import platform.posix.usleep

actual fun platformSleep(ms: Long) {
    var left = ms
    while (left > 0) {
        val chunk = minOf(left, 1000L)
        usleep((chunk * 1000).toUInt())
        left -= chunk
    }
}

private val lockTable = co.touchlab.stately.collections.ConcurrentMutableMap<Int, platform.Foundation.NSRecursiveLock>()

@OptIn(kotlin.experimental.ExperimentalNativeApi::class)
fun iosLockFor(o: Any): platform.Foundation.NSRecursiveLock {
    val h = o.identityHashCode()
    return lockTable.block { m -> m.getOrPut(h) { platform.Foundation.NSRecursiveLock() } }
}

@Suppress("NOTHING_TO_INLINE")
actual inline fun <R> synchronized(lock: Any, block: () -> R): R {
    val l = iosLockFor(lock)
    l.lock()
    try {
        return block()
    } finally {
        l.unlock()
    }
}

actual val platformFileSystem: FileSystem = FileSystem.SYSTEM

private fun dir(kind: platform.Foundation.NSSearchPathDirectory): String =
    (NSSearchPathForDirectoriesInDomains(kind, NSUserDomainMask, true).firstOrNull() as? String) ?: "/tmp"

private fun ensureDir(path: String): String {
    NSFileManager.defaultManager.createDirectoryAtPath(path, true, null, null)
    return path
}

fun topViewController(): UIViewController? {
    val app = UIApplication.sharedApplication
    var window: UIWindow? = null
    for (scene in app.connectedScenes) {
        val ws = scene as? UIWindowScene ?: continue
        for (w in ws.windows) {
            val win = w as? UIWindow ?: continue
            if (win.isKeyWindow()) { window = win; break }
            if (window == null) window = win
        }
    }
    @Suppress("DEPRECATION")
    if (window == null) window = app.keyWindow
    var vc = window?.rootViewController
    while (vc?.presentedViewController != null && vc.presentedViewController?.isBeingDismissed() == false) vc = vc.presentedViewController
    return vc
}

fun presentOnTop(vc: UIViewController) {
    val top = topViewController() ?: return
    vc.popoverPresentationController?.let { pop ->
        pop.sourceView = top.view
        pop.sourceRect = platform.CoreGraphics.CGRectMake(
            platform.CoreGraphics.CGRectGetMidX(top.view.bounds), platform.CoreGraphics.CGRectGetMidY(top.view.bounds), 1.0, 1.0
        )
    }
    top.presentViewController(vc, animated = true, completion = null)
}

private object Keychain {
    private const val SERVICE = "ru.allisighs.funpaytools"

    fun get(account: String): String? = memScoped {
        val q = CFDictionaryCreateMutable(null, 5, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        CFDictionaryAddValue(q, kSecClass, kSecClassGenericPassword)
        CFDictionaryAddValue(q, kSecAttrService, CFBridgingRetain(SERVICE as NSString))
        CFDictionaryAddValue(q, kSecAttrAccount, CFBridgingRetain(account as NSString))
        CFDictionaryAddValue(q, kSecReturnData, kCFBooleanTrue)
        CFDictionaryAddValue(q, kSecMatchLimit, kSecMatchLimitOne)
        val result = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(q, result.ptr)
        if (status != errSecSuccess) return null
        val data = CFBridgingRelease(result.value) as? NSData ?: return null
        NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString()
    }

    fun set(account: String, value: String) {
        val del = CFDictionaryCreateMutable(null, 3, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        CFDictionaryAddValue(del, kSecClass, kSecClassGenericPassword)
        CFDictionaryAddValue(del, kSecAttrService, CFBridgingRetain(SERVICE as NSString))
        CFDictionaryAddValue(del, kSecAttrAccount, CFBridgingRetain(account as NSString))
        SecItemDelete(del)
        val add = CFDictionaryCreateMutable(null, 5, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        CFDictionaryAddValue(add, kSecClass, kSecClassGenericPassword)
        CFDictionaryAddValue(add, kSecAttrService, CFBridgingRetain(SERVICE as NSString))
        CFDictionaryAddValue(add, kSecAttrAccount, CFBridgingRetain(account as NSString))
        CFDictionaryAddValue(add, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlock)
        val data = (value as NSString).dataUsingEncoding(NSUTF8StringEncoding)
        CFDictionaryAddValue(add, kSecValueData, CFBridgingRetain(data))
        SecItemAdd(add, null)
    }
}

actual object PlatformBridge {
    actual val filesDir: String by lazy { ensureDir(dir(NSApplicationSupportDirectory) + "/files") }
    actual val cacheDir: String by lazy { ensureDir(dir(NSCachesDirectory) + "/fpt") }
    actual val documentsDir: String by lazy { ensureDir(dir(NSDocumentDirectory)) }

    actual fun openUrl(url: String): Boolean {
        val u = NSURL.URLWithString(url) ?: NSURL.URLWithString(uriEncode(url, ":/?#[]@!$&'()*+,;=%")) ?: return false
        runOnMain { UIApplication.sharedApplication.openURL(u, emptyMap<Any?, Any?>(), null) }
        return true
    }

    actual fun openAppSettings() {
        openUrl(UIApplicationOpenSettingsURLString)
    }

    actual fun shareText(text: String, title: String?) = runOnMain {
        val vc = UIActivityViewController(listOf(text), null)
        presentOnTop(vc)
    }

    actual fun shareFile(path: String, mime: String?) = runOnMain {
        val url = NSURL.fileURLWithPath(path)
        val vc = UIActivityViewController(listOf(url), null)
        presentOnTop(vc)
    }

    actual fun setClipboard(text: String) = runOnMain { UIPasteboard.generalPasteboard.string = text }
    actual fun getClipboard(): String? = UIPasteboard.generalPasteboard.string

    actual fun deviceId(): String {
        Keychain.get("device_id")?.let { return it }
        val id = UIDevice.currentDevice.identifierForVendor?.UUIDString ?: NSUUID().UUIDString
        Keychain.set("device_id", id)
        return id
    }

    actual fun vibrate(ms: Long) { AudioServicesPlaySystemSound(4095u) }

    actual fun keepScreenOn(on: Boolean) = runOnMain { UIApplication.sharedApplication.idleTimerDisabled = on }

    actual fun saveImageToGallery(bytes: ByteArray): Boolean {
        val img = UIImage.imageWithData(bytes.toNSData()) ?: return false
        UIImageWriteToSavedPhotosAlbum(img, null, null, null)
        return true
    }

    actual val deviceModel: String by lazy {
        memScoped {
            val u = alloc<utsname>()
            uname(u.ptr)
            u.machine.toKString().ifEmpty { UIDevice.currentDevice.model }
        }
    }

    actual val osVersion: String get() = UIDevice.currentDevice.systemVersion

    actual fun runOnMain(block: () -> Unit) {
        if (NSThread.isMainThread) block() else dispatch_async(dispatch_get_main_queue()) { block() }
    }

    actual fun isNetworkAvailable(): Boolean = true

    actual fun setClipboardImage(bytes: ByteArray): Boolean {
        val img = UIImage.imageWithData(bytes.toNSData()) ?: return false
        runOnMain { UIPasteboard.generalPasteboard.image = img }
        return true
    }

    actual fun clipboardHasImage(): Boolean = UIPasteboard.generalPasteboard.hasImages

    actual fun clipboardImageToFile(): String? {
        val img = UIPasteboard.generalPasteboard.image ?: return null
        val data = platform.UIKit.UIImagePNGRepresentation(img) ?: return null
        val dir = cacheDir + "/clipboard"
        NSFileManager.defaultManager.createDirectoryAtPath(dir, true, null, null)
        val path = "$dir/paste_${System.currentTimeMillis()}.png"
        File(path).writeBytes(data.toByteArray())
        return path
    }

    actual fun clipboardChangeCount(): Long = UIPasteboard.generalPasteboard.changeCount

    actual fun setSystemBarsHidden(hidden: Boolean) = runOnMain {
        IosUiBridge.statusBarHidden = hidden
        IosUiBridge.notifyChanged()
    }

    actual fun exportFile(path: String, suggestedName: String) = runOnMain {
        val url = NSURL.fileURLWithPath(path)
        val picker = UIDocumentPickerViewController(forExportingURLs = listOf(url), asCopy = true)
        presentOnTop(picker)
    }
}


/** State read by the Swift root view controller (status bar, orientation). */
object IosUiBridge {
    var statusBarDarkIcons: Boolean = false
    var statusBarHidden: Boolean = false
    /** 0 = portrait, 1 = landscape, 2 = any */
    var orientationMode: Int = 0
    var onChange: (() -> Unit)? = null
    fun notifyChanged() { onChange?.invoke() }
}
