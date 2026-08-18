package fr.vriege.anilib.platform.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.vriege.anilib.feature.library.MediaKind
import fr.vriege.anilib.feature.library.PublicationStatus
import fr.vriege.anilib.feature.library.LibraryCategory
import fr.vriege.anilib.feature.library.LibraryCategoryUpdatePolicy
import fr.vriege.anilib.feature.discovery.ui.DiscoveryPresentation
import fr.vriege.anilib.feature.library.ui.LibraryPresentation
import fr.vriege.anilib.feature.player.ui.PlayerPresentation
import fr.vriege.anilib.feature.source.SourceId
import fr.vriege.anilib.feature.tracker.ui.TrackerPresentation
import fr.vriege.anilib.feature.applicationupdate.ui.ApplicationUpdatePresentation
import fr.vriege.anilib.feature.applicationupdate.ApplicationUpdateChannel
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CategoriesScreen(presentation: LibraryPresentation, goBack: () -> Unit) {
    var revision by remember(presentation) { mutableStateOf(0) }
    val overview = remember(presentation, revision) { presentation.library() }
    var newCategory by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    fun update(action: () -> Unit) {
        try {
            action()
            error = null
            revision++
        } catch (failure: RuntimeException) {
            error = failure.message ?: "Unable to update categories."
        }
    }
    MoreScaffold("Categories", goBack) { padding ->
        val uncategorized = overview.titles().count { it.categories().isEmpty() }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = newCategory,
                        onValueChange = { newCategory = it },
                        label = { Text("New category") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        enabled = newCategory.isNotBlank(),
                        onClick = {
                            update { presentation.createCategory(newCategory.trim()) }
                            if (error == null) newCategory = ""
                        },
                    ) { Text("Add") }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            item { SummaryCard("Default", "$uncategorized titles") }
            overview.categoryConfigurations().forEachIndexed { index, category ->
                val count = overview.titles().count { category.name() in it.categories() }
                item(key = category.name()) {
                    CategoryCard(
                        category,
                        count,
                        index,
                        overview.categoryConfigurations().lastIndex,
                        rename = { next ->
                            update { presentation.renameCategory(category.name(), next) }
                        },
                        move = { target ->
                            update { presentation.moveCategory(category.name(), target) }
                        },
                        setUpdatePolicy = { policy ->
                            update { presentation.setCategoryUpdatePolicy(category.name(), policy) }
                        },
                        delete = { update { presentation.deleteCategory(category.name()) } },
                    )
                }
            }
            if (overview.categories().isEmpty() && uncategorized == 0) {
                item { EmptyPage("Categories will appear when titles are added to the library.") }
            }
        }
    }
}

