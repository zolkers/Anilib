package fr.vriege.anilib.platform.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.vriege.anilib.feature.network.NetworkMaintenance
import fr.vriege.anilib.feature.settings.SettingsSnapshot
import fr.vriege.anilib.feature.settings.ui.SettingsPresentation
import com.multiplatform.webview.cookie.WebViewCookieManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    presentation: SettingsPresentation,
    settings: SettingsSnapshot,
    maintenance: NetworkMaintenance,
    browserDataController: BrowserDataController,
    openExtensionRepositories: () -> Unit,
    openTracking: () -> Unit,
    openBackup: () -> Unit,
    openAbout: () -> Unit,
    goBack: () -> Unit,
) {
    var confirmation by remember { mutableStateOf<MaintenanceAction?>(null) }
    var result by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    val browserCookies = remember { WebViewCookieManager() }
    val scope = rememberCoroutineScope()
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
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search settings") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
                )
            }
            item { SettingsSection("Appearance") }
            if (settingMatches(query, "Theme", "System light dark appearance")) item {
                SettingsRow("Theme", themeLabel(settings)) {
                    presentation.setThemeMode(settings.themeMode().next())
                }
            }
            if (settingMatches(query, "Language and start screen", "System language Library")) item {
                SettingsRow("Language and start screen", "System language and Library")
            }
            item { SettingsSection("Content and privacy") }
            if (settingMatches(query, "Show adult content", "sources titles mature")) item {
                SettingsSwitchRow(
                    "Show adult content",
                    "Allow sources and titles marked as adult",
                    settings.showAdultContent(),
                    presentation::setShowAdultContent,
                )
            }
            if (settingMatches(query, "Incognito mode", "history privacy")) item {
                SettingsSwitchRow(
                    "Incognito mode",
                    "Do not add newly opened titles to history",
                    settings.incognitoMode(),
                    presentation::setIncognitoMode,
                )
            }
            item { SettingsSection("Features") }
            if (settingMatches(query, "Sources and repositories", "extensions Git repositories")) item {
                SettingsRow(
                    "Sources and repositories",
                    "Languages, installed sources, trust, and repository URLs",
                    openExtensionRepositories,
                )
            }
            if (settingMatches(query, "Library", "Categories display update duplicate")) item {
                SettingsRow("Library", "Categories, display, update, and duplicate policy")
            }
            if (settingMatches(query, "Reader", "Reading mode controls display navigation")) item {
                SettingsRow("Reader", "Reading mode, controls, display, and navigation")
            }
            if (settingMatches(query, "Player", "Playback decoder audio subtitles gestures")) item {
                SettingsRow("Player", "Playback, decoder, audio, subtitles, and gestures")
            }
            if (settingMatches(query, "Wi-Fi only downloads", "network metered")) item {
                SettingsSwitchRow(
                    "Wi-Fi only downloads",
                    "Keep automatic and queued downloads off metered connections",
                    settings.downloadOnlyOnWifi(),
                    presentation::setDownloadOnlyOnWifi,
                )
            }
            if (settingMatches(query, "Wi-Fi only updates", "library network")) item {
                SettingsSwitchRow(
                    "Wi-Fi only updates",
                    "Refresh the library automatically only on Wi-Fi",
                    settings.updateOnlyOnWifi(),
                    presentation::setUpdateOnlyOnWifi,
                )
            }
            if (settingMatches(query, "Tracking", "Accounts sync score privacy")) item {
                SettingsRow("Tracking", "Accounts, sync, score, and privacy", openTracking)
            }
            if (settingMatches(query, "Backup", "Automatic restore storage import")) item {
                SettingsRow("Backup", "Backups, restore, imports, and storage", openBackup)
            }
            item { SettingsSection("Advanced") }
            if (settingMatches(query, "Clear cookies", "browser source sessions")) item {
                SettingsRow("Clear cookies", "Sign out browser sessions for every source") {
                    confirmation = MaintenanceAction.COOKIES
                }
            }
            if (settingMatches(query, "Clear network cache", "HTTP responses")) item {
                SettingsRow("Clear network cache", "Remove cached HTTP responses") {
                    confirmation = MaintenanceAction.CACHE
                }
            }
            if (settingMatches(query, "Clear WebView data", "browser cache site storage")) item {
                SettingsRow("Clear WebView data", "Remove browser cookies, cache, and site storage") {
                    confirmation = MaintenanceAction.BROWSER_DATA
                }
            }
            if (settingMatches(query, "Clean database", "unused orphaned feature data")) item {
                SettingsRow("Clean database", "Remove feature data for titles no longer in the library") {
                    confirmation = MaintenanceAction.UNUSED_DATA
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
            if (settingMatches(query, "About", "Version licences diagnostics update channel")) item {
                SettingsRow("About", "Version, runtime, formats, and platforms", openAbout)
            }
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
                        MaintenanceAction.COOKIES -> scope.launch {
                            val browserCleared = runCatching {
                                browserCookies.removeAllCookies()
                            }.isSuccess
                            maintenance.clearCookies()
                            result = if (browserCleared) {
                                action.result
                            } else {
                                "HTTP cookies cleared; WebView cookies were unavailable."
                            }
                        }
                        MaintenanceAction.CACHE -> {
                            maintenance.clearResponseCache()
                            result = action.result
                        }
                        MaintenanceAction.BROWSER_DATA -> scope.launch {
                            val browserCookiesCleared = runCatching {
                                browserCookies.removeAllCookies()
                            }.isSuccess
                            maintenance.clearCookies()
                            val browserData = browserDataController.clearData()
                            result = when {
                                !browserData.successful ->
                                    "HTTP cookies cleared. ${browserData.message}"
                                !browserCookiesCleared ->
                                    "HTTP cookies cleared. ${browserData.message} " +
                                        "WebView cookies were unavailable."
                                else -> "HTTP and WebView cookies cleared. ${browserData.message}"
                            }
                        }
                        MaintenanceAction.UNUSED_DATA -> {
                            val cleanup = presentation.cleanUnusedData()
                            result = if (cleanup.totalRemoved() == 0) {
                                "Database is already clean."
                            } else {
                                "Removed ${cleanup.totalRemoved()} unused database entries."
                            }
                        }
                    }
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

@Composable
private fun SettingsSwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(3.dp))
            Text(summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun themeLabel(settings: SettingsSnapshot): String = when (settings.themeMode()) {
    fr.vriege.anilib.feature.settings.ThemeMode.SYSTEM -> "Follow the system theme"
    fr.vriege.anilib.feature.settings.ThemeMode.LIGHT -> "Light"
    fr.vriege.anilib.feature.settings.ThemeMode.DARK -> "Dark"
}

private fun settingMatches(query: String, title: String, keywords: String): Boolean =
    query.isBlank() || title.contains(query, ignoreCase = true) || keywords.contains(query, ignoreCase = true)

private enum class MaintenanceAction(val title: String, val warning: String, val result: String) {
    COOKIES(
        "Clear cookies?",
        "Source websites may sign you out. This cannot be undone.",
        "HTTP and WebView cookies cleared.",
    ),
    CACHE(
        "Clear network cache?",
        "Cached source responses will be downloaded again when needed.",
        "Network cache cleared.",
    ),
    BROWSER_DATA(
        "Clear WebView data?",
        "Browser sessions and stored website data will be removed. This cannot be undone.",
        "WebView data cleared.",
    ),
    UNUSED_DATA(
        "Clean database?",
        "Player, download, tracking, and update records for removed titles will be deleted.",
        "Unused database entries removed.",
    ),
}
