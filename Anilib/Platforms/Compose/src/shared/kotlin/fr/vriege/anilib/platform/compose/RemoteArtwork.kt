package fr.vriege.anilib.platform.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import java.net.URI

@Composable
internal fun RemoteArtwork(
    uri: URI?,
    title: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val environment = LocalExtensionIconEnvironment.current
    val cacheKey = remember(uri, environment) {
        if (uri == null || environment == null) null else remoteImageCacheKey("artwork", uri, environment)
    }
    var image by remember(cacheKey) { mutableStateOf(cacheKey?.let(RemoteImageCache::get)) }
    var failed by remember(cacheKey) { mutableStateOf(false) }
    var retryRevision by remember(cacheKey) { mutableStateOf(0) }
    DisposableEffect(cacheKey) {
        cacheKey?.let(RemoteImageCache::acquire)
        onDispose { cacheKey?.let(RemoteImageCache::release) }
    }
    CrashSafeLaunchedEffect(cacheKey, retryRevision) {
        failed = false
        if (cacheKey == null || uri == null || environment == null || image != null) {
            return@CrashSafeLaunchedEffect
        }
        RemoteImageCache.load(cacheKey) {
            loadRemoteImage(
                environment = environment,
                purpose = "artwork",
                uri = uri,
                maximumBytes = MAX_ARTWORK_BYTES,
                forceRefresh = retryRevision > 0,
            )
        }.onSuccess { image = it }.onFailure { failed = true }
    }
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        image?.let {
            Image(
                bitmap = it,
                contentDescription = UiTranslations.format(
                    "dynamic.title.cover",
                    LocalLanguagePack.current,
                    title,
                ),
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        } ?: if (failed) {
            IconButton(
                onClick = {
                    failed = false
                    retryRevision += 1
                },
            ) {
                Icon(
                    Icons.Outlined.Refresh,
                    contentDescription = "ui.retry",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Icon(
                Icons.Outlined.Image,
                contentDescription = UiTranslations.format(
                    "dynamic.title.cover",
                    LocalLanguagePack.current,
                    title,
                ),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val MAX_ARTWORK_BYTES = 8 * 1024 * 1024
