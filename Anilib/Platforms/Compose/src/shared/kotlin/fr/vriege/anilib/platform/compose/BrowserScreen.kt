package fr.vriege.anilib.platform.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.multiplatform.webview.cookie.Cookie
import com.multiplatform.webview.web.LoadingState
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.rememberWebViewNavigator
import com.multiplatform.webview.web.rememberWebViewState
import fr.vriege.anilib.framework.http.HttpCookieJar
import java.net.URI
import kotlinx.coroutines.launch

/** Shared source browser backed by Android WebView or desktop KCEF. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BrowserScreen(
    uri: URI,
    cookieJar: HttpCookieJar,
    runtimeStatus: BrowserRuntimeStatus,
    close: () -> Unit,
) {
    if (!runtimeStatus.available) {
        BrowserUnavailable(runtimeStatus.message, close)
        return
    }
    val initialHeaders = remember(uri, cookieJar) { browserHeaders(cookieJar, uri) }
    val state = rememberWebViewState(uri.toString(), initialHeaders)
    val navigator = rememberWebViewNavigator()
    val scope = rememberCoroutineScope()
    LaunchedEffect(uri, state.cookieManager) {
        seedCookies(uri, initialHeaders, state.cookieManager)
    }
    val closeBrowser: () -> Unit = {
        scope.launch {
            val loadedUri = runCatching {
                URI.create(state.lastLoadedUrl ?: uri.toString())
            }.getOrDefault(uri)
            syncCookies(loadedUri, state.cookieManager, cookieJar)
            close()
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
            WebView(
                state = state,
                navigator = navigator,
                captureBackPresses = true,
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

private fun browserHeaders(cookieJar: HttpCookieJar, uri: URI): Map<String, String> =
    cookieJar.requestHeaders(uri).mapValues { (_, values) -> values.joinToString("; ") }

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
