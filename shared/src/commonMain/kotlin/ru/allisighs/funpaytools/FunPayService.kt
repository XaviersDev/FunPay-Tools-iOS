/*
 *
 *  * Copyright (c) 2026 XaviersDev (AlliSighs). All rights reserved.
 *  *
 *  * This code is proprietary. Modification, distribution, or use
 *  * of this file without express written permission is strictly prohibited.
 *  * Unauthorized use will be prosecuted.
 *
 */

package ru.allisighs.funpaytools

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Keeps the process alive in background where the platform allows it (iOS: silent audio session). */
expect object PlatformBackground {
    fun setKeepAlive(enabled: Boolean)
    val isKeepAliveActive: Boolean
}

/**
 * iOS port of the Android foreground service: the same multi-account work loop,
 * running while the app process is alive (foreground, or background with keep-alive).
 */
object FunPayService {
    private var serviceScope: CoroutineScope? = null
    private lateinit var mainRepository: FunPayRepository
    private val lastMessageHashes = mutableMapOf<String, Int>()
    private val processedMessagesCache = mutableMapOf<String, String>()
    private val accountJobs = mutableMapOf<String, Job>()
    val running = MutableStateFlow(false)

    private val context: Context get() = AppContext

    fun isRunning(): Boolean = serviceScope != null

    fun start() {
        if (serviceScope != null) return
        val prefs = context.getSharedPreferences("funpay_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("app_fully_disabled", false)) {
            LogManager.addLog("⏸ SERVICE: Приложение выключено полностью — сервис не запускается")
            return
        }
        mainRepository = FunPayRepository(context)
        try {
            PluginEngine.init(context, mainRepository)
        } catch (e: Exception) {
            LogManager.addLog("❌ Ошибка инициализации плагинов: ${e.message}")
        }
        createNotificationChannel()
        serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        running.value = true
        PlatformBackground.setKeepAlive(prefs.getBoolean("ios_background_keepalive", true))
        LogManager.addLog("✅ SERVICE: Запущен менеджер мультиаккаунтов")
        startWorkLoop()
    }

    fun stop() {
        serviceScope?.cancel()
        serviceScope = null
        accountJobs.clear()
        running.value = false
        PlatformBackground.setKeepAlive(false)
        LogManager.addLog("⏹ SERVICE: Остановлен")
    }

    fun restart() {
        stop()
        start()
    }

    private fun startWorkLoop() {
        val scope = serviceScope ?: return
        scope.launch {
            while (isActive) {
                if (!PlatformBridge.isNetworkAvailable()) {
                    delay(25000)
                    continue
                }

                val allAccounts = mainRepository.getAllAccounts()
                val validAccounts = allAccounts.filter { it.isActive || it.runInBackground }

                for (account in validAccounts) {
                    if (accountJobs[account.id]?.isActive != true) {
                        LogManager.addLogDebug("🚀 Запуск фонового потока для аккаунта: ${account.username}")
                        accountJobs[account.id] = launchAccountLoop(scope, account.id)
                    }
                }

                val validIds = validAccounts.map { it.id }
                accountJobs.keys.toList().forEach { id ->
                    if (id !in validIds) {
                        LogManager.addLogDebug("🛑 Остановка фонового потока для аккаунта: $id")
                        accountJobs[id]?.cancel()
                        accountJobs.remove(id)
                    }
                }
                delay(20000)
            }
        }
    }

