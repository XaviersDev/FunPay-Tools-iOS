package ru.allisighs.funpaytools

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Saves crash details so the next launch can show them (testers have no Xcode). */
object CrashReport {
    private val file: File get() = File(PlatformBridge.filesDir, "last_crash.txt")

    fun save(kind: String, text: String) {
        try {
            val sb = StringBuilder()
            sb.append("FunPay Tools iOS ").append(APP_VERSION).append(" | ").append(PlatformBridge.deviceModel)
                .append(" iOS ").append(PlatformBridge.osVersion).append('\n')
            sb.append(SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.US).format(Date())).append(" | ").append(kind).append("\n\n")
            sb.append(text.take(20000))
            file.writeText(sb.toString())
        } catch (_: Throwable) {}
    }

    fun save(t: Throwable) = save("Kotlin exception", t.stackTraceToString())

    fun pending(): String? = try { if (file.exists()) file.readText() else null } catch (_: Throwable) { null }
    fun clear() { try { file.delete() } catch (_: Throwable) {} }
}

@Composable
fun CrashReportDialog() {
    var text by remember { mutableStateOf(CrashReport.pending()) }
    val t = text ?: return
    AlertDialog(
        onDismissRequest = { CrashReport.clear(); text = null },
        title = { Text("Приложение вылетело в прошлый раз") },
        text = {
            Column {
                Text("Скопируйте отчёт и отправьте разработчику, это поможет быстро исправить ошибку.", fontSize = 13.sp)
                SelectionContainer {
                    Text(
                        t,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                PlatformBridge.setClipboard(t)
                Toast.makeText(AppContext, "Отчёт скопирован", Toast.LENGTH_SHORT).show()
                CrashReport.clear(); text = null
            }) { Text("Скопировать") }
        },
        dismissButton = {
            TextButton(onClick = { PlatformBridge.shareText(t, "Crash report"); CrashReport.clear(); text = null }) { Text("Поделиться") }
        }
    )
}
