package fr.vriege.anilib.platform.android

import android.content.Context
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewDatabase
import fr.vriege.anilib.platform.compose.BrowserDataClearResult
import fr.vriege.anilib.platform.compose.BrowserDataController

internal class AndroidBrowserDataController(private val context: Context) : BrowserDataController {
    override fun clearData(): BrowserDataClearResult = runCatching {
        WebStorage.getInstance().deleteAllData()
        WebViewDatabase.getInstance(context).clearHttpAuthUsernamePassword()
        WebView.clearClientCertPreferences(null)
        val webView = WebView(context)
        try {
            webView.clearCache(true)
            webView.clearHistory()
            webView.clearFormData()
            webView.clearSslPreferences()
        } finally {
            webView.destroy()
        }
    }.fold(
        onSuccess = {
            BrowserDataClearResult(true, "WebView cache and site storage cleared.")
        },
        onFailure = { failure ->
            BrowserDataClearResult(
                false,
                failure.message ?: "Android WebView data could not be cleared.",
            )
        },
    )
}
