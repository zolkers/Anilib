package fr.vriege.anilib.platform.android

import android.app.DownloadManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.os.Message
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.multiplatform.webview.web.AccompanistWebChromeClient
import com.multiplatform.webview.web.PlatformWebViewParams
import fr.vriege.anilib.feature.settings.BrowserPolicy
import fr.vriege.anilib.platform.compose.BrowserPlatformBridge
import fr.vriege.anilib.platform.compose.BrowserPlatformController

class AndroidBrowserPlatformController : BrowserPlatformController {
    @Composable
    override fun rememberBridge(policy: BrowserPolicy, report: (String) -> Unit): BrowserPlatformBridge {
        val pendingFiles = remember { arrayOfNulls<ValueCallback<Array<Uri>>>(1) }
        val chooser = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            pendingFiles[0]?.onReceiveValue(uris.toTypedArray())
            pendingFiles[0] = null
        }
        DisposableEffect(Unit) {
            onDispose {
                pendingFiles[0]?.onReceiveValue(null)
                pendingFiles[0] = null
            }
        }
        val chromeClient = remember(policy) {
            object : AccompanistWebChromeClient() {
                override fun onShowFileChooser(
                    webView: WebView,
                    filePathCallback: ValueCallback<Array<Uri>>,
                    fileChooserParams: WebChromeClient.FileChooserParams,
                ): Boolean {
                    if (!policy.fileChooserEnabled()) {
                        filePathCallback.onReceiveValue(null)
                        report("File chooser blocked by browser settings")
                        return true
                    }
                    pendingFiles[0]?.onReceiveValue(null)
                    pendingFiles[0] = filePathCallback
                    val types = fileChooserParams.acceptTypes.filter(String::isNotBlank)
                        .ifEmpty { listOf("*/*") }
                    chooser.launch(types.toTypedArray())
                    return true
                }

                override fun onCreateWindow(
                    view: WebView,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: Message,
                ): Boolean {
                    if (!policy.popupsEnabled()) {
                        report("Pop-up blocked by browser settings")
                        return false
                    }
                    val popup = WebView(view.context)
                    popup.webViewClient = object : WebViewClient() {
                        var forwarded = false

                        override fun shouldOverrideUrlLoading(
                            popupView: WebView,
                            request: WebResourceRequest,
                        ): Boolean = forward(popupView, request.url.toString())

                        override fun onPageStarted(popupView: WebView, url: String, favicon: Bitmap?) {
                            if (url != "about:blank") {
                                forward(popupView, url)
                            }
                        }

                        private fun forward(popupView: WebView, url: String): Boolean {
                            if (!forwarded) {
                                forwarded = true
                                view.loadUrl(url)
                                popupView.destroy()
                                report("Pop-up opened in the current browser")
                            }
                            return true
                        }
                    }
                    val transport = resultMsg.obj as WebView.WebViewTransport
                    transport.webView = popup
                    resultMsg.sendToTarget()
                    return true
                }
            }
        }
        return remember(policy, chromeClient) {
            BrowserPlatformBridge(PlatformWebViewParams(chromeClient = chromeClient)) { webView ->
                webView.settings.setSupportMultipleWindows(policy.popupsEnabled())
                webView.settings.javaScriptCanOpenWindowsAutomatically = policy.popupsEnabled()
                webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                    if (!policy.downloadsEnabled()) {
                        report("Download blocked by browser settings")
                        return@setDownloadListener
                    }
                    runCatching {
                        val name = URLUtil.guessFileName(url, contentDisposition, mimeType)
                        val request = DownloadManager.Request(Uri.parse(url))
                            .setMimeType(mimeType)
                            .setTitle(name)
                            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                            .setDestinationInExternalFilesDir(
                                webView.context,
                                Environment.DIRECTORY_DOWNLOADS,
                                name,
                            )
                        CookieManager.getInstance().getCookie(url)?.let { request.addRequestHeader("Cookie", it) }
                        userAgent?.let { request.addRequestHeader("User-Agent", it) }
                        val manager = webView.context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                        manager.enqueue(request)
                    }.onSuccess {
                        report("Download handed to Android")
                    }.onFailure {
                        report(it.message ?: "Android download hand-off failed")
                    }
                }
            }
        }
    }
}
