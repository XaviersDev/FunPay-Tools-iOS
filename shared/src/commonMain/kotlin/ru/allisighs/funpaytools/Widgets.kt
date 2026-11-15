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

import kotlinx.coroutines.IO

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil3.ImageLoader
import coil3.request.ImageRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


object WidgetManager {
    private const val PREFS_NAME = "widget_prefs"
    private val gson = Gson()

    fun getTransparency(context: Context): Float {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat("transparency", 0.8f)
    }

    fun setTransparency(context: Context, value: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putFloat("transparency", value).apply()
        updateAllWidgets(context)
    }

    fun saveProfileCache(context: Context, profile: UserProfile) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("cached_profile", gson.toJson(profile))
            .putLong("last_updated", System.currentTimeMillis())
            .apply()
    }

    fun getProfileCache(context: Context): UserProfile? {
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("cached_profile", null) ?: return null
        return try {
            gson.fromJson(json, UserProfile::class.java)
        } catch (e: Exception) { null }
    }

    /** Возвращает строку вида "14:32" или null если ещё не обновлялось */
    fun getLastUpdatedString(context: Context): String? {
        val ts = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong("last_updated", 0L)
        if (ts == 0L) return null
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))
    }

    fun updateAllWidgets(context: Context) {
        WidgetBridge.publish(context)
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetsScreen(
    navController: NavController,
    currentTheme: AppTheme
) {
    val context = LocalContext.current
    var transparency by remember { mutableFloatStateOf(WidgetManager.getTransparency(context)) }
    var lastUpdated by remember { mutableStateOf(WidgetManager.getLastUpdatedString(context)) }
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Виджеты",
                        color = ThemeManager.parseColor(currentTheme.textPrimaryColor),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            Icons.Default.ArrowBack,
                            "Назад",
                            tint = ThemeManager.parseColor(currentTheme.textPrimaryColor)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ThemeManager.parseColor(currentTheme.surfaceColor)
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.Black),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                listOf(Color(0xFF1A237E), Color(0xFF000000))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .width(200.dp)
                            .height(100.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = ThemeManager.parseColor(currentTheme.surfaceColor)
                                .copy(alpha = transparency)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val cachedProfile = remember { WidgetManager.getProfileCache(context) }
                            Text(
                                cachedProfile?.totalBalance ?: "15 420 ₽",
                                color = ThemeManager.parseColor(currentTheme.accentColor),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "FunPay Balance",
                                color = ThemeManager.parseColor(currentTheme.textSecondaryColor),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = ThemeManager.parseColor(currentTheme.surfaceColor)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Прозрачность фона",
                        color = ThemeManager.parseColor(currentTheme.textPrimaryColor),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = transparency,
                        onValueChange = {
                            transparency = it
                            context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
                                .edit().putFloat("transparency", it).apply()
                        },
                        onValueChangeFinished = {
                            WidgetManager.updateAllWidgets(context)
                        },
                        valueRange = 0.2f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = ThemeManager.parseColor(currentTheme.accentColor),
                            activeTrackColor = ThemeManager.parseColor(currentTheme.accentColor)
                        )
                    )
                    Text(
                        "${(transparency * 100).toInt()}%",
                        color = ThemeManager.parseColor(currentTheme.textSecondaryColor),
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }

            
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = ThemeManager.parseColor(currentTheme.surfaceColor)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Последнее обновление",
                            color = ThemeManager.parseColor(currentTheme.textPrimaryColor),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            lastUpdated?.let { "Сегодня в $it" } ?: "Ещё не обновлялось",
                            color = ThemeManager.parseColor(currentTheme.textSecondaryColor),
                            fontSize = 12.sp
                        )
                    }

                    Button(
                        onClick = {
                            isRefreshing = true
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val repository = FunPayRepository(context)
                                    val profile = repository.getSelfProfile()
                                    if (profile != null) {
                                        WidgetManager.saveProfileCache(context, profile)
                                    }
                                    WidgetManager.updateAllWidgets(context)
                                    withContext(Dispatchers.Main) {
                                        lastUpdated = WidgetManager.getLastUpdatedString(context)
                                    }
                                } catch (e: Exception) {
                                    
                                } finally {
                                    withContext(Dispatchers.Main) {
                                        isRefreshing = false
                                    }
                                }
                            }
                        },
                        enabled = !isRefreshing,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ThemeManager.parseColor(currentTheme.accentColor)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Обновить",
                                modifier = Modifier.size(16.dp),
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Обновить", color = Color.White, fontSize = 13.sp)
                        }
                    }
                }
            }

            
            if (AndroidBuild.VERSION.SDK_INT >= AndroidBuild.VERSION_CODES.O) {
                val appWidgetManager = AppWidgetManager.getInstance(context)

                if (appWidgetManager.isRequestPinAppWidgetSupported) {
                    Text(
                        "Добавить на главный экран",
                        color = ThemeManager.parseColor(currentTheme.textPrimaryColor),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PinWidgetButton(
                            title = "Баланс",
                            icon = Icons.Default.Widgets,
                            theme = currentTheme,
                            modifier = Modifier.weight(1f)
                        ) {
                            val provider = ComponentName(context, BalanceWidget::class.java)
                            appWidgetManager.requestPinAppWidget(provider, null, null)
                        }

                        PinWidgetButton(
                            title = "Профиль",
                            icon = Icons.Default.Add,
                            theme = currentTheme,
                            modifier = Modifier.weight(1f)
                        ) {
                            val provider = ComponentName(context, ProfileWidget::class.java)
                            appWidgetManager.requestPinAppWidget(provider, null, null)
                        }
                    }
                }
            }

            Text(
                "Как добавить на iPhone: удерживайте палец на главном экране → «+» в углу → найдите «FunPay Tools» → выберите «Баланс» или «Профиль».",
                color = ThemeManager.parseColor(currentTheme.textPrimaryColor),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 8.dp)
            )

            Text(
                "Виджеты обновляются автоматически сервисом или вручную кнопкой выше.",
                color = ThemeManager.parseColor(currentTheme.textSecondaryColor),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun PinWidgetButton(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    theme: AppTheme,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = ThemeManager.parseColor(theme.surfaceColor).copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = ThemeManager.parseColor(theme.accentColor),
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                title,
                color = ThemeManager.parseColor(theme.textPrimaryColor),
                fontWeight = FontWeight.Medium
            )
        }
    }
}


