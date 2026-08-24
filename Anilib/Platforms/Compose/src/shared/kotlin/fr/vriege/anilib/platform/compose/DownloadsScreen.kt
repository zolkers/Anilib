package fr.vriege.anilib.platform.compose

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.AutoMode
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.vriege.anilib.feature.downloads.AutomaticDownloadCategoryRule
import fr.vriege.anilib.feature.downloads.AutomaticDownloadPolicy
import fr.vriege.anilib.feature.downloads.DownloadCleanupPolicy
import fr.vriege.anilib.feature.downloads.DownloadContentType
import fr.vriege.anilib.feature.downloads.DownloadJobSnapshot
import fr.vriege.anilib.feature.downloads.DownloadPriority
import fr.vriege.anilib.feature.downloads.DownloadRecoveryMode
import fr.vriege.anilib.feature.downloads.DownloadStatus
import fr.vriege.anilib.feature.downloads.DownloadStorageSnapshot
import fr.vriege.anilib.feature.downloads.ui.DownloadPresentation
import fr.vriege.anilib.feature.library.LibraryItemId
import java.nio.file.Path
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DownloadsScreen(presentation: DownloadPresentation, goBack: () -> Unit) {
    val scope = rememberCrashSafeCoroutineScope()
    var commandError by remember(presentation) { mutableStateOf<String?>(null) }
    var filter by remember(presentation) { mutableStateOf(DownloadFilter.ALL) }
    var confirmRemoveAll by remember(presentation) { mutableStateOf(false) }
    var storageDialog by remember(presentation) { mutableStateOf(false) }
    var automationDialog by remember(presentation) { mutableStateOf(false) }
    var repairMessage by remember(presentation) { mutableStateOf<String?>(null) }
    var confirmRemoveTitle by remember(presentation) {
        mutableStateOf<Pair<LibraryItemId, String>?>(null)
    }
    val preparing = LocalDownloadPreparationState.current?.pendingCount ?: 0
    val queue = rememberDownloadQueueSnapshot(presentation)
    if (queue == null) {
        DownloadQueueLoading(goBack)
        return
    }
    val jobs = queue.jobs().filter(filter::accepts)
    val command: (() -> Unit) -> Unit = { action ->
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { action() } }
                .onSuccess { commandError = null }
                .onFailure { commandError = it.message ?: "Download command failed." }
        }
    }

    AnilibSubScreenScaffold(
        title = "Download queue",
        goBack = goBack,
        actions = {
            IconButton(onClick = { command(presentation::pauseAll) }) {
                Icon(Icons.Default.Pause, contentDescription = "ui.pause.all")
            }
            IconButton(onClick = { command(presentation::resumeAll) }, enabled = !queue.offlineMode()) {
                Icon(Icons.Default.PlayArrow, contentDescription = "ui.resume.all")
            }
            IconButton(onClick = { confirmRemoveAll = true }, enabled = queue.jobs().isNotEmpty()) {
                Icon(Icons.Default.Delete, contentDescription = "ui.delete.all")
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
                    DownloadQueueControls(
                        offlineMode = queue.offlineMode(),
                        usedStorageBytes = queue.usedStorageBytes(),
                        maximumStorageBytes = queue.maximumStorageBytes(),
                        concurrentJobs = queue.concurrentJobs(),
                        activeJobs = queue.jobs().count { it.status() == DownloadStatus.DOWNLOADING },
                        queuedJobs = queue.jobs().count { it.status() == DownloadStatus.QUEUED },
                        openStorage = { storageDialog = true },
                        openAutomation = { automationDialog = true },
                        setOfflineMode = { enabled -> command { presentation.setOfflineMode(enabled) } },
                        setConcurrentJobs = { jobs ->
                            command { presentation.configureConcurrentJobs(jobs) }
                        },
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        DownloadFilter.entries.forEach { value ->
                            FilterChip(
                                selected = filter == value,
                                onClick = { filter = value },
                                label = { Text(value.label) },
                            )
                        }
                    }
                }
                commandError?.let { message -> item { DownloadMessageSurface(message, true) } }
                repairMessage?.let { message -> item { DownloadMessageSurface(message, false) } }
                if (preparing > 0) {
                    item(key = "download-preparation") { DownloadPreparationCard() }
                }
                if (queue.jobs().isEmpty()) {
                    if (preparing == 0) {
                        item { EmptyPage("ui.download.queue.empty") }
                    }
                } else if (jobs.isEmpty()) {
                    item { EmptyPage("ui.no.downloads.match.filter") }
                } else {
                    jobs.groupBy { it.libraryItemId() }.values.forEach { group ->
                        item(key = "group-${group.first().libraryItemId().value()}") {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "${group.first().title()} · ${group.size}",
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = {
                                    command { presentation.pauseTitle(group.first().libraryItemId()) }
                                }) { Icon(Icons.Default.Pause, contentDescription = "ui.pause.title") }
                                IconButton(onClick = {
                                    command { presentation.resumeTitle(group.first().libraryItemId()) }
                                }, enabled = !queue.offlineMode()) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "ui.resume.title")
                                }
                                IconButton(onClick = {
                                    confirmRemoveTitle = group.first().libraryItemId() to group.first().title()
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "ui.delete.title.downloads")
                                }
                            }
                        }
                        items(group, key = { it.id().toString() }) { job ->
                            DownloadJobCard(
                                job = job,
                                offlineMode = queue.offlineMode(),
                                pause = { command { presentation.pause(job.id()) } },
                                resume = { command { presentation.resume(job.id()) } },
                                retry = { mode -> command { presentation.retry(job.id(), mode) } },
                                setPriority = { priority ->
                                    command { presentation.setPriority(job.id(), priority) }
                                },
                                move = { position -> command { presentation.move(job.id(), position) } },
                                cancel = { command { presentation.cancel(job.id()) } },
                                remove = { command { presentation.remove(job.id()) } },
                            )
                        }
                    }
                }
            }
        }
    }
    if (confirmRemoveAll) {
        AlertDialog(
            onDismissRequest = { confirmRemoveAll = false },
            title = { Text("ui.delete.all.downloads") },
            text = { Text("ui.completed.files.and.partial.data.will.be.permanently.removed") },
            confirmButton = {
                TextButton(onClick = {
                    command(presentation::removeAll)
                    confirmRemoveAll = false
                }) { Text("ui.delete.all") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemoveAll = false }) { Text("ui.cancel") }
            },
        )
    }
    if (storageDialog) {
        DownloadStorageDialog(
            presentation = presentation,
            maximumStorageBytes = queue.maximumStorageBytes(),
            repair = {
                scope.launch {
                    runCatching { withContext(Dispatchers.IO) { presentation.repairIndex() } }
                        .onSuccess { result ->
                            repairMessage = "Index repaired: ${result.repairedJobs()} jobs, " +
                                "${result.orphanedDirectoriesRemoved()} orphans, " +
                                formatBytes(result.indexedBytes())
                        }
                        .onFailure { commandError = it.message ?: "Index repair failed." }
                }
            },
            close = { storageDialog = false },
        )
    }
    if (automationDialog) {
        AutomaticDownloadDialog(
            presentation = presentation,
            save = { policy ->
                var error: String? = null
                runCatching { presentation.configureAutomaticDownloads(policy) }
                    .onSuccess { automationDialog = false }
                    .onFailure { error = it.message ?: "Unable to save automatic download rules." }
                error
            },
            synchronize = {
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            presentation.synchronizeAutomaticDownloads()
                        }
                    }.onSuccess { result ->
                        repairMessage = "Automatic downloads: ${result.enqueuedJobs()} queued, " +
                            "${result.removedJobs()} cleaned, ${result.failures().size} failed"
                    }.onFailure { commandError = it.message ?: "Automatic download scan failed." }
                }
            },
            clean = {
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) { presentation.cleanAutomaticDownloads() }
                    }.onSuccess { removed -> repairMessage = "$removed downloads cleaned" }
                        .onFailure { commandError = it.message ?: "Download cleanup failed." }
                }
            },
            close = { automationDialog = false },
        )
    }
    confirmRemoveTitle?.let { (itemId, title) ->
        AlertDialog(
            onDismissRequest = { confirmRemoveTitle = null },
            title = {
                Text(UiTranslations.format("dynamic.delete.downloads.question", LocalLanguagePack.current, title))
            },
            text = { Text("ui.all.completed.files.and.partial.jobs.for.this.title.will.be.removed") },
            confirmButton = {
                TextButton(onClick = {
                    command { presentation.removeTitle(itemId) }
                    confirmRemoveTitle = null
                }) { Text("ui.delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemoveTitle = null }) { Text("ui.cancel") }
            },
        )
    }
}

@Composable
private fun DownloadPreparationCard() {
    AnilibGroup {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
            Text("ui.download.preparing", fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun DownloadQueueLoading(goBack: () -> Unit) {
    AnilibSubScreenScaffold(title = "ui.download.queue", goBack = goBack) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun DownloadQueueControls(
    offlineMode: Boolean,
    usedStorageBytes: Long,
    maximumStorageBytes: Long,
    concurrentJobs: Int,
    activeJobs: Int,
    queuedJobs: Int,
    openStorage: () -> Unit,
    openAutomation: () -> Unit,
    setOfflineMode: (Boolean) -> Unit,
    setConcurrentJobs: (Int) -> Unit,
) {
    AnilibGroup {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("ui.offline.mode", fontWeight = FontWeight.Medium)
                    Text(
                        "ui.use.downloaded.content.without.the.online.fallback",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Switch(checked = offlineMode, onCheckedChange = setOfflineMode)
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 14.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("ui.simultaneous.downloads", fontWeight = FontWeight.Medium)
                    Text(
                        UiTranslations.format(
                            "dynamic.download.queue.activity",
                            LocalLanguagePack.current,
                            activeJobs,
                            queuedJobs,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                IconButton(
                    onClick = { setConcurrentJobs(concurrentJobs - 1) },
                    enabled = concurrentJobs > 1,
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "ui.decrease")
                }
                Text(concurrentJobs.toString(), fontWeight = FontWeight.Bold)
                IconButton(
                    onClick = { setConcurrentJobs(concurrentJobs + 1) },
                    enabled = concurrentJobs < 8,
                ) {
                    Icon(Icons.Default.Add, contentDescription = "ui.increase")
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 14.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Text("ui.storage", fontWeight = FontWeight.Medium)
            Text(
                UiTranslations.format(
                    "dynamic.storage.used",
                    LocalLanguagePack.current,
                    formatBytes(usedStorageBytes),
                    formatBytes(maximumStorageBytes),
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            LinearProgressIndicator(
                progress = {
                    if (maximumStorageBytes <= 0L) 0f else
                        (usedStorageBytes.toDouble() / maximumStorageBytes).toFloat().coerceIn(0f, 1f)
                },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                TextButton(onClick = openStorage, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Storage, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("ui.storage")
                }
                TextButton(onClick = openAutomation, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.AutoMode, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("ui.automatic")
                }
            }
        }
    }
}

@Composable
private fun DownloadMessageSurface(message: String, error: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = if (error) MaterialTheme.colorScheme.errorContainer else
            MaterialTheme.colorScheme.primaryContainer,
    ) {
        Text(
            message,
            color = if (error) MaterialTheme.colorScheme.onErrorContainer else
                MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun AutomaticDownloadDialog(
    presentation: DownloadPresentation,
    save: (AutomaticDownloadPolicy) -> String?,
    synchronize: () -> Unit,
    clean: () -> Unit,
    close: () -> Unit,
) {
    val current = remember(presentation) { presentation.automaticPolicy() }
    var enabled by remember(presentation) { mutableStateOf(current.enabled()) }
    var favoritesOnly by remember(presentation) { mutableStateOf(current.favoritesOnly()) }
    var includeUncategorized by remember(presentation) {
        mutableStateOf(current.includeUncategorized())
    }
    var episodeLimit by remember(presentation) {
        mutableStateOf(current.defaultEpisodeLimit().toString())
    }
    var chapterLimit by remember(presentation) {
        mutableStateOf(current.defaultChapterLimit().toString())
    }
    var retention by remember(presentation) {
        mutableStateOf(current.retainedCompletedPerTitle().toString())
    }
    var cleanup by remember(presentation) { mutableStateOf(current.cleanupPolicy()) }
    var categoryRules by remember(presentation) {
        mutableStateOf(current.categoryRules().joinToString("\n") {
            "${it.category()}:${it.episodeLimit()}:${it.chapterLimit()}"
        })
    }
    var error by remember(presentation) { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("ui.automatic.downloads") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    AutomaticToggleRow("Enable after successful library updates", enabled) { enabled = it }
                    AutomaticToggleRow("Favorites only", favoritesOnly) { favoritesOnly = it }
                    AutomaticToggleRow("Include titles without a category", includeUncategorized) {
                        includeUncategorized = it
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = episodeLimit,
                            onValueChange = { episodeLimit = it },
                            label = { Text("ui.episode.limit") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = chapterLimit,
                            onValueChange = { chapterLimit = it },
                            label = { Text("ui.chapter.limit") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                    }
                }
                item {
                    Text("ui.cleanup.policy", fontWeight = FontWeight.Medium)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        DownloadCleanupPolicy.entries.forEach { value ->
                            FilterChip(
                                selected = cleanup == value,
                                onClick = { cleanup = value },
                                label = {
                                    Text(value.name.replace('_', ' ').lowercase().replaceFirstChar {
                                        it.uppercase()
                                    })
                                },
                                modifier = Modifier.padding(end = 6.dp),
                            )
                        }
                    }
                    if (cleanup == DownloadCleanupPolicy.KEEP_LATEST) {
                        OutlinedTextField(
                            value = retention,
                            onValueChange = { retention = it },
                            label = { Text("ui.completed.items.retained.per.title") },
                            singleLine = true,
                        )
                    }
                }
                item {
                    OutlinedTextField(
                        value = categoryRules,
                        onValueChange = { categoryRules = it },
                        label = { Text("ui.category.rules") },
                        supportingText = { Text("ui.one.per.line.category.episodes.chapters") },
                        minLines = 3,
                    )
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Row {
                        TextButton(onClick = synchronize) { Text("ui.run.now") }
                        TextButton(onClick = clean) { Text("ui.clean.now") }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = runCatching {
                    AutomaticDownloadPolicy(
                        enabled,
                        favoritesOnly,
                        includeUncategorized,
                        episodeLimit.toInt(),
                        chapterLimit.toInt(),
                        cleanup,
                        retention.toInt(),
                        parseAutomaticRules(categoryRules),
                    )
                }.fold(save) { it.message ?: "Invalid automatic download rules." }
            }) { Text("ui.save") }
        },
        dismissButton = { TextButton(onClick = close) { Text("ui.cancel") } },
    )
}

private fun parseAutomaticRules(value: String): List<AutomaticDownloadCategoryRule> =
    value.lineSequence()
        .filter(String::isNotBlank)
        .map { line ->
            val parts = line.split(':')
            require(parts.size == 3) { "Each category rule needs a name and two limits." }
            AutomaticDownloadCategoryRule(parts[0].trim(), parts[1].trim().toInt(), parts[2].trim().toInt())
        }
        .toList()

@Composable
private fun AutomaticToggleRow(label: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = change)
    }
}

@Composable
private fun DownloadStorageDialog(
    presentation: DownloadPresentation,
    maximumStorageBytes: Long,
    repair: () -> Unit,
    close: () -> Unit,
) {
    val scope = rememberCrashSafeCoroutineScope()
    var storage by remember(presentation) {
        mutableStateOf<DownloadStorageSnapshot?>(null)
    }
    var location by remember(presentation) { mutableStateOf("") }
    var maximumGiB by remember(presentation, maximumStorageBytes) {
        mutableStateOf((maximumStorageBytes / GIBIBYTE_BYTES).toString())
    }
    var saving by remember(presentation) { mutableStateOf(false) }
    var error by remember(presentation) { mutableStateOf<String?>(null) }
    CrashSafeLaunchedEffect(presentation) {
        storage = withContext(Dispatchers.IO) { presentation.storage() }
        location = storage?.location()?.toString().orEmpty()
    }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("ui.download.storage") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val currentStorage = storage
                if (currentStorage == null) {
                    CircularProgressIndicator()
                } else {
                    Text(
                        if (currentStorage.writable()) {
                            "Writable · ${formatBytes(currentStorage.availableBytes())} available"
                        } else {
                            "Storage is not writable"
                        },
                    )
                }
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("ui.storage.directory") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = maximumGiB,
                    onValueChange = { maximumGiB = it.filter(Char::isDigit) },
                    label = { Text("ui.maximum.download.storage.gib") },
                    supportingText = { Text("ui.maximum.download.storage.gib.description") },
                    singleLine = true,
                )
                Text(
                    "ui.changing.this.path.validates.the.destination.copies.every.indexed.page.then.removes." +
                        "the.old.managed.copies",
                )
                TextButton(onClick = repair) { Text("ui.repair.download.index") }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (saving) return@TextButton
                saving = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            val maximumBytes = parseStorageLimit(maximumGiB)
                            presentation.configureMaximumStorageBytes(maximumBytes)
                            val currentLocation = storage?.location()?.toString()
                            if (location != currentLocation) {
                                presentation.changeStorageLocation(Path.of(location))
                            }
                        }
                    }
                    result.onSuccess { close() }
                        .onFailure { error = it.message ?: "Unable to update download storage." }
                    saving = false
                }
            }, enabled = !saving && storage != null) { Text("ui.save") }
        },
        dismissButton = { TextButton(onClick = close) { Text("ui.cancel") } },
    )
}

