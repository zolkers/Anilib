package fr.vriege.anilib.platform.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import fr.vriege.anilib.framework.http.HttpCachePolicy
import fr.vriege.anilib.framework.http.HttpRequest
import java.net.URI
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun RemoteArtwork(
    uri: URI?,
    title: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val environment = LocalExtensionIconEnvironment.current
    var image by remember(uri, environment) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(uri, environment) { mutableStateOf(false) }
    CrashSafeLaunchedEffect(uri, environment) {
        image = null
        failed = false
        if (uri == null || environment == null) return@CrashSafeLaunchedEffect
        runCatching {
            withContext(Dispatchers.IO) {
                val response = environment.httpClient.execute(
                    HttpRequest.builder(uri)
                        .cache(HttpCachePolicy.preferCache(Duration.ofDays(7)))
                        .build(),
                )
                check(response.statusCode() in 200..299)
                check(response.body().size <= MAX_ARTWORK_BYTES)
                environment.decode(response.body()) ?: error("Unsupported artwork format")
            }
        }.onSuccess { image = it }.onFailure { failed = true }
    }
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        image?.let {
            Image(
                bitmap = it,
                contentDescription = "$title cover",
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        } ?: Icon(
            Icons.Outlined.Image,
            contentDescription = if (failed) "$title cover unavailable" else "$title cover",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val MAX_ARTWORK_BYTES = 8 * 1024 * 1024
