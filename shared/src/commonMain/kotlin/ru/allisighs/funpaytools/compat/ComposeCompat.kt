package ru.allisighs.funpaytools

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.savedstate.SavedState
import androidx.savedstate.read

val LocalContext: ProvidableCompositionLocal<Context> = staticCompositionLocalOf { AppContext }

class Configuration {
    var screenWidthDp: Int = 390
    var screenHeightDp: Int = 844
    var orientation: Int = ORIENTATION_PORTRAIT
    var densityDpi: Int = 460
    var fontScale: Float = 1f
    var uiMode: Int = UI_MODE_NIGHT_NO
    var smallestScreenWidthDp: Int = 390
    companion object {
        const val ORIENTATION_PORTRAIT = 1
        const val ORIENTATION_LANDSCAPE = 2
        const val UI_MODE_NIGHT_MASK = 0x30
        const val UI_MODE_NIGHT_YES = 0x20
        const val UI_MODE_NIGHT_NO = 0x10
    }
}

object LocalConfiguration {
    val current: Configuration
        @Composable get() {
            val info = LocalWindowInfo.current
            val density = LocalDensity.current
            val w = with(density) { info.containerSize.width.toDp().value.toInt() }
            val h = with(density) { info.containerSize.height.toDp().value.toInt() }
            return Configuration().apply {
                if (w > 0) screenWidthDp = w
                if (h > 0) screenHeightDp = h
                smallestScreenWidthDp = minOf(screenWidthDp, screenHeightDp)
                orientation = if (screenWidthDp > screenHeightDp) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
                densityDpi = (density.density * 160).toInt()
                fontScale = density.fontScale
            }
        }
}

/** androidx.activity.compose.BackHandler — iOS has no hardware back; handled by edge-swipe via platform. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    androidx.compose.ui.backhandler.BackHandler(enabled, onBack)
}

// ------------------------------------------------------------------------------------------------
// Drawable resources
// ------------------------------------------------------------------------------------------------
val FunPayArrowsVector: ImageVector by lazy {
    ImageVector.Builder("funpay_arrows", 24.dp, 24.dp, 1502f, 1502f).apply {
        addPath(
            pathData = addPathNodes("M 849.55 782.02 C 827.02 753.14 780.90 693.98 747.05 650.56 C 713.20 607.15 667.05 547.97 644.49 519.06 L 603.49 466.50 L 545.23 466.00 L 486.98 465.50 L 520.77 425.50 C 539.36 403.50 598.75 333.19 652.75 269.25 L 750.92 153.01 L 882.95 309.25 L 1014.97 465.50 L 952.99 465.76 L 891.01 466.02 L 890.75 650.28 L 890.50 834.55 Z"),
            fill = SolidColor(Color(0xFF1E88E5))
        )
        addPath(
            pathData = addPathNodes("M 731.89 1326.64 C 721.66 1314.46 677.54 1262.20 633.85 1210.50 C 590.16 1158.80 539.21 1098.50 520.63 1076.50 L 486.85 1036.50 L 549.42 1036.24 L 611.99 1035.98 L 612.25 851.28 L 612.50 666.58 L 723.98 810.54 C 785.29 889.72 849.57 972.73 866.83 995.00 L 898.20 1035.50 L 956.67 1036.00 L 1015.13 1036.50 L 885.46 1190.00 C 814.15 1274.42 754.60 1344.69 753.15 1346.14 L 750.50 1348.79 Z"),
            fill = SolidColor(Color(0xFF1E88E5))
        )
    }.build()
}

@Composable
fun painterResource(id: Int): Painter = rememberVectorPainter(FunPayArrowsVector)

// ------------------------------------------------------------------------------------------------
// Navigation arguments (Bundle-like access on SavedState)
// ------------------------------------------------------------------------------------------------
fun SavedState.getString(key: String): String? = read { if (contains(key)) getStringOrNull(key) else null }
fun SavedState.getString(key: String, def: String): String = getString(key) ?: def
fun SavedState.getInt(key: String, def: Int = 0): Int = read { if (contains(key)) getIntOrNull(key) ?: def else def }
fun SavedState.getLong(key: String, def: Long = 0L): Long = read { if (contains(key)) getLongOrNull(key) ?: def else def }
fun SavedState.getBoolean(key: String, def: Boolean = false): Boolean = read { if (contains(key)) getBooleanOrNull(key) ?: def else def }

// ------------------------------------------------------------------------------------------------
// Activity result API (pickers / documents / permissions)
// ------------------------------------------------------------------------------------------------
class PickVisualMediaRequest(val mediaType: ActivityResultContracts.PickVisualMedia.VisualMediaType = ActivityResultContracts.PickVisualMedia.ImageAndVideo) {
    class Builder {
        private var type: ActivityResultContracts.PickVisualMedia.VisualMediaType = ActivityResultContracts.PickVisualMedia.ImageAndVideo
        fun setMediaType(t: ActivityResultContracts.PickVisualMedia.VisualMediaType) = apply { type = t }
        fun build() = PickVisualMediaRequest(type)
    }
}

fun PickVisualMediaRequest(mediaType: ActivityResultContracts.PickVisualMedia.VisualMediaType, maxItems: Int): PickVisualMediaRequest = PickVisualMediaRequest(mediaType)

sealed class ActivityResultContract<I, O>

object ActivityResultContracts {
    class PickVisualMedia : ActivityResultContract<PickVisualMediaRequest, Uri?>() {
        sealed interface VisualMediaType
        object ImageOnly : VisualMediaType
        object VideoOnly : VisualMediaType
        object ImageAndVideo : VisualMediaType
        class SingleMimeType(val mimeType: String) : VisualMediaType
        companion object {
            val ImageOnly: VisualMediaType get() = PickVisualMedia.ImageOnly
            val VideoOnly: VisualMediaType get() = PickVisualMedia.VideoOnly
            val ImageAndVideo: VisualMediaType get() = PickVisualMedia.ImageAndVideo
            fun isPhotoPickerAvailable(): Boolean = true
            fun isPhotoPickerAvailable(c: Context): Boolean = true
        }
    }
    class PickMultipleVisualMedia(val maxItems: Int = 20) : ActivityResultContract<PickVisualMediaRequest, List<Uri>>()
    class GetContent : ActivityResultContract<String, Uri?>()
    class GetMultipleContents : ActivityResultContract<String, List<Uri>>()
    class OpenDocument : ActivityResultContract<Array<String>, Uri?>()
    class OpenMultipleDocuments : ActivityResultContract<Array<String>, List<Uri>>()
    class CreateDocument(val mimeType: String = "*/*") : ActivityResultContract<String, Uri?>()
    class RequestPermission : ActivityResultContract<String, Boolean>()
    class RequestMultiplePermissions : ActivityResultContract<Array<String>, Map<String, Boolean>>()
    class StartActivityForResult : ActivityResultContract<Intent, ActivityResult>()
    class TakePicturePreview : ActivityResultContract<Unit?, Bitmap?>()
}

