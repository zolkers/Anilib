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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
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
import fr.vriege.anilib.feature.backup.BackupContentOption
import fr.vriege.anilib.feature.backup.BackupPolicy
import fr.vriege.anilib.feature.backup.BackupSchedule
import fr.vriege.anilib.feature.backup.AniyomiBackupInspection
import fr.vriege.anilib.feature.backup.ui.BackupImportFormat
import fr.vriege.anilib.feature.backup.ui.BackupImportPreview
import fr.vriege.anilib.feature.backup.ui.BackupPresentation
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val backupDateFormatter = DateTimeFormatter
    .ofLocalizedDateTime(FormatStyle.MEDIUM)
    .withLocale(Locale.getDefault())
    .withZone(ZoneId.systemDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BackupScreen(
    presentation: BackupPresentation,
    importPicker: BackupImportPicker,
    goBack: () -> Unit,
) {
    val scope = rememberCrashSafeCoroutineScope()
    var revision by remember(presentation) { mutableIntStateOf(0) }
    var message by remember(presentation) { mutableStateOf<String?>(null) }
    var error by remember(presentation) { mutableStateOf<String?>(null) }
    var pendingRestore by remember(presentation) { mutableStateOf<BackupInspection?>(null) }
    var pendingImport by remember(presentation) { mutableStateOf<BackupImportPreview?>(null) }
    var importBusy by remember(presentation) { mutableStateOf(false) }
    var pendingDelete by remember(presentation) { mutableStateOf<BackupFileSnapshot?>(null) }
    var editingPolicy by remember(presentation) { mutableStateOf(false) }
    DisposableEffect(presentation) {
        val registration = presentation.observe { revision++ }
        onDispose { runCatching { registration.close() } }
    }
    val pendingImportPath = pendingImport?.path()
    DisposableEffect(pendingImportPath) {
        onDispose { pendingImportPath?.let(importPicker::release) }
    }
    val backups = remember(presentation, revision) { presentation.backups() }
    val policy = remember(presentation, revision) { presentation.policy() }
    val contentOptions = remember(presentation, revision) { presentation.contentOptions() }

    val createBackup = {
        scope.launch {
            withContext(Dispatchers.IO) { runCatching { presentation.createBackup() } }
                .onSuccess {
                    message = "Backup created: ${it.path().fileName}"
                    error = null
                }
                .onFailure {
                    error = it.message ?: "Backup creation failed."
                    message = null
                }
        }
        Unit
    }
    val requestRestore: (BackupFileSnapshot) -> Unit = { backup ->
        scope.launch {
            withContext(Dispatchers.IO) { runCatching { presentation.inspect(backup.path()) } }
                .onSuccess {
                    pendingRestore = it
                    error = null
                }
                .onFailure { error = it.message ?: "Backup inspection failed." }
        }
    }
    val chooseBackup = {
        importPicker.choose(
            { path ->
                scope.launch {
                    withContext(Dispatchers.IO) { runCatching { presentation.inspectImport(path) } }
                        .onSuccess {
                            pendingImport = it
                            error = null
                        }
                        .onFailure {
                            importPicker.release(path)
                            error = "The selected file is not a supported Anilib or Aniyomi backup."
                            message = null
                        }
                }
            },
            { failure ->
                error = failure
                message = null
            },
        )
        Unit
    }

    AnilibSubScreenScaffold(title = "Backup and restore", goBack = goBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                BackupOverview(
                    directory = presentation.backupDirectory().toString(),
                    importEnabled = pendingImport == null && !importBusy,
                    create = createBackup,
                    editPolicy = { editingPolicy = true },
                    chooseBackup = chooseBackup,
                )
            }
            message?.let { value -> item { Text(value, color = MaterialTheme.colorScheme.primary) } }
            error?.let { value -> item { Text(value, color = MaterialTheme.colorScheme.error) } }
            if (backups.isEmpty()) {
                item { EmptyPage("No local backups yet.") }
            } else {
                items(backups, key = { it.path().toString() }) { backup ->
                    BackupCard(
                        backup = backup,
                        restore = { requestRestore(backup) },
                        delete = { pendingDelete = backup },
                        export = {
                            importPicker.export(
                                backup.path(),
                                { destination ->
                                    message = "Backup exported: $destination"
                                    error = null
                                },
                                { failure -> error = failure },
                            )
                        },
                        share = {
                            importPicker.share(backup.path()) { failure -> error = failure }
                        },
                    )
                }
            }
        }
    }

    pendingRestore?.let { inspection ->
        RestoreDialog(
            inspection = inspection,
            dismiss = { pendingRestore = null },
            confirm = {
                pendingRestore = null
                scope.launch {
                    withContext(Dispatchers.IO) { runCatching { presentation.restore(inspection.path()) } }
                        .onSuccess {
                            message = "Restored ${it.entryCount()} entries from " +
                                "${it.restoredSections().size} sections."
                            error = null
                        }
                        .onFailure {
                            error = it.message ?: "Backup restore failed."
                            message = null
                        }
                }
            },
        )
    }
    if (editingPolicy) {
        BackupPolicyDialog(
            policy = policy,
            options = contentOptions,
            dismiss = { editingPolicy = false },
            save = { replacement ->
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) { presentation.savePolicy(replacement) }
                    }.onSuccess {
                        message = "Backup policy saved."
                        error = null
                        editingPolicy = false
                    }.onFailure { error = it.message ?: "Unable to save backup policy." }
                }
            },
        )
    }
    pendingImport?.let { preview ->
        when (preview.format()) {
            BackupImportFormat.ANILIB -> RestoreDialog(
                inspection = preview.anilib().orElseThrow(),
                enabled = !importBusy,
                dismiss = { if (!importBusy) pendingImport = null },
                confirm = {
                    importBusy = true
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            runCatching { presentation.restore(preview.path()) }
                        }.onSuccess {
                            message = "Restored ${it.entryCount()} entries from " +
                                "${it.restoredSections().size} sections."
                            error = null
                        }.onFailure {
                            error = it.message ?: "Backup restore failed."
                            message = null
                        }
                        importBusy = false
                        pendingImport = null
                    }
                },
            )
            BackupImportFormat.ANIYOMI -> AniyomiImportDialog(
                inspection = preview.aniyomi().orElseThrow(),
                enabled = !importBusy,
                dismiss = { if (!importBusy) pendingImport = null },
                confirm = {
                    importBusy = true
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            runCatching { presentation.importAniyomi(preview.path()) }
                        }.onSuccess {
                            message = "Imported ${it.importedCount()} titles " +
                                "(${it.createdCount()} new, ${it.updatedCount()} updated)."
                            error = null
                        }.onFailure {
                            error = it.message ?: "Aniyomi backup import failed."
                            message = null
                        }
                        importBusy = false
                        pendingImport = null
                    }
                },
            )
        }
    }
    pendingDelete?.let { backup ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete backup?") },
            text = { Text(backup.path().fileName.toString()) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) { presentation.delete(backup.path()) }
                            }.onSuccess {
                                message = "Backup deleted."
                                error = null
                            }.onFailure { error = it.message ?: "Backup deletion failed." }
                        }
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
private fun BackupOverview(
    directory: String,
    importEnabled: Boolean,
    create: () -> Unit,
    editPolicy: () -> Unit,
    chooseBackup: () -> Unit,
) {
    AnilibGroup {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                "Create a local backup of your library, history, progress, and source settings.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = create, modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
                Text("Create backup")
            }
            TextButton(onClick = editPolicy, modifier = Modifier.fillMaxWidth()) {
                Text("Backup schedule, content, retention, and destination")
            }
            OutlinedButton(
                onClick = chooseBackup,
                enabled = importEnabled,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            ) {
                Icon(Icons.Default.UploadFile, contentDescription = null)
                Text("Import backup", modifier = Modifier.padding(start = 6.dp))
            }
            Text(
                "Anilib and Aniyomi formats are detected automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                directory,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun BackupCard(
    backup: BackupFileSnapshot,
    restore: () -> Unit,
    delete: () -> Unit,
    export: () -> Unit,
    share: () -> Unit,
) {
    AnilibGroup {
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
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = export) { Text("Export") }
                TextButton(onClick = share) { Text("Share") }
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
private fun BackupPolicyDialog(
    policy: BackupPolicy,
    options: List<BackupContentOption>,
    dismiss: () -> Unit,
    save: (BackupPolicy) -> Unit,
) {
    var schedule by remember(policy) { mutableStateOf(policy.schedule()) }
    var retention by remember(policy) { mutableStateOf(policy.retentionCount().toString()) }
    var destination by remember(policy) { mutableStateOf(policy.destination().toString()) }
    var selected by remember(policy) { mutableStateOf(policy.includedSections().toSet()) }
    var validationError by remember(policy) { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Backup policy") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Schedule", fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    BackupSchedule.entries.forEach { value ->
                        TextButton(onClick = { schedule = value }) {
                            Text(if (schedule == value) "• ${backupScheduleName(value)}" else backupScheduleName(value))
                        }
                    }
                }
                OutlinedTextField(
                    value = retention,
                    onValueChange = { retention = it },
                    label = { Text("Backups to keep (1–100)") },
                    singleLine = true,
                )
                Text("Included content", fontWeight = FontWeight.Medium)
                options.forEach { option ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(option.displayName())
                            Text(
                                "${option.entryCount()} entries",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = selected.contains(option.id()),
                            onCheckedChange = { checked ->
                                selected = if (checked) selected + option.id() else selected - option.id()
                            },
                        )
                    }
                }
                OutlinedTextField(
                    value = destination,
                    onValueChange = { destination = it },
                    label = { Text("Automatic backup folder") },
                    singleLine = true,
                )
                Text(
                    "Export uses the native document picker (including Android SAF).",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                validationError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                runCatching {
                    BackupPolicy(schedule, retention.toInt(), selected, Path.of(destination))
                }.onSuccess {
                    validationError = null
                    save(it)
                }.onFailure {
                    validationError = it.message ?: "Invalid backup policy."
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
    )
}

private fun backupScheduleName(schedule: BackupSchedule): String = schedule.name
    .lowercase()
    .replaceFirstChar(Char::uppercase)

@Composable
private fun RestoreDialog(
    inspection: BackupInspection,
    dismiss: () -> Unit,
    confirm: () -> Unit,
    enabled: Boolean = true,
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
        confirmButton = { TextButton(onClick = confirm, enabled = enabled) { Text("Restore") } },
        dismissButton = { TextButton(onClick = dismiss, enabled = enabled) { Text("Cancel") } },
    )
}

@Composable
private fun AniyomiImportDialog(
    inspection: AniyomiBackupInspection,
    dismiss: () -> Unit,
    confirm: () -> Unit,
    enabled: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Import Aniyomi backup?") },
        text = {
            Column {
                Text("Existing titles from the same source are merged; other Anilib titles are kept.")
                Spacer(Modifier.height(10.dp))
                Text("Manga: ${inspection.mangaCount()}")
                Text("Anime: ${inspection.animeCount()}")
                Text("Categories: ${inspection.categoryCount()}")
                Text("History entries: ${inspection.historyCount()}")
                Text("Titles with progress: ${inspection.progressCount()}")
                if (inspection.skippedEntryCount() > 0) {
                    Text(
                        "${inspection.skippedEntryCount()} unsupported preference, tracker, or extension " +
                            "entries will be skipped.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = confirm, enabled = enabled) { Text("Import") } },
        dismissButton = { TextButton(onClick = dismiss, enabled = enabled) { Text("Cancel") } },
    )
}

private fun formatBackupBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format(Locale.ROOT, "%.1f MB", bytes / 1048576.0)
    bytes >= 1024L -> String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}
