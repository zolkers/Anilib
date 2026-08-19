package fr.vriege.anilib.platform.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

internal val mediaDateTimeFormatter: DateTimeFormatter = DateTimeFormatter
    .ofLocalizedDateTime(FormatStyle.MEDIUM)
    .withLocale(Locale.getDefault())
    .withZone(ZoneId.systemDefault())

internal data class MediaDetailsUiModel(
    val title: String,
    val authors: List<String>,
    val status: String,
    val sourceName: String,
    val description: String,
    val genres: List<String>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MediaDetailsScreen(
    model: MediaDetailsUiModel,
    artwork: @Composable (Modifier) -> Unit,
    favorite: Boolean,
    contentLabel: String,
    canTrack: Boolean,
    canOpenWeb: Boolean,
    canDownload: Boolean,
    primaryLabel: String,
    canOpenPrimary: Boolean,
    errors: List<String>,
    toggleFavorite: () -> Unit,
    track: () -> Unit,
    openWeb: () -> Unit,
    download: () -> Unit,
    share: () -> Unit,
    edit: (() -> Unit)?,
    openPrimary: () -> Unit,
    goBack: () -> Unit,
    content: LazyListScope.() -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(model.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = goBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    edit?.let { action ->
                        IconButton(onClick = action) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                    }
                    IconButton(onClick = share) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                    IconButton(onClick = download, enabled = canDownload) {
                        Icon(Icons.Outlined.Download, contentDescription = "Download")
                    }
                },
            )
        },
        floatingActionButton = {
            if (canOpenPrimary) {
                ExtendedFloatingActionButton(
                    onClick = openPrimary,
                    icon = { Icon(Icons.Default.PlayArrow, contentDescription = primaryLabel) },
                    text = { Text(primaryLabel) },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 104.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item { MediaDetailsHero(model, artwork) }
            item {
                MediaDetailsActions(
                    favorite = favorite,
                    contentLabel = contentLabel,
                    canTrack = canTrack,
                    canOpenWeb = canOpenWeb,
                    toggleFavorite = toggleFavorite,
                    track = track,
                    openWeb = openWeb,
                )
            }
            item {
                Column(Modifier.widthIn(max = 900.dp).fillMaxWidth()) {
                    Text(
                        model.description.ifBlank { "No description available." },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (model.genres.isNotEmpty()) {
                        MediaGenreChips(model.genres)
                    }
                }
            }
            errors.filter(String::isNotBlank).forEach { message ->
                item { Text(message, color = MaterialTheme.colorScheme.error) }
            }
            content()
        }
    }
}

@Composable
private fun MediaDetailsHero(
    model: MediaDetailsUiModel,
    artwork: @Composable (Modifier) -> Unit,
) {
    BoxWithConstraints(Modifier.widthIn(max = 900.dp).fillMaxWidth().padding(top = 8.dp, bottom = 12.dp)) {
        val coverWidth = if (maxWidth < 600.dp) 112.dp else 156.dp
        val coverHeight = if (maxWidth < 600.dp) 168.dp else 232.dp
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            artwork(
                Modifier.width(coverWidth).height(coverHeight)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(model.title, style = MaterialTheme.typography.headlineSmall)
                Text(
                    model.authors.firstOrNull()?.let { "By $it" } ?: "Unknown author",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${model.status} · ${model.sourceName}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MediaDetailsActions(
    favorite: Boolean,
    contentLabel: String,
    canTrack: Boolean,
    canOpenWeb: Boolean,
    toggleFavorite: () -> Unit,
    track: () -> Unit,
    openWeb: () -> Unit,
) {
    Row(
        modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        MediaDetailAction(
            if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            if (favorite) "In Library" else "Favorite",
            true,
            toggleFavorite,
        )
        MediaDetailAction(Icons.Default.History, contentLabel, false) {}
        MediaDetailAction(Icons.Default.MoreHoriz, "Tracking", canTrack, track)
        MediaDetailAction(Icons.Default.Public, "WebView", canOpenWeb, openWeb)
    }
}

@Composable
private fun MediaDetailAction(icon: ImageVector, label: String, enabled: Boolean, action: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(92.dp)) {
        IconButton(onClick = action, enabled = enabled) {
            Icon(icon, contentDescription = label)
        }
        Text(
            label,
            modifier = Modifier.fillMaxWidth().heightIn(min = 32.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MediaGenreChips(genres: List<String>) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        genres.forEach { genre ->
            FilterChip(selected = false, onClick = {}, label = { Text(genre) })
        }
    }
}

@Composable
internal fun MediaContentHeading(label: String) {
    Text(
        label,
        modifier = Modifier.widthIn(max = 900.dp).fillMaxWidth().padding(top = 20.dp, bottom = 8.dp),
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
    )
}

@Composable
internal fun MediaUnitRow(
    title: String,
    summary: String,
    open: () -> Unit,
    download: () -> Unit,
    muted: Boolean = false,
) {
    Row(
        modifier = Modifier
            .widthIn(max = 900.dp)
            .fillMaxWidth()
            .clickable(onClick = open)
            .alpha(if (muted) 0.5f else 1f)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = download) {
            Icon(Icons.Outlined.Download, contentDescription = "Download $title")
        }
    }
    HorizontalDivider(modifier = Modifier.widthIn(max = 900.dp).fillMaxWidth())
}
