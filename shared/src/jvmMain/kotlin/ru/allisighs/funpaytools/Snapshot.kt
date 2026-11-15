package ru.allisighs.funpaytools

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.EncodedImageFormat

/** Headless renderer used to eyeball screens on CI/Linux: renders composable into PNG frames. */
object Snapshot {
    fun render(name: String, frames: Int = 120, stepMs: Long = 50, content: @Composable () -> Unit) {
        val scene = ImageComposeScene(width = 1170, height = 2532, density = Density(3f), content = content)
        var t = 0L
        var img = scene.render(t)
        repeat(frames) {
            t += stepMs * 1_000_000
            java.lang.Thread.sleep(stepMs / 5)
            img = scene.render(t)
        }
        val data = img.encodeToData(EncodedImageFormat.PNG)!!
        val out = java.io.File(java.lang.System.getProperty("snap.dir", "/tmp/snaps"), "$name.png")
        out.parentFile.mkdirs()
        out.writeBytes(data.bytes)
        println("saved ${out.absolutePath}")
        scene.close()
    }
}

fun main(args: Array<String>) {
    val which = args.firstOrNull() ?: "root"
    javax.swing.SwingUtilities.invokeAndWait {
        AppBootstrap.init()
        for (w in which.split("+")) {
            if (w == "root") { Snapshot.render("root", frames = 160) { FunPayToolsRoot() }; continue }
            Snapshot.render(w.replace("/", "_"), frames = 100) {
                val theme = androidx.compose.runtime.remember { ThemeManager.loadTheme(AppContext) }
                DesignTokens.theme = theme
                androidx.compose.material3.MaterialTheme(colorScheme = colorSchemeFor(theme)) {
                    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
                        FunPayToolsApp(w, AppBootstrap.repository, theme, pendingChatIdState) { }
                        ToastOverlay()
                    }
                }
            }
        }
    }
    SharedPreferencesImpl.flushAll()
    kotlin.system.exitProcess(0)
}

