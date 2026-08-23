package fr.vriege.anilib.platform.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.vriege.anilib.feature.extensionrepository.ExtensionArtifactFormat
import fr.vriege.anilib.feature.extensionrepository.ExtensionContentKind
import fr.vriege.anilib.feature.extensionrepository.ExtensionPlatformAvailability
import fr.vriege.anilib.feature.extensionrepository.ExtensionInstallationState
import fr.vriege.anilib.feature.extensionrepository.ExtensionPackageMetadata
import fr.vriege.anilib.feature.extensionrepository.ExtensionSourceMetadata
import fr.vriege.anilib.feature.extensionrepository.InstalledExtensionPackage
import fr.vriege.anilib.feature.extensionrepository.ui.ExtensionRepositoryPresentation
import fr.vriege.anilib.feature.extensionrepository.ui.ExtensionRepositoryRow
import fr.vriege.anilib.feature.extensionrepository.ui.ExtensionRepositoryView
import fr.vriege.anilib.feature.extensionrepository.ui.ApkExtensionCompatibility
import fr.vriege.anilib.feature.extensionrepository.ui.ApkExtensionPlatform
import fr.vriege.anilib.feature.extensionrepository.ui.ApkExtensionRuntimeReport
import fr.vriege.anilib.feature.extensionrepository.ui.ApkExtensionRuntimeState
import fr.vriege.anilib.feature.extensionrepository.ui.InstalledApkExtension
import fr.vriege.anilib.feature.discovery.ui.DiscoveryPresentation
import fr.vriege.anilib.feature.source.SourceDescriptor
import fr.vriege.anilib.feature.source.SourceId
import fr.vriege.anilib.feature.settings.LanguagePack
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.ColumnScope
import java.util.concurrent.CompletableFuture

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExtensionRepositoriesScreen(
    presentation: ExtensionRepositoryPresentation,
    goBack: () -> Unit,
) {
    var view by remember { mutableStateOf(presentation.snapshot()) }
    var adding by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingRepositoryRemoval by remember { mutableStateOf<String?>(null) }
    val scope = rememberCrashSafeCoroutineScope()

    DisposableEffect(presentation) {
        val observation = presentation.observe { view = presentation.snapshot() }
        onDispose { observation.close() }
    }

    fun refresh() {
        if (loading) return
        loading = true
        error = null
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { presentation.refreshAll().get() }
            }.onSuccess { view = it }
                .onFailure { failure ->
                    error = failure.cause?.message ?: failure.message ?: "Repository refresh failed."
                }
            loading = false
        }
    }

    CrashSafeLaunchedEffect(Unit) {
        if (view.repositories().isNotEmpty()) refresh()
    }

    AnilibSubScreenScaffold(
        title = "repositories.title",
        goBack = goBack,
        actions = {
            IconButton(onClick = ::refresh, enabled = !loading) {
                Icon(Icons.Default.Refresh, contentDescription = "repositories.refresh")
            }
            IconButton(onClick = { adding = true }) {
                Icon(Icons.Default.Add, contentDescription = "repository.add")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (loading) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
            if (view.repositories().isEmpty() && !loading) {
                item { EmptyPage("ui.no.extension.repository.configured") }
            } else {
                items(view.repositories(), key = { it.indexUri().toString() }) { repository ->
                    RepositoryCard(repository) {
                        pendingRepositoryRemoval = repository.indexUri().toString()
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    if (adding) {
        AddRepositoryDialog(
            dismiss = { adding = false },
            add = { url ->
                runCatching { presentation.add(url) }
                    .onSuccess {
                        adding = false
                        view = presentation.snapshot()
                        refresh()
                    }
                    .onFailure { error = it.message ?: "Invalid repository URL." }
            },
        )
    }
    pendingRepositoryRemoval?.let { repository ->
        ConfirmRepositoryRemovalDialog(
            repository = repository,
            dismiss = { pendingRepositoryRemoval = null },
            confirm = {
                pendingRepositoryRemoval = null
                runCatching { presentation.remove(repository) }
                    .onFailure { error = it.message ?: "Repository removal failed." }
            },
        )
    }
    error?.let { message ->
        UiNoticeDialog(
            kind = UiNoticeKind.ERROR,
            message = message,
            dismiss = { error = null },
            retry = ::refresh,
        )
    }
}

@Composable
internal fun ExtensionDiscoveryList(
    presentation: ExtensionRepositoryPresentation,
    apkExtensionPlatform: ApkExtensionPlatform,
    discovery: DiscoveryPresentation,
    kind: ExtensionContentKind,
    query: String,
    manageRepositories: () -> Unit,
    onSourcesChanged: () -> Unit,
    onSourcePreferenceChanged: () -> Unit,
) {
    var view by remember { mutableStateOf(presentation.snapshot()) }
    var installedApkPackages by remember(apkExtensionPlatform) {
        mutableStateOf(runCatching(apkExtensionPlatform::installedPackageNames).getOrDefault(emptySet()))
    }
    var activeApkPackages by remember(apkExtensionPlatform) {
        mutableStateOf(runCatching(apkExtensionPlatform::activePackageNames).getOrDefault(emptySet()))
    }
    var loadingPackage by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingRemoval by remember { mutableStateOf<ExtensionRemovalRequest?>(null) }
    var selectedExtension by remember { mutableStateOf<ExtensionPackageMetadata?>(null) }
    var sourceStateRevision by remember { mutableStateOf(0) }
    val scope = rememberCrashSafeCoroutineScope()

    DisposableEffect(presentation) {
        val observation = presentation.observe { view = presentation.snapshot() }
        onDispose { observation.close() }
    }
    CrashSafeLaunchedEffect(Unit) {
        if (view.repositories().isNotEmpty() && view.packages().isEmpty()) {
            refreshing = true
            runCatching { withContext(Dispatchers.IO) { presentation.refreshAll().get() } }
                .onSuccess { view = it }
                .onFailure { error = it.cause?.message ?: it.message }
            refreshing = false
        }
    }

    val packages = view.packages().filter { extension ->
        (extension.contentKind() == kind || extension.contentKind() == ExtensionContentKind.MIXED) &&
            extensionMatches(extension, query)
    }.sortedWith(
        compareByDescending<ExtensionPackageMetadata> { extension ->
            extension.packageName() in installedApkPackages || view.installed().any {
                it.packageName() == extension.packageName()
            }
        }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.displayName() },
    )
    selectedExtension?.let { extension ->
        ExtensionSourcesDetailScreen(
            extension = extension,
            discovery = discovery,
            revision = sourceStateRevision,
            goBack = { selectedExtension = null },
            sourceStateChanged = {
                sourceStateRevision++
                onSourcePreferenceChanged()
            },
            reportFailure = { error = it },
        )
        return
    }
    if (view.repositories().isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("ui.add.a.repository.to.discover.extensions", color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = manageRepositories) { Text("repositories.manage") }
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (refreshing) {
            item {
                Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                }
            }
        }
        if (!refreshing && packages.isEmpty()) {
            item {
                EmptyPage(
                    UiTranslations.format(
                        "dynamic.no.extension.kind",
                        LocalLanguagePack.current,
                        UiTranslations.translate(
                            if (kind == ExtensionContentKind.ANIME) "ui.anime" else "ui.manga",
                            LocalLanguagePack.current,
                        ).lowercase(),
                    ),
                )
            }
        }
        items(packages, key = { it.packageName() }) { extension ->
            val installedPortable = view.installed().firstOrNull {
                it.packageName() == extension.packageName()
            }
            val apkInstalled = extension.packageName() in installedApkPackages
            val blockedByAdultPolicy = extension.adult() && !view.adultContentEnabled()
            Card(
                modifier = Modifier.fillMaxWidth().clickable { selectedExtension = extension },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ExtensionIcon(extension.icon().orElse(null), extension.displayName())
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(extension.displayName(), fontWeight = FontWeight.Medium)
                        Text(
                            "${extension.languageTag()} · ${extension.versionName()}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val installed = apkInstalled || installedPortable != null
                        val installationStatus = when {
                            apkInstalled && extension.packageName() in activeApkPackages ->
                                "extension.status.source_active"
                            apkInstalled -> "extension.status.desktop_incompatible"
                            installedPortable != null -> "extension.installed"
                            else -> "Available"
                        }
                        Text(
                            installationStatus,
                            color = if (installed) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        if (blockedByAdultPolicy) {
                            Text(
                                "extension.adult.install.blocked",
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                    }
                    if (!apkInstalled && installedPortable == null) {
                        Button(
                            enabled = loadingPackage == null && !blockedByAdultPolicy,
                            onClick = {
                                loadingPackage = extension.packageName()
                                error = null
                                message = null
                                val hasPortable = extension.artifacts().any {
                                    it.format() == ExtensionArtifactFormat.ANILIB_BUNDLE
                                }
                                if (hasPortable) {
                                    scope.launch {
                                        runCatching {
                                            withContext(Dispatchers.IO) { presentation.install(extension).get() }
                                        }.onSuccess {
                                            view = it
                                            message = "${extension.displayName()} installed. " +
                                                "Restart Anilib to activate its sources."
                                        }.onFailure { failure ->
                                            error = failure.cause?.message ?: failure.message
                                        }
                                        loadingPackage = null
                                    }
                                } else {
                                    installApkExtension(
                                        apkExtensionPlatform,
                                        extension,
                                        scope,
                                        { busy -> if (!busy) loadingPackage = null },
                                    ) { feedback, failure ->
                                        error = failure
                                        message = feedback
                                        if (failure == null) {
                                            installedApkPackages = runCatching(
                                                apkExtensionPlatform::installedPackageNames,
                                            ).getOrDefault(installedApkPackages)
                                            activeApkPackages = runCatching(
                                                apkExtensionPlatform::activePackageNames,
                                            ).getOrDefault(activeApkPackages)
                                            onSourcesChanged()
                                        }
                                    }
                                }
                            },
                        ) {
                            Text(
                                if (loadingPackage == extension.packageName()) "Installing…"
                                else "extension.install",
                            )
                        }
                    } else {
                        TextButton(
                            enabled = loadingPackage == null,
                            onClick = {
                                pendingRemoval = ExtensionRemovalRequest(
                                    extension.packageName(),
                                    extension.displayName(),
                                    apkInstalled,
                                )
                            },
                        ) { Text("extension.uninstall") }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
    pendingRemoval?.let { target ->
        ConfirmExtensionRemovalDialog(
            target = target,
            dismiss = { pendingRemoval = null },
            confirm = {
                pendingRemoval = null
                loadingPackage = target.packageName
                error = null
                message = null
                if (target.apk) {
                    uninstallApkExtension(apkExtensionPlatform, target.packageName, scope) { feedback, failure ->
                        loadingPackage = null
                        error = failure
                        message = feedback
                        if (failure == null) {
                            installedApkPackages = runCatching(apkExtensionPlatform::installedPackageNames)
                                .getOrDefault(installedApkPackages - target.packageName)
                            activeApkPackages = runCatching(apkExtensionPlatform::activePackageNames)
                                .getOrDefault(activeApkPackages - target.packageName)
                            onSourcesChanged()
                        }
                    }
                } else {
                    runCatching { presentation.removeInstalled(target.packageName) }
                        .onSuccess {
                            loadingPackage = null
                            view = presentation.snapshot()
                            message = "${target.displayName} removed. Restart Anilib to unload its sources."
                        }.onFailure { failure ->
                            loadingPackage = null
                            error = failure.message ?: "Extension removal failed."
                        }
                }
            },
        )
    }
    error?.let { value ->
        UiNoticeDialog(UiNoticeKind.ERROR, value, dismiss = { error = null })
    }
    message?.let { value ->
        UiNoticeDialog(UiNoticeKind.INFO, value, dismiss = { message = null })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExtensionSourcesDetailScreen(
    extension: ExtensionPackageMetadata,
    discovery: DiscoveryPresentation,
    revision: Int,
    goBack: () -> Unit,
    sourceStateChanged: () -> Unit,
    reportFailure: (String) -> Unit,
) {
    val sourceRows = remember(extension, discovery, revision) {
        extension.sources().map { metadata ->
            metadata to installedSource(discovery, metadata)
        }
    }
    val multiLanguage = extension.sources().map { it.languageTag() }.distinct().size > 1
    AnilibSubScreenScaffold(title = extension.displayName(), goBack = goBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ExtensionIcon(extension.icon().orElse(null), extension.displayName())
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(extension.displayName(), fontWeight = FontWeight.SemiBold)
                        Text(
                            "${extensionLanguageName(extension.languageTag())} · v${extension.versionName()}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item { SectionTitle("extension.sources") }
            items(sourceRows, key = { it.first.sourceId() }) { (metadata, source) ->
                val enabled = source?.id()?.let(discovery::sourceEnabled) ?: false
                val label = if (multiLanguage && extension.sources()
                        .map { it.displayName() }
                        .distinct()
                        .size == 1
                ) {
                    extensionLanguageName(metadata.languageTag())
                } else {
                    metadata.displayName()
                }
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(label, fontWeight = FontWeight.Medium)
                        Text(
                            if (source == null) {
                                extensionLanguageName(metadata.languageTag())
                            } else {
                                "${source.displayName()} · ${source.id()}"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (source == null) {
                            Text(
                                "extension.source.available_after_installation",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Switch(
                        checked = enabled,
                        enabled = source != null,
                        onCheckedChange = { checked ->
                            val descriptor = source ?: return@Switch
                            runCatching { discovery.setSourceEnabled(descriptor.id(), checked) }
                                .onSuccess { sourceStateChanged() }
                                .onFailure {
                                    reportFailure(it.message ?: "extension.source.state_failed")
                                }
                        },
                    )
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

private fun installedSource(
    discovery: DiscoveryPresentation,
    metadata: ExtensionSourceMetadata,
): SourceDescriptor? = metadata.runtimeSourceIds().asSequence()
    .mapNotNull { identity ->
        runCatching { discovery.source(SourceId.of(identity)).orElse(null) }.getOrNull()
    }
    .firstOrNull()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExtensionRepositoryCatalogueScreen(
    presentation: ExtensionRepositoryPresentation,
    apkExtensionPlatform: ApkExtensionPlatform,
    goBack: () -> Unit,
) {
    var view by remember { mutableStateOf(presentation.snapshot()) }
    var adding by remember { mutableStateOf(false) }
    var trusting by remember { mutableStateOf(false) }
    var filteringLanguages by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var operationLabel by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableStateOf<(() -> Unit)?>(null) }
    var selectedExtension by remember { mutableStateOf<ExtensionPackageMetadata?>(null) }
    var installedQuery by remember { mutableStateOf("") }
    var pendingRemoval by remember { mutableStateOf<ExtensionRemovalRequest?>(null) }
    var pendingRepositoryRemoval by remember { mutableStateOf<String?>(null) }
    val scope = rememberCrashSafeCoroutineScope()
    var pendingApkTrust by remember { mutableStateOf<InstalledApkExtension?>(null) }
    var installedApkExtensions by remember(apkExtensionPlatform) {
        mutableStateOf(runCatching(apkExtensionPlatform::discoverInstalled).getOrDefault(emptyList()))
    }
    var installedApkPackages by remember(apkExtensionPlatform) {
        mutableStateOf(runCatching(apkExtensionPlatform::installedPackageNames).getOrDefault(emptySet()))
    }
    var activeApkPackages by remember(apkExtensionPlatform) {
        mutableStateOf(runCatching(apkExtensionPlatform::activePackageNames).getOrDefault(emptySet()))
    }
    var apkRuntimeReports by remember(apkExtensionPlatform) {
        mutableStateOf(inspectApkRuntimes(apkExtensionPlatform, installedApkExtensions))
    }
    DisposableEffect(presentation) {
        val observation = presentation.observe { view = presentation.snapshot() }
        onDispose { observation.close() }
    }

    fun complete(label: String, operation: () -> CompletableFuture<ExtensionRepositoryView>) {
        loading = true
        operationLabel = label
        error = null
        feedback = null
        retry = null
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { operation().get() }
            }.onSuccess { refreshed ->
                view = refreshed
            }.onFailure { failure ->
                error = buildString {
                    append(failure.cause?.message ?: failure.message ?: "Extension operation failed.")
                    append("\n")
                    append("Diagnostic: ")
                    append((failure.cause ?: failure)::class.simpleName ?: "unknown failure")
                }
                retry = { complete(label, operation) }
            }
            loading = false
            operationLabel = null
        }
    }

    fun refresh() {
        runCatching(apkExtensionPlatform::discoverInstalled)
            .onSuccess {
                installedApkExtensions = it
                apkRuntimeReports = inspectApkRuntimes(apkExtensionPlatform, it)
                installedApkPackages = runCatching(apkExtensionPlatform::installedPackageNames)
                    .getOrDefault(emptySet())
                activeApkPackages = runCatching(apkExtensionPlatform::activePackageNames)
                    .getOrDefault(emptySet())
            }
            .onFailure { error = it.message ?: "Installed APK discovery failed." }
        complete("Refreshing repositories") { presentation.refreshAll() }
    }

    selectedExtension?.let { extension ->
        val installed = view.installed().firstOrNull { it.packageName() == extension.packageName() }
        ExtensionDetailScreen(
            extension = extension,
            installed = installed,
            apkInstalled = extension.packageName() in installedApkPackages,
            trustedKeyIds = view.trustedKeyIds().toSet(),
            adultContentEnabled = view.adultContentEnabled(),
            pinned = extension.packageName() in view.pinnedPackages(),
            loading = loading,
            operationLabel = operationLabel,
            error = error,
            retry = retry,
            goBack = { selectedExtension = null },
            togglePinned = { pinned ->
                runCatching {
                    presentation.setPinned(extension.packageName(), pinned)
                    view = presentation.snapshot()
                }
                    .onFailure { error = it.message ?: "Extension pinning failed." }
            },
            install = { complete("Installing ${extension.displayName()}") { presentation.install(extension) } },
            update = { complete("Updating ${extension.displayName()}") { presentation.update(extension) } },
            toggle = { enabled ->
                runCatching { presentation.setEnabled(extension.packageName(), enabled) }
                    .onFailure { error = it.message ?: "Extension state change failed." }
            },
            remove = {
                pendingRemoval = ExtensionRemovalRequest(
                    extension.packageName(),
                    extension.displayName(),
                    false,
                )
            },
            installApk = if (apkExtensionPlatform.installationSupported()) {
                { installApkExtension(apkExtensionPlatform, extension, scope, { state ->
                    loading = state
                    operationLabel = if (state) apkExtensionPlatform.installProgressLabel() else null
                }) { message, failure ->
                    error = failure
                    feedback = message
                    if (failure == null) {
                        installedApkPackages = runCatching(apkExtensionPlatform::installedPackageNames)
                            .getOrDefault(installedApkPackages)
                    }
                } }
            } else {
                null
            },
            installApkLabel = apkExtensionPlatform.installActionLabel(),
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("repositories.title") },
                navigationIcon = {
                    IconButton(onClick = goBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ui.back")
                    }
                },
                actions = {
                    if (view.availableLanguages().isNotEmpty()) {
                        TextButton(onClick = { filteringLanguages = true }) { Text("ui.languages") }
                    }
                    TextButton(onClick = { trusting = true }) { Text("ui.trust.key") }
                    IconButton(onClick = { refresh() }, enabled = !loading) {
                        Icon(Icons.Default.Refresh, contentDescription = "repositories.refresh")
                    }
                    IconButton(onClick = { adding = true }) {
                        Icon(Icons.Default.Add, contentDescription = "repository.add")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    apkExtensionPlatform.availabilityDescription(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("settings.auto_updates.title", fontWeight = FontWeight.Medium)
                        Text(
                            "ui.checks.every.6.hours.only.the.same.package.and.signing.key.update.silently",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = view.automaticUpdatesEnabled(),
                        onCheckedChange = { enabled ->
                            runCatching { presentation.setAutomaticUpdatesEnabled(enabled) }
                                .onFailure { error = it.message ?: "Automatic update policy failed." }
                        },
                    )
                }
            }
            if (loading) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                        operationLabel?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            error?.let { message ->
                item { ExtensionFailure(message, retry) }
            }
            feedback?.let { message ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    ) {
                        Text(
                            message,
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
            if (view.updates().isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Button(
                            onClick = {
                                complete("Updating all extensions") {
                                    presentation.updateAllAvailable()
                                }
                            },
                            enabled = !loading,
                        ) {
                            Text(
                                UiTranslations.format(
                                    "dynamic.update.all",
                                    LocalLanguagePack.current,
                                    view.updates().size,
                                ),
                            )
                        }
                    }
                }
            }
            val normalizedInstalledQuery = installedQuery.trim().lowercase(Locale.ROOT)
            val metadataByPackage = view.packages().associateBy { it.packageName() }
            val visibleInstalledApks = installedApkExtensions.filter { extension ->
                installedExtensionMatches(
                    extension.packageName(),
                    extension.displayName(),
                    metadataByPackage[extension.packageName()],
                    normalizedInstalledQuery,
                )
            }
            val visibleInstalledBundles = view.installed().filter { extension ->
                installedExtensionMatches(
                    extension.packageName(),
                    extension.displayName(),
                    metadataByPackage[extension.packageName()],
                    normalizedInstalledQuery,
                )
            }
            val inventoriedPackages = installedApkExtensions.map { it.packageName() }.toSet()
            val visibleEnginePackages = installedApkPackages
                .filterNot { it in inventoriedPackages }
                .filter { packageName ->
                    val metadata = metadataByPackage[packageName]
                    installedExtensionMatches(
                        packageName,
                        metadata?.displayName() ?: packageName,
                        metadata,
                        normalizedInstalledQuery,
                    )
                }
                .sortedBy { metadataByPackage[it]?.displayName()?.lowercase(Locale.ROOT) ?: it }
            if (installedApkPackages.isNotEmpty() || view.installed().isNotEmpty()) {
                item { SectionTitle("ui.installed.extensions") }
                item {
                    OutlinedTextField(
                        value = installedQuery,
                        onValueChange = { installedQuery = it },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        placeholder = { Text("extensions.search.installed") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (visibleInstalledApks.isNotEmpty()) {
                item { SectionTitle("ui.installed.apk.extensions") }
                items(visibleInstalledApks, key = { it.packageName() }) { extension ->
                    val runtime = apkRuntimeReports.getValue(extension.packageName())
                    ApkExtensionCard(
                        extension = extension,
                        runtime = runtime,
                        trust = { pendingApkTrust = extension },
                        forgetTrust = {
                            runCatching { apkExtensionPlatform.forgetCertificateTrust(extension) }
                                .onSuccess { report ->
                                    apkRuntimeReports = apkRuntimeReports +
                                        (report.packageName() to report)
                                }
                                .onFailure { error = it.message ?: "Certificate trust removal failed." }
                        },
                        uninstall = {
                            pendingRemoval = ExtensionRemovalRequest(
                                extension.packageName(),
                                extension.displayName(),
                                true,
                            )
                        },
                    )
                }
            }
            if (visibleEnginePackages.isNotEmpty()) {
                item { SectionTitle("ui.installed.desktop.extensions") }
                items(visibleEnginePackages, key = { it }) { packageName ->
                    InstalledEngineExtensionCard(
                        packageName = packageName,
                        metadata = metadataByPackage[packageName],
                        active = packageName in activeApkPackages,
                        uninstall = {
                            pendingRemoval = ExtensionRemovalRequest(
                                packageName,
                                metadataByPackage[packageName]?.displayName() ?: packageName,
                                true,
                            )
                        },
                    )
                }
            }
            if (visibleInstalledBundles.isNotEmpty()) {
                item { SectionTitle("ui.installed.anilib.bundles") }
                items(visibleInstalledBundles, key = { it.packageName() }) { installed ->
                    InstalledExtensionCard(
                        installed = installed,
                        toggle = { enabled -> presentation.setEnabled(installed.packageName(), enabled) },
                        remove = {
                            pendingRemoval = ExtensionRemovalRequest(
                                installed.packageName(),
                                installed.displayName(),
                                false,
                            )
                        },
                    )
                }
            }
            if (installedQuery.isNotBlank() &&
                visibleInstalledApks.isEmpty() && visibleEnginePackages.isEmpty() &&
                visibleInstalledBundles.isEmpty()
            ) {
                item { EmptyPage("ui.no.installed.extension.matches.search") }
            }
            if (view.trustedKeyIds().isNotEmpty()) {
                item { SectionTitle("ui.trusted.publishers") }
                items(view.trustedKeyIds(), key = { it }) { keyId ->
                    TrustedKeyCard(keyId) {
                        runCatching { presentation.forgetTrust(keyId) }
                            .onFailure { error = it.message ?: "Publisher-key removal failed." }
                    }
                }
            }
            item { SectionTitle("ui.repositories") }
            if (view.repositories().isEmpty()) {
                item { EmptyPage("ui.no.extension.repository.configured") }
            } else {
                items(view.repositories(), key = { it.indexUri().toString() }) { repository ->
                    RepositoryCard(repository) {
                        pendingRepositoryRemoval = repository.indexUri().toString()
                    }
                }
            }
            if (view.packages().isNotEmpty()) {
                item { SectionTitle("ui.available.extensions") }
                val availablePackages = view.packages().filter { extension ->
                    extension.packageName() !in installedApkPackages && view.installed().none {
                        it.packageName() == extension.packageName()
                    }
                }
                items(availablePackages, key = { it.packageName() }) { extension ->
                    val installed = view.installed().firstOrNull { it.packageName() == extension.packageName() }
                    ExtensionPackageCard(
                        extension = extension,
                        installed = installed,
                        apkInstalled = extension.packageName() in installedApkPackages,
                        pinned = extension.packageName() in view.pinnedPackages(),
                        adultContentEnabled = view.adultContentEnabled(),
                        busy = loading,
                        openDetails = { selectedExtension = extension },
                        togglePinned = { pinned ->
                            runCatching {
                                presentation.setPinned(extension.packageName(), pinned)
                                view = presentation.snapshot()
                            }
                                .onFailure { error = it.message ?: "Extension pinning failed." }
                        },
                        install = {
                            complete("Installing ${extension.displayName()}") {
                                presentation.install(extension)
                            }
                        },
                        update = {
                            complete("Updating ${extension.displayName()}") {
                                presentation.update(extension)
                            }
                        },
                        toggle = { enabled ->
                            runCatching { presentation.setEnabled(extension.packageName(), enabled) }
                                .onFailure { error = it.message ?: "Extension state change failed." }
                        },
                        remove = {
                            runCatching { presentation.removeInstalled(extension.packageName()) }
                                .onFailure { error = it.message ?: "Extension removal failed." }
                        },
                        installApk = if (apkExtensionPlatform.installationSupported()) {
                            {
                                installApkExtension(apkExtensionPlatform, extension, scope, { state ->
                                    loading = state
                                    operationLabel = if (state) apkExtensionPlatform.installProgressLabel() else null
                                }) { message, failure ->
                                    error = failure
                                    feedback = message
                                    if (failure == null) {
                                        installedApkPackages =
                                            runCatching(apkExtensionPlatform::installedPackageNames)
                                                .getOrDefault(installedApkPackages)
                                        activeApkPackages = runCatching(apkExtensionPlatform::activePackageNames)
                                            .getOrDefault(activeApkPackages)
                                    }
                                }
                            }
                        } else {
                            null
                        },
                        installApkLabel = apkExtensionPlatform.installActionLabel(),
                    )
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }

    if (adding) {
        AddRepositoryDialog(
            dismiss = { adding = false },
            add = { url ->
                runCatching { presentation.add(url) }
                    .onSuccess {
                        adding = false
                        refresh()
                    }
                    .onFailure { error = it.message ?: "Invalid repository URL." }
            },
        )
    }
    if (trusting) {
        TrustKeyDialog(
            dismiss = { trusting = false },
            trust = { keyId, publicKey ->
                runCatching { presentation.trustKey(keyId, publicKey) }
                    .onSuccess { trusting = false }
                    .onFailure { error = it.message ?: "Invalid publisher key." }
            },
        )
    }
    if (filteringLanguages) {
        ExtensionLanguageDialog(
            available = view.availableLanguages(),
            enabled = view.enabledLanguages(),
            dismiss = { filteringLanguages = false },
            toggle = { language, enabled ->
                runCatching { presentation.setLanguageEnabled(language, enabled) }
                    .onFailure { error = it.message ?: "Extension language selection failed." }
            },
        )
    }
    pendingApkTrust?.let { extension ->
        ApkCertificateTrustDialog(
            extension = extension,
            dismiss = { pendingApkTrust = null },
            trust = { certificate ->
                runCatching { apkExtensionPlatform.trustCertificate(extension, certificate) }
                    .onSuccess { report ->
                        apkRuntimeReports = apkRuntimeReports + (report.packageName() to report)
                        pendingApkTrust = null
                    }
                    .onFailure { error = it.message ?: "Certificate trust failed." }
            },
        )
    }
    pendingRemoval?.let { target ->
        ConfirmExtensionRemovalDialog(
            target = target,
            dismiss = { pendingRemoval = null },
            confirm = {
                pendingRemoval = null
                if (target.apk) {
                    loading = true
                    operationLabel = "Uninstalling ${target.displayName}"
                    uninstallApkExtension(apkExtensionPlatform, target.packageName, scope) { message, failure ->
                        loading = false
                        operationLabel = null
                        error = failure
                        feedback = message
                        if (failure == null) {
                            installedApkExtensions = runCatching(apkExtensionPlatform::discoverInstalled)
                                .getOrDefault(installedApkExtensions.filterNot {
                                    it.packageName() == target.packageName
                                })
                            installedApkPackages = runCatching(apkExtensionPlatform::installedPackageNames)
                                .getOrDefault(installedApkPackages - target.packageName)
                            activeApkPackages = runCatching(apkExtensionPlatform::activePackageNames)
                                .getOrDefault(activeApkPackages - target.packageName)
                            apkRuntimeReports = inspectApkRuntimes(apkExtensionPlatform, installedApkExtensions)
                        }
                    }
                } else {
                    runCatching { presentation.removeInstalled(target.packageName) }
                        .onSuccess {
                            view = presentation.snapshot()
                            feedback = "${target.displayName} removed. Restart Anilib to unload its sources."
                            selectedExtension = null
                        }.onFailure { error = it.message ?: "Extension removal failed." }
                }
            },
        )
    }
    pendingRepositoryRemoval?.let { repository ->
        ConfirmRepositoryRemovalDialog(
            repository = repository,
            dismiss = { pendingRepositoryRemoval = null },
            confirm = {
                pendingRepositoryRemoval = null
                runCatching { presentation.remove(repository) }
                    .onFailure { error = it.message ?: "Repository removal failed." }
            },
        )
    }
}

@Composable
private fun ApkExtensionCard(
    extension: InstalledApkExtension,
    runtime: ApkExtensionRuntimeReport,
    trust: () -> Unit,
    forgetTrust: () -> Unit,
    uninstall: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(extension.displayName(), fontWeight = FontWeight.Medium)
            Text(
                UiTranslations.format(
                    "dynamic.apk.extension.metadata",
                    LocalLanguagePack.current,
                    UiTranslations.translate(
                        if (extension.contentKind() == ExtensionContentKind.ANIME) "ui.anime" else "ui.manga",
                        LocalLanguagePack.current,
                    ),
                    extension.versionName(),
                    extension.libraryVersion(),
                    if (extension.adult()) " · 18+" else "",
                    if (extension.torrent()) {
                        UiTranslations.translate("ui.torrent.suffix", LocalLanguagePack.current)
                    } else {
                        ""
                    },
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                UiTranslations.format(
                    "dynamic.apk.source.summary",
                    LocalLanguagePack.current,
                    extension.sourceEntrypoints().size,
                    if (extension.sourceFactory().isPresent) {
                        UiTranslations.translate("ui.factory.suffix", LocalLanguagePack.current)
                    } else {
                        ""
                    },
                    UiTranslations.translate(
                        apkCompatibilityKey(extension.compatibility()),
                        LocalLanguagePack.current,
                    ),
                ),
                color = if (extension.compatibility() == ApkExtensionCompatibility.COMPATIBLE_METADATA) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
            extension.signingCertificateSha256().firstOrNull()?.let { certificate ->
                Text(
                    UiTranslations.format(
                        "dynamic.signing.certificate",
                        LocalLanguagePack.current,
                        certificate.take(16),
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                apkRuntimeStatus(runtime, LocalLanguagePack.current),
                color = when (runtime.state()) {
                    ApkExtensionRuntimeState.ACTIVE -> MaterialTheme.colorScheme.primary
                    ApkExtensionRuntimeState.HOST_ABI_AVAILABLE -> MaterialTheme.colorScheme.tertiary
                    ApkExtensionRuntimeState.HOST_ABI_MISSING -> MaterialTheme.colorScheme.tertiary
                    ApkExtensionRuntimeState.UNSUPPORTED_PLATFORM -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.error
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (runtime.state() == ApkExtensionRuntimeState.TRUST_REQUIRED &&
                    extension.signingCertificateSha256().isNotEmpty()
                ) {
                    TextButton(onClick = trust) { Text("ui.trust.certificate") }
                }
                if (runtime.trustedCertificateSha256().isPresent) {
                    TextButton(onClick = forgetTrust) { Text("ui.forget.trust") }
                }
                TextButton(onClick = uninstall) { Text("extension.uninstall") }
            }
        }
    }
}

private fun inspectApkRuntimes(
    platform: ApkExtensionPlatform,
    extensions: List<InstalledApkExtension>,
): Map<String, ApkExtensionRuntimeReport> = extensions.associate { extension ->
    extension.packageName() to platform.runtimeReport(extension)
}

private fun extensionMatches(extension: ExtensionPackageMetadata, query: String): Boolean {
    val normalized = query.trim().lowercase(Locale.ROOT)
    return normalized.isEmpty() || extension.displayName().lowercase(Locale.ROOT).contains(normalized) ||
        extension.packageName().lowercase(Locale.ROOT).contains(normalized) ||
        extension.languageTag().lowercase(Locale.ROOT).contains(normalized) ||
        extension.sources().any { source ->
            source.displayName().lowercase(Locale.ROOT).contains(normalized)
        }
}

private fun installedExtensionMatches(
    packageName: String,
    displayName: String,
    metadata: ExtensionPackageMetadata?,
    normalizedQuery: String,
): Boolean = normalizedQuery.isEmpty() ||
    packageName.lowercase(Locale.ROOT).contains(normalizedQuery) ||
    displayName.lowercase(Locale.ROOT).contains(normalizedQuery) ||
    metadata?.sources()?.any { source ->
        source.displayName().lowercase(Locale.ROOT).contains(normalizedQuery) ||
            source.languageTag().lowercase(Locale.ROOT).contains(normalizedQuery)
    } == true

private fun uninstallApkExtension(
    platform: ApkExtensionPlatform,
    packageName: String,
    scope: CoroutineScope,
    complete: (String?, String?) -> Unit,
) {
    if (!platform.uninstallationSupported()) {
        complete(null, "Extension removal is unavailable on this platform.")
        return
    }
    scope.launch {
        runCatching { withContext(Dispatchers.IO) { platform.uninstall(packageName).get() } }
            .onSuccess { complete(it, null) }
            .onFailure { failure ->
                complete(null, failure.cause?.message ?: failure.message ?: "Extension removal failed.")
            }
    }
}

private data class ExtensionRemovalRequest(
    val packageName: String,
    val displayName: String,
    val apk: Boolean,
)

@Composable
private fun ConfirmExtensionRemovalDialog(
    target: ExtensionRemovalRequest,
    dismiss: () -> Unit,
    confirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("extension.uninstall.confirm") },
        text = {
            Text(
                UiTranslations.format(
                    "dynamic.uninstall.extension.body",
                    LocalLanguagePack.current,
                    target.displayName,
                ),
            )
        },
        confirmButton = { Button(onClick = confirm) { Text("extension.uninstall") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("ui.cancel") } },
    )
}

@Composable
private fun ConfirmRepositoryRemovalDialog(
    repository: String,
    dismiss: () -> Unit,
    confirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("repository.remove.confirm") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("repository.remove.explanation")
                Text(repository, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { Button(onClick = confirm) { Text("repository.remove") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("ui.cancel") } },
    )
}

private fun apkRuntimeStatus(runtime: ApkExtensionRuntimeReport, language: LanguagePack): String =
    when (runtime.state()) {
        ApkExtensionRuntimeState.UNSUPPORTED_PLATFORM ->
            UiTranslations.translate("ui.apk.runtime.unsupported.platform", language)
        ApkExtensionRuntimeState.INCOMPATIBLE_METADATA ->
            UiTranslations.translate("ui.apk.runtime.incompatible.metadata", language)
        ApkExtensionRuntimeState.TRUST_REQUIRED ->
            UiTranslations.translate("ui.apk.runtime.trust.required", language)
        ApkExtensionRuntimeState.HOST_ABI_MISSING -> UiTranslations.format(
            "dynamic.apk.runtime.host.abi.missing",
            language,
            runtime.missingHostClasses().size,
        )
        ApkExtensionRuntimeState.HOST_ABI_AVAILABLE ->
            UiTranslations.translate("ui.apk.runtime.host.abi.available", language)
        ApkExtensionRuntimeState.ACTIVATION_FAILED -> UiTranslations.format(
            "dynamic.apk.runtime.activation.failed",
            language,
            runtime.activationFailure().orElse(
                UiTranslations.translate("ui.apk.runtime.unknown.error", language),
            ),
        )
        ApkExtensionRuntimeState.ACTIVE -> UiTranslations.translate("ui.apk.runtime.active", language)
}

private fun apkCompatibilityKey(compatibility: ApkExtensionCompatibility): String = when (compatibility) {
    ApkExtensionCompatibility.COMPATIBLE_METADATA ->
        "ui.apk.compatibility.detected"
    ApkExtensionCompatibility.UNSUPPORTED_LIBRARY -> "ui.apk.compatibility.unsupported.library"
    ApkExtensionCompatibility.MISSING_ENTRYPOINT -> "ui.apk.compatibility.missing.entrypoint"
    ApkExtensionCompatibility.UNSIGNED -> "ui.apk.compatibility.unsigned"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExtensionDetailScreen(
    extension: ExtensionPackageMetadata,
    installed: InstalledExtensionPackage?,
    apkInstalled: Boolean,
    trustedKeyIds: Set<String>,
    adultContentEnabled: Boolean,
    pinned: Boolean,
    loading: Boolean,
    operationLabel: String?,
    error: String?,
    retry: (() -> Unit)?,
    goBack: () -> Unit,
    togglePinned: (Boolean) -> Unit,
    install: () -> Unit,
    update: () -> Unit,
    toggle: (Boolean) -> Unit,
    remove: () -> Unit,
    installApk: (() -> Unit)?,
    installApkLabel: String,
) {
    val portable = extension.artifacts().any {
        it.format() == ExtensionArtifactFormat.ANILIB_BUNDLE
    }
    val apk = extension.artifacts().any {
        it.format() == ExtensionArtifactFormat.ANIYOMI_APK
    }
    val blockedByAdultPolicy = extension.adult() && !adultContentEnabled
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(extension.displayName()) },
                navigationIcon = {
                    IconButton(onClick = goBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ui.back")
                    }
                },
                actions = {
                    IconButton(onClick = { togglePinned(!pinned) }) {
                        Icon(
                            if (pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = if (pinned) "Unpin extension" else "Pin extension",
                            tint = if (pinned) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
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
                Text(
                    "${extension.packageName()} · ${extension.languageTag()} · " +
                        extension.contentKind().name.lowercase(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (loading) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CircularProgressIndicator()
                            Text(operationLabel ?: "Extension operation in progress…")
                        }
                    }
                }
            }
            error?.let { message -> item { ExtensionFailure(message, retry) } }
            if (blockedByAdultPolicy) {
                item {
                    ExtensionDetailCard {
                        Text("settings.adult.title", fontWeight = FontWeight.Medium)
                        Text(
                            "extension.adult.install.explanation",
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
            }
            item { SectionTitle("ui.versions") }
            item {
                ExtensionDetailCard {
                    Text(
                        UiTranslations.format(
                            "dynamic.available.version",
                            LocalLanguagePack.current,
                            extension.versionName(),
                            extension.versionCode(),
                        ),
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        installed?.let {
                            "Installed: ${it.versionName()} (${it.versionCode()}) · " +
                                it.state().name.lowercase()
                        } ?: if (apkInstalled) "APK installed and active" else "Not installed",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item { SectionTitle("ui.permissions.and.sources") }
            items(extension.sources(), key = { it.sourceId() }) { source ->
                ExtensionDetailCard {
                    Text(source.displayName(), fontWeight = FontWeight.Medium)
                    Text(
                        UiTranslations.format(
                            "dynamic.source.language",
                            LocalLanguagePack.current,
                            source.sourceId(),
                            source.languageTag(),
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        source.baseUri().map { "Network access: ${it.scheme}://${it.host}" }
                            .orElse("No repository-declared network origin"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item { SectionTitle("ui.trust.and.artifacts") }
            items(extension.artifacts(), key = { it.format().name }) { artifact ->
                val keyId = artifact.signingKeyId().orElse(null)
                ExtensionDetailCard {
                    Text(artifact.format().name.replace('_', ' '), fontWeight = FontWeight.Medium)
                    Text(artifact.uri().toString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        keyId?.let {
                            "Publisher $it · ${if (it in trustedKeyIds) "trusted" else "trust required"}"
                        } ?: "No publisher signature declared",
                        color = if (keyId != null && keyId !in trustedKeyIds) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Text(
                        artifact.sha256().map { "SHA-256 ${it.take(16)}…" }
                            .orElse("No checksum declared"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    artifact.requiredApiVersion().orElse(null)?.let {
                        Text(
                            UiTranslations.format("dynamic.required.source.api", LocalLanguagePack.current, it),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            installed?.signingKeyId()?.orElse(null)?.let { keyId ->
                item {
                    Text(
                        UiTranslations.format(
                            "dynamic.installed.publisher",
                            LocalLanguagePack.current,
                            keyId,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item { SectionTitle("ui.changelog") }
            item {
                ExtensionDetailCard {
                    Text(extension.changelog().orElse("No changelog supplied by this repository."))
                }
            }
            if (installed == null && !apkInstalled && !portable && apk && installApk == null) {
                item {
                    ExtensionDetailCard {
                        Text("ui.apk.extension.runtime.is.unavailable", fontWeight = FontWeight.Medium)
                        Text(
                            "ui.enable.the.desktop.extension.runtime.or.use.a.repository.that.publishes.portable." +
                                "anilib.bundles",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when {
                        installed == null && portable -> Button(
                            onClick = install,
                            enabled = !loading && !blockedByAdultPolicy,
                        ) { Text("extension.install") }
                        installed != null -> {
                            TextButton(
                                onClick = {
                                    toggle(installed.state() == ExtensionInstallationState.DISABLED)
                                },
                                enabled = !loading,
                            ) {
                                Text(if (installed.state() == ExtensionInstallationState.ENABLED) {
                                    "Disable"
                                } else {
                                    "Enable"
                                })
                            }
                            if (extension.versionCode() > installed.versionCode() && portable) {
                                Button(onClick = update, enabled = !loading) { Text("ui.update") }
                            }
                            TextButton(onClick = remove, enabled = !loading) { Text("ui.remove") }
                        }
                    }
                    if (installed == null && !apkInstalled && !portable && extension.artifacts().any {
                            it.format() == ExtensionArtifactFormat.ANIYOMI_APK
                        } && installApk != null
                    ) {
                        Button(onClick = installApk, enabled = !loading && !blockedByAdultPolicy) {
                            Text(installApkLabel)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable
private fun ExtensionDetailCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
            content = content,
        )
    }
}

@Composable
private fun ExtensionFailure(message: String, retry: (() -> Unit)?) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
            retry?.let { action ->
                TextButton(onClick = action) { Text("ui.retry") }
            }
        }
    }
}

private fun installApkExtension(
    platform: ApkExtensionPlatform,
    extension: ExtensionPackageMetadata,
    scope: CoroutineScope,
    setLoading: (Boolean) -> Unit,
    complete: (String?, String?) -> Unit,
) {
    setLoading(true)
    scope.launch {
        runCatching {
            withContext(Dispatchers.IO) { platform.install(extension).get() }
        }.onSuccess { message ->
            complete(message, null)
        }.onFailure { failure ->
            complete(null, failure.cause?.message ?: failure.message ?: "APK hand-off failed.")
        }
        setLoading(false)
    }
}

@Composable
private fun ApkCertificateTrustDialog(
    extension: InstalledApkExtension,
    dismiss: () -> Unit,
    trust: (String) -> Unit,
) {
    val certificate = extension.signingCertificateSha256().firstOrNull() ?: return
    AlertDialog(
        onDismissRequest = dismiss,
        title = {
            Text(UiTranslations.format("dynamic.trust", LocalLanguagePack.current, extension.displayName()))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    UiTranslations.format(
                        "dynamic.trust.apk.body",
                        LocalLanguagePack.current,
                        extension.packageName(),
                    ),
                )
                Text("SHA-256", fontWeight = FontWeight.SemiBold)
                Text(certificate)
            }
        },
        confirmButton = {
            Button(onClick = { trust(certificate) }) { Text("ui.trust.certificate") }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("ui.cancel") } },
    )
}

@Composable
private fun SectionTitle(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun TrustedKeyCard(keyId: String, forget: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(keyId, modifier = Modifier.weight(1f))
            TextButton(onClick = forget) { Text("ui.forget") }
        }
    }
}

@Composable
@Suppress("DEPRECATION")
private fun RepositoryCard(repository: ExtensionRepositoryRow, remove: () -> Unit) {
    val address = repository.indexUri().toString()
    val clipboard = LocalClipboardManager.current
    val scope = rememberCrashSafeCoroutineScope()
    var copied by remember(address) { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                address,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            val status = repository.failure().map { "Failed: $it" }.orElseGet {
                if (repository.fetchedAt().isPresent) {
                    "${repository.packageCount()} packages · ${repository.fetchedAt().orElseThrow()}"
                } else {
                    "Not refreshed yet"
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    status,
                    color = if (repository.failure().isPresent) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    clipboard.setText(AnnotatedString(address))
                    copied = true
                    scope.launch {
                        delay(1_500L)
                        copied = false
                    }
                }) {
                    Icon(
                        if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = UiTranslations.translate(
                            if (copied) "repository.copied" else "repository.copy",
                            LocalLanguagePack.current,
                        ),
                    )
                }
                IconButton(onClick = remove) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = UiTranslations.translate(
                            "repository.remove",
                            LocalLanguagePack.current,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun ExtensionPackageCard(
    extension: ExtensionPackageMetadata,
    installed: InstalledExtensionPackage?,
    apkInstalled: Boolean,
    pinned: Boolean,
    adultContentEnabled: Boolean,
    busy: Boolean,
    openDetails: () -> Unit,
    togglePinned: (Boolean) -> Unit,
    install: () -> Unit,
    update: () -> Unit,
    toggle: (Boolean) -> Unit,
    remove: () -> Unit,
    installApk: (() -> Unit)?,
    installApkLabel: String,
) {
    val blockedByAdultPolicy = extension.adult() && !adultContentEnabled
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = openDetails)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
            ExtensionIcon(extension.icon().orElse(null), extension.displayName())
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(extension.displayName(), fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { togglePinned(!pinned) }) {
                        Icon(
                            if (pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = if (pinned) "Unpin extension" else "Pin extension",
                            tint = if (pinned) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
                Text(
                    "${extension.languageTag()} · v${extension.versionName()} · "
                        + extension.contentKind().name.lowercase(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val formats = extension.artifacts().joinToString(" + ") {
                    when (it.format()) {
                        ExtensionArtifactFormat.ANILIB_BUNDLE -> "Anilib Bundle"
                        ExtensionArtifactFormat.ANIYOMI_APK -> "Aniyomi APK"
                    }
                }
                Text(
                    UiTranslations.format(
                        "dynamic.artifact.formats",
                        LocalLanguagePack.current,
                        formats,
                        extension.sources().size,
                        if (extension.adult()) " · 18+" else "",
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (blockedByAdultPolicy) {
                    Text(
                        "extension.adult.listed",
                        color = MaterialTheme.colorScheme.tertiary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                val availability = ExtensionPlatformAvailability.from(extension)
                val portable = availability.portableArtifact().isPresent
                val apk = availability.apkArtifact().isPresent
                if (installed == null && !apkInstalled && apk && installApk == null) {
                    Text(
                        "ui.apk.extension.runtime.is.unavailable",
                        color = MaterialTheme.colorScheme.tertiary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (apkInstalled) {
                    Text(
                        "ui.installed.sources.active.in.browse",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    when {
                        installed == null && portable -> Button(
                            onClick = install,
                            enabled = !busy && !blockedByAdultPolicy,
                        ) { Text("extension.install") }
                        installed != null -> {
                            TextButton(onClick = {
                                toggle(installed.state() == ExtensionInstallationState.DISABLED)
                            }) {
                                Text(
                                    if (installed.state() == ExtensionInstallationState.ENABLED) {
                                        "Disable"
                                    } else {
                                        "Enable"
                                    },
                                )
                            }
                            if (extension.versionCode() > installed.versionCode() && portable) {
                                Button(onClick = update, enabled = !busy) { Text("ui.update") }
                            }
                            TextButton(onClick = remove) { Text("ui.remove") }
                        }
                    }
                    if (apk && installApk != null && installed == null && !apkInstalled) {
                        Button(onClick = installApk, enabled = !busy && !blockedByAdultPolicy) {
                            Text(installApkLabel)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtensionLanguageDialog(
    available: List<String>,
    enabled: Set<String>,
    dismiss: () -> Unit,
    toggle: (String, Boolean) -> Unit,
) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("extension.languages") },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                items(available, key = { it }) { language ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { toggle(language, language !in enabled) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = language in enabled,
                            onCheckedChange = null,
                        )
                        Text(extensionLanguageName(language))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text("ui.done") } },
    )
}

private fun extensionLanguageName(languageTag: String): String {
    if (languageTag == "und") return "All languages"
    return Locale.forLanguageTag(languageTag).getDisplayName(Locale.getDefault())
        .ifBlank { languageTag }
}

@Composable
private fun InstalledExtensionCard(
    installed: InstalledExtensionPackage,
    toggle: (Boolean) -> Unit,
    remove: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(installed.displayName(), fontWeight = FontWeight.Medium)
            Text(
                "v${installed.versionName()} · ${installed.state().name.lowercase()}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { toggle(installed.state() == ExtensionInstallationState.DISABLED) }) {
                    Text(if (installed.state() == ExtensionInstallationState.ENABLED) "Disable" else "Enable")
                }
                TextButton(onClick = remove) { Text("ui.remove") }
            }
        }
    }
}

@Composable
private fun InstalledEngineExtensionCard(
    packageName: String,
    metadata: ExtensionPackageMetadata?,
    active: Boolean,
    uninstall: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ExtensionIcon(metadata?.icon()?.orElse(null), metadata?.displayName() ?: packageName)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(metadata?.displayName() ?: packageName, fontWeight = FontWeight.Medium)
                Text(
                    metadata?.let { "${it.languageTag()} · ${it.versionName()}" } ?: packageName,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (active) "Available in Sources" else "No compatible source activated",
                    color = if (active) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
            TextButton(onClick = uninstall) { Text("extension.uninstall") }
        }
    }
}

@Composable
private fun AddRepositoryDialog(dismiss: () -> Unit, add: (String) -> Unit) {
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("repository.add") },
        text = {
            Column {
                Text("ui.paste.a.trusted.github.repository.url.or.a.direct.https.json.index.url")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("ui.github.repository.or.index.url") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = { add(url.trim()) }, enabled = url.isNotBlank()) { Text("ui.add") }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("ui.cancel") } },
    )
}

@Composable
private fun TrustKeyDialog(dismiss: () -> Unit, trust: (String, String) -> Unit) {
    var keyId by remember { mutableStateOf("") }
    var publicKey by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("ui.trust.publisher.key") },
        text = {
            Column {
                Text("ui.only.import.an.ed25519.key.fingerprinted.by.a.publisher.you.trust")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = keyId,
                    onValueChange = { keyId = it },
                    label = { Text("ui.key.id") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = publicKey,
                    onValueChange = { publicKey = it },
                    label = { Text("ui.base64.x.509.public.key") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { trust(keyId.trim(), publicKey.trim()) },
                enabled = keyId.isNotBlank() && publicKey.isNotBlank(),
            ) {
                Text("ui.trust")
            }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("ui.cancel") } },
    )
}