private fun parseStorageLimit(value: String): Long {
    val gibibytes = value.toLongOrNull()
        ?: throw IllegalArgumentException("Storage limit must be a whole number of GiB")
    require(gibibytes > 0L) { "Storage limit must be at least 1 GiB" }
    return Math.multiplyExact(gibibytes, GIBIBYTE_BYTES)
}

@Composable
private fun DownloadJobCard(
    job: DownloadJobSnapshot,
    offlineMode: Boolean,
    pause: () -> Unit,
    resume: () -> Unit,
    retry: (DownloadRecoveryMode) -> Unit,
    setPriority: (DownloadPriority) -> Unit,
    move: (Int) -> Unit,
    cancel: () -> Unit,
    remove: () -> Unit,
) {
    AnilibGroup {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(job.title(), fontWeight = FontWeight.SemiBold)
            Text(job.contentUnit().title(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { job.progress().toFloat() },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = UiTranslations.format(
                    if (job.contentType() == DownloadContentType.VIDEO) {
                        "dynamic.video.download.progress"
                    } else {
                        "dynamic.download.progress"
                    },
                    LocalLanguagePack.current,
                    formatStatus(job.status()),
                    job.completedPages(),
                    job.totalPages(),
                    formatBytes(job.downloadedBytes()),
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            if (job.bytesPerSecond() > 0L) {
                val eta = job.estimatedRemainingMillis().map(::formatDuration).orElse("Calculating ETA")
                Text(
                    "${formatBytes(job.bytesPerSecond())}/s · $eta",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val error = job.error().orElse(null)
            if (error != null) {
                Text(error, color = MaterialTheme.colorScheme.error)
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { setPriority(nextPriority(job.priority())) }) {
                    Text(job.priority().name.lowercase().replaceFirstChar(Char::uppercase))
                }
                IconButton(onClick = { move((job.queuePosition() - 1).coerceAtLeast(0)) }) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = "ui.move.up")
                }
                IconButton(onClick = { move(job.queuePosition() + 1) }) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = "ui.move.down")
                }
                when (job.status()) {
                    DownloadStatus.QUEUED,
                    DownloadStatus.DOWNLOADING,
                    -> IconButton(onClick = pause) {
                        Icon(Icons.Default.Pause, contentDescription = "ui.pause")
                    }
                    DownloadStatus.PAUSED,
                    DownloadStatus.FAILED,
                    -> IconButton(onClick = resume, enabled = !offlineMode) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "ui.resume")
                    }
                    else -> Unit
                }
                if (job.status() == DownloadStatus.FAILED && job.hasPartialData()) {
                    TextButton(onClick = { retry(DownloadRecoveryMode.RESTART) }) {
                        Text("ui.restart")
                    }
                }
                if (job.status() != DownloadStatus.COMPLETED &&
                    job.status() != DownloadStatus.CANCELLED
                ) {
                    IconButton(onClick = cancel) {
                        Icon(Icons.Default.Cancel, contentDescription = "ui.cancel")
                    }
                }
                if (job.status() == DownloadStatus.COMPLETED ||
                    job.status() == DownloadStatus.CANCELLED
                ) {
                    IconButton(onClick = remove) {
                        Icon(Icons.Default.Delete, contentDescription = "ui.remove")
                    }
                }
            }
        }
    }
}

