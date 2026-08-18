package fr.vriege.anilib.platform.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.vriege.anilib.feature.network.NetworkMaintenance

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(maintenance: NetworkMaintenance, goBack: () -> Unit) {
    var confirmation by remember { mutableStateOf<MaintenanceAction?>(null) }
    var result by remember { mutableStateOf<String?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = goBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item { SettingsSection("General") }
            item { SettingsRow("Appearance", "Theme, colors, language, and start screen") }
            item { SettingsRow("Library", "Categories, display, update, and duplicate policy") }
            item { SettingsRow("Reader", "Reading mode, controls, display, and navigation") }
            item { SettingsRow("Player", "Playback, decoder, audio, subtitles, and gestures") }
            item { SettingsRow("Downloads", "Storage, automatic downloads, and network policy") }
            item { SettingsRow("Tracking", "Accounts, sync, score, and privacy") }
            item { SettingsRow("Backup", "Automatic backups, restore, and storage") }
            item { SettingsRow("Security and privacy", "Incognito, secure screen, and trusted sources") }
            item { SettingsSection("Advanced") }
            item {
                SettingsRow("Clear cookies", "Sign out browser sessions for every source") {
                    confirmation = MaintenanceAction.COOKIES
                }
            }
            item {
                SettingsRow("Clear network cache", "Remove cached HTTP responses") {
                    confirmation = MaintenanceAction.CACHE
                }
            }
            result?.let { message ->
                item {
                    Text(
                        message,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    )
                }
            }
            item { SettingsRow("About", "Version, licences, diagnostics, and update channel") }
        }
    }
    confirmation?.let { action ->
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = { Text(action.title) },
            text = { Text(action.warning) },
            confirmButton = {
                TextButton(onClick = {
                    when (action) {
                        MaintenanceAction.COOKIES -> maintenance.clearCookies()
                        MaintenanceAction.CACHE -> maintenance.clearResponseCache()
                    }
                    result = action.result
                    confirmation = null
                }) {
                    Text("Clear")
                }
            },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SettingsSection(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsRow(title: String, summary: String, action: (() -> Unit)? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (action == null) Modifier else Modifier.clickable(onClick = action))
            .padding(horizontal = 24.dp, vertical = 14.dp),
    ) {
        Text(title, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(3.dp))
        Text(summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private enum class MaintenanceAction(val title: String, val warning: String, val result: String) {
    COOKIES(
        "Clear cookies?",
        "Source websites may sign you out. This cannot be undone.",
        "HTTP cookies cleared.",
    ),
    CACHE(
        "Clear network cache?",
        "Cached source responses will be downloaded again when needed.",
        "Network cache cleared.",
    ),
}
