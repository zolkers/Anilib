package fr.vriege.anilib.platform.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ChromeReaderMode
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VideoSettings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.multiplatform.webview.cookie.WebViewCookieManager
import fr.vriege.anilib.feature.network.NetworkMaintenance
import fr.vriege.anilib.feature.network.NetworkPolicy
import fr.vriege.anilib.feature.settings.SettingsSnapshot
import fr.vriege.anilib.feature.settings.DiagnosticResetArea
import fr.vriege.anilib.feature.settings.DiagnosticResetPlan
import fr.vriege.anilib.feature.settings.BrowserPolicy
import fr.vriege.anilib.feature.settings.LanguagePack
import fr.vriege.anilib.feature.settings.ThemeMode
import fr.vriege.anilib.feature.settings.TypographyScale
import fr.vriege.anilib.feature.settings.ui.SettingsPresentation
import kotlinx.coroutines.launch
import java.net.URI
import java.time.Duration
import java.util.Optional

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    presentation: SettingsPresentation,
    settings: SettingsSnapshot,
    maintenance: NetworkMaintenance,
    browserDataController: BrowserDataController,
    diagnosticExportPicker: BackupImportPicker,
    openExtensionRepositories: () -> Unit,
    openTracking: () -> Unit,
    openBackup: () -> Unit,
    openDownloads: () -> Unit,
    openAbout: () -> Unit,
    goBack: () -> Unit,
) {
    var destination by remember { mutableStateOf<SettingsDestination?>(null) }
    var confirmation by remember { mutableStateOf<MaintenanceAction?>(null) }
    var result by remember { mutableStateOf<String?>(null) }
    var networkPolicyDialog by remember { mutableStateOf(false) }
    var diagnosticsDialog by remember { mutableStateOf(false) }
    var resetPlan by remember { mutableStateOf<DiagnosticResetPlan?>(null) }
    var browserSettingsDialog by remember { mutableStateOf(false) }
    val browserCookies = remember { WebViewCookieManager() }
    val scope = rememberCoroutineScope()

    val runMaintenance: (MaintenanceAction) -> Unit = { action ->
        when (action) {
            MaintenanceAction.COOKIES -> scope.launch {
                val browserCleared = runCatching { browserCookies.removeAllCookies() }.isSuccess
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
                val browserCookiesCleared = runCatching { browserCookies.removeAllCookies() }.isSuccess
                maintenance.clearCookies()
                val browserData = browserDataController.clearData()
                result = when {
                    !browserData.successful -> "HTTP cookies cleared. ${browserData.message}"
                    !browserCookiesCleared ->
                        "HTTP cookies cleared. ${browserData.message} WebView cookies were unavailable."
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
    }

    val selected = destination
    if (selected == null) {
        SettingsHome(
            openDestination = { destination = it },
            openExtensionRepositories = openExtensionRepositories,
            openTracking = openTracking,
            openBackup = openBackup,
            openAbout = openAbout,
            goBack = goBack,
        )
    } else {
        SettingsDetail(
            destination = selected,
            presentation = presentation,
            settings = settings,
            result = result,
            openDownloads = openDownloads,
            openBackup = openBackup,
            openAbout = openAbout,
            requestMaintenance = { confirmation = it },
            openNetworkPolicy = { networkPolicyDialog = true },
            openDiagnostics = { diagnosticsDialog = true },
            openBrowserSettings = { browserSettingsDialog = true },
            goBack = { destination = null },
        )
    }

    confirmation?.let { action ->
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = { Text(action.title) },
            text = { Text(action.warning) },
            confirmButton = {
                TextButton(onClick = {
                    runMaintenance(action)
                    confirmation = null
                }) {
                    Text("Clear")
                }
            },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text("Cancel") } },
        )
    }
    if (networkPolicyDialog) {
        NetworkPolicyDialog(maintenance, close = { networkPolicyDialog = false })
    }
    if (diagnosticsDialog) {
        DiagnosticsDialog(
            presentation,
            diagnosticExportPicker,
            requestReset = { resetPlan = it },
            close = { diagnosticsDialog = false },
        )
    }
    if (browserSettingsDialog) {
        BrowserSettingsDialog(
            settings.browserPolicy(),
            presentation::setBrowserPolicy,
            close = { browserSettingsDialog = false },
        )
    }
    resetPlan?.let { plan ->
        AlertDialog(
            onDismissRequest = { resetPlan = null },
            title = { Text("Confirm safe reset") },
            text = {
                Text(
                    "Remove ${plan.targets().size} allowlisted targets and reclaim " +
                        "${formatDiagnosticBytes(plan.reclaimableBytes())}?",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    presentation.executeReset(plan)
                    resetPlan = null
                    diagnosticsDialog = false
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { resetPlan = null }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsHome(
    openDestination: (SettingsDestination) -> Unit,
    openExtensionRepositories: () -> Unit,
    openTracking: () -> Unit,
    openBackup: () -> Unit,
    openAbout: () -> Unit,
    goBack: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val general = settingMatches(query, "General", "Language start screen navigation")
    val appearance = settingMatches(query, "Appearance", "Theme colors typography navigation")
    val privacy = settingMatches(query, "Content and privacy", "Adult incognito history")
    val library = settingMatches(query, "Library and updates", "Categories refresh Wi-Fi duplicate")
    val reader = settingMatches(query, "Reader", "Reading mode controls display navigation")
    val player = settingMatches(query, "Player", "Playback decoder audio subtitles gestures")
    val downloads = settingMatches(query, "Downloads", "Wi-Fi queue storage offline")
    val extensions = settingMatches(query, "Sources and repositories", "Extensions Git trust languages")
    val tracking = settingMatches(query, "Tracking", "Accounts sync score privacy")
    val backup = settingMatches(query, "Backup", "Automatic restore storage import")
    val advanced = settingMatches(query, "Data and storage", "Cookies cache WebView database cleanup")
    val about = settingMatches(query, "About", "Version licences diagnostics update channel")
    Scaffold(
        topBar = { SettingsTopBar("Settings", goBack) },
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
            if (general || appearance || privacy) {
                item { SettingsSection("Application") }
                item {
                    SettingsGroup {
                        if (general) {
                            SettingsRow(
                                "General",
                                "Language and start screen",
                                { openDestination(SettingsDestination.GENERAL) },
                                Icons.Outlined.Language,
                            )
                        }
                        if (appearance) {
                            SettingsRow(
                                "Appearance",
                                "Theme and visual preferences",
                                { openDestination(SettingsDestination.APPEARANCE) },
                                Icons.Outlined.Palette,
                            )
                        }
                        if (privacy) {
                            SettingsRow(
                                "Content and privacy",
                                "Adult content and incognito mode",
                                { openDestination(SettingsDestination.PRIVACY) },
                                Icons.Outlined.Security,
                            )
                        }
                    }
                }
            }
            if (library || reader || player || downloads) {
                item { SettingsSection("Library and media") }
                item {
                    SettingsGroup {
                        if (library) {
                            SettingsRow(
                                "Library and updates",
                                "Refresh and content policies",
                                { openDestination(SettingsDestination.LIBRARY) },
                                Icons.Outlined.CollectionsBookmark,
                            )
                        }
                        if (reader) {
                            SettingsRow(
                                "Reader",
                                "Reading behavior and per-title controls",
                                { openDestination(SettingsDestination.READER) },
                                Icons.AutoMirrored.Outlined.ChromeReaderMode,
                            )
                        }
                        if (player) {
                            SettingsRow(
                                "Player",
                                "Playback behavior and per-episode controls",
                                { openDestination(SettingsDestination.PLAYER) },
                                Icons.Outlined.VideoSettings,
                            )
                        }
                        if (downloads) {
                            SettingsRow(
                                "Downloads",
                                "Network policy and download queue",
                                { openDestination(SettingsDestination.DOWNLOADS) },
                                Icons.Outlined.Download,
                            )
                        }
                    }
                }
            }
            if (extensions || tracking || backup) {
                item { SettingsSection("Services") }
                item {
                    SettingsGroup {
                        if (extensions) {
                            SettingsRow(
                                "Sources and repositories",
                                "Languages, installed sources, trust, and repository URLs",
                                openExtensionRepositories,
                                Icons.Outlined.Extension,
                            )
                        }
                        if (tracking) {
                            SettingsRow(
                                "Tracking",
                                "Accounts, sync, score, and privacy",
                                openTracking,
                                Icons.Outlined.Sync,
                            )
                        }
                        if (backup) {
                            SettingsRow(
                                "Backup",
                                "Backups, restore, imports, and storage",
                                openBackup,
                                Icons.Outlined.Backup,
                            )
                        }
                    }
                }
            }
            if (advanced || about) {
                item { SettingsSection("Advanced") }
                item {
                    SettingsGroup {
                        if (advanced) {
                            SettingsRow(
                                "Data and storage",
                                "Network, browser, and unused data",
                                { openDestination(SettingsDestination.ADVANCED) },
                                Icons.Outlined.Storage,
                            )
                        }
                        if (about) {
                            SettingsRow(
                                "About",
                                "Version, updates, project, and help",
                                openAbout,
                                Icons.Outlined.Info,
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsDetail(
    destination: SettingsDestination,
    presentation: SettingsPresentation,
    settings: SettingsSnapshot,
    result: String?,
    openDownloads: () -> Unit,
    openBackup: () -> Unit,
    openAbout: () -> Unit,
    requestMaintenance: (MaintenanceAction) -> Unit,
    openNetworkPolicy: () -> Unit,
    openDiagnostics: () -> Unit,
    openBrowserSettings: () -> Unit,
    goBack: () -> Unit,
) {
    Scaffold(topBar = { SettingsTopBar(destination.title, goBack) }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (destination) {
                SettingsDestination.GENERAL -> {
                    item { SettingsSection("Navigation") }
                    item {
                        SettingsRow("Start screen", settings.startScreen().displayName()) {
                            presentation.setStartScreen(settings.startScreen().next())
                        }
                    }
                    item {
                        SettingsRow("Language", languageLabel(settings.languagePack())) {
                            presentation.setLanguagePack(settings.languagePack().next())
                        }
                    }
                    item { SettingsHint("Start screen changes apply the next time Anilib opens.") }
                }
                SettingsDestination.APPEARANCE -> {
                    item { SettingsSection("Theme") }
                    item {
                        SettingsRow("Theme", themeLabel(settings)) {
                            presentation.setThemeMode(settings.themeMode().next())
                        }
                    }
                    item {
                        SettingsRow("Theme family", settings.themeFamily().displayName()) {
                            presentation.setThemeFamily(settings.themeFamily().next())
                        }
                    }
                    item {
                        SettingsRow("Accent color", settings.accentColor().displayName()) {
                            presentation.setAccentColor(settings.accentColor().next())
                        }
                    }
                    item {
                        SettingsRow("Typography", typographyLabel(settings.typographyScale())) {
                            presentation.setTypographyScale(settings.typographyScale().next())
                        }
                    }
                    item {
                        SettingsSwitchRow(
                            "Reduce motion",
                            "Disable reader page transitions and nonessential animation",
                            settings.reducedMotion(),
                            presentation::setReducedMotion,
                        )
                    }
                    item {
                        SettingsRow("Navigation", settings.navigationStyle().displayName()) {
                            presentation.setNavigationStyle(settings.navigationStyle().next())
                        }
                    }
                    item { SettingsHint("Appearance changes apply immediately on Android and desktop.") }
                }
                SettingsDestination.PRIVACY -> {
                    item { SettingsSection("Content") }
                    item {
                        SettingsSwitchRow(
                            "Show adult content",
                            "Allow sources and titles marked as adult",
                            settings.showAdultContent(),
                            presentation::setShowAdultContent,
                        )
                    }
                    item { SettingsSection("Privacy") }
                    item {
                        SettingsSwitchRow(
                            "Incognito mode",
                            "Do not write new reader or player history and progress",
                            settings.incognitoMode(),
                            presentation::setIncognitoMode,
                        )
                    }
                }
                SettingsDestination.LIBRARY -> {
                    item { SettingsSection("Library updates") }
                    item {
                        SettingsSwitchRow(
                            "Wi-Fi only updates",
                            "Refresh the library automatically only on Wi-Fi",
                            settings.updateOnlyOnWifi(),
                            presentation::setUpdateOnlyOnWifi,
                        )
                    }
                    item { SettingsHint("Schedule and skip controls remain available on the Updates screen.") }
                }
                SettingsDestination.READER -> {
                    item { SettingsSection("Reader behavior") }
                    item { SettingsRow("Reading direction", "Choose LTR, RTL, vertical, or webtoon in Reader") }
                    item { SettingsRow("Prefetch and retry", "Managed by the shared Reader pipeline") }
                    item { SettingsHint("Direction and position are retained per title.") }
                }
                SettingsDestination.PLAYER -> {
                    item { SettingsSection("Player behavior") }
                    item { SettingsRow("Quality and subtitles", "Choose them from the episode screen") }
                    item { SettingsRow("Resume", "Playback position is retained per episode") }
                    item { SettingsHint("Android and desktop use the same stream and subtitle policy.") }
                }
                SettingsDestination.DOWNLOADS -> {
                    item { SettingsSection("Network") }
                    item {
                        SettingsSwitchRow(
                            "Wi-Fi only downloads",
                            "Keep queued downloads off metered connections",
                            settings.downloadOnlyOnWifi(),
                            presentation::setDownloadOnlyOnWifi,
                        )
                    }
                    item { SettingsSection("Queue and storage") }
                    item { SettingsRow("Manage downloads", "Queue, offline mode, and storage usage", openDownloads) }
                }
                SettingsDestination.ADVANCED -> {
                    item { SettingsSection("Network and browser") }
                    item {
                        SettingsRow(
                            "Network policy",
                            "User agent, proxy, DNS-over-HTTPS, timeout, cache, and diagnostics",
                            openNetworkPolicy,
                        )
                    }
                    item {
                        SettingsRow(
                            "Browser settings",
                            "JavaScript, storage, files, pop-ups, downloads, challenge retry, and text zoom",
                            openBrowserSettings,
                        )
                    }
                    item {
                        SettingsRow("Clear cookies", "Sign out browser sessions for every source") {
                            requestMaintenance(MaintenanceAction.COOKIES)
                        }
                    }
                    item {
                        SettingsRow("Clear network cache", "Remove cached HTTP responses") {
                            requestMaintenance(MaintenanceAction.CACHE)
                        }
                    }
                    item {
                        SettingsRow("Clear WebView data", "Remove browser cookies, cache, and site storage") {
                            requestMaintenance(MaintenanceAction.BROWSER_DATA)
                        }
                    }
                    item { SettingsSection("Application data") }
                    item {
                        SettingsRow(
                            "Storage and diagnostics",
                            "Inspect storage, logs, crash reports, export, and safe reset",
                            openDiagnostics,
                        )
                    }
                    item {
                        SettingsRow("Clean database", "Remove records for titles no longer in the library") {
                            requestMaintenance(MaintenanceAction.UNUSED_DATA)
                        }
                    }
                    item { SettingsRow("Backup and restore", "Protect or import your library", openBackup) }
                    item { SettingsRow("About Anilib", "Version, updates, project, and help", openAbout) }
                    result?.let { message -> item { SettingsResult(message) } }
                }
            }
        }
    }
}

@Composable
private fun BrowserSettingsDialog(
    initial: BrowserPolicy,
    save: (BrowserPolicy) -> Unit,
    close: () -> Unit,
) {
    var javaScript by remember(initial) { mutableStateOf(initial.javaScriptEnabled()) }
    var domStorage by remember(initial) { mutableStateOf(initial.domStorageEnabled()) }
    var fileChooser by remember(initial) { mutableStateOf(initial.fileChooserEnabled()) }
    var popups by remember(initial) { mutableStateOf(initial.popupsEnabled()) }
    var downloads by remember(initial) { mutableStateOf(initial.downloadsEnabled()) }
    var challengeRetry by remember(initial) { mutableStateOf(initial.automaticChallengeRetry()) }
    var textZoom by remember(initial) { mutableStateOf(initial.textZoomPercent().toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Browser settings") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                SettingsSwitchRow("JavaScript", "Allow website scripts", javaScript) { javaScript = it }
                SettingsSwitchRow("DOM storage", "Allow website local storage", domStorage) { domStorage = it }
                SettingsSwitchRow("File chooser", "Allow user-initiated file selection", fileChooser) {
                    fileChooser = it
                }
                SettingsSwitchRow("Pop-ups", "Open requested windows in the current browser", popups) {
                    popups = it
                }
                SettingsSwitchRow("Downloads", "Hand downloads to the platform", downloads) { downloads = it }
                SettingsSwitchRow(
                    "Automatic challenge retry",
                    "Retry the source as soon as all completion cookies exist",
                    challengeRetry,
                ) { challengeRetry = it }
                OutlinedTextField(textZoom, { textZoom = it }, label = { Text("Text zoom (50–200%)") })
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                runCatching {
                    BrowserPolicy(
                        javaScript,
                        domStorage,
                        fileChooser,
                        popups,
                        downloads,
                        challengeRetry,
                        textZoom.toInt(),
                    )
                }.onSuccess {
                    save(it)
                    close()
                }.onFailure { error = it.message ?: "Invalid browser settings" }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = close) { Text("Cancel") } },
    )
}

@Composable
private fun DiagnosticsDialog(
    presentation: SettingsPresentation,
    exportPicker: BackupImportPicker,
    requestReset: (DiagnosticResetPlan) -> Unit,
    close: () -> Unit,
) {
    var revision by remember { mutableStateOf(0) }
    val snapshot = remember(presentation, revision) { presentation.diagnostics() }
    var feedback by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Storage and diagnostics") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("${formatDiagnosticBytes(snapshot.totalBytes())} in ${snapshot.totalFiles()} files")
                Text(snapshot.dataDirectory().toString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                snapshot.storage().forEach { usage ->
                    SettingsRow(
                        usage.area(),
                        "${formatDiagnosticBytes(usage.bytes())} · ${usage.files()} files",
                    )
                }
                SettingsSection("Reports")
                if (snapshot.reports().isEmpty()) {
                    SettingsHint("No log or crash report is available.")
                } else {
                    snapshot.reports().forEach { report ->
                        SettingsRow(
                            report.name(),
                            "${report.type().name.lowercase()} · ${formatDiagnosticBytes(report.bytes())}",
                        )
                    }
                }
                TextButton(onClick = {
                    runCatching { presentation.exportDiagnostics() }
                        .onSuccess { archive ->
                            exportPicker.export(
                                archive,
                                { feedback = "Diagnostics exported" },
                                { feedback = it },
                            )
                        }
                        .onFailure { feedback = it.message ?: "Diagnostics export failed" }
                }) { Text("Export diagnostics") }
                TextButton(onClick = {
                    requestReset(
                        presentation.planReset(
                            setOf(
                                DiagnosticResetArea.NETWORK_CACHE,
                                DiagnosticResetArea.LOGS,
                                DiagnosticResetArea.CRASH_REPORTS,
                            ),
                        ),
                    )
                }) { Text("Clear cache and reports") }
                TextButton(onClick = {
                    requestReset(presentation.planReset(setOf(DiagnosticResetArea.SETTINGS)))
                }) { Text("Reset settings") }
                feedback?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            }
        },
        confirmButton = {
            TextButton(onClick = { revision++ }) { Text("Refresh") }
        },
        dismissButton = { TextButton(onClick = close) { Text("Close") } },
    )
}

private fun formatDiagnosticBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> "%.1f GiB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    bytes >= 1024L * 1024L -> "%.1f MiB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> "%.1f KiB".format(bytes / 1024.0)
    else -> "$bytes B"
}

@Composable
private fun NetworkPolicyDialog(maintenance: NetworkMaintenance, close: () -> Unit) {
    val initial = remember(maintenance) { maintenance.policy() }
    var userAgent by remember { mutableStateOf(initial.userAgent()) }
    var proxy by remember { mutableStateOf(initial.proxy().map(URI::toASCIIString).orElse("")) }
    var doh by remember { mutableStateOf(initial.dnsOverHttps().map(URI::toASCIIString).orElse("")) }
    var timeout by remember { mutableStateOf(initial.timeout().toSeconds().toString()) }
    var cacheEnabled by remember { mutableStateOf(initial.responseCacheEnabled()) }
    var sourceId by remember { mutableStateOf("") }
    var endpoint by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Network policy") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(userAgent, { userAgent = it }, label = { Text("User agent") })
                OutlinedTextField(proxy, { proxy = it }, label = { Text("HTTP proxy (optional)") })
                OutlinedTextField(doh, { doh = it }, label = { Text("DNS-over-HTTPS URL (optional)") })
                OutlinedTextField(timeout, { timeout = it }, label = { Text("Timeout in seconds") })
                SettingsSwitchRow(
                    "Response cache",
                    "Read and write shared HTTP cache entries",
                    cacheEnabled,
                    { cacheEnabled = it },
                )
                SettingsSection("Per-source diagnostic")
                OutlinedTextField(sourceId, { sourceId = it }, label = { Text("Source ID") })
                OutlinedTextField(endpoint, { endpoint = it }, label = { Text("HTTPS endpoint") })
                TextButton(onClick = {
                    feedback = runCatching {
                        val diagnostic = maintenance.diagnose(sourceId, URI.create(endpoint))
                        "${diagnostic.sourceId()}: ${diagnostic.message()} in ${diagnostic.elapsed().toMillis()} ms"
                    }.fold({ it }, { it.message ?: "Diagnostic failed" })
                }) { Text("Run diagnostic") }
                feedback?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                feedback = runCatching {
                    maintenance.savePolicy(
                        NetworkPolicy(
                            userAgent,
                            Optional.ofNullable(proxy.trim().takeIf(String::isNotEmpty)?.let(URI::create)),
                            Optional.ofNullable(doh.trim().takeIf(String::isNotEmpty)?.let(URI::create)),
                            Duration.ofSeconds(timeout.toLong()),
                            cacheEnabled,
                        ),
                    )
                    close()
                    "Saved"
                }.exceptionOrNull()?.message ?: feedback
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = close) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTopBar(title: String, goBack: () -> Unit) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = goBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
    )
}

@Composable
private fun SettingsSection(label: String) {
    Text(
        label,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 10.dp),
    )
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsRow(
    title: String,
    summary: String,
    action: (() -> Unit)? = null,
    icon: ImageVector? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (action == null) Modifier else Modifier.clickable(onClick = action))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val leadingIcon = icon ?: if (action != null) Icons.Outlined.Settings else null
        if (leadingIcon != null) {
            SettingsIcon(leadingIcon)
            Spacer(Modifier.width(16.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(3.dp))
            Text(
                summary,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (action != null) {
            Spacer(Modifier.width(12.dp))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    summary: String,
    icon: ImageVector? = null,
    action: () -> Unit,
) {
    SettingsRow(title, summary, action, icon)
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIcon(icon)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(3.dp))
            Text(summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingsSwitchRow(title, summary, checked, onCheckedChange, Icons.Outlined.Tune)
}

@Composable
private fun SettingsIcon(icon: ImageVector) {
    Surface(
        modifier = Modifier.size(40.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun SettingsHint(message: String) {
    Text(
        message,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
    )
}

@Composable
private fun SettingsResult(message: String) {
    Text(
        message,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
    )
}

private fun themeLabel(settings: SettingsSnapshot): String = when (settings.themeMode()) {
    ThemeMode.SYSTEM -> "Follow the system theme"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}

private fun languageLabel(language: LanguagePack): String = when (language) {
    LanguagePack.SYSTEM -> "System language"
    LanguagePack.ENGLISH -> "English"
    LanguagePack.FRENCH -> "Français"
}

private fun typographyLabel(scale: TypographyScale): String = when (scale) {
    TypographyScale.COMPACT -> "Compact (90%)"
    TypographyScale.STANDARD -> "Standard (100%)"
    TypographyScale.LARGE -> "Large (115%)"
}

private fun Enum<*>.displayName(): String = name
    .lowercase()
    .replace('_', ' ')
    .replaceFirstChar(Char::uppercase)

private fun settingMatches(query: String, title: String, keywords: String): Boolean =
    query.isBlank() || title.contains(query, ignoreCase = true) || keywords.contains(query, ignoreCase = true)

private enum class SettingsDestination(val title: String) {
    GENERAL("General"),
    APPEARANCE("Appearance"),
    PRIVACY("Content and privacy"),
    LIBRARY("Library and updates"),
    READER("Reader"),
    PLAYER("Player"),
    DOWNLOADS("Downloads"),
    ADVANCED("Data and storage"),
}

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
