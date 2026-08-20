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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ChromeReaderMode
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VideoSettings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import fr.vriege.anilib.feature.settings.DiagnosticSnapshot
import fr.vriege.anilib.feature.settings.BrowserPolicy
import fr.vriege.anilib.feature.settings.LanguagePack
import fr.vriege.anilib.feature.settings.PlayerWindowMode
import fr.vriege.anilib.feature.settings.ThemeMode
import fr.vriege.anilib.feature.settings.TypographyScale
import fr.vriege.anilib.feature.settings.ui.SettingsPresentation
import java.net.URI
import java.time.Duration
import java.util.Optional
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    presentation: SettingsPresentation,
    settings: SettingsSnapshot,
    supportsPlayerWindowModes: Boolean,
    maintenance: NetworkMaintenance,
    browserDataController: BrowserDataController,
    diagnosticExportPicker: BackupImportPicker,
    openBackup: () -> Unit,
    openDownloads: () -> Unit,
    openTracking: () -> Unit,
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
    val scope = rememberCrashSafeCoroutineScope()

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
                scope.launch {
                    withContext(Dispatchers.IO) { maintenance.clearResponseCache() }
                    result = action.result
                }
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
                scope.launch {
                    val cleanup = withContext(Dispatchers.IO) { presentation.cleanUnusedData() }
                    result = if (cleanup.totalRemoved() == 0) {
                        "Database is already clean."
                    } else {
                        "Removed ${cleanup.totalRemoved()} unused database entries."
                    }
                }
            }
        }
    }

    val selected = destination
    if (selected == null) {
        SettingsHome(
            openDestination = { destination = it },
            openTracking = openTracking,
            openAbout = openAbout,
            goBack = goBack,
        )
    } else {
        SettingsDetail(
            destination = selected,
            presentation = presentation,
            settings = settings,
            supportsPlayerWindowModes = supportsPlayerWindowModes,
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
                    Text("ui.clear")
                }
            },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text("ui.cancel") } },
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
            title = { Text("ui.confirm.safe.reset") },
            text = {
                Text(
                    UiTranslations.format(
                        "dynamic.safe.reset.summary",
                        LocalLanguagePack.current,
                        plan.targets().size,
                        formatDiagnosticBytes(plan.reclaimableBytes()),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { presentation.executeReset(plan) }
                        resetPlan = null
                        diagnosticsDialog = false
                    }
                }) { Text("ui.reset") }
            },
            dismissButton = { TextButton(onClick = { resetPlan = null }) { Text("ui.cancel") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsHome(
    openDestination: (SettingsDestination) -> Unit,
    openTracking: () -> Unit,
    openAbout: () -> Unit,
    goBack: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    val general = settingMatches(query, "General", "Language start screen navigation")
    val appearance = settingMatches(query, "Appearance", "Theme colors typography navigation")
    val privacy = settingMatches(query, "Content and privacy", "Adult incognito history")
    val library = settingMatches(query, "Library and updates", "Categories refresh Wi-Fi duplicate")
    val reader = settingMatches(query, "Reader", "Reading mode controls display navigation")
    val player = settingMatches(query, "Player", "Playback decoder audio subtitles gestures")
    val downloads = settingMatches(query, "Downloads", "Wi-Fi queue storage offline")
    val tracking = settingMatches(query, "Tracking", "AniList Kitsu progress synchronization services")
    val advanced = settingMatches(query, "Data and storage", "Cookies cache WebView database cleanup")
    val about = settingMatches(query, "About", "Version licences diagnostics update channel")
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (!searching) Text("ui.settings") else OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("ui.search.settings") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = goBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ui.back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (searching) query = ""
                        searching = !searching
                    }) {
                        Icon(
                            if (searching) Icons.Outlined.Close else Icons.Outlined.Search,
                            contentDescription = if (searching) "Close search" else "Search settings",
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (general || appearance || privacy) {
                item { SettingsSection("ui.application") }
                item {
                    SettingsGroup {
                        if (general) {
                            SettingsRow(
                                "ui.general",
                                "ui.language.and.start.screen",
                                { openDestination(SettingsDestination.GENERAL) },
                                Icons.Outlined.Language,
                            )
                        }
                        if (appearance) {
                            SettingsRow(
                                "ui.appearance",
                                "ui.theme.and.visual.preferences",
                                { openDestination(SettingsDestination.APPEARANCE) },
                                Icons.Outlined.Palette,
                            )
                        }
                        if (privacy) {
                            SettingsRow(
                                "ui.content.and.privacy",
                                "ui.adult.content.and.incognito.mode",
                                { openDestination(SettingsDestination.PRIVACY) },
                                Icons.Outlined.Security,
                            )
                        }
                    }
                }
            }
            if (library || reader || player || downloads || tracking) {
                item { SettingsSection("ui.library.and.media") }
                item {
                    SettingsGroup {
                        if (library) {
                            SettingsRow(
                                "ui.library.and.updates",
                                "ui.refresh.and.content.policies",
                                { openDestination(SettingsDestination.LIBRARY) },
                                Icons.Outlined.CollectionsBookmark,
                            )
                        }
                        if (reader) {
                            SettingsRow(
                                "ui.reader",
                                "ui.reading.behavior.and.per.title.controls",
                                { openDestination(SettingsDestination.READER) },
                                Icons.AutoMirrored.Outlined.ChromeReaderMode,
                            )
                        }
                        if (player) {
                            SettingsRow(
                                "ui.player",
                                "ui.playback.behavior.and.per.episode.controls",
                                { openDestination(SettingsDestination.PLAYER) },
                                Icons.Outlined.VideoSettings,
                            )
                        }
                        if (downloads) {
                            SettingsRow(
                                "ui.downloads",
                                "ui.network.policy.and.download.queue",
                                { openDestination(SettingsDestination.DOWNLOADS) },
                                Icons.Outlined.Download,
                            )
                        }
                        if (tracking) {
                            SettingsRow(
                                "ui.tracking",
                                "ui.link.anilist.kitsu.and.synchronize.progress",
                                openTracking,
                                Icons.Outlined.Person,
                            )
                        }
                    }
                }
            }
            if (advanced || about) {
                item { SettingsSection("ui.advanced") }
                item {
                    SettingsGroup {
                        if (advanced) {
                            SettingsRow(
                                "ui.data.and.storage",
                                "ui.network.browser.and.unused.data",
                                { openDestination(SettingsDestination.ADVANCED) },
                                Icons.Outlined.Storage,
                            )
                        }
                        if (about) {
                            SettingsRow(
                                "ui.about",
                                "ui.version.updates.project.and.help",
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
    supportsPlayerWindowModes: Boolean,
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
    var choosingLanguage by remember { mutableStateOf(false) }
    var choosingPlayerWindowMode by remember { mutableStateOf(false) }
    AnilibSubScreenScaffold(title = destination.title, goBack = goBack) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (destination) {
                SettingsDestination.GENERAL -> {
                    item { SettingsSection("ui.navigation") }
                    item {
                        SettingsRow("ui.start.screen", settings.startScreen().displayName()) {
                            presentation.setStartScreen(settings.startScreen().next())
                        }
                    }
                    item {
                        SettingsRow("ui.language", languageLabel(settings.languagePack())) {
                            choosingLanguage = true
                        }
                    }
                    item { SettingsHint("ui.start.screen.changes.apply.the.next.time.anilib.opens") }
                }
                SettingsDestination.APPEARANCE -> {
                    item { SettingsSection("ui.theme") }
                    item {
                        SettingsRow("ui.theme", themeLabel(settings)) {
                            presentation.setThemeMode(settings.themeMode().next())
                        }
                    }
                    item {
                        SettingsRow("ui.theme.family", settings.themeFamily().displayName()) {
                            presentation.setThemeFamily(settings.themeFamily().next())
                        }
                    }
                    item {
                        SettingsRow("ui.accent.color", settings.accentColor().displayName()) {
                            presentation.setAccentColor(settings.accentColor().next())
                        }
                    }
                    item {
                        SettingsRow("ui.typography", typographyLabel(settings.typographyScale())) {
                            presentation.setTypographyScale(settings.typographyScale().next())
                        }
                    }
                    item {
                        SettingsSwitchRow(
                            "ui.reduce.motion",
                            "ui.disable.reader.page.transitions.and.nonessential.animation",
                            settings.reducedMotion(),
                            presentation::setReducedMotion,
                        )
                    }
                    item {
                        SettingsRow("ui.navigation", settings.navigationStyle().displayName()) {
                            presentation.setNavigationStyle(settings.navigationStyle().next())
                        }
                    }
                    item { SettingsHint("ui.appearance.changes.apply.immediately.on.android.and.desktop") }
                }
                SettingsDestination.PRIVACY -> {
                    item { SettingsSection("ui.content") }
                    item {
                        SettingsSwitchRow(
                            "ui.show.adult.content",
                            "ui.allow.sources.and.titles.marked.as.adult",
                            settings.showAdultContent(),
                            presentation::setShowAdultContent,
                        )
                    }
                    item { SettingsSection("ui.privacy") }
                    item {
                        SettingsSwitchRow(
                            "ui.incognito.mode",
                            "ui.do.not.write.new.reader.or.player.history.and.progress",
                            settings.incognitoMode(),
                            presentation::setIncognitoMode,
                        )
                    }
                }
                SettingsDestination.LIBRARY -> {
                    item { SettingsSection("ui.library.updates") }
                    item {
                        SettingsSwitchRow(
                            "ui.wi.fi.only.updates",
                            "ui.refresh.the.library.automatically.only.on.wi.fi",
                            settings.updateOnlyOnWifi(),
                            presentation::setUpdateOnlyOnWifi,
                        )
                    }
                    item { SettingsHint("ui.schedule.and.skip.controls.remain.available.on.the.updates.screen") }
                }
                SettingsDestination.READER -> {
                    item { SettingsSection("ui.reader.behavior") }
                    item { SettingsRow("ui.reading.direction", "ui.choose.ltr.rtl.vertical.or.webtoon.in.reader") }
                    item { SettingsRow("ui.prefetch.and.retry", "ui.managed.by.the.shared.reader.pipeline") }
                    item { SettingsHint("ui.direction.and.position.are.retained.per.title") }
                }
                SettingsDestination.PLAYER -> {
                    item { SettingsSection("ui.player.behavior") }
                    if (supportsPlayerWindowModes) {
                        item {
                            SettingsRow(
                                "ui.player.window.mode",
                                playerWindowModeLabel(settings.playerWindowMode()),
                            ) { choosingPlayerWindowMode = true }
                        }
                    }
                    item { SettingsRow("ui.quality.and.subtitles", "ui.choose.them.from.the.episode.screen") }
                    item { SettingsRow("ui.resume", "ui.playback.position.is.retained.per.episode") }
                    item { SettingsHint("ui.android.and.desktop.use.the.same.stream.and.subtitle.policy") }
                }
                SettingsDestination.DOWNLOADS -> {
                    item { SettingsSection("ui.network") }
                    item {
                        SettingsSwitchRow(
                            "ui.wi.fi.only.downloads",
                            "ui.keep.queued.downloads.off.metered.connections",
                            settings.downloadOnlyOnWifi(),
                            presentation::setDownloadOnlyOnWifi,
                        )
                    }
                    item { SettingsSection("ui.queue.and.storage") }
                    item {
                        SettingsRow(
                            "ui.manage.downloads",
                            "ui.queue.offline.mode.and.storage.usage",
                            openDownloads,
                        )
                    }
                }
                SettingsDestination.ADVANCED -> {
                    item { SettingsSection("ui.network.and.browser") }
                    item {
                        SettingsRow(
                            "ui.network.policy",
                            "ui.user.agent.proxy.dns.over.https.timeout.cache.and.diagnostics",
                            openNetworkPolicy,
                        )
                    }
                    item {
                        SettingsRow(
                            "ui.browser.settings",
                            "ui.javascript.storage.files.pop.ups.downloads.challenge.retry.and.text.zoom",
                            openBrowserSettings,
                        )
                    }
                    item {
                        SettingsRow("ui.clear.cookies", "ui.sign.out.browser.sessions.for.every.source") {
                            requestMaintenance(MaintenanceAction.COOKIES)
                        }
                    }
                    item {
                        SettingsRow("ui.clear.network.cache", "ui.remove.cached.http.responses") {
                            requestMaintenance(MaintenanceAction.CACHE)
                        }
                    }
                    item {
                        SettingsRow("ui.clear.webview.data", "ui.remove.browser.cookies.cache.and.site.storage") {
                            requestMaintenance(MaintenanceAction.BROWSER_DATA)
                        }
                    }
                    item { SettingsSection("ui.application.data") }
                    item {
                        SettingsRow(
                            "ui.storage.and.diagnostics",
                            "ui.inspect.storage.logs.crash.reports.export.and.safe.reset",
                            openDiagnostics,
                        )
                    }
                    item {
                        SettingsRow("ui.clean.database", "ui.remove.records.for.titles.no.longer.in.the.library") {
                            requestMaintenance(MaintenanceAction.UNUSED_DATA)
                        }
                    }
                    item { SettingsRow("ui.backup.and.restore", "ui.protect.or.import.your.library", openBackup) }
                    item { SettingsRow("ui.about.anilib", "ui.version.updates.project.and.help", openAbout) }
                    result?.let { message -> item { SettingsResult(message) } }
                }
            }
        }
    }
    if (choosingLanguage) {
        LanguageDialog(
            selected = settings.languagePack(),
            select = {
                presentation.setLanguagePack(it)
                choosingLanguage = false
            },
            close = { choosingLanguage = false },
        )
    }
    if (choosingPlayerWindowMode) {
        PlayerWindowModeDialog(
            selected = settings.playerWindowMode(),
            select = {
                presentation.setPlayerWindowMode(it)
                choosingPlayerWindowMode = false
            },
            close = { choosingPlayerWindowMode = false },
        )
    }
}

@Composable
private fun LanguageDialog(
    selected: LanguagePack,
    select: (LanguagePack) -> Unit,
    close: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = close,
        title = { Text("ui.choose.language") },
        text = {
            Column {
                LanguagePack.entries.forEach { language ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { select(language) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selected == language,
                            onClick = { select(language) },
                        )
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text(languageLabel(language), fontWeight = FontWeight.SemiBold)
                            Text(languageDescription(language), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = close) { Text("ui.cancel") } },
    )
}

@Composable
private fun PlayerWindowModeDialog(
    selected: PlayerWindowMode,
    select: (PlayerWindowMode) -> Unit,
    close: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = close,
        title = { Text("ui.choose.player.window.mode") },
        text = {
            Column {
                PlayerWindowMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { select(mode) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selected == mode,
                            onClick = { select(mode) },
                        )
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text(playerWindowModeLabel(mode), fontWeight = FontWeight.SemiBold)
                            Text(playerWindowModeDescription(mode), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = close) { Text("ui.cancel") } },
    )
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
        title = { Text("ui.browser.settings") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                SettingsSwitchRow("ui.javascript", "ui.allow.website.scripts", javaScript) { javaScript = it }
                SettingsSwitchRow("ui.dom.storage", "ui.allow.website.local.storage", domStorage) { domStorage = it }
                SettingsSwitchRow("ui.file.chooser", "ui.allow.user.initiated.file.selection", fileChooser) {
                    fileChooser = it
                }
                SettingsSwitchRow("ui.pop.ups", "ui.open.requested.windows.in.the.current.browser", popups) {
                    popups = it
                }
                SettingsSwitchRow("ui.downloads", "ui.hand.downloads.to.the.platform", downloads) { downloads = it }
                SettingsSwitchRow(
                    "ui.automatic.challenge.retry",
                    "source.cookies.retry",
                    challengeRetry,
                ) { challengeRetry = it }
                OutlinedTextField(textZoom, { textZoom = it }, label = { Text("ui.text.zoom.50200") })
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
            }) { Text("ui.save") }
        },
        dismissButton = { TextButton(onClick = close) { Text("ui.cancel") } },
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
    var snapshot by remember(presentation) { mutableStateOf<DiagnosticSnapshot?>(null) }
    var feedback by remember { mutableStateOf<String?>(null) }
    val scope = rememberCrashSafeCoroutineScope()
    CrashSafeLaunchedEffect(presentation, revision) {
        snapshot = withContext(Dispatchers.IO) { presentation.diagnostics() }
    }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("ui.storage.and.diagnostics") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                val current = snapshot
                if (current == null) {
                    SettingsHint(UiTranslations.translate("ui.loading", LocalLanguagePack.current))
                } else {
                    Text(
                        UiTranslations.format(
                            "dynamic.storage.files",
                            LocalLanguagePack.current,
                            formatDiagnosticBytes(current.totalBytes()),
                            current.totalFiles(),
                        ),
                    )
                    Text(current.dataDirectory().toString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    current.storage().forEach { usage ->
                        SettingsRow(
                            usage.area(),
                            "${formatDiagnosticBytes(usage.bytes())} · ${usage.files()} files",
                        )
                    }
                    SettingsSection("ui.reports")
                    if (current.reports().isEmpty()) {
                        SettingsHint("ui.no.log.or.crash.report.is.available")
                    } else {
                        current.reports().forEach { report ->
                            SettingsRow(
                                report.name(),
                                "${report.type().name.lowercase()} · ${formatDiagnosticBytes(report.bytes())}",
                            )
                        }
                    }
                }
                TextButton(onClick = {
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) { presentation.exportDiagnostics() }
                        }.onSuccess { archive ->
                            exportPicker.export(
                                archive,
                                { feedback = "Diagnostics exported" },
                                { feedback = it },
                            )
                        }.onFailure { feedback = it.message ?: "Diagnostics export failed" }
                    }
                }) { Text("ui.export.diagnostics") }
                TextButton(onClick = {
                    scope.launch {
                        requestReset(withContext(Dispatchers.IO) {
                            presentation.planReset(
                                setOf(
                                    DiagnosticResetArea.NETWORK_CACHE,
                                    DiagnosticResetArea.LOGS,
                                    DiagnosticResetArea.CRASH_REPORTS,
                                ),
                            )
                        })
                    }
                }) { Text("ui.clear.cache.and.reports") }
                TextButton(onClick = {
                    scope.launch {
                        requestReset(withContext(Dispatchers.IO) {
                            presentation.planReset(setOf(DiagnosticResetArea.SETTINGS))
                        })
                    }
                }) { Text("ui.reset.settings") }
                feedback?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            }
        },
        confirmButton = {
            TextButton(onClick = { revision++ }) { Text("ui.refresh") }
        },
        dismissButton = { TextButton(onClick = close) { Text("ui.close") } },
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
        title = { Text("ui.network.policy") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(userAgent, { userAgent = it }, label = { Text("ui.user.agent") })
                OutlinedTextField(proxy, { proxy = it }, label = { Text("ui.http.proxy.optional") })
                OutlinedTextField(doh, { doh = it }, label = { Text("ui.dns.over.https.url.optional") })
                OutlinedTextField(timeout, { timeout = it }, label = { Text("ui.timeout.in.seconds") })
                SettingsSwitchRow(
                    "ui.response.cache",
                    "ui.read.and.write.shared.http.cache.entries",
                    cacheEnabled,
                    { cacheEnabled = it },
                )
                SettingsSection("ui.per.source.diagnostic")
                OutlinedTextField(sourceId, { sourceId = it }, label = { Text("ui.source.id") })
                OutlinedTextField(endpoint, { endpoint = it }, label = { Text("ui.https.endpoint") })
                TextButton(onClick = {
                    feedback = runCatching {
                        val diagnostic = maintenance.diagnose(sourceId, URI.create(endpoint))
                        "${diagnostic.sourceId()}: ${diagnostic.message()} in ${diagnostic.elapsed().toMillis()} ms"
                    }.fold({ it }, { it.message ?: "Diagnostic failed" })
                }) { Text("ui.run.diagnostic") }
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
            }) { Text("ui.save") }
        },
        dismissButton = { TextButton(onClick = close) { Text("ui.cancel") } },
    )
}

