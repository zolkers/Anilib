package fr.vriege.anilib.platform.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.multiplatform.webview.cookie.Cookie
import com.multiplatform.webview.web.LoadingState
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.rememberWebViewNavigator
import com.multiplatform.webview.web.rememberWebViewState
import fr.vriege.anilib.framework.http.HttpCookieJar
import fr.vriege.anilib.feature.source.SourceWebPage
import fr.vriege.anilib.feature.settings.BrowserPolicy
import java.net.URI
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

internal val LocalBrowserPolicy = staticCompositionLocalOf { BrowserPolicy.defaults() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BrowserScreen(
    page: SourceWebPage,
    cookieJar: HttpCookieJar,
    runtimeStatus: BrowserRuntimeStatus,
    close: () -> Unit,
    challengeComplete: () -> Unit = close,
) {
    if (!runtimeStatus.available) {
        BrowserUnavailable(runtimeStatus.message, close)
        return
    }
    val uri = page.location()
    val policy = LocalBrowserPolicy.current
    val platformController = LocalBrowserPlatformController.current
    val initialHeaders = remember(page, cookieJar) { browserHeaders(cookieJar, page) }
    val state = rememberWebViewState(uri.toString(), initialHeaders) {
        customUserAgentString = page.userAgent().orElse(null)
        isJavaScriptEnabled = policy.javaScriptEnabled()
        androidWebSettings.domStorageEnabled = policy.domStorageEnabled()
        androidWebSettings.textZoom = policy.textZoomPercent()
        desktopWebSettings.disablePopupWindows = !policy.popupsEnabled()
    }
    val navigator = rememberWebViewNavigator()
    val scope = rememberCoroutineScope()
    var challengeSolved by remember(page) { mutableStateOf(page.completionCookies().isEmpty()) }
    var challengeChecked by remember(page) { mutableStateOf(false) }
    var platformMessage by remember(page) { mutableStateOf<String?>(null) }
    val platformBridge = platformController.rememberBridge(policy) { platformMessage = it }
    LaunchedEffect(uri, state.cookieManager) {
        seedCookies(uri, initialHeaders, state.cookieManager)
    }
    LaunchedEffect(state.loadingState, policy.automaticChallengeRetry()) {
        if (state.loadingState is LoadingState.Finished
            && policy.automaticChallengeRetry()
            && page.completionCookies().isNotEmpty()
            && !challengeSolved
        ) {
            delay(400)
            val loadedUri = currentWebUri(state.lastLoadedUrl, uri)
            val complete = runCatching {
                challengeCookiesPresent(
                    listOf(uri, loadedUri),
                    state.cookieManager,
                    page.completionCookies(),
                )
            }.getOrDefault(false)
            if (complete) {
                challengeSolved = true
                runCatching { syncCookies(uri, state.cookieManager, cookieJar) }
                if (loadedUri != uri) {
                    runCatching { syncCookies(loadedUri, state.cookieManager, cookieJar) }
                }
                challengeComplete()
            }
        }
    }
    val closeBrowser: () -> Unit = {
        scope.launch {
            val loadedUri = currentWebUri(state.lastLoadedUrl, uri)
            try {
                runCatching { syncCookies(uri, state.cookieManager, cookieJar) }
                if (loadedUri != uri) {
                    runCatching { syncCookies(loadedUri, state.cookieManager, cookieJar) }
                }
            } finally {
                close()
            }
        }
    }
    val checkChallenge: () -> Unit = {
        scope.launch {
            val loadedUri = currentWebUri(state.lastLoadedUrl, uri)
            challengeSolved = runCatching {
                challengeCookiesPresent(
                    listOf(uri, loadedUri),
                    state.cookieManager,
                    page.completionCookies(),
                )
            }.getOrDefault(false)
            challengeChecked = true
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(state.pageTitle?.takeIf(String::isNotBlank) ?: uri.host)
                },
                navigationIcon = {
                    IconButton(onClick = closeBrowser) {
                        Icon(Icons.Default.Close, contentDescription = "Close browser")
                    }
                },
                actions = {
                    IconButton(onClick = navigator::navigateBack, enabled = navigator.canGoBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous page")
                    }
                    IconButton(onClick = navigator::navigateForward, enabled = navigator.canGoForward) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next page")
                    }
                    IconButton(onClick = navigator::reload) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload")
                    }
                    if (page.completionCookies().isNotEmpty()) {
                        IconButton(onClick = checkChallenge) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Check web challenge")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            val loading = state.loadingState
            if (loading is LoadingState.Loading) {
                LinearProgressIndicator(
                    progress = { loading.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (page.completionCookies().isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = when {
                            challengeSolved -> "Challenge complete. Close to retry the source."
                            challengeChecked -> "Challenge cookie not found yet."
                            else -> "Complete the website challenge, then check it."
                        },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = checkChallenge) {
                        Text("Check")
                    }
                }
            }
            platformMessage?.let { message ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(message, modifier = Modifier.weight(1f))
                    TextButton(onClick = { platformMessage = null }) { Text("Dismiss") }
                }
            }
            WebView(
                state = state,
                navigator = navigator,
                captureBackPresses = true,
                platformWebViewParams = platformBridge.parameters,
                onCreated = platformBridge.onCreated,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowserUnavailable(message: String, close: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WebView unavailable") },
                navigationIcon = {
                    IconButton(onClick = close) {
                        Icon(Icons.Default.Close, contentDescription = "Close browser")
                    }
                },
            )
        },
    ) { padding ->
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            Text(message)
        }
    }
}

