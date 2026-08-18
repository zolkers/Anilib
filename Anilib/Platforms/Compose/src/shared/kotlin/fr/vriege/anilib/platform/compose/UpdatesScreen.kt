package fr.vriege.anilib.platform.compose

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import fr.vriege.anilib.feature.updates.LibraryUpdateEvent
import fr.vriege.anilib.feature.updates.LibraryUpdatePolicy
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

/** Shared Updates feed, progress, schedule, and Aniyomi-style filtering controls. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UpdatesScreen(presentation: UpdatePresentation) {
    var revision by remember(presentation) { mutableIntStateOf(0) }
    var commandError by remember(presentation) { mutableStateOf<String?>(null) }
    DisposableEffect(presentation) {
        val registration = presentation.observe { revision++ }
        onDispose { runCatching { registration.close() } }
    }
    val snapshot = remember(presentation, revision) { presentation.snapshot() }
    val running = snapshot.status() == LibraryUpdateStatus.RUNNING
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
            if (snapshot.events().isEmpty()) {
                item {
                    Spacer(Modifier.height(24.dp))
                    EmptyPage("No new chapters or episodes yet. Refresh once to establish the library baseline.")
                }
            } else {
                items(
                    snapshot.events(),
                    key = { "${it.libraryItemId().value()}-${it.sourceContentId()}" },
                ) { event -> UpdateEventCard(event) }
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
                TextButton(onClick = {
                    val values = UpdateInterval.values()
                    configure(copyPolicy(policy, interval = values[(policy.interval().ordinal + 1) % values.size]))
                }) { Text("Change") }
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
private fun UpdateEventCard(event: LibraryUpdateEvent) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (event.read()) {
                MaterialTheme.colorScheme.surfaceContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
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
    }
}

private fun copyPolicy(
    policy: LibraryUpdatePolicy,
    interval: UpdateInterval = policy.interval(),
    favoritesOnly: Boolean = policy.favoritesOnly(),
    skipCompleted: Boolean = policy.skipCompleted(),
    skipNotStarted: Boolean = policy.skipNotStarted(),
): LibraryUpdatePolicy = LibraryUpdatePolicy(
    interval,
    favoritesOnly,
    skipCompleted,
    skipNotStarted,
    policy.includedCategories(),
    policy.excludedCategories(),
)

private fun intervalLabel(interval: UpdateInterval): String = when (interval) {
    UpdateInterval.MANUAL -> "Manual only"
    UpdateInterval.SIX_HOURS -> "Every 6 hours"
    UpdateInterval.TWELVE_HOURS -> "Every 12 hours"
    UpdateInterval.DAILY -> "Daily"
    UpdateInterval.EVERY_TWO_DAYS -> "Every 2 days"
    UpdateInterval.WEEKLY -> "Weekly"
}
