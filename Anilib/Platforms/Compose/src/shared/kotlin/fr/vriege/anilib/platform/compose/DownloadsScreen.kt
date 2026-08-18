package fr.vriege.anilib.platform.compose

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import fr.vriege.anilib.feature.downloads.DownloadJobSnapshot
import fr.vriege.anilib.feature.downloads.DownloadStatus
import fr.vriege.anilib.feature.downloads.ui.DownloadPresentation
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DownloadsScreen(presentation: DownloadPresentation, goBack: () -> Unit) {
    var revision by remember(presentation) { mutableIntStateOf(0) }
    var commandError by remember(presentation) { mutableStateOf<String?>(null) }
    var filter by remember(presentation) { mutableStateOf(DownloadFilter.ALL) }
    DisposableEffect(presentation) {
        val registration = presentation.observe { revision++ }
        onDispose { runCatching { registration.close() } }
    }
    val queue = remember(presentation, revision) { presentation.queue() }
    val jobs = queue.jobs().filter(filter::accepts)
    val command: (() -> Unit) -> Unit = { action ->
        runCatching(action)
            .onSuccess { commandError = null }
            .onFailure { commandError = it.message ?: "Download command failed." }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Download queue") },
                navigationIcon = {
                    IconButton(onClick = goBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
        ) {
            DownloadQueueControls(
                offlineMode = queue.offlineMode(),
                pauseAll = { command(presentation::pauseAll) },
                resumeAll = { command(presentation::resumeAll) },
                setOfflineMode = { enabled -> command { presentation.setOfflineMode(enabled) } },
            )
            Text(
                text = "${formatBytes(queue.usedStorageBytes())} of " +
                    "${formatBytes(queue.maximumStorageBytes())} used",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = {
                    (queue.usedStorageBytes().toDouble() / queue.maximumStorageBytes()).toFloat()
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            )
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
            commandError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(8.dp))
            if (queue.jobs().isEmpty()) {
                EmptyPage("Your download queue is empty.")
            } else if (jobs.isEmpty()) {
                EmptyPage("No downloads match the active filter.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(jobs, key = { it.id().toString() }) { job ->
                        DownloadJobCard(
                            job = job,
                            offlineMode = queue.offlineMode(),
                            pause = { command { presentation.pause(job.id()) } },
                            resume = { command { presentation.resume(job.id()) } },
                            cancel = { command { presentation.cancel(job.id()) } },
                            remove = { command { presentation.remove(job.id()) } },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadQueueControls(
    offlineMode: Boolean,
    pauseAll: () -> Unit,
    resumeAll: () -> Unit,
    setOfflineMode: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row {
            IconButton(onClick = pauseAll) {
                Icon(Icons.Default.Pause, contentDescription = "Pause all")
            }
            IconButton(onClick = resumeAll, enabled = !offlineMode) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Resume all")
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Offline mode", fontWeight = FontWeight.Medium)
            Switch(checked = offlineMode, onCheckedChange = setOfflineMode)
        }
    }
}

@Composable
private fun DownloadJobCard(
    job: DownloadJobSnapshot,
    offlineMode: Boolean,
    pause: () -> Unit,
    resume: () -> Unit,
    cancel: () -> Unit,
    remove: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(job.title(), fontWeight = FontWeight.SemiBold)
            Text(job.contentUnit().title(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { job.progress().toFloat() },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "${formatStatus(job.status())} • ${job.completedPages()} / " +
                    "${job.totalPages()} pages • ${formatBytes(job.downloadedBytes())}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            val error = job.error().orElse(null)
            if (error != null) {
                Text(error, color = MaterialTheme.colorScheme.error)
            }
            Row(modifier = Modifier.align(Alignment.End)) {
                when (job.status()) {
                    DownloadStatus.QUEUED,
                    DownloadStatus.DOWNLOADING,
                    -> IconButton(onClick = pause) {
                        Icon(Icons.Default.Pause, contentDescription = "Pause")
                    }
                    DownloadStatus.PAUSED,
                    DownloadStatus.FAILED,
                    -> IconButton(onClick = resume, enabled = !offlineMode) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Resume")
                    }
                    else -> Unit
                }
                if (job.status() != DownloadStatus.COMPLETED &&
                    job.status() != DownloadStatus.CANCELLED
                ) {
                    IconButton(onClick = cancel) {
                        Icon(Icons.Default.Cancel, contentDescription = "Cancel")
                    }
                }
                if (job.status() == DownloadStatus.COMPLETED ||
                    job.status() == DownloadStatus.CANCELLED
                ) {
                    IconButton(onClick = remove) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove")
                    }
                }
            }
        }
    }
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
