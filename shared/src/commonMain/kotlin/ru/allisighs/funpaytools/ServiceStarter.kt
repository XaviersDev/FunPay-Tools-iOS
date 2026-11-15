package ru.allisighs.funpaytools

object ServiceStarter {
    fun startSafely(context: Context) {
        FunPayService.start()
    }
}

/** Android watchdog workers have no iOS equivalent; background time is managed by IosBackground. */
object WatchdogWorker {
    fun start(context: Context) {}
    fun startOneTime(context: Context) {}
    fun stop(context: Context) {}
}
