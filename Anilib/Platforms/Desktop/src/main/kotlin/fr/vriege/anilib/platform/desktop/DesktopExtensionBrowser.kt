package fr.vriege.anilib.platform.desktop

import dev.datlag.kcef.KCEF
import dev.datlag.kcef.KCEFFrame
import dev.datlag.kcef.KCEFBrowser
import fr.vriege.anilib.platform.compose.DesktopBrowserRuntime
import fr.vriege.anilib.platform.desktopextensionhost.ExtensionBrowser
import fr.vriege.anilib.platform.desktopextensionhost.ExtensionPlatformServices
import fr.vriege.anilib.platform.desktopextensionhost.ExtensionScriptValues
import fr.vriege.anilib.platform.desktopextensionhost.compat.android.webkit.CookieManager
import org.cef.CefApp
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.browser.CefRendering
import org.cef.handler.CefLifeSpanHandlerAdapter
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.handler.CefRequestHandler
import org.cef.handler.CefResourceRequestHandlerAdapter
import org.cef.misc.BoolRef
import org.cef.network.CefRequest
import org.cef.network.CefResponse
import java.awt.EventQueue
import java.awt.Frame
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

/* Real Chromium sessions for extension WebViews, sharing the app's owned runtime. */
internal class DesktopExtensionBrowser(
    resource: Consumer<String>,
    finished: Consumer<String>,
) : ExtensionBrowser {
    private val client = KCEF.newClientBlocking()
    private val ready = CompletableFuture<Unit>()
    private val closed = AtomicBoolean()
    private val pending = ConcurrentHashMap.newKeySet<CompletableFuture<String>>()
    private val browser: KCEFBrowser
    private var hostWindow: Frame? = null
    private val lifetime = CompletableFuture<Unit>()

    init {
        client.addLifeSpanHandler(object : CefLifeSpanHandlerAdapter() {
            override fun onAfterCreated(browser: CefBrowser) { ready.complete(Unit) }
            override fun onBeforePopup(browser: CefBrowser, frame: CefFrame, url: String, name: String): Boolean = true
            override fun onBeforeClose(browser: CefBrowser) {
                closed.set(true)
                lifetime.complete(Unit)
                failPending(IllegalStateException("Extension browser closed"))
                EventQueue.invokeLater {
                    hostWindow?.dispose()
                    hostWindow = null
                }
            }
        })
        client.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(browser: CefBrowser, frame: CefFrame, status: Int) {
                if (frame.isMain && !closed.get() && browser.url != "about:blank") finished.accept(browser.url)
            }
        })
        client.addRequestHandler(object : CefRequestHandlerAdapter() {
            override fun onRenderProcessTerminated(browser: CefBrowser, status: CefRequestHandler.TerminationStatus) {
                failPending(IllegalStateException("Extension browser renderer terminated: $status"))
                close()
            }
            override fun getResourceRequestHandler(
                browser: CefBrowser, frame: CefFrame, request: CefRequest,
                navigation: Boolean, download: Boolean, initiator: String, disable: BoolRef,
            ) = object : CefResourceRequestHandlerAdapter() {
                override fun onBeforeResourceLoad(browser: CefBrowser, frame: CefFrame, request: CefRequest): Boolean {
                    if (closed.get()) return true
                    CookieManager.getInstance().getCookie(request.url)?.let {
                        request.setHeaderByName("Cookie", it, true)
                    }
                    if (request.url.startsWith("https://") || request.url.startsWith("http://")) {
                        resource.accept(request.url)
                    }
                    return false
                }
                override fun onResourceResponse(
                    browser: CefBrowser, frame: CefFrame, request: CefRequest, response: CefResponse,
                ): Boolean {
                    val headers = mutableMapOf<String, String>()
                    response.getHeaderMap(headers)
                    headers.filterKeys { it.equals("Set-Cookie", true) }.values.forEach {
                        CookieManager.getInstance().setCookie(request.url, it)
                    }
                    return false
                }
            }
        })
        browser = client.createBrowser("about:blank", CefRendering.DEFAULT, false)
        lifetime.orTimeout(60, TimeUnit.SECONDS).whenComplete { _, failure -> if (failure != null) close() }
        ready.orTimeout(20, TimeUnit.SECONDS).whenComplete { _, failure -> if (failure != null) close() }
        EventQueue.invokeLater {
            if (!closed.get()) {
                browser.uiComponent.setSize(800, 600)
                hostWindow = Frame().apply {
                    isUndecorated = true
                    add(browser.uiComponent)
                    setSize(800, 600)
                    addNotify()
                }
                browser.createImmediately()
            }
        }
    }

    override fun load(url: String, headers: Map<String, String>) {
        check(!closed.get()) { "Extension browser is closed" }
        ready.thenRun {
            EventQueue.invokeLater {
                if (!closed.get()) {
                    val request = CefRequest.create()
                    request.url = url
                    request.method = "GET"
                    request.setHeaderMap(headers)
                    browser.loadRequest(request)
                }
            }
        }
    }

    override fun evaluate(script: String): CompletableFuture<String> {
        check(!closed.get()) { "Extension browser is closed" }
        require(script.length <= 4 * 1024 * 1024) { "Extension script exceeds 4 MiB" }
        val result = CompletableFuture<String>().orTimeout(15, TimeUnit.SECONDS)
        pending.add(result)
        result.whenComplete { _, failure ->
            pending.remove(result)
            if (failure != null) close()
        }
        ready.whenComplete { _, failure ->
            if (failure != null) result.completeExceptionally(failure)
            else EventQueue.invokeLater {
                if (closed.get()) result.completeExceptionally(IllegalStateException("Extension browser is closed"))
                else runCatching {
                    val expression = """
                        return (function(){try{const value=(0,eval)(${ExtensionScriptValues.quote(script)});
                        return "ok:"+(JSON.stringify(value)??"null");}catch(error){return "error:"+String(error);}})()
                    """.trimIndent()
                    browser.evaluateJavaScript(expression, KCEFFrame.EvaluateJavascriptCallback {
                        if (it != null && it.startsWith("ok:")) result.complete(it.removePrefix("ok:"))
                        else result.completeExceptionally(IllegalStateException(it ?: "JavaScript returned no result"))
                    })
                }.onFailure { result.completeExceptionally(it) }
            }
        }
        return result
    }

    override fun stop() { EventQueue.invokeLater { if (!closed.get()) browser.stopLoad() } }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        lifetime.complete(Unit)
        failPending(IllegalStateException("Extension browser is closed"))
        EventQueue.invokeLater {
            // Disposing the owner closes its browsers. Do not dispose it again from onBeforeClose;
            // that races with KCEF shutdown and can free the native client twice.
            CefApp.getInstanceIfAny()?.let { app ->
                synchronized(app) {
                    if (CefApp.getState() == CefApp.CefAppState.INITIALIZED) client.dispose()
                }
            }
        }
    }

    private fun failPending(failure: Throwable) {
        ready.completeExceptionally(failure)
        pending.toList().forEach { it.completeExceptionally(failure) }
    }

    companion object {
        fun install(dataDirectory: Path) {
            ExtensionPlatformServices.installBrowser { resource, finished ->
                val status = DesktopBrowserRuntime.initialize(dataDirectory)
                check(status.available) { status.message }
                DesktopExtensionBrowser(resource, finished)
            }
        }
    }
}
