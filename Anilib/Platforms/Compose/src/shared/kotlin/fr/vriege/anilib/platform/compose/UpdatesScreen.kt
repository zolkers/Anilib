package fr.vriege.anilib.platform.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.vriege.anilib.feature.library.MediaKind
import fr.vriege.anilib.feature.downloads.ui.DownloadPresentation
import fr.vriege.anilib.feature.updates.LibraryUpdateEvent
import fr.vriege.anilib.feature.updates.LibraryUpdateEventId
import fr.vriege.anilib.feature.updates.LibraryUpdatePolicy
import fr.vriege.anilib.feature.updates.LibraryUpdateSkip
import fr.vriege.anilib.feature.updates.LibraryUpdateSkipReason
import fr.vriege.anilib.feature.updates.LibraryUpdateStatus
import fr.vriege.anilib.feature.updates.UpdateInterval
import fr.vriege.anilib.feature.updates.ui.UpdatePresentation
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import fr.vriege.anilib.feature.library.LibraryItemId

private val updateDateFormatter = DateTimeFormatter
    .ofLocalizedDateTime(FormatStyle.MEDIUM)
    .withLocale(Locale.getDefault())
    .withZone(ZoneId.systemDefault())
private val updateDayFormatter = DateTimeFormatter
    .ofLocalizedDate(FormatStyle.LONG)
    .withLocale(Locale.getDefault())
    .withZone(ZoneId.systemDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UpdatesScreen(
    presentation: UpdatePresentation,
    downloads: DownloadPresentation,
) {
    var revision by remember(presentation) { mutableIntStateOf(0) }
    var commandError by remember(presentation) { mutableStateOf<String?>(null) }
    var kind by remember { mutableStateOf<MediaKind?>(null) }
    var unreadOnly by remember { mutableStateOf(false) }
    var showSkipped by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf<Set<LibraryUpdateEventId>>(emptySet()) }
    DisposableEffect(presentation) {
        val registration = presentation.observe { revision++ }
        onDispose { runCatching { registration.close() } }
    }
    val snapshot = remember(presentation, revision) { presentation.snapshot() }
    val running = snapshot.status() == LibraryUpdateStatus.RUNNING
    val events = snapshot.events().filter {
        (kind == null || it.kind() == kind) && (!unreadOnly || !it.read())
    }
    val command: (() -> Unit) -> Unit = { action ->
        runCatching(action)
            .onSuccess { commandError = null }
            .onFailure { commandError = it.message ?: "Library update command failed." }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Updates") },
                actions = {
                    if (snapshot.unreadCount() > 0) {
                        IconButton(onClick = { command(presentation::markAllRead) }) {
                            Icon(Icons.Default.DoneAll, contentDescription = "Mark all read")
                        }
                    }
                    TextButton(onClick = {
                        selection = if (selection.isEmpty()) events.map(LibraryUpdateEvent::id).toSet() else emptySet()
                    }) { Text(if (selection.isEmpty()) "Select" else "Clear") }
                    IconButton(
                        onClick = {
                            if (running) {
                                command { presentation.cancel() }
                            } else {
                                command { presentation.refresh() }
                            }
                        },
                    ) {
                        Icon(
                            if (running) Icons.Default.Cancel else Icons.Default.Refresh,
                            contentDescription = if (running) "Cancel update" else "Refresh library",
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                UpdateScheduleCard(snapshot.policy()) { policy -> command { presentation.configure(policy) } }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(selected = kind == null, onClick = { kind = null }, label = { Text("All") })
                    FilterChip(
                        selected = kind == MediaKind.ANIME,
                        onClick = { kind = MediaKind.ANIME },
                        label = { Text("Anime") },
                    )
                    FilterChip(
                        selected = kind == MediaKind.MANGA,
                        onClick = { kind = MediaKind.MANGA },
                        label = { Text("Manga") },
                    )
                    FilterChip(
                        selected = unreadOnly,
                        onClick = { unreadOnly = !unreadOnly },
                        label = { Text("Unread") },
                    )
                    FilterChip(
                        selected = showSkipped,
                        onClick = { showSkipped = !showSkipped },
                        label = { Text("Skipped (${snapshot.skippedTitles().size})") },
                    )
                }
            }
            if (selection.isNotEmpty()) {
                item {
                    SelectionActions(
                        count = selection.size,
                        markRead = { command { presentation.setEventsRead(selection, true) } },
                        markUnread = { command { presentation.setEventsRead(selection, false) } },
                        remove = {
                            command { presentation.removeEvents(selection) }
                            selection = emptySet()
                        },
                        exclude = {
                            val itemIds = events.filter { selection.contains(it.id()) }
                                .map(LibraryUpdateEvent::libraryItemId)
                                .toSet()
                            command {
                                presentation.configure(copyPolicy(
                                    snapshot.policy(),
                                    includedTitles = snapshot.policy().includedTitles() - itemIds,
                                    excludedTitles = snapshot.policy().excludedTitles() + itemIds,
                                ))
                            }
                        },
                        download = {
                            events.filter { selection.contains(it.id()) }
                                .filter { downloads.canEnqueue(it.libraryItemId()) }
                                .forEach { event ->
                                    command { downloads.enqueue(event.libraryItemId(), event.sourceContentId()) }
                                }
                        },
                    )
                }
            }
            if (running) {
                item {
                    Column {
                        LinearProgressIndicator(
                            progress = {
                                if (snapshot.totalTitles() == 0) 0f else
                                    snapshot.completedTitles().toFloat() / snapshot.totalTitles()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "${snapshot.completedTitles()} / ${snapshot.totalTitles()} titles" +
                                snapshot.activeTitles().firstOrNull()?.let { " • $it" }.orEmpty(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
            commandError?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
            items(snapshot.failures(), key = { "failure-${it.libraryItemId().value()}" }) { failure ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(failure.title(), fontWeight = FontWeight.SemiBold)
                        Text(failure.message(), color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
            if (showSkipped) {
                if (snapshot.skippedTitles().isEmpty()) {
                    item { EmptyPage("No titles are currently skipped by the update policy.") }
                } else {
                    items(snapshot.skippedTitles(), key = { "skip-${it.libraryItemId().value()}" }) { skipped ->
                        SkippedTitleCard(skipped) {
                            command {
                                presentation.configure(copyPolicy(
                                    snapshot.policy(),
                                    includedTitles = snapshot.policy().includedTitles() + skipped.libraryItemId(),
                                    excludedTitles = snapshot.policy().excludedTitles() - skipped.libraryItemId(),
                                ))
                            }
                        }
                    }
                }
            } else if (snapshot.events().isEmpty()) {
                item {
                    Spacer(Modifier.height(24.dp))
                    EmptyPage("No new chapters or episodes yet. Refresh once to establish the library baseline.")
                }
            } else if (events.isEmpty()) {
                item { EmptyPage("No updates match the active filters.") }
            } else {
                events.groupBy { eventDate(it) }.forEach { (date, datedEvents) ->
                    item(key = "date-$date") {
                        Text(date, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                    }
                    items(
                        datedEvents,
                        key = { "${it.libraryItemId().value()}-${it.sourceContentId()}" },
                    ) { event ->
                        UpdateEventCard(
                            event = event,
                            selected = selection.contains(event.id()),
                            selectionMode = selection.isNotEmpty(),
                            select = {
                                selection = if (selection.contains(event.id())) {
                                    selection - event.id()
                                } else {
                                    selection + event.id()
                                }
                            },
                            canDownload = downloads.canEnqueue(event.libraryItemId()),
                            download = {
                                command { downloads.enqueue(event.libraryItemId(), event.sourceContentId()) }
                            },
                        )
                    }
                }
            }
            item {
                val lastRun = snapshot.lastRunAt().orElse(null)
                val nextRun = snapshot.nextRunAt().orElse(null)
                Text(
                    listOfNotNull(
                        lastRun?.let { "Last: ${updateDateFormatter.format(it)}" },
                        nextRun?.let { "Next: ${updateDateFormatter.format(it)}" },
                    ).joinToString(" • ").ifEmpty { "Automatic updates are disabled." },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 14.dp),
                )
            }
        }
    }
}

@Composable
private fun UpdateScheduleCard(policy: LibraryUpdatePolicy, configure: (LibraryUpdatePolicy) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("Library update schedule", fontWeight = FontWeight.SemiBold)
                    Text(intervalLabel(policy.interval()), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                UpdateInterval.entries.forEach { interval ->
                    FilterChip(
                        selected = policy.interval() == interval,
                        onClick = { configure(copyPolicy(policy, interval = interval)) },
                        label = { Text(intervalLabel(interval)) },
                        modifier = Modifier.padding(end = 6.dp),
                    )
                }
            }
            UpdateSwitch("Favorites only", policy.favoritesOnly()) {
                configure(copyPolicy(policy, favoritesOnly = it))
            }
            UpdateSwitch("Skip completed titles", policy.skipCompleted()) {
                configure(copyPolicy(policy, skipCompleted = it))
            }
            UpdateSwitch("Skip titles not started", policy.skipNotStarted()) {
                configure(copyPolicy(policy, skipNotStarted = it))
            }
        }
    }
}

@Composable
private fun UpdateSwitch(label: String, checked: Boolean, update: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = update)
    }
}

@Composable
private fun UpdateEventCard(
    event: LibraryUpdateEvent,
    selected: Boolean,
    selectionMode: Boolean,
    select: () -> Unit,
    canDownload: Boolean,
    download: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = select),
        colors = CardDefaults.cardColors(
            containerColor = if (event.read()) {
                MaterialTheme.colorScheme.surfaceContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
        ),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = { select() })
            }
            Column(Modifier.weight(1f)) {
                Text(event.libraryTitle(), fontWeight = FontWeight.SemiBold)
            Text(event.contentTitle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "${if (event.kind() == MediaKind.ANIME) "Episode" else "Chapter"} • " +
                    updateDateFormatter.format(event.publishedAt().orElse(event.discoveredAt())),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            }
            TextButton(onClick = download, enabled = canDownload && !selectionMode) { Text("Download") }
        }
    }
}

@Composable
private fun SelectionActions(
    count: Int,
    markRead: () -> Unit,
    markUnread: () -> Unit,
    remove: () -> Unit,
    exclude: () -> Unit,
    download: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Text("$count selected", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(12.dp))
        TextButton(onClick = markRead) { Text("Read") }
        TextButton(onClick = markUnread) { Text("Unread") }
        TextButton(onClick = download) { Text("Download") }
        TextButton(onClick = exclude) { Text("Exclude titles") }
        TextButton(onClick = remove) { Text("Remove") }
    }
}

