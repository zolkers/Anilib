package fr.vriege.anilib.platform.compose

import com.multiplatform.webview.web.NativeWebView
import com.multiplatform.webview.web.WebContent
import com.multiplatform.webview.web.WebViewFactoryParam
import com.multiplatform.webview.web.defaultWebViewFactory
import dev.datlag.kcef.KCEF
import dev.datlag.kcef.KCEFClient
import java.awt.EventQueue
import java.util.concurrent.ConcurrentHashMap

object OwnedWebViewFactory {
    private const val BLANK_PAGE = "about:blank"
    private val clients = ConcurrentHashMap<NativeWebView, KCEFClient>()

    fun create(parameters: WebViewFactoryParam): NativeWebView {
        val client = KCEF.newClientBlocking()
        return runCatching {
            createBrowser(parameters, client).also { browser -> clients[browser] = client }
        }.getOrElse { failure ->
            client.dispose()
            throw failure
        }
    }

    private fun createBrowser(
        parameters: WebViewFactoryParam,
        client: KCEFClient,
    ): NativeWebView {
        val ownedParameters = WebViewFactoryParam(
            state = parameters.state,
            client = client,
            fileContent = parameters.fileContent,
        )
        return runCatching { defaultWebViewFactory(ownedParameters) }
            .recoverCatching { failure ->
                if (!isUnavailableDedicatedRequestContext(failure)) {
                    throw failure
                }

                // compose-webview 2.0.3 assumes CefRequestContext.createContext() never returns
                // null. JCEF explicitly permits the global context as a browser fallback, so keep
                // the same owned client and settings while avoiding that invalid assertion.
                client.createBrowser(
                    initialUrl(parameters),
                    ownedParameters.rendering,
                    ownedParameters.transparent,
                )
            }.getOrThrow()
    }

    private fun initialUrl(parameters: WebViewFactoryParam): String =
        when (val content = parameters.state.content) {
            is WebContent.Url -> content.url
            is WebContent.Post -> content.url
            else -> BLANK_PAGE
        }.takeIf(String::isNotBlank) ?: BLANK_PAGE

    internal fun isUnavailableDedicatedRequestContext(failure: Throwable): Boolean =
        generateSequence(failure) { it.cause }
            .any { cause ->
                cause is NullPointerException &&
                    cause.message.orEmpty().contains("createContext", ignoreCase = true)
            }

    fun dispose(browser: NativeWebView) {
        browser.dispose()
    }

    fun release(browser: NativeWebView) {
        val client = clients.remove(browser) ?: return
        EventQueue.invokeLater { client.dispose() }
    }
}
