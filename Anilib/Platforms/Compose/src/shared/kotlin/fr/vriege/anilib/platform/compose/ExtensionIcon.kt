package fr.vriege.anilib.platform.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.vriege.anilib.framework.http.AnilibHttpClient
import fr.vriege.anilib.framework.http.HttpCachePolicy
import fr.vriege.anilib.framework.http.HttpRequest
import java.net.URI
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class ExtensionIconEnvironment(
    val httpClient: AnilibHttpClient,
    val decode: (ByteArray) -> ImageBitmap?,
)

internal val LocalExtensionIconEnvironment =
    staticCompositionLocalOf<ExtensionIconEnvironment?> { null }

@Composable
internal fun ExtensionIcon(
    iconUri: URI?,
    displayName: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val environment = LocalExtensionIconEnvironment.current
    val description = "$displayName · ${UiTranslations.translate("Extension icon", LocalLanguagePack.current)}"
    val unavailableDescription =
        "$displayName · ${UiTranslations.translate("Extension icon unavailable", LocalLanguagePack.current)}"
    var image by remember(iconUri, environment) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(iconUri, environment) { mutableStateOf(false) }

    LaunchedEffect(iconUri, environment) {
        image = null
        failed = false
        if (iconUri == null || environment == null) return@LaunchedEffect
        runCatching {
            withContext(Dispatchers.IO) {
                val response = environment.httpClient.execute(
                    HttpRequest.builder(iconUri)
                        .cache(HttpCachePolicy.preferCache(Duration.ofDays(7)))
                        .build(),
                )
                check(response.statusCode() in 200..299) {
                    "Extension icon request failed with HTTP ${response.statusCode()}"
                }
                check(response.body().size <= MAX_EXTENSION_ICON_BYTES) {
                    "Extension icon exceeds the 2 MiB limit"
                }
                environment.decode(response.body()) ?: error("Unsupported extension icon format")
            }
        }.onSuccess { image = it }.onFailure { failed = true }
    }

    Card(
        modifier = modifier.size(size),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val loaded = image
            if (loaded != null) {
                Image(
                    bitmap = loaded,
                    contentDescription = description,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.Extension,
                    contentDescription = if (failed) {
                        unavailableDescription
                    } else {
                        description
                    },
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(size * 0.55f),
                )
            }
        }
    }
}

private const val MAX_EXTENSION_ICON_BYTES = 2 * 1024 * 1024
