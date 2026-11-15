package ru.allisighs.funpaytools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * iOS: the Start.io rewarded SDK is Android-only here. The unlock flows already grant the
 * reward when an ad fails to load, so we report "no ad" and let them continue.
 */
object Ads {
    fun init(context: Context) {}

    fun showRewardedAd(activity: Activity, onComplete: (Boolean) -> Unit) {
        appScope.launch(Dispatchers.Main) {
            delay(400)
            onComplete(false)
        }
    }
}

object Stats {
    private var deviceId: String = ""

    fun init(context: Context) {
        val prefs = context.getSharedPreferences("stats", Context.MODE_PRIVATE)
        deviceId = prefs.getString("id", null) ?: run {
            val id = UUID.randomUUID().toString()
            prefs.edit().putString("id", id).apply()
            id
        }
    }
}