class ActivityResult(val resultCode: Int, val data: Intent?)

object ActivityResultCodes {
    const val RESULT_OK = -1
    const val RESULT_CANCELED = 0
}

abstract class ManagedActivityResultLauncher<I> {
    abstract fun launch(input: I)
    fun launch(input: I, options: Any?) = launch(input)
    fun unregister() {}
}

/** Platform pickers. Results are delivered as file:// Uris of copies inside the app sandbox. */
expect object PlatformPickers {
    fun pickImages(multiple: Boolean, onResult: (List<String>) -> Unit)
    fun pickDocument(mimeTypes: List<String>, onResult: (String?) -> Unit)
}

@Suppress("UNCHECKED_CAST")
@Composable
fun <I, O> rememberLauncherForActivityResult(contract: ActivityResultContract<I, O>, onResult: (O) -> Unit): ManagedActivityResultLauncher<I> {
    val cb = rememberUpdatedState(onResult)
    return remember(contract::class) {
        object : ManagedActivityResultLauncher<I>() {
            override fun launch(input: I) {
                val deliver: (Any?) -> Unit = { v -> PlatformBridge.runOnMain { cb.value(v as O) } }
                when (contract) {
                    is ActivityResultContracts.PickVisualMedia -> PlatformPickers.pickImages(false) { deliver(it.firstOrNull()?.let { p -> Uri.parse(p) }) }
                    is ActivityResultContracts.PickMultipleVisualMedia -> PlatformPickers.pickImages(true) { deliver(it.map { p -> Uri.parse(p) }) }
                    is ActivityResultContracts.GetContent -> {
                        val mime = input as? String ?: "*/*"
                        if (mime.startsWith("image/")) PlatformPickers.pickImages(false) { deliver(it.firstOrNull()?.let { p -> Uri.parse(p) }) }
                        else PlatformPickers.pickDocument(listOf(mime)) { deliver(it?.let { p -> Uri.parse(p) }) }
                    }
                    is ActivityResultContracts.GetMultipleContents -> {
                        val mime = input as? String ?: "*/*"
                        if (mime.startsWith("image/")) PlatformPickers.pickImages(true) { deliver(it.map { p -> Uri.parse(p) }) }
                        else PlatformPickers.pickDocument(listOf(mime)) { deliver(listOfNotNull(it?.let { p -> Uri.parse(p) })) }
                    }
                    is ActivityResultContracts.OpenDocument -> {
                        val mimes = (input as? Array<String>)?.toList() ?: listOf("*/*")
                        PlatformPickers.pickDocument(mimes) { deliver(it?.let { p -> Uri.parse(p) }) }
                    }
                    is ActivityResultContracts.OpenMultipleDocuments -> {
                        val mimes = (input as? Array<String>)?.toList() ?: listOf("*/*")
                        PlatformPickers.pickDocument(mimes) { deliver(listOfNotNull(it?.let { p -> Uri.parse(p) })) }
                    }
                    is ActivityResultContracts.CreateDocument -> {
                        val name = (input as? String)?.ifBlank { null } ?: "export"
                        val f = File(File(PlatformBridge.cacheDir, "exports"), name)
                        f.parentFile?.mkdirs()
                        PendingExports.register(f, ExportKind.FILE)
                        deliver(Uri.fromFile(f))
                    }
                    is ActivityResultContracts.RequestPermission -> {
                        val p = input as? String ?: ""
                        if (p.contains("NOTIFICATIONS")) PlatformNotifications.requestPermission { deliver(it) } else deliver(true)
                    }
                    is ActivityResultContracts.RequestMultiplePermissions -> {
                        val ps = (input as? Array<String>)?.toList() ?: emptyList()
                        if (ps.any { it.contains("NOTIFICATIONS") }) PlatformNotifications.requestPermission { ok -> deliver(ps.associateWith { if (it.contains("NOTIFICATIONS")) ok else true }) }
                        else deliver(ps.associateWith { true })
                    }
                    is ActivityResultContracts.StartActivityForResult -> {
                        (input as? Intent)?.let { IntentRouter.startActivity(it) }
                        deliver(ActivityResult(ActivityResultCodes.RESULT_CANCELED, null))
                    }
                    is ActivityResultContracts.TakePicturePreview -> deliver(null)
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// Misc compose helpers that exist only on Android
// ------------------------------------------------------------------------------------------------

@Composable
fun isSystemInDarkThemeCompat(): Boolean = androidx.compose.foundation.isSystemInDarkTheme()

object HapticCompat