    private fun launchAccountLoop(scope: CoroutineScope, accountId: String): Job = scope.launch {
        val repository = FunPayRepository(context, targetAccountId = accountId)
        val accName = repository.getActiveAccount()?.username ?: accountId
        var lastCleanup = 0L
        var lastWidgetUpdate = 0L
        var lastOnlinePing = 0L

        while (isActive) {
            if (!PlatformBridge.isNetworkAvailable()) {
                delay(25000)
                continue
            }

            val account = repository.getActiveAccount()
            if (account != null) {
                try {
                    val chats = repository.getChats()

                    chats.forEach { chat ->
                        val msgHash = chat.lastMessage.hashCode()
                        if (lastMessageHashes[chat.id] != msgHash) {
                            lastMessageHashes[chat.id] = msgHash
                            val lastSent = FunPayRepository.lastOutgoingMessages[chat.id]
                            val isImageEcho = (lastSent == "__image__" && (chat.lastMessage.contains("Изображение", ignoreCase = true) || chat.lastMessage.contains("Image", ignoreCase = true)))
                            if (lastSent != chat.lastMessage && !isImageEcho) {
                                val safeText = chat.lastMessage.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
                                PluginEngine.dispatchEvent("onNewMessage", """{"chatId": "${chat.id}", "username": "${chat.username}", "text": "$safeText", "isMe": false}""")
                            }
                        }
                    }

                    if (repository.getSetting("push_notifications")) {
                        checkPushNotifications(chats)
                    }

                    // Режим сотрудника: только чаты и уведомления, автоматизацию ведёт владелец
                    if (!EmployeeMode.isOn(context)) {
                        var busySettings = ChatFolderManager.getBusyMode(context)
                        val activeSlot = try {
                            ChatFolderManager.getActiveScheduleSlot(context)
                        } catch (_: Exception) { null }
                        if (activeSlot != null && !busySettings.enabled) {
                            val cal = Calendar.getInstance()
                            val dayStamp = cal.get(Calendar.YEAR) * 10000L +
                                    cal.get(Calendar.MONTH) * 100L +
                                    cal.get(Calendar.DAY_OF_MONTH)
                            val slotStamp = (activeSlot.id.hashCode().toLong() and 0xFFFFFFFFL)
                            val scheduledEnabledAt = dayStamp * 1_000_000_000L + slotStamp
                            busySettings = busySettings.copy(
                                enabled = true,
                                enabledAt = scheduledEnabledAt,
                                message = if (activeSlot.overrideMessage && activeSlot.message.isNotBlank())
                                    activeSlot.message
                                else busySettings.message,
                                cooldownMinutes = if (activeSlot.overrideCooldown)
                                    activeSlot.cooldownMinutes
                                else busySettings.cooldownMinutes
                            )
                        }

                        if (busySettings.enabled) {
                            repository.checkBusyModeReplies(chats, busySettings)
                            if (busySettings.keepRaise) repository.raiseAllLots()
                            if (busySettings.keepAutoResponse) repository.checkAutoResponse(chats)
                            if (busySettings.keepGreeting) repository.checkGreetings(chats)
                        } else {
                            resolveNewUnreadChats(chats, repository)
                            val unreadChatsForDelivery = chats.filter { it.isUnread }
                            for (c in unreadChatsForDelivery) {
                                AutoDeliveryManager.checkAutoDelivery(c, repository, context)
                            }
                            repository.checkAutoResponse(chats)
                            repository.checkGreetings(chats)
                            repository.raiseAllLots()
                            repository.checkOrderConfirmations(chats)
                            repository.checkAutoRefund(chats)
                            repository.checkReviewReplies(chats)
                            repository.runDumperCycle()
                            repository.checkOrderReminders(chats)
                            repository.checkAutoTicketCycle()
                        }

                        try {
                            if (account.isActive) {
                                ConcurentManager.tick(context, repository)
                            }
                        } catch (e: Exception) { }

                        if (repository.getSetting("always_online")) {
                            if (System.currentTimeMillis() - lastOnlinePing > 4 * 60 * 1000L) {
                                repository.pingOnlineStatus()
                                lastOnlinePing = System.currentTimeMillis()
                            }
                        }
                    }

                    if (System.currentTimeMillis() - lastCleanup > 24 * 60 * 60 * 1000L) {
                        repository.cleanupOldProcessedEvents()
                        lastCleanup = System.currentTimeMillis()
                    }

                    if (account.isActive) {
                        if (System.currentTimeMillis() - lastWidgetUpdate > 5 * 60 * 1000L) {
                            try {
                                val profile = repository.getSelfProfile()
                                if (profile != null) {
                                    WidgetManager.saveProfileCache(context, profile)
                                    try {
                                        DynamicAvatarManager.tickFromService(context, repository, profile)
                                    } catch (e: Exception) {
                                        LogManager.addLogDebug("DynAvatar tick: ${e.message}")
                                    }
                                }
                                WidgetManager.updateAllWidgets(context)
                            } catch (e: Exception) { }
                            lastWidgetUpdate = System.currentTimeMillis()
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    LogManager.addLog("❌ Ошибка в потоке [$accName]: ${e.message}")
                    e.printStackTrace()
                }
            }

            val calendar = Calendar.getInstance()
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val isNight = hour in 2..6
            val dumperMinIntervalSec: Int = try {
                val dumper = repository.getDumperSettings()
                if (dumper.enabled && dumper.lots.isNotEmpty()) {
                    dumper.lots.filter { it.enabled }
                        .minOfOrNull { it.updateInterval.coerceAtLeast(1) }
                        ?: Int.MAX_VALUE
                } else Int.MAX_VALUE
            } catch (_: Exception) { Int.MAX_VALUE }
            val concurentMinSec: Int = try {
                val cs = ConcurentManager.getSettings(context)
                if (cs.enabled) {
                    val leftMs = cs.nextPostAt - System.currentTimeMillis()
                    if (leftMs <= 0) 1 else (leftMs / 1000).toInt().coerceAtLeast(1)
                } else Int.MAX_VALUE
            } catch (_: Exception) { Int.MAX_VALUE }
            val defaultDelay = if (isNight) 15000L else 6513L
            val tightest = minOf(dumperMinIntervalSec, concurentMinSec)
            val delayTime = if (tightest < Int.MAX_VALUE) {
                (tightest * 1000L).coerceAtMost(defaultDelay).coerceAtLeast(1000L)
            } else defaultDelay
            delay(delayTime)
        }
    }

    private suspend fun resolveNewUnreadChats(chats: List<ChatItem>, repo: FunPayRepository) {
        val authData = repo.getCsrfAndId() ?: return
        val newlyUnread = chats.filter { chat ->
            chat.isUnread &&
                    !FunPayRepository.lastOutgoingMessages.containsKey(chat.id) &&
                    !repo.knownUnreadChats.contains(chat.id)
        }
        for (chat in newlyUnread) {
            repo.knownUnreadChats.add(chat.id)
        }
        val unreadIds = chats.filter { it.isUnread }.map { it.id }.toSet()
        repo.knownUnreadChats.retainAll(unreadIds)
    }

    private fun checkPushNotifications(chats: List<ChatItem>) {
        for (chat in chats) {
            if (chat.isUnread) {
                val lastMsg = chat.lastMessage
                val lastSelf = FunPayRepository.lastOutgoingMessages[chat.id]
                if (lastSelf != null) {
                    if (lastSelf == "__image__") {
                        FunPayRepository.lastOutgoingMessages.remove(chat.id)
                        continue
                    }
                    val cleanIncoming = lastMsg.replace("...", "").trim().lowercase()
                    val cleanSelf = lastSelf.trim().lowercase()
                    if (cleanSelf.contains(cleanIncoming) ||
                        cleanIncoming.contains(cleanSelf) ||
                        cleanSelf.startsWith(cleanIncoming) ||
                        cleanIncoming.startsWith(cleanSelf)) {
                        continue
                    }
                }
                val cachedMsg = processedMessagesCache[chat.id]
                if (cachedMsg != lastMsg) {
                    sendChatNotification(chat)
                    processedMessagesCache[chat.id] = lastMsg
                }
            } else {
                processedMessagesCache.remove(chat.id)
            }
        }
    }

    private fun sendChatNotification(chat: ChatItem) {
        val notificationId = chat.id.hashCode()
        val channelId = "fp_push_channel_v4"
        val replyLabel = "Ответить"
        val remoteInput = RemoteInput.Builder("key_text_reply").setLabel(replyLabel).build()
        val replyIntent = Intent(context, ReplyReceiver::class).apply {
            putExtra("chat_id", chat.id)
            putExtra("notification_id", notificationId)
        }
        val replyPendingIntent = PendingIntent.getBroadcast(context, notificationId, replyIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        val action = Notification.Action.Builder(R.drawable.ic_menu_send, replyLabel, replyPendingIntent).addRemoteInput(remoteInput).build()
        val intent = Intent(context, MainActivity::class).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("chat_id", chat.id)
            putExtra("chat_username", chat.username)
        }
        val pendingIntent = PendingIntent.getActivity(context, notificationId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        val notification = Notification.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .setContentTitle(chat.username)
            .setContentText(chat.lastMessage)
            .setStyle(Notification.BigTextStyle().bigText(chat.lastMessage))
            .setContentIntent(pendingIntent)
            .addAction(action)
            .setAutoCancel(true)
            .setGroup("chat_${chat.id}")
            .build()
        NotificationManager.notify(notificationId, notification)
    }

    private fun createNotificationChannel() {
        NotificationManager.createNotificationChannel(NotificationChannel("fp_service", "FunPay Tools Core", NotificationManager.IMPORTANCE_LOW))
        NotificationManager.createNotificationChannel(NotificationChannel("fp_push_channel_v4", "Сообщения", NotificationManager.IMPORTANCE_HIGH))
    }
}
