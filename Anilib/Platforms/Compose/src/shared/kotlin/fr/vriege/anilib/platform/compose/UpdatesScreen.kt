package fr.vriege.anilib.platform.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
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
import fr.vriege.anilib.feature.downloads.ui.DownloadPresentation
import fr.vriege.anilib.feature.library.LibraryItemId
import fr.vriege.anilib.feature.library.MediaKind
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

private val updateDateFormatter = DateTimeFormatter
    .ofLocalizedDateTime(FormatStyle.MEDIUM)
    .withLocale(Locale.getDefault())
    .withZone(ZoneId.systemDefault())
private val updateDayFormatter = DateTimeFormatter
    .ofLocalizedDate(FormatStyle.LONG)
    .withLocale(Locale.getDefault())
    .withZone(ZoneId.systemDefault())

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
    var scheduleExpanded by remember { mutableStateOf(false) }
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

    AnilibSubScreenScaffold(
        title = "Updates",
        actions = {
            if (snapshot.unreadCount() > 0) {
                IconButton(onClick = { command(presentation::markAllRead) }) {
                    Icon(Icons.Default.DoneAll, contentDescription = "ui.mark.all.read")
                }
            }
            IconButton(onClick = {
                selection = if (selection.isEmpty()) {
                    events.map(LibraryUpdateEvent::id).toSet()
                } else {
                    emptySet()
                }
            }) {
                Icon(
                    Icons.Default.SelectAll,
                    contentDescription = if (selection.isEmpty()) "Select" else "Clear",
                )
            }
            IconButton(onClick = {
                if (running) command { presentation.cancel() } else command { presentation.refresh() }
            }) {
                Icon(
                    if (running) Icons.Default.Cancel else Icons.Default.Refresh,
                    contentDescription = if (running) "Cancel update" else "Refresh library",
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth()
                    .widthIn(max = 900.dp)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    UpdateScheduleCard(
                        policy = snapshot.policy(),
                        expanded = scheduleExpanded,
                        lastRun = snapshot.lastRunAt().orElse(null)?.let(updateDateFormatter::format),
                        nextRun = snapshot.nextRunAt().orElse(null)?.let(updateDateFormatter::format),
                        toggle = { scheduleExpanded = !scheduleExpanded },
                        configure = { policy -> command { presentation.configure(policy) } },
                    )
                }
                if (running) {
                    item { UpdateProgress(snapshot.completedTitles(), snapshot.totalTitles(), snapshot.activeTitles()) }
                }
                item {
                    UpdateFilters(
                        kind = kind,
                        unreadOnly = unreadOnly,
                        showSkipped = showSkipped,
                        skippedCount = snapshot.skippedTitles().size,
                        selectKind = { kind = it },
                        toggleUnread = { unreadOnly = !unreadOnly },
                        toggleSkipped = { showSkipped = !showSkipped },
                    )
                }
                if (selection.isNotEmpty()) {
                    item {
                        AnilibGroup {
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
                                            command {
                                                downloads.enqueue(event.libraryItemId(), event.sourceContentId())
                                            }
                                        }
                                },
                            )
                        }
                    }
                }
                commandError?.let { message -> item { UpdateMessageSurface(message) } }
                items(snapshot.failures(), key = { "failure-${it.libraryItemId().value()}" }) { failure ->
                    UpdateMessageSurface("${failure.title()}\n${failure.message()}")
                }
                if (showSkipped) {
                    if (snapshot.skippedTitles().isEmpty()) {
                        item { EmptyPage("ui.no.titles.skipped.by.update.policy") }
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
                        EmptyPage("ui.no.updates.refresh.for.baseline")
                    }
                } else if (events.isEmpty()) {
                    item { EmptyPage("ui.no.updates.match.filters") }
                } else {
                    events.groupBy(::eventDate).forEach { (date, datedEvents) ->
                        item(key = "date-$date") {
                            Text(
                                date,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 2.dp),
                            )
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
                                    command {
                                        downloads.enqueue(event.libraryItemId(), event.sourceContentId())
                                    }
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
private fun UpdateScheduleCard(
    policy: LibraryUpdatePolicy,
    expanded: Boolean,
    lastRun: String?,
    nextRun: String?,
    toggle: () -> Unit,
    configure: (LibraryUpdatePolicy) -> Unit,
) {
    AnilibGroup {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = toggle).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnilibLeadingIcon(Icons.Outlined.Schedule)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("ui.library.updates", fontWeight = FontWeight.Medium)
                Text(
                    scheduleSummary(policy, lastRun, nextRun),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Icon(
                if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expanded) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
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
}

private fun scheduleSummary(policy: LibraryUpdatePolicy, lastRun: String?, nextRun: String?): String {
    val timing = nextRun?.let { "Next: $it" } ?: lastRun?.let { "Last: $it" }
    return listOfNotNull(intervalLabel(policy.interval()), timing).joinToString(" • ")
}

@Composable
private fun UpdateProgress(completed: Int, total: Int, activeTitles: List<String>) {
    AnilibGroup {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("ui.updating.library", fontWeight = FontWeight.SemiBold)
            LinearProgressIndicator(
                progress = { if (total == 0) 0f else completed.toFloat() / total },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            Text(
                UiTranslations.format(
                    "dynamic.update.progress",
                    LocalLanguagePack.current,
                    completed,
                    total,
                    activeTitles.firstOrNull()?.let { " • $it" }.orEmpty(),
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun UpdateFilters(
    kind: MediaKind?,
    unreadOnly: Boolean,
    showSkipped: Boolean,
    skippedCount: Int,
    selectKind: (MediaKind?) -> Unit,
    toggleUnread: () -> Unit,
    toggleSkipped: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(selected = kind == null, onClick = { selectKind(null) }, label = { Text("ui.all") })
        FilterChip(
            selected = kind == MediaKind.ANIME,
            onClick = { selectKind(MediaKind.ANIME) },
                    label = { Text("ui.anime") },
        )
        FilterChip(
            selected = kind == MediaKind.MANGA,
            onClick = { selectKind(MediaKind.MANGA) },
                    label = { Text("ui.manga") },
        )
        FilterChip(selected = unreadOnly, onClick = toggleUnread, label = { Text("ui.unread") })
        FilterChip(
            selected = showSkipped,
            onClick = toggleSkipped,
            label = { Text(UiTranslations.format("dynamic.skipped", LocalLanguagePack.current, skippedCount)) },
        )
    }
}

@Composable
private fun UpdateSwitch(label: String, checked: Boolean, update: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, modifier = Modifier.weight(1f))
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
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = select),
        shape = RoundedCornerShape(18.dp),
        color = if (event.read()) {
            MaterialTheme.colorScheme.surfaceContainerLow
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        tonalElevation = 1.dp,
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = { select() })
            } else {
                AnilibLeadingIcon(
                    if (event.kind() == MediaKind.ANIME) Icons.Outlined.Movie else
                        Icons.AutoMirrored.Outlined.MenuBook,
                )
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(event.libraryTitle(), fontWeight = FontWeight.SemiBold)
                Text(event.contentTitle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    UiTranslations.format(
                        "dynamic.update.event.kind",
                        LocalLanguagePack.current,
                        UiTranslations.translate(
                            if (event.kind() == MediaKind.ANIME) "ui.episode" else "ui.chapter",
                            LocalLanguagePack.current,
                        ),
                        updateDateFormatter.format(event.publishedAt().orElse(event.discoveredAt())),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            IconButton(onClick = download, enabled = canDownload && !selectionMode) {
                Icon(Icons.Outlined.Download, contentDescription = "ui.download")
            }
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
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            UiTranslations.format("dynamic.selected.count", LocalLanguagePack.current, count),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(12.dp),
        )
        TextButton(onClick = markRead) { Text("ui.read") }
        TextButton(onClick = markUnread) { Text("ui.unread") }
        TextButton(onClick = download) { Text("ui.download") }
        TextButton(onClick = exclude) { Text("ui.exclude.titles") }
        TextButton(onClick = remove) { Text("ui.remove") }
    }
}

@Composable
private fun SkippedTitleCard(skipped: LibraryUpdateSkip, include: () -> Unit) {
    AnilibGroup {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(skipped.title(), fontWeight = FontWeight.SemiBold)
                Text(skipReasonLabel(skipped.reason()), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = include) { Text("ui.always.include") }
        }
    }
}

@Composable
private fun UpdateMessageSurface(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Text(
            message,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(16.dp),
        )
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