/** iOS: home-screen widgets are a WidgetKit extension fed through [WidgetBridge]. */
class BalanceWidget
class ProfileWidget

/** Hook for the iOS widget extension (shared App Group storage + WidgetCenter reload, set from Swift). */
object WidgetBridge {
    var publisher: ((String) -> Unit)? = null

    fun publish(context: Context) {
        val p = publisher ?: return
        try {
            val profile = WidgetManager.getProfileCache(context)
            val theme = ThemeManager.loadTheme(context)
            val o = JSONObject()
            o.put("username", profile?.username ?: "")
            o.put("balance", profile?.totalBalance ?: "")
            o.put("rating", profile?.rating?.toString() ?: "")
            o.put("reviews", profile?.reviewCount?.toString() ?: "")
            o.put("avatar", profile?.avatarUrl ?: "")
            o.put("activeSales", profile?.activeSales ?: 0)
            o.put("updated", WidgetManager.getLastUpdatedString(context) ?: "")
            o.put("transparency", WidgetManager.getTransparency(context).toDouble())
            o.put("bg", theme.surfaceColor)
            o.put("accent", theme.accentColor)
            o.put("text", theme.textPrimaryColor)
            o.put("text2", theme.textSecondaryColor)
            p(o.toString())
        } catch (e: Exception) {
            LogManager.addLogDebug("WidgetBridge: ${e.message}")
        }
    }
}
