package fr.vriege.anilib.platform.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
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
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import fr.vriege.anilib.feature.tracker.TrackerAiringSchedule
import androidx.compose.foundation.shape.RoundedCornerShape

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
    val nextAiring: TrackerAiringSchedule? = null,
)

internal data class MediaUnitSelectionUiModel(
    val selecting: Boolean,
    val selectedKeys: Set<String>,
    val positiveLabel: String,
    val negativeLabel: String,
    val toggle: () -> Unit,
    val selectAll: () -> Unit,
    val deselectAll: () -> Unit,
    val select: (String) -> Unit,
    val markPositive: () -> Unit,
    val markNegative: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MediaDetailsScreen(
    model: MediaDetailsUiModel,
    artwork: @Composable (Modifier) -> Unit,
    inLibrary: Boolean,
    contentLabel: String,
    canTrack: Boolean,
    trackingCount: Int,
    canOpenWeb: Boolean,
    canDownload: Boolean,
    downloadProgress: DownloadUiProgress?,
    canShare: Boolean,
    primaryLabel: String,
    canOpenPrimary: Boolean,
    errors: List<String>,
    toggleLibraryMembership: () -> Unit,
    refreshing: Boolean,
    refresh: (() -> Unit)?,
    track: () -> Unit,
    openWeb: () -> Unit,
    download: () -> Unit,
    share: () -> Unit,
    manageCategories: (() -> Unit)?,
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ui.back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { manageCategories?.invoke() },
                        enabled = manageCategories != null,
                    ) {
                        Icon(
                            Icons.Outlined.Category,
                            contentDescription = "ui.categories",
                        )
                    }
                    IconButton(
                        onClick = { edit?.invoke() },
                        enabled = edit != null,
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "ui.edit")
                    }
                    IconButton(onClick = share, enabled = canShare) {
                        Icon(Icons.Default.Share, contentDescription = "ui.share")
                    }
                    DownloadActionButton(
                        progress = downloadProgress,
                        enabled = canDownload,
                        action = download,
                        contentDescription = "ui.download",
                    )
                    refresh?.let { action ->
                        IconButton(onClick = action, enabled = !refreshing) {
                            if (refreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "ui.refresh")
                            }
                        }
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
                    inLibrary = inLibrary,
                    contentLabel = contentLabel,
                    canTrack = canTrack,
                    trackingCount = trackingCount,
                    canOpenWeb = canOpenWeb,
                    toggleLibraryMembership = toggleLibraryMembership,
                    track = track,
                    openWeb = openWeb,
                )
            }
            model.nextAiring?.let { schedule ->
                item {
                    NextAiringBanner(
                        schedule,
                        Modifier.widthIn(max = 900.dp).padding(vertical = 8.dp),
                    )
                }
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
                    .clip(RoundedCornerShape(8.dp)),
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
    inLibrary: Boolean,
    contentLabel: String,
    canTrack: Boolean,
    trackingCount: Int,
    canOpenWeb: Boolean,
    toggleLibraryMembership: () -> Unit,
    track: () -> Unit,
    openWeb: () -> Unit,
) {
    Row(
        modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        MediaDetailAction(
            icon = if (inLibrary) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            label = if (inLibrary) "ui.remove.from.library" else "ui.add.to.library",
            enabled = true,
            action = toggleLibraryMembership,
            modifier = Modifier.weight(1f),
        )
        MediaDetailAction(Icons.Default.History, contentLabel, false, {}, Modifier.weight(1f))
        MediaDetailAction(
            icon = if (trackingCount > 0) Icons.Default.CheckCircle else Icons.Outlined.Sync,
            label = "ui.tracking",
            enabled = canTrack,
            action = track,
            modifier = Modifier.weight(1f),
            selected = trackingCount > 0,
        )
        MediaDetailAction(Icons.Default.Public, "WebView", canOpenWeb, openWeb, Modifier.weight(1f))
    }
}