@Composable
private fun CategoryCard(
    category: LibraryCategory,
    count: Int,
    index: Int,
    lastIndex: Int,
    rename: (String) -> Unit,
    move: (Int) -> Unit,
    setUpdatePolicy: (LibraryCategoryUpdatePolicy) -> Unit,
    delete: () -> Unit,
) {
    var editing by remember(category.name()) { mutableStateOf(false) }
    var confirmingDelete by remember(category.name()) { mutableStateOf(false) }
    var nextName by remember(category.name()) { mutableStateOf(category.name()) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(category.name(), fontWeight = FontWeight.Medium)
                    Text("$count titles", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(enabled = index > 0, onClick = { move(index - 1) }) { Text("Up") }
                TextButton(enabled = index < lastIndex, onClick = { move(index + 1) }) { Text("Down") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { editing = true }) { Text("Rename") }
                TextButton(onClick = {
                    setUpdatePolicy(category.updatePolicy().next())
                }) { Text("Updates: ${category.updatePolicy().label()}") }
                TextButton(onClick = { confirmingDelete = true }) { Text("Delete") }
            }
        }
    }
    if (editing) {
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Rename category") },
            text = {
                OutlinedTextField(
                    value = nextName,
                    onValueChange = { nextName = it },
                    label = { Text("Category name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = nextName.isNotBlank(),
                    onClick = {
                        rename(nextName.trim())
                        editing = false
                    },
                ) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancel") } },
        )
    }
    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete ${category.name()}?") },
            text = { Text("Titles stay in the library and lose this category assignment.") },
            confirmButton = {
                TextButton(onClick = {
                    delete()
                    confirmingDelete = false
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") }
            },
        )
    }
}

private fun LibraryCategoryUpdatePolicy.next(): LibraryCategoryUpdatePolicy = when (this) {
    LibraryCategoryUpdatePolicy.DEFAULT -> LibraryCategoryUpdatePolicy.INCLUDE
    LibraryCategoryUpdatePolicy.INCLUDE -> LibraryCategoryUpdatePolicy.EXCLUDE
    LibraryCategoryUpdatePolicy.EXCLUDE -> LibraryCategoryUpdatePolicy.DEFAULT
}

private fun LibraryCategoryUpdatePolicy.label(): String =
    name.lowercase().replaceFirstChar(Char::uppercase)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StatisticsScreen(
    library: LibraryPresentation,
    discovery: DiscoveryPresentation,
    player: PlayerPresentation,
    tracking: TrackerPresentation,
    goBack: () -> Unit,
) {
    var snapshot by remember(library, discovery, player, tracking) {
        mutableStateOf<StatisticsSnapshot?>(null)
    }
    var error by remember(library, discovery, player, tracking) { mutableStateOf<String?>(null) }
    LaunchedEffect(library, discovery, player, tracking) {
        runCatching {
            withContext(Dispatchers.IO) {
                statisticsSnapshot(library, discovery, player, tracking, Instant.now())
            }
        }.onSuccess {
            snapshot = it
            error = null
        }.onFailure {
            error = it.message ?: "Unable to calculate library statistics."
        }
    }
    MoreScaffold("Statistics", goBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            val value = snapshot
            if (value == null && error == null) {
                item { Text("Calculating statistics…") }
            } else if (value != null) {
                item { StatisticsHeading("Library") }
                item { SummaryCard("Titles", "${value.totalTitles} total") }
                item { SummaryCard("Anime / Manga", "${value.animeTitles} / ${value.mangaTitles}") }
                item { SummaryCard("Favorites", "${value.favoriteTitles} titles") }
                item { SummaryCard("Categories", "${value.categoryCount} custom categories") }
                item { StatisticsHeading("Publication status") }
                value.statuses.forEach { (label, count) ->
                    item(key = "status-$label") { SummaryCard(label, "$count titles") }
                }
                item { StatisticsHeading("Sources and languages") }
                value.sources.forEach { (label, count) ->
                    item(key = "source-$label") { SummaryCard(label, "$count titles") }
                }
                value.languages.forEach { (label, count) ->
                    item(key = "language-$label") { SummaryCard("Language $label", "$count titles") }
                }
                item { StatisticsHeading("Scores") }
                item {
                    SummaryCard(
                        "Average tracker score",
                        value.averageScore?.let { String.format(Locale.ROOT, "%.2f / 10", it) }
                            ?: "No scored entries",
                    )
                }
                value.scoreBuckets.forEach { (label, count) ->
                    item(key = "score-$label") { SummaryCard(label, "$count entries") }
                }
                item { StatisticsHeading("Duration and progress") }
                item { SummaryCard("Watched", durationLabel(value.watchedMillis)) }
                item { SummaryCard("Known episode duration", durationLabel(value.knownDurationMillis)) }
                item {
                    SummaryCard(
                        "Average title progress",
                        value.averageProgress?.let { "${(it * 100.0).toInt()}%" }
                            ?: "No measurable progress",
                    )
                }
                item { SummaryCard("Started", "${value.startedTitles} titles") }
                item { StatisticsHeading("Activity") }
                item { SummaryCard("Last 7 days", "${value.activity7Days} visits") }
                item { SummaryCard("Last 30 days", "${value.activity30Days} visits") }
                item { SummaryCard("Last 365 days", "${value.activity365Days} visits") }
                if (value.unavailableEpisodeSources > 0) {
                    item {
                        Text(
                            "Duration excludes ${value.unavailableEpisodeSources} unavailable anime sources.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatisticsHeading(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp),
    )
}

private data class StatisticsSnapshot(
    val totalTitles: Int,
    val animeTitles: Int,
    val mangaTitles: Int,
    val favoriteTitles: Int,
    val categoryCount: Int,
    val statuses: List<Pair<String, Int>>,
    val sources: List<Pair<String, Int>>,
    val languages: List<Pair<String, Int>>,
    val averageScore: Double?,
    val scoreBuckets: List<Pair<String, Int>>,
    val watchedMillis: Long,
    val knownDurationMillis: Long,
    val averageProgress: Double?,
    val startedTitles: Int,
    val activity7Days: Int,
    val activity30Days: Int,
    val activity365Days: Int,
    val unavailableEpisodeSources: Int,
)

private fun statisticsSnapshot(
    library: LibraryPresentation,
    discovery: DiscoveryPresentation,
    player: PlayerPresentation,
    tracking: TrackerPresentation,
    now: Instant,
): StatisticsSnapshot {
    val overview = library.library()
    val details = overview.titles().mapNotNull { library.details(it.id()).orElse(null) }
    val descriptors = details.mapNotNull { detail -> detail.origin().orElse(null)?.sourceId() }
        .distinct()
        .associateWith { sourceId ->
            runCatching { discovery.source(SourceId.of(sourceId)).orElse(null) }.getOrNull()
        }
    val sources = details.groupingBy { detail ->
        detail.origin().map { origin ->
            descriptors[origin.sourceId()]?.displayName() ?: origin.sourceId()
        }.orElse("Local")
    }.eachCount().sortedCounts()
    val languages = details.groupingBy { detail ->
        detail.origin().map { origin ->
            descriptors[origin.sourceId()]?.languageTag() ?: "Unknown"
        }.orElse("Local")
    }.eachCount().sortedCounts()
    val statuses = PublicationStatus.entries.mapNotNull { status ->
        details.count { it.publicationStatus() == status }
            .takeIf { it > 0 }
            ?.let { status.name.lowercase().replaceFirstChar(Char::uppercase) to it }
    }
    val scores = details.flatMap { detail ->
        runCatching { tracking.entries(detail.id()) }.getOrDefault(emptyList())
    }.map { entry -> entry.score().orElse(Double.NaN) }.filter(Double::isFinite)
    val scoreBuckets = listOf(
        "Score 0–3" to scores.count { it < 4.0 },
        "Score 4–6" to scores.count { it >= 4.0 && it < 7.0 },
        "Score 7–8" to scores.count { it >= 7.0 && it < 9.0 },
        "Score 9–10" to scores.count { it >= 9.0 },
    ).filter { it.second > 0 }
    var watchedMillis = 0L
    var knownDurationMillis = 0L
    var unavailableEpisodeSources = 0
    details.filter { it.kind() == MediaKind.ANIME }.forEach { detail ->
        runCatching { player.episodes(detail.id()) }
            .onSuccess { episodes ->
                episodes.mapNotNull { it.playback().orElse(null) }.forEach { playback ->
                    watchedMillis = saturatedAdd(watchedMillis, playback.positionMillis())
                    if (playback.durationMillis() >= 0) {
                        knownDurationMillis = saturatedAdd(
                            knownDurationMillis,
                            playback.durationMillis(),
                        )
                    }
                }
            }
            .onFailure { unavailableEpisodeSources++ }
    }
    val progress = details.mapNotNull { detail ->
        detail.progress().orElse(null)?.completion()?.orElse(Double.NaN)
            ?.takeIf(Double::isFinite)
    }
    val history = library.history().entries()
    return StatisticsSnapshot(
        totalTitles = details.size,
        animeTitles = details.count { it.kind() == MediaKind.ANIME },
        mangaTitles = details.count { it.kind() == MediaKind.MANGA },
        favoriteTitles = overview.favoriteCount(),
        categoryCount = overview.categories().size,
        statuses = statuses,
        sources = sources,
        languages = languages,
        averageScore = scores.takeIf { it.isNotEmpty() }?.average(),
        scoreBuckets = scoreBuckets,
        watchedMillis = watchedMillis,
        knownDurationMillis = knownDurationMillis,
        averageProgress = progress.takeIf { it.isNotEmpty() }?.average(),
        startedTitles = details.count { it.progress().isPresent },
        activity7Days = history.count { !it.openedAt().isBefore(now.minus(Duration.ofDays(7))) },
        activity30Days = history.count { !it.openedAt().isBefore(now.minus(Duration.ofDays(30))) },
        activity365Days = history.count { !it.openedAt().isBefore(now.minus(Duration.ofDays(365))) },
        unavailableEpisodeSources = unavailableEpisodeSources,
    )
}

private fun Map<String, Int>.sortedCounts(): List<Pair<String, Int>> = entries
    .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
    .map { it.key to it.value }

private fun saturatedAdd(left: Long, right: Long): Long =
    if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right

private fun durationLabel(milliseconds: Long): String {
    val minutes = milliseconds / 60_000L
    val hours = minutes / 60L
    val remaining = minutes % 60L
    return if (hours > 0) "$hours h $remaining min" else "$remaining min"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AboutScreen(
    componentCount: Int,
    updates: ApplicationUpdatePresentation,
    goBack: () -> Unit,
) {
    var snapshot by remember(updates) { mutableStateOf(updates.snapshot()) }
    var checking by remember(updates) { mutableStateOf(false) }
    var installing by remember(updates) { mutableStateOf(false) }
    var downloadedBytes by remember(updates) { mutableStateOf(0L) }
    var updateMessage by remember(updates) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val platformController = LocalApplicationUpdatePlatformController.current
    val available = snapshot.availableRelease().orElse(null)
    MoreScaffold("About", goBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Anilib", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "A cross-platform anime and manga library built from explicit, removable feature bundles.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SummaryCard("Runtime", "$componentCount feature bundles active")
            SummaryCard("Version", snapshot.currentVersion().display())
            SummaryCard(
                "Update channel",
                "${snapshot.channel().name.lowercase().replaceFirstChar(Char::uppercase)} · " +
                    snapshot.platform().name.lowercase(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ApplicationUpdateChannel.entries.forEach { channel ->
                    TextButton(
                        enabled = !checking && !installing && snapshot.channel() != channel,
                        onClick = {
                            snapshot = updates.setChannel(channel)
                            updateMessage = "${channel.name.lowercase().replaceFirstChar(Char::uppercase)} " +
                                "channel selected"
                        },
                    ) {
                        Text(channel.name.lowercase().replaceFirstChar(Char::uppercase))
                    }
                }
            }
            SummaryCard("Source format", "Signed portable Anilib Bundles")
            SummaryCard("Platforms", "Android and desktop")
            snapshot.error().orElse(null)?.let { error ->
                Text(error, color = MaterialTheme.colorScheme.error)
            }
            updateMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            if (available == null) {
                Button(
                    enabled = !checking,
                    onClick = {
                        checking = true
                        scope.launch {
                            snapshot = withContext(Dispatchers.IO) { updates.checkNow() }
                            checking = false
                        }
                    },
                ) {
                    Text(if (checking) "Checking…" else "Check for updates")
                }
            } else {
                Text(
                    "Anilib ${available.version().display()} is available.",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                if (available.changelog().isNotBlank()) {
                    SummaryCard("Changelog", available.changelog())
                }
                available.artifact().orElse(null)?.let { artifact ->
                    if (installing) {
                        Text("Downloaded $downloadedBytes of ${artifact.sizeBytes()} bytes")
                    }
                    Button(
                        enabled = !installing,
                        onClick = {
                            installing = true
                            downloadedBytes = 0L
                            updateMessage = "Downloading the signed installer…"
                            scope.launch {
                                runCatching {
                                    val path = platformController.download(artifact) {
                                        scope.launch { downloadedBytes = it }
                                    }
                                    val verification = withContext(Dispatchers.IO) {
                                        updates.verifyDownloadedArtifact(path)
                                    }
                                    platformController.install(verification)
                                }.onSuccess {
                                    updateMessage = "Installer verified; complete installation in the system prompt."
                                }.onFailure {
                                    updateMessage = it.message ?: "Application update failed"
                                }
                                installing = false
                            }
                        },
                    ) {
                        Text(if (installing) "Preparing…" else "Download and install")
                    }
                }
                Button(onClick = { uriHandler.openUri(available.releasePage().toString()) }) {
                    Text("Open release")
                }
                TextButton(onClick = { uriHandler.openUri(available.licensePage().toString()) }) {
                    Text("Licence")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { uriHandler.openUri("https://github.com/zolkers/Anilib") }) {
                    Text("Project")
                }
                TextButton(onClick = { uriHandler.openUri("https://github.com/zolkers/Anilib/issues") }) {
                    Text("Help")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    uriHandler.openUri("https://github.com/zolkers/Anilib/blob/main/THIRD_PARTY.md")
                }) {
                    Text("Third-party notices")
                }
                TextButton(onClick = {
                    uriHandler.openUri("https://github.com/zolkers/Anilib/blob/main/PRIVACY.md")
                }) {
                    Text("Privacy")
                }
            }
            Text(
                "Copyright 2026 Victor Riegert · Apache License 2.0",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoreScaffold(
    title: String,
    goBack: () -> Unit,
    content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = goBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        content = content,
    )
}

@Composable
private fun SummaryCard(title: String, summary: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
            Text(title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
            Text(summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
