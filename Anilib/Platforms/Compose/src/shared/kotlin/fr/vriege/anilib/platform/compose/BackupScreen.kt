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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import fr.vriege.anilib.feature.backup.BackupFileSnapshot
import fr.vriege.anilib.feature.backup.BackupInspection
import fr.vriege.anilib.feature.backup.ui.BackupPresentation
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val backupDateFormatter = DateTimeFormatter
    .ofLocalizedDateTime(FormatStyle.MEDIUM)
    .withLocale(Locale.getDefault())
    .withZone(ZoneId.systemDefault())

/** Shared Aniyomi-style local backup creation and restore management screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BackupScreen(presentation: BackupPresentation, goBack: () -> Unit) {
    var revision by remember(presentation) { mutableIntStateOf(0) }
    var message by remember(presentation) { mutableStateOf<String?>(null) }
    var error by remember(presentation) { mutableStateOf<String?>(null) }
    var pendingRestore by remember(presentation) { mutableStateOf<BackupInspection?>(null) }
    var pendingDelete by remember(presentation) { mutableStateOf<BackupFileSnapshot?>(null) }
    DisposableEffect(presentation) {
        val registration = presentation.observe { revision++ }
        onDispose { runCatching { registration.close() } }
    }
    val backups = remember(presentation, revision) { presentation.backups() }

    val createBackup = {
        runCatching { presentation.createBackup() }
            .onSuccess {
                message = "Backup created: ${it.path().fileName}"
                error = null
            }
            .onFailure {
                error = it.message ?: "Backup creation failed."
                message = null
            }
        Unit
    }
    val requestRestore: (BackupFileSnapshot) -> Unit = { backup ->
        runCatching { presentation.inspect(backup.path()) }
            .onSuccess {
                pendingRestore = it
                error = null
            }
            .onFailure { error = it.message ?: "Backup inspection failed." }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup and restore") },
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
            Text(
                "Create a local backup of your library, history, progress, and source settings.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = createBackup, modifier = Modifier.fillMaxWidth()) {
                Text("Create backup")
            }
            Text(
                presentation.backupDirectory().toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 10.dp),
            )
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(8.dp))
            if (backups.isEmpty()) {
                EmptyPage("No local backups yet.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(backups, key = { it.path().toString() }) { backup ->
                        BackupCard(
                            backup = backup,
                            restore = { requestRestore(backup) },
                            delete = { pendingDelete = backup },
                        )
                    }
                }
            }
        }
    }

    pendingRestore?.let { inspection ->
        RestoreDialog(
            inspection = inspection,
            dismiss = { pendingRestore = null },
            confirm = {
                runCatching { presentation.restore(inspection.path()) }
                    .onSuccess {
                        message = "Restored ${it.entryCount()} entries from " +
                            "${it.restoredSections().size} sections."
                        error = null
                    }
                    .onFailure {
                        error = it.message ?: "Backup restore failed."
                        message = null
                    }
                pendingRestore = null
            },
        )
    }
    pendingDelete?.let { backup ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete backup?") },
            text = { Text(backup.path().fileName.toString()) },
            confirmButton = {
                TextButton(
                    onClick = {
                        runCatching { presentation.delete(backup.path()) }
                            .onSuccess {
                                message = "Backup deleted."
                                error = null
                            }
                            .onFailure { error = it.message ?: "Backup deletion failed." }
                        pendingDelete = null
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun BackupCard(
    backup: BackupFileSnapshot,
    restore: () -> Unit,
    delete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(backup.path().fileName.toString(), fontWeight = FontWeight.SemiBold)
            Text(
                backupDateFormatter.format(backup.createdAt()),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "${backup.sectionCount()} sections • ${backup.entryCount()} entries • " +
                    formatBackupBytes(backup.sizeBytes()),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = restore) {
                    Icon(Icons.Default.Restore, contentDescription = null)
                    Text("Restore", modifier = Modifier.padding(start = 6.dp))
                }
                IconButton(onClick = delete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete backup")
                }
            }
        }
    }
}

@Composable
private fun RestoreDialog(
    inspection: BackupInspection,
    dismiss: () -> Unit,
    confirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Restore backup?") },
        text = {
            Column {
                Text("Existing entries are updated; newer entries not in this backup are kept.")
                Spacer(Modifier.height(10.dp))
                inspection.sections().forEach { section ->
                    val availability = if (section.restorable()) "" else " (not installed)"
                    Text("• ${section.displayName()}: ${section.entryCount()}$availability")
                }
            }
        },
        confirmButton = { TextButton(onClick = confirm) { Text("Restore") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
    )
}

private fun formatBackupBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format(Locale.ROOT, "%.1f MB", bytes / 1048576.0)
    bytes >= 1024L -> String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}