@Composable
private fun MediaDetailAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    action: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        IconButton(onClick = action, enabled = enabled) {
            Icon(
                icon,
                contentDescription = label,
                tint = when {
                    selected -> MaterialTheme.colorScheme.primary
                    enabled -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                },
            )
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

@OptIn(ExperimentalFoundationApi::class)
internal fun <T> LazyListScope.mediaUnitsSection(
    label: String,
    units: List<T>,
    key: (T) -> String,
    title: (T) -> String,
    summary: (T) -> String,
    muted: (T) -> Boolean = { false },
    selection: MediaUnitSelectionUiModel? = null,
    open: (T) -> Unit,
    download: ((T) -> Unit)? = null,
    downloadProgress: (T) -> DownloadUiProgress? = { null },
) {
    if (units.isEmpty()) return
    stickyHeader(key = "media-section:$label") {
        MediaContentSectionHeader(label, units.size, selection)
    }
    items(units, key = key) { unit ->
        val unitKey = key(unit)
        MediaUnitRow(
            title = title(unit),
            summary = summary(unit),
            open = { open(unit) },
            download = { download?.invoke(unit) },
            muted = muted(unit),
            canDownload = download != null,
            downloadProgress = downloadProgress(unit),
            selectionMode = selection?.selecting == true,
            selected = unitKey in selection?.selectedKeys.orEmpty(),
            select = { selection?.select?.invoke(unitKey) },
        )
    }
}

@Composable
private fun MediaContentSectionHeader(
    label: String,
    unitCount: Int,
    selection: MediaUnitSelectionUiModel?,
) {
    Column(
        modifier = Modifier
            .widthIn(max = 900.dp)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                modifier = Modifier.weight(1f).padding(top = 8.dp, bottom = 4.dp),
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium,
            )
            selection?.let {
                TextButton(onClick = it.toggle) {
                    Text(UiTranslations.translate(
                        if (it.selecting) "ui.deselect" else "ui.select",
                        LocalLanguagePack.current,
                    ))
                }
            }
        }
        if (selection?.selecting == true) {
            val allSelected = selection.selectedKeys.size == unitCount
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    UiTranslations.format(
                        "dynamic.selected.count",
                        LocalLanguagePack.current,
                        selection.selectedKeys.size,
                    ),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                TextButton(onClick = if (allSelected) selection.deselectAll else selection.selectAll) {
                    Text(UiTranslations.translate(
                        if (allSelected) "ui.deselect.all" else "ui.select.all",
                        LocalLanguagePack.current,
                    ))
                }
                TextButton(
                    onClick = selection.markPositive,
                    enabled = selection.selectedKeys.isNotEmpty(),
                ) {
                    Text(UiTranslations.translate(selection.positiveLabel, LocalLanguagePack.current))
                }
                TextButton(
                    onClick = selection.markNegative,
                    enabled = selection.selectedKeys.isNotEmpty(),
                ) {
                    Text(UiTranslations.translate(selection.negativeLabel, LocalLanguagePack.current))
                }
            }
        }
    }
}

@Composable
internal fun MediaUnitRow(
    title: String,
    summary: String,
    open: () -> Unit,
    download: () -> Unit,
    muted: Boolean = false,
    canDownload: Boolean = true,
    downloadProgress: DownloadUiProgress? = null,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    select: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .widthIn(max = 900.dp)
            .fillMaxWidth()
            .clickable(onClick = if (selectionMode) select else open)
            .alpha(if (muted) 0.5f else 1f)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            Checkbox(checked = selected, onCheckedChange = { select() })
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DownloadActionButton(
            progress = downloadProgress,
            enabled = canDownload && !selectionMode,
            action = download,
            contentDescription = UiTranslations.format(
                "dynamic.download.title",
                LocalLanguagePack.current,
                title,
            ),
        )
    }
    HorizontalDivider(modifier = Modifier.widthIn(max = 900.dp).fillMaxWidth())
}
