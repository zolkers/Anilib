package fr.vriege.anilib.platform.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.multiplatform.webview.web.PlatformWebViewParams
import fr.vriege.anilib.feature.settings.BrowserPolicy
import fr.vriege.anilib.platform.compose.BrowserPlatformBridge
import fr.vriege.anilib.platform.compose.BrowserPlatformController
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.callback.CefBeforeDownloadCallback
import org.cef.callback.CefDownloadItem
import org.cef.callback.CefFileDialogCallback
import org.cef.handler.CefDialogHandler
import org.cef.handler.CefDownloadHandlerAdapter
import org.cef.handler.CefLifeSpanHandlerAdapter
import java.nio.file.Files
import java.nio.file.Path
import java.util.Vector

class DesktopBrowserPlatformController : BrowserPlatformController {
    @Composable
    override fun rememberBridge(policy: BrowserPolicy, report: (String) -> Unit): BrowserPlatformBridge =
        remember(policy) {
            BrowserPlatformBridge(PlatformWebViewParams()) { browser ->
                if (!policy.fileChooserEnabled()) {
                    browser.client.addDialogHandler(object : CefDialogHandler {
                        override fun onFileDialog(
                            cefBrowser: CefBrowser,
                            mode: CefDialogHandler.FileDialogMode,
                            title: String,
                            defaultFilePath: String,
                            acceptFilters: Vector<String>,
                            callback: CefFileDialogCallback,
                        ): Boolean {
                            callback.Cancel()
                            report("File chooser blocked by browser settings")
                            return true
                        }
                    })
                }
                browser.client.addLifeSpanHandler(object : CefLifeSpanHandlerAdapter() {
                    override fun onBeforePopup(
                        cefBrowser: CefBrowser,
                        frame: CefFrame,
                        targetUrl: String,
                        targetFrameName: String,
                    ): Boolean {
                        if (policy.popupsEnabled() && targetUrl.startsWith("http")) {
                            browser.loadURL(targetUrl)
                            report("Pop-up opened in the current browser")
                        } else {
                            report("Pop-up blocked by browser settings")
                        }
                        return true
                    }
                })
                browser.client.addDownloadHandler(object : CefDownloadHandlerAdapter() {
                    override fun onBeforeDownload(
                        cefBrowser: CefBrowser,
                        downloadItem: CefDownloadItem,
                        suggestedName: String,
                        callback: CefBeforeDownloadCallback,
                    ) {
                        if (!policy.downloadsEnabled()) {
                            report("Download blocked by browser settings")
                            return
                        }
                        val directory = Path.of(System.getProperty("user.home"), "Downloads")
                            .toAbsolutePath().normalize()
                        Files.createDirectories(directory)
                        val safeName = suggestedName.replace(Regex("[^A-Za-z0-9._ -]"), "_")
                            .ifBlank { "download" }
                        val destination = directory.resolve(safeName).normalize()
                        require(destination.parent == directory) { "Download escaped the desktop folder" }
                        callback.Continue(destination.toString(), false)
                        report("Download handed to $destination")
                    }
                })
            }
        }
}
