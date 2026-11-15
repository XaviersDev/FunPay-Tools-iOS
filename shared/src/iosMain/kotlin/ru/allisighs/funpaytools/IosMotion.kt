package ru.allisighs.funpaytools

import kotlinx.cinterop.useContents
import platform.CoreMotion.CMMotionManager
import platform.Foundation.NSOperationQueue

actual object PlatformMotion {
    private val manager = CMMotionManager()

    actual fun start(cb: (FloatArray) -> Unit) {
        if (!manager.accelerometerAvailable) return
        manager.accelerometerUpdateInterval = 1.0 / 30.0
        manager.startAccelerometerUpdatesToQueue(NSOperationQueue.mainQueue) { data, _ ->
            val d = data ?: return@startAccelerometerUpdatesToQueue
            d.acceleration.useContents {
                // iOS reports in g with inverted sign compared to Android (m/s²)
                cb(floatArrayOf((-x * 9.81).toFloat(), (-y * 9.81).toFloat(), (-z * 9.81).toFloat()))
            }
        }
    }

    actual fun stop() {
        manager.stopAccelerometerUpdates()
    }
}