@Composable
private fun SkippedTitleCard(skipped: LibraryUpdateSkip, include: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(skipped.title(), fontWeight = FontWeight.SemiBold)
                Text(skipReasonLabel(skipped.reason()), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = include) { Text("Always include") }
        }
    }
}

private fun copyPolicy(
    policy: LibraryUpdatePolicy,
    interval: UpdateInterval = policy.interval(),
    favoritesOnly: Boolean = policy.favoritesOnly(),
    skipCompleted: Boolean = policy.skipCompleted(),
    skipNotStarted: Boolean = policy.skipNotStarted(),
    includedTitles: Set<LibraryItemId> = policy.includedTitles(),
    excludedTitles: Set<LibraryItemId> = policy.excludedTitles(),
): LibraryUpdatePolicy = LibraryUpdatePolicy(
    interval,
    favoritesOnly,
    skipCompleted,
    skipNotStarted,
    policy.includedCategories(),
    policy.excludedCategories(),
    includedTitles,
    excludedTitles,
)

private fun eventDate(event: LibraryUpdateEvent): String =
    updateDayFormatter.format(event.publishedAt().orElse(event.discoveredAt()))

private fun skipReasonLabel(reason: LibraryUpdateSkipReason): String = when (reason) {
    LibraryUpdateSkipReason.NO_SOURCE_ORIGIN -> "No source origin"
    LibraryUpdateSkipReason.TITLE_EXCLUDED -> "Excluded for this title"
    LibraryUpdateSkipReason.NOT_FAVORITE -> "Not a favorite"
    LibraryUpdateSkipReason.PUBLICATION_COMPLETED -> "Publication completed"
    LibraryUpdateSkipReason.NOT_STARTED -> "Not started"
    LibraryUpdateSkipReason.CATEGORY_EXCLUDED -> "Category excluded"
    LibraryUpdateSkipReason.CATEGORY_NOT_INCLUDED -> "Category not included"
}

private fun intervalLabel(interval: UpdateInterval): String = when (interval) {
    UpdateInterval.MANUAL -> "Manual only"
    UpdateInterval.SIX_HOURS -> "Every 6 hours"
    UpdateInterval.TWELVE_HOURS -> "Every 12 hours"
    UpdateInterval.DAILY -> "Daily"
    UpdateInterval.EVERY_TWO_DAYS -> "Every 2 days"
    UpdateInterval.WEEKLY -> "Weekly"
}
