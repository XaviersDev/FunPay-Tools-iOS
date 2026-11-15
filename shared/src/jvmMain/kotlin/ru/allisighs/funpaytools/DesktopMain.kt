package ru.allisighs.funpaytools

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(
        onCloseRequest = { SharedPreferencesImpl.flushAll(); exitApplication() },
        title = "FunPay Tools",
        state = rememberWindowState(size = DpSize(400.dp, 860.dp))
    ) {
        FunPayToolsRoot()
    }
}
