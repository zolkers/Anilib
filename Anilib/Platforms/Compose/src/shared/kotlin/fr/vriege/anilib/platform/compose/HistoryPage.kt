package fr.vriege.anilib.platform.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.vriege.anilib.feature.library.LibraryItemId
import fr.vriege.anilib.feature.library.MediaKind
import fr.vriege.anilib.feature.library.ui.LibraryCard
import fr.vriege.anilib.feature.library.ui.LibraryHistoryRow
import fr.vriege.anilib.feature.library.ui.LibraryNavigator
import fr.vriege.anilib.feature.library.ui.LibraryPresentation
import fr.vriege.anilib.feature.source.SourceContentUnitId
import fr.vriege.anilib.feature.source.SourceEpisodeId
import fr.vriege.anilib.feature.reader.ui.ReaderPresentation
import fr.vriege.anilib.feature.player.ui.PlayerPresentation
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class HistoryContentKey(
    val libraryItemId: LibraryItemId,
    val contentId: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryPage(
    presentation: LibraryPresentation,
    reader: ReaderPresentation,
    player: PlayerPresentation,
    openReader: (LibraryItemId, SourceContentUnitId?) -> Unit,
    openPlayer: (LibraryItemId, SourceEpisodeId?) -> Unit,
    resumeError: String?,
    goBack: () -> Unit,
    navigate: (LibraryHistoryRow, (LibraryNavigator) -> Unit) -> Unit,
) {
    var revision by remember { mutableStateOf(0) }
    DisposableEffect(presentation) {
        val observation = presentation.observe { revision++ }
        onDispose { runCatching { observation.close() } }
    }
    val history = remember(revision) { presentation.history() }
    val cards = remember(revision) { presentation.library().titles().associateBy { it.id() } }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    val searchFocus = rememberSearchFocusRequester(searching)
    var kind by remember { mutableStateOf(MediaKind.ANIME) }
    var contentLabels by remember(reader, player) {
        mutableStateOf<Map<HistoryContentKey, String>>(emptyMap())
    }
    var contentUnitIds by remember(reader) {
        mutableStateOf<Map<HistoryContentKey, SourceContentUnitId>>(emptyMap())
    }
    var episodeIds by remember(player) {
        mutableStateOf<Map<HistoryContentKey, SourceEpisodeId>>(emptyMap())
    }
    CrashSafeLaunchedEffect(reader, player, kind, history) {
        val rows = history.entries().filter { it.kind() == kind }
        val content = withContext(Dispatchers.IO) {
            val labels = mutableMapOf<HistoryContentKey, String>()
            val resolvedContentUnitIds = mutableMapOf<HistoryContentKey, SourceContentUnitId>()
            val resolvedEpisodeIds = mutableMapOf<HistoryContentKey, SourceEpisodeId>()
            rows.groupBy { it.libraryItemId() }.forEach { (libraryItemId, titleRows) ->
                if (kind == MediaKind.ANIME) {
                    runCatching { player.episodes(libraryItemId) }.getOrDefault(emptyList()).forEach { episode ->
                        val key = HistoryContentKey(libraryItemId, episode.episode().id().value())
                        val fallbackPosition = titleRows
                            .filter { it.contentId() == episode.episode().id().value() }
                            .maxOfOrNull { it.position() }
                            ?: 0L
                        val position = episode.playback()
                            .map { it.positionMillis() }
                            .orElse(fallbackPosition)
                        labels[key] = "${episode.episode().title()} · ${formatMediaPosition(position)}"
                        resolvedEpisodeIds[key] = episode.episode().id()
                    }
                } else {
                    runCatching { reader.contentUnits(libraryItemId) }.getOrDefault(emptyList()).forEach { unit ->
                        val key = HistoryContentKey(libraryItemId, unit.id().value())
                        labels[key] = unit.title()
                        resolvedContentUnitIds[key] = unit.id()
                    }
                }
            }
            Triple(labels, resolvedContentUnitIds, resolvedEpisodeIds)
        }
        contentLabels = content.first
        contentUnitIds = content.second
        episodeIds = content.third
    }
    val entries = history.entries().filter {
        val contentLabel = contentLabels[HistoryContentKey(it.libraryItemId(), it.contentId())]
        it.kind() == kind &&
            (query.isBlank() || it.title().contains(query, ignoreCase = true) ||
                contentLabel?.contains(query, ignoreCase = true) == true)
    }
    val groups = entries.groupBy { row ->
        row.openedAt().atZone(ZoneId.systemDefault()).toLocalDate()
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searching) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("ui.search.history") },
                            singleLine = true,
                            keyboardOptions = searchKeyboardOptions(),
                            keyboardActions = searchKeyboardActions(),
                            modifier = Modifier.fillMaxWidth().searchFocus(searchFocus),
                        )
                    } else {
                        Text("ui.history")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = goBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ui.back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        searching = !searching
                        if (!searching) query = ""
                    }) {
                        Icon(Icons.Default.Search, contentDescription = "ui.search.history")
                    }
                    IconButton(
                        enabled = history.entries().any { it.kind() == kind },
                        onClick = {
                            history.entries().filter { it.kind() == kind }.forEach { row ->
                                presentation.removeHistoryEntry(
                                    row.libraryItemId(),
                                    row.contentId(),
                                    row.openedAt(),
                                )
                            }
                        },
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "ui.clear.history")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            PrimaryTabRow(selectedTabIndex = if (kind == MediaKind.ANIME) 0 else 1) {
                Tab(
                    selected = kind == MediaKind.ANIME,
                    onClick = { kind = MediaKind.ANIME },
                        text = { Text("ui.anime") },
                )
                Tab(
                    selected = kind == MediaKind.MANGA,
                    onClick = { kind = MediaKind.MANGA },
                        text = { Text("ui.manga") },
                )
            }
            resumeError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            }
            if (history.entries().isEmpty()) {
                EmptyPage("ui.opened.titles.appear.here")
            } else if (entries.isEmpty()) {
                EmptyPage("ui.no.history.entries.match.search")
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    groups.forEach { (date, rows) ->
                        item(key = "history-date-$date") {
                            Text(
                                historyDateLabel(date),
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                            )
                        }
                        items(
                            rows,
                            key = { row ->
                                "${row.libraryItemId().value()}-${row.contentId()}-${row.openedAt()}"
                            },
                        ) { row ->
                            HistoryCard(
                                row,
                                cards[row.libraryItemId()],
                                contentLabels[HistoryContentKey(row.libraryItemId(), row.contentId())],
                                resume = {
                                    val key = HistoryContentKey(row.libraryItemId(), row.contentId())
                                    if (row.kind() == MediaKind.ANIME) {
                                        episodeIds[key]?.let { openPlayer(row.libraryItemId(), it) }
                                            ?: navigate(row) { it.openDetails(row.libraryItemId()) }
                                    } else {
                                        contentUnitIds[key]?.let { openReader(row.libraryItemId(), it) }
                                            ?: navigate(row) { it.openDetails(row.libraryItemId()) }
                                    }
                                },
                                remove = {
                                    presentation.removeHistoryEntry(
                                        row.libraryItemId(),
                                        row.contentId(),
                                        row.openedAt(),
                                    )
                                },
                                toggleFavorite = {
                                    val favorite = cards[row.libraryItemId()]?.favorite() == true
                                    presentation.setFavorite(setOf(row.libraryItemId()), !favorite)
                                },
                                openDetails = { transition ->
                                    navigate(row, transition)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun HistoryCard(
    row: LibraryHistoryRow,
    card: LibraryCard?,
    contentLabel: String?,
    resume: () -> Unit,
    remove: () -> Unit,
    toggleFavorite: () -> Unit,
    openDetails: ((LibraryNavigator) -> Unit) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable {
            openDetails { it.openDetails(row.libraryItemId()) }
        }.padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemoteArtwork(
            card?.artwork()?.orElse(null),
            row.title(),
            modifier = Modifier.width(56.dp).height(82.dp).clip(RoundedCornerShape(6.dp)),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                row.title(),
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = contentLabel ?: if (row.kind() == MediaKind.ANIME) {
                    "Episode · ${formatMediaPosition(row.position())}"
                } else {
                    "Chapter"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = resume) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = if (row.kind() == MediaKind.ANIME) "Resume" else "Continue",
            )
        }
        IconButton(onClick = toggleFavorite) {
            Icon(
                if (card?.favorite() == true) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = if (card?.favorite() == true) {
                    "ui.unfavorite"
                } else {
                    "ui.favorite"
                },
            )
        }
        IconButton(onClick = remove) {
            Icon(Icons.Default.Delete, contentDescription = "ui.remove.history.entry")
        }
    }
}

internal fun historyDateLabel(date: LocalDate): String {
    val today = LocalDate.now(ZoneId.systemDefault())
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(Locale.getDefault())
            .format(date)
    }
}