@Composable
private fun SettingsSection(label: String) {
    AnilibSection(label)
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    AnilibGroup(modifier = Modifier.padding(horizontal = 16.dp), content = content)
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
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val leadingIcon = icon ?: if (action != null) Icons.Outlined.Settings else null
        if (leadingIcon != null) {
            SettingsIcon(leadingIcon)
            Spacer(Modifier.width(16.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(3.dp))
            Text(
                summary,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
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
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIcon(icon)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(3.dp))
            Text(summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
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
    Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
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

private fun languageDescription(language: LanguagePack): String = when (language) {
    LanguagePack.SYSTEM -> "Use the device language"
    LanguagePack.ENGLISH -> "Always display Anilib in English"
    LanguagePack.FRENCH -> "Toujours afficher Anilib en français"
}

private fun typographyLabel(scale: TypographyScale): String = when (scale) {
    TypographyScale.COMPACT -> "Compact (90%)"
    TypographyScale.STANDARD -> "Standard (100%)"
    TypographyScale.LARGE -> "Large (115%)"
}

private fun playerWindowModeLabel(mode: PlayerWindowMode): String = when (mode) {
    PlayerWindowMode.WINDOWED -> "ui.player.window.mode.windowed"
    PlayerWindowMode.FULLSCREEN -> "ui.player.window.mode.fullscreen"
    PlayerWindowMode.BORDERLESS -> "ui.player.window.mode.borderless"
}

private fun playerWindowModeDescription(mode: PlayerWindowMode): String = when (mode) {
    PlayerWindowMode.WINDOWED -> "ui.player.window.mode.windowed.description"
    PlayerWindowMode.FULLSCREEN -> "ui.player.window.mode.fullscreen.description"
    PlayerWindowMode.BORDERLESS -> "ui.player.window.mode.borderless.description"
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
