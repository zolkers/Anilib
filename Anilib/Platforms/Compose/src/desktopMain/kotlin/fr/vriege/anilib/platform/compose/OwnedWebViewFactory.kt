package fr.vriege.anilib.platform.compose

import com.multiplatform.webview.web.NativeWebView
import com.multiplatform.webview.web.WebViewFactoryParam
import com.multiplatform.webview.web.defaultWebViewFactory
import dev.datlag.kcef.KCEF
import dev.datlag.kcef.KCEFClient
import java.awt.EventQueue
import java.util.concurrent.ConcurrentHashMap

object OwnedWebViewFactory {
    private val clients = ConcurrentHashMap<NativeWebView, KCEFClient>()

    fun create(parameters: WebViewFactoryParam): NativeWebView {
        val client = KCEF.newClientBlocking()
        return runCatching {
            defaultWebViewFactory(
                WebViewFactoryParam(
                    state = parameters.state,
                    client = client,
                    fileContent = parameters.fileContent,
                ),
            ).also { browser -> clients[browser] = client }
        }.getOrElse { failure ->
            client.dispose()
            throw failure
        }
    }

    fun dispose(browser: NativeWebView) {
        browser.dispose()
    }

    fun release(browser: NativeWebView) {
        val client = clients.remove(browser) ?: return
        EventQueue.invokeLater { client.dispose() }
    }
}