private fun browserHeaders(cookieJar: HttpCookieJar, page: SourceWebPage): Map<String, String> = buildMap {
    putAll(page.headers())
    cookieJar.requestHeaders(page.location()).forEach { (name, values) ->
        put(name, values.joinToString("; "))
    }
}

private fun currentWebUri(value: String?, fallback: URI): URI {
    val uri = runCatching { URI.create(value ?: fallback.toString()) }.getOrDefault(fallback)
    return if (uri.scheme.equals("http", ignoreCase = true)
        || uri.scheme.equals("https", ignoreCase = true)
    ) {
        uri
    } else {
        fallback
    }
}

private suspend fun challengeCookiesPresent(
    locations: List<URI>,
    manager: com.multiplatform.webview.cookie.CookieManager,
    expectedNames: Set<String>,
): Boolean {
    val cookieNames = locations.distinct()
        .flatMap { manager.getCookies(it.toString()) }
        .map { it.name.lowercase() }
        .toSet()
    return expectedNames.all { it.lowercase() in cookieNames }
}

private suspend fun seedCookies(
    uri: URI,
    headers: Map<String, String>,
    manager: com.multiplatform.webview.cookie.CookieManager,
) {
    headers.entries
        .filter { it.key.equals("Cookie", ignoreCase = true) }
        .flatMap { it.value.split(';') }
        .map(String::trim)
        .filter(String::isNotEmpty)
        .forEach { pair ->
            val separator = pair.indexOf('=')
            if (separator > 0) {
                manager.setCookie(
                    uri.toString(),
                    Cookie(
                        name = pair.substring(0, separator),
                        value = pair.substring(separator + 1),
                        domain = uri.host,
                        path = "/",
                        isSecure = uri.scheme.equals("https", ignoreCase = true),
                    ),
                )
            }
        }
}

private suspend fun syncCookies(
    uri: URI,
    manager: com.multiplatform.webview.cookie.CookieManager,
    cookieJar: HttpCookieJar,
) {
    if (!uri.scheme.equals("http", ignoreCase = true)
        && !uri.scheme.equals("https", ignoreCase = true)
    ) {
        return
    }
    val cookies = manager.getCookies(uri.toString())
    if (cookies.isNotEmpty()) {
        cookieJar.store(uri, mapOf("Set-Cookie" to cookies.map(Cookie::toString)))
    }
}
