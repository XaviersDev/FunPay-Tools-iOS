package ru.allisighs.funpaytools

import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionMixWithOthers
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.UIKit.UIApplication
import platform.UIKit.UIBackgroundTaskIdentifier
import platform.UIKit.UIBackgroundTaskInvalid

actual object PlatformUi {
    actual fun setStatusBarDarkIcons(dark: Boolean) = PlatformBridge.runOnMain {
        if (IosUiBridge.statusBarDarkIcons != dark) {
            IosUiBridge.statusBarDarkIcons = dark
            IosUiBridge.notifyChanged()
        }
    }
}

/**
 * Background keep-alive for sideloaded builds: a muted, looping audio session keeps the process
 * running so the FunPay work loop (auto-replies, raise, online, dumper) continues like the Android service.
 */
actual object PlatformBackground {
    private var player: AVAudioPlayer? = null
    private var bgTask: UIBackgroundTaskIdentifier = UIBackgroundTaskInvalid
    private var wanted = false

    actual val isKeepAliveActive: Boolean get() = player?.playing == true

    actual fun setKeepAlive(enabled: Boolean) = PlatformBridge.runOnMain {
        wanted = enabled
        if (enabled) startAudio() else stopAudio()
    }

    /** Called when the app goes to background: short grace period even without keep-alive. */
    fun onEnterBackground() = PlatformBridge.runOnMain {
        if (bgTask == UIBackgroundTaskInvalid) {
            bgTask = UIApplication.sharedApplication.beginBackgroundTaskWithExpirationHandler {
                UIApplication.sharedApplication.endBackgroundTask(bgTask)
                bgTask = UIBackgroundTaskInvalid
            }
        }
        if (wanted && player?.playing != true) startAudio()
    }

    fun onEnterForeground() = PlatformBridge.runOnMain {
        if (bgTask != UIBackgroundTaskInvalid) {
            UIApplication.sharedApplication.endBackgroundTask(bgTask)
            bgTask = UIBackgroundTaskInvalid
        }
        if (wanted && player?.playing != true) startAudio()
    }

    /** Audio interruptions (calls, other apps) stop playback — resume afterwards. */
    fun onAudioInterruptionEnded() = PlatformBridge.runOnMain { if (wanted) startAudio() }

    private fun startAudio() {
        try {
            val session = AVAudioSession.sharedInstance()
            session.setCategory(AVAudioSessionCategoryPlayback, AVAudioSessionCategoryOptionMixWithOthers, null)
            session.setActive(true, null)
            val p = player ?: AVAudioPlayer(silentWav().toNSData(), null).also { player = it }
            p.numberOfLoops = -1
            p.volume = 0.0f
            p.prepareToPlay()
            p.play()
        } catch (e: Throwable) {
            println("keepalive start failed: ${e.message}")
        }
    }

    private fun stopAudio() {
        try {
            player?.stop()
            player = null
            AVAudioSession.sharedInstance().setActive(false, null)
        } catch (_: Throwable) {}
    }

    /** 1 second of 8 kHz mono 16-bit silence as a WAV file. */
    private fun silentWav(): ByteArray {
        val sampleRate = 8000
        val samples = sampleRate
        val dataLen = samples * 2
        val out = ByteArray(44 + dataLen)
        fun putStr(o: Int, s: String) { s.encodeToByteArray().copyInto(out, o) }
        fun putInt(o: Int, v: Int) { for (i in 0..3) out[o + i] = (v shr (8 * i)).toByte() }
        fun putShort(o: Int, v: Int) { out[o] = v.toByte(); out[o + 1] = (v shr 8).toByte() }
        putStr(0, "RIFF"); putInt(4, 36 + dataLen); putStr(8, "WAVE")
        putStr(12, "fmt "); putInt(16, 16); putShort(20, 1); putShort(22, 1)
        putInt(24, sampleRate); putInt(28, sampleRate * 2); putShort(32, 2); putShort(34, 16)
        putStr(36, "data"); putInt(40, dataLen)
        return out
    }
}
