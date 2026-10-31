package fr.vriege.anilib.platform.desktop

import com.sun.net.httpserver.HttpServer
import dev.datlag.kcef.KCEF
import fr.vriege.anilib.platform.compose.DesktopBrowserRuntime
import fr.vriege.anilib.platform.desktopextensionhost.ExtensionPlatformServices
import fr.vriege.anilib.platform.desktopextensionhost.compat.quickjs.QuickJs
import fr.vriege.anilib.platform.desktopextensionhost.compat.android.content.ContextWrapper
import fr.vriege.anilib.platform.desktopextensionhost.compat.android.webkit.WebView
import fr.vriege.anilib.platform.desktopextensionhost.compat.android.webkit.WebViewClient
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

/* Opt-in real Chromium test against a local JavaScript page; no external source or user cache. */
object DesktopExtensionBrowserSmoke {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 1) { "Expected an existing Chromium runtime directory" }
        val runtime = Path.of(args[0]).toAbsolutePath().normalize()
        Files.createDirectories(runtime)
        val cache = Files.createTempDirectory("anilib-extension-chromium-test-")
        val pageLoaded = CompletableFuture<String>()
        val mediaSeen = CountDownLatch(1)
        val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/page") { exchange ->
            val html = """
                <html><body><div id="result">loading</div><script>
                document.querySelector('#result').textContent=String(6*7);
                fetch('/stream.mp4');
                </script></body></html>
            """.trimIndent().toByteArray()
            exchange.responseHeaders.set("Content-Type", "text/html")
            exchange.sendResponseHeaders(200, html.size.toLong())
            exchange.responseBody.use { it.write(html) }
        }
        server.createContext("/stream.mp4") { exchange -> exchange.sendResponseHeaders(204, -1); exchange.close() }
        server.start()
        var failure: Throwable? = null
        try {
            KCEF.initBlocking(
                builder = {
                    installDir(runtime.toFile())
                    download { github { release(DesktopBrowserRuntime.NATIVE_RELEASE) } }
                    settings {
                        DesktopBrowserRuntime.configureNativePaths(this, runtime)
                        cachePath = cache.toString()
                    }
                },
                onError = { failure = it },
                onRestartRequired = { error("Browser runtime requires restart") },
            )
            failure?.let { throw it }
            ExtensionPlatformServices.installBrowser { resource, finished ->
                DesktopExtensionBrowser(resource, finished)
            }
            val browser = DesktopExtensionBrowser(
                Consumer { if (it.endsWith("/stream.mp4")) mediaSeen.countDown() },
                Consumer { pageLoaded.complete(it) },
            )
            browser.use {
                browser.load("http://127.0.0.1:${server.address.port}/page", mapOf("X-Extension-Test" to "yes"))
                pageLoaded.get(30, TimeUnit.SECONDS)
                check(mediaSeen.await(5, TimeUnit.SECONDS)) { "JavaScript resource request was not intercepted" }
                val answer = browser.evaluate("document.querySelector('#result').textContent")
                    .get(5, TimeUnit.SECONDS)
                check(answer == "\"42\"")
            }
            QuickJs.create().use { engine ->
                check(engine.evaluate("var counter=20; counter+1") == 21)
                check(engine.evaluate("counter+2") == 22)
                check(engine.evaluate("'é日本' + String(6*7)") == "é日本42")
            }
            val callback = CompletableFuture<String>()
            val webView = WebView(ContextWrapper())
            try {
                webView.setWebViewClient(object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        view.evaluateJavascript("document.querySelector('#result').textContent") {
                            callback.complete(it)
                        }
                    }
                })
                webView.loadUrl("http://127.0.0.1:${server.address.port}/page")
                check(callback.get(15, TimeUnit.SECONDS) == "\"42\"")
            } finally {
                webView.destroy()
            }
            println("Real Chromium extension smoke: DOM, JavaScript, resource interception and persistence passed")
        } finally {
            server.stop(0)
            KCEF.disposeBlocking()
            // Chromium may hold cache files briefly after disposal; the temporary cache is isolated from user data.
        }
    }
}