private const val GIBIBYTE_BYTES = 1024L * 1024L * 1024L

private fun nextPriority(priority: DownloadPriority): DownloadPriority {
    val values = DownloadPriority.entries
    return values[(priority.ordinal + 1) % values.size]
}

private fun formatDuration(milliseconds: Long): String {
    val seconds = milliseconds / 1000L
    val minutes = seconds / 60L
    return if (minutes > 0L) "${minutes}m ${seconds % 60L}s left" else "${seconds}s left"
}

private fun formatStatus(status: DownloadStatus): String = status.name
    .replace('_', ' ')
    .lowercase(Locale.ROOT)
    .replaceFirstChar(Char::uppercase)

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> String.format(Locale.ROOT, "%.1f GB", bytes / 1073741824.0)
    bytes >= 1024L * 1024L -> String.format(Locale.ROOT, "%.1f MB", bytes / 1048576.0)
    bytes >= 1024L -> String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}

private enum class DownloadFilter(val label: String) {
    ALL("All") {
        override fun accepts(job: DownloadJobSnapshot): Boolean = true
    },
    ACTIVE("Active") {
        override fun accepts(job: DownloadJobSnapshot): Boolean = job.status() in setOf(
            DownloadStatus.QUEUED,
            DownloadStatus.DOWNLOADING,
            DownloadStatus.PAUSED,
        )
    },
    COMPLETED("Completed") {
        override fun accepts(job: DownloadJobSnapshot): Boolean = job.status() == DownloadStatus.COMPLETED
    },
    FAILED("Failed") {
        override fun accepts(job: DownloadJobSnapshot): Boolean = job.status() == DownloadStatus.FAILED
    },
    ;

    abstract fun accepts(job: DownloadJobSnapshot): Boolean
}
