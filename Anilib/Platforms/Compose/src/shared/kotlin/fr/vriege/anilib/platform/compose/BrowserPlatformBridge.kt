package fr.vriege.anilib.platform.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.multiplatform.webview.web.NativeWebView
import com.multiplatform.webview.web.PlatformWebViewParams
import fr.vriege.anilib.feature.settings.BrowserPolicy

data class BrowserPlatformBridge(
    val parameters: PlatformWebViewParams?,
    val onCreated: (NativeWebView) -> Unit,
)

interface BrowserPlatformController {
    @Composable
    fun rememberBridge(
        policy: BrowserPolicy,
        report: (String) -> Unit,
    ): BrowserPlatformBridge
}

internal val LocalBrowserPlatformController =
    staticCompositionLocalOf<BrowserPlatformController> {
        object : BrowserPlatformController {
            @Composable
            override fun rememberBridge(
                policy: BrowserPolicy,
                report: (String) -> Unit,
            ) = BrowserPlatformBridge(null) { }
        }
    }
