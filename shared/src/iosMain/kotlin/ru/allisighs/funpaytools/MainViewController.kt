package ru.allisighs.funpaytools

import androidx.compose.ui.window.ComposeUIViewController
import platform.AVFAudio.AVAudioSessionInterruptionNotification
import platform.AVFAudio.AVAudioSessionInterruptionTypeEnded
import platform.AVFAudio.AVAudioSessionInterruptionTypeKey
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationWillTerminateNotification
import platform.UIKit.UIViewController
import platform.UIKit.UIPasteboardChangedNotification
import kotlinx.coroutines.launch

private var observersInstalled = false

private fun installLifecycleObservers() {
    if (observersInstalled) return
    observersInstalled = true
    val nc = NSNotificationCenter.defaultCenter
    val q = NSOperationQueue.mainQueue
    nc.addObserverForName(UIApplicationDidEnterBackgroundNotification, null, q) { _ ->
        SharedPreferencesImpl.flushAll()
        PlatformBackground.onEnterBackground()
    }
    nc.addObserverForName(UIApplicationDidBecomeActiveNotification, null, q) { _ ->
        PlatformBackground.onEnterForeground()
        if (AppBootstrapReady.ready && !FunPayService.isRunning()) {
            try { if (AppBootstrap.repository.hasAuth()) FunPayService.start() } catch (_: Throwable) {}
        }
    }
    nc.addObserverForName(UIPasteboardChangedNotification, null, q) { _ -> ClipboardManager.notifyChanged() }
    nc.addObserverForName(UIApplicationWillTerminateNotification, null, q) { _ ->
        SharedPreferencesImpl.flushAll()
    }
    nc.addObserverForName(AVAudioSessionInterruptionNotification, null, q) { n ->
        val type = (n?.userInfo?.get(AVAudioSessionInterruptionTypeKey) as? NSNumber)?.unsignedIntegerValue
        if (type == AVAudioSessionInterruptionTypeEnded) PlatformBackground.onAudioInterruptionEnded()
    }
}

object AppBootstrapReady {
    var ready = false
}

private fun installOrientationBridge() {
    appScope.launch(kotlinx.coroutines.Dispatchers.Main) {
        OrientationController.requested.collect { o ->
            IosUiBridge.orientationMode = when (o) {
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE -> 1
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, ActivityInfo.SCREEN_ORIENTATION_SENSOR, ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR, ActivityInfo.SCREEN_ORIENTATION_USER -> 2
                else -> 0
            }
            IosUiBridge.notifyChanged()
        }
    }
}

/** Called from Swift at launch, before the UI is created (so notification taps are not lost). */
fun appDidFinishLaunching() {
    PlatformNotifications.install()
    installLifecycleObservers()
    installOrientationBridge()
}

fun MainViewController(): UIViewController {
    appDidFinishLaunching()
    return ComposeUIViewController(configure = { enforceStrictPlistSanityCheck = false }) {
        AppBootstrapReady.ready = true
        FunPayToolsRoot()
    }
}
