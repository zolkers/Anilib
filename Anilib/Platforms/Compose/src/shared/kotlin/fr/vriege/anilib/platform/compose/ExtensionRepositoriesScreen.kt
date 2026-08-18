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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.vriege.anilib.feature.extensionrepository.ExtensionArtifactFormat
import fr.vriege.anilib.feature.extensionrepository.ExtensionInstallationState
import fr.vriege.anilib.feature.extensionrepository.ExtensionPackageMetadata
import fr.vriege.anilib.feature.extensionrepository.InstalledExtensionPackage
import fr.vriege.anilib.feature.extensionrepository.ui.ExtensionRepositoryPresentation
import fr.vriege.anilib.feature.extensionrepository.ui.ExtensionRepositoryRow
import fr.vriege.anilib.feature.extensionrepository.ui.ExtensionRepositoryView
import fr.vriege.anilib.feature.extensionrepository.ui.LegacyExtensionCompatibility
import fr.vriege.anilib.feature.extensionrepository.ui.LegacyExtensionInstaller
import fr.vriege.anilib.feature.extensionrepository.ui.LegacyExtensionPackage
import fr.vriege.anilib.feature.extensionrepository.ui.LegacyExtensionRuntimeReport
import fr.vriege.anilib.feature.extensionrepository.ui.LegacyExtensionRuntimeState
import java.util.concurrent.CompletableFuture

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExtensionRepositoriesScreen(
    presentation: ExtensionRepositoryPresentation,
    legacyInstaller: LegacyExtensionInstaller,
    goBack: () -> Unit,
) {
    var view by remember { mutableStateOf(presentation.snapshot()) }
    var adding by remember { mutableStateOf(false) }
    var trusting by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingLegacyTrust by remember { mutableStateOf<LegacyExtensionPackage?>(null) }
    var legacyExtensions by remember(legacyInstaller) {
        mutableStateOf(runCatching(legacyInstaller::discoverInstalled).getOrDefault(emptyList()))
    }
    var legacyRuntimeReports by remember(legacyInstaller) {
        mutableStateOf(inspectLegacyRuntimes(legacyInstaller, legacyExtensions))
    }
    DisposableEffect(presentation) {
        val observation = presentation.observe { view = presentation.snapshot() }
        onDispose { observation.close() }
    }

    fun complete(operation: CompletableFuture<ExtensionRepositoryView>) {
        loading = true
        error = null
        operation.whenComplete { refreshed, failure ->
            if (failure == null) {
                view = refreshed
            } else {
                error = failure.cause?.message ?: failure.message ?: "Extension operation failed."
            }
            loading = false
        }
    }

    fun refresh() {
        runCatching(legacyInstaller::discoverInstalled)
            .onSuccess {
                legacyExtensions = it
                legacyRuntimeReports = inspectLegacyRuntimes(legacyInstaller, it)
            }
            .onFailure { error = it.message ?: "Installed APK discovery failed." }
        complete(presentation.refreshAll())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Extension repositories") },
                navigationIcon = {
                    IconButton(onClick = goBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { trusting = true }) { Text("Trust key") }
                    IconButton(onClick = { refresh() }, enabled = !loading) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh repositories")
                    }
                    IconButton(onClick = { adding = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add repository")
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
                    "Anilib ships with no source catalogue. Add only repository URLs and publisher keys you trust.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (loading) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
            error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
            if (legacyExtensions.isNotEmpty()) {
                item { SectionTitle("Installed Aniyomi APKs") }
                items(legacyExtensions, key = { it.packageName() }) { extension ->
                    val runtime = legacyRuntimeReports.getValue(extension.packageName())
                    LegacyExtensionCard(
                        extension = extension,
                        runtime = runtime,
                        trust = { pendingLegacyTrust = extension },
                        forgetTrust = {
                            runCatching { legacyInstaller.forgetCertificateTrust(extension) }
                                .onSuccess { report ->
                                    legacyRuntimeReports = legacyRuntimeReports +
                                        (report.packageName() to report)
                                }
                                .onFailure { error = it.message ?: "Certificate trust removal failed." }
                        },
                    )
                }
            }
            if (view.trustedKeyIds().isNotEmpty()) {
                item { SectionTitle("Trusted publishers") }
                items(view.trustedKeyIds(), key = { it }) { keyId ->
                    TrustedKeyCard(keyId) {
                        runCatching { presentation.forgetTrust(keyId) }
                            .onFailure { error = it.message ?: "Publisher-key removal failed." }
                    }
                }
            }
            item { SectionTitle("Repositories") }
            if (view.repositories().isEmpty()) {
                item { EmptyPage("No extension repository configured.") }
            } else {
                items(view.repositories(), key = { it.indexUri().toString() }) { repository ->
                    RepositoryCard(repository) {
                        runCatching { presentation.remove(repository.indexUri().toString()) }
                            .onFailure { error = it.message ?: "Repository removal failed." }
                    }
                }
            }
            if (view.packages().isNotEmpty()) {
                item { SectionTitle("Available extensions") }
                items(view.packages(), key = { it.packageName() }) { extension ->
                    val installed = view.installed().firstOrNull { it.packageName() == extension.packageName() }
                    ExtensionPackageCard(
                        extension = extension,
                        installed = installed,
                        busy = loading,
                        install = { complete(presentation.install(extension)) },
                        update = { complete(presentation.update(extension)) },
                        toggle = { enabled ->
                            runCatching { presentation.setEnabled(extension.packageName(), enabled) }
                                .onFailure { error = it.message ?: "Extension state change failed." }
                        },
                        remove = {
                            runCatching { presentation.removeInstalled(extension.packageName()) }
                                .onFailure { error = it.message ?: "Extension removal failed." }
                        },
                        installLegacy = if (legacyInstaller.available()) {
                            {
                                loading = true
                                error = null
                                legacyInstaller.install(extension).whenComplete { message, failure ->
                                    error = if (failure == null) {
                                        message
                                    } else {
                                        failure.cause?.message ?: failure.message ?: "APK hand-off failed."
                                    }
                                    loading = false
                                }
                            }
                        } else {
                            null
                        },
                    )
                }
            }
            val unavailable = view.installed().filter { installed ->
                view.packages().none { it.packageName() == installed.packageName() }
            }
            if (unavailable.isNotEmpty()) {
                item { SectionTitle("Installed but unavailable") }
                items(unavailable, key = { it.packageName() }) { installed ->
                    InstalledExtensionCard(
                        installed = installed,
                        toggle = { enabled -> presentation.setEnabled(installed.packageName(), enabled) },
                        remove = { presentation.removeInstalled(installed.packageName()) },
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
    pendingLegacyTrust?.let { extension ->
        LegacyCertificateTrustDialog(
            extension = extension,
            dismiss = { pendingLegacyTrust = null },
            trust = { certificate ->
                runCatching { legacyInstaller.trustCertificate(extension, certificate) }
                    .onSuccess { report ->
                        legacyRuntimeReports = legacyRuntimeReports + (report.packageName() to report)
                        pendingLegacyTrust = null
                    }
                    .onFailure { error = it.message ?: "Certificate trust failed." }
            },
        )
    }
}

@Composable
private fun LegacyExtensionCard(
    extension: LegacyExtensionPackage,
    runtime: LegacyExtensionRuntimeReport,
    trust: () -> Unit,
    forgetTrust: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(extension.displayName(), fontWeight = FontWeight.Medium)
            Text(
                "v${extension.versionName()} - Aniyomi library ${extension.libraryVersion()}"
                    + if (extension.adult()) " - 18+" else ""
                    + if (extension.torrent()) " - torrent" else "",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "${extension.sourceEntrypoints().size} source entrypoint(s)"
                    + if (extension.sourceFactory().isPresent) " - factory" else ""
                    + " - "
                    + legacyCompatibility(extension.compatibility()),
                color = if (extension.compatibility() == LegacyExtensionCompatibility.COMPATIBLE_METADATA) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
            extension.signingCertificateSha256().firstOrNull()?.let { certificate ->
                Text(
                    "Signing certificate ${certificate.take(16)}...",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                legacyRuntimeStatus(runtime),
                color = when (runtime.state()) {
                    LegacyExtensionRuntimeState.HOST_ABI_AVAILABLE -> MaterialTheme.colorScheme.primary
                    LegacyExtensionRuntimeState.HOST_ABI_MISSING -> MaterialTheme.colorScheme.tertiary
                    LegacyExtensionRuntimeState.UNSUPPORTED_PLATFORM -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.error
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (runtime.state() == LegacyExtensionRuntimeState.TRUST_REQUIRED &&
                    extension.signingCertificateSha256().isNotEmpty()
                ) {
                    TextButton(onClick = trust) { Text("Trust certificate") }
                }
                if (runtime.trustedCertificateSha256().isPresent) {
                    TextButton(onClick = forgetTrust) { Text("Forget trust") }
                }
            }
        }
    }
}

private fun inspectLegacyRuntimes(
    installer: LegacyExtensionInstaller,
    extensions: List<LegacyExtensionPackage>,
): Map<String, LegacyExtensionRuntimeReport> = extensions.associate { extension ->
    extension.packageName() to installer.runtimeReport(extension)
}

private fun legacyRuntimeStatus(runtime: LegacyExtensionRuntimeReport): String = when (runtime.state()) {
    LegacyExtensionRuntimeState.UNSUPPORTED_PLATFORM -> "Legacy runtime unavailable on this platform"
    LegacyExtensionRuntimeState.INCOMPATIBLE_METADATA -> "Runtime blocked by incompatible metadata"
    LegacyExtensionRuntimeState.TRUST_REQUIRED -> "Explicit signing-certificate trust required"
    LegacyExtensionRuntimeState.HOST_ABI_MISSING ->
        "Trusted; ${runtime.missingHostClasses().size} required host ABI classes are missing"
    LegacyExtensionRuntimeState.HOST_ABI_AVAILABLE ->
        "Trusted; host ABI available for the future activation bridge"
}

private fun legacyCompatibility(compatibility: LegacyExtensionCompatibility): String = when (compatibility) {
    LegacyExtensionCompatibility.COMPATIBLE_METADATA ->
        "detected; Aniyomi execution runtime still required"
    LegacyExtensionCompatibility.UNSUPPORTED_LIBRARY -> "unsupported Aniyomi library version"
    LegacyExtensionCompatibility.MISSING_ENTRYPOINT -> "missing source entrypoint metadata"
    LegacyExtensionCompatibility.UNSIGNED -> "unsigned package"
}

@Composable
private fun LegacyCertificateTrustDialog(
    extension: LegacyExtensionPackage,
    dismiss: () -> Unit,
    trust: (String) -> Unit,
) {
    val certificate = extension.signingCertificateSha256().firstOrNull() ?: return
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Trust ${extension.displayName()}?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "This trusts the current APK signing certificate for ${extension.packageName()}. " +
                        "It authorizes a future restart-time code load once the Aniyomi host ABI is available; " +
                        "nothing is executed now.",
                )
                Text("SHA-256", fontWeight = FontWeight.SemiBold)
                Text(certificate)
            }
        },
        confirmButton = {
            Button(onClick = { trust(certificate) }) { Text("Trust certificate") }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
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
            TextButton(onClick = forget) { Text("Forget") }
        }
    }
}

@Composable
private fun RepositoryCard(repository: ExtensionRepositoryRow, remove: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                repository.indexUri().toString(),
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
            Text(
                status,
                color = if (repository.failure().isPresent) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = remove) { Text("Remove") }
            }
        }
    }
}

@Composable
private fun ExtensionPackageCard(
    extension: ExtensionPackageMetadata,
    installed: InstalledExtensionPackage?,
    busy: Boolean,
    install: () -> Unit,
    update: () -> Unit,
    toggle: (Boolean) -> Unit,
    remove: () -> Unit,
    installLegacy: (() -> Unit)?,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(extension.displayName(), fontWeight = FontWeight.Medium)
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
                "$formats · ${extension.sources().size} source(s)"
                    + if (extension.adult()) " · 18+" else "",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val portable = extension.artifacts().any { it.format() == ExtensionArtifactFormat.ANILIB_BUNDLE }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                when {
                    installed == null && portable -> Button(onClick = install, enabled = !busy) { Text("Install") }
                    installed != null -> {
                        TextButton(onClick = {
                            toggle(installed.state() == ExtensionInstallationState.DISABLED)
                        }) {
                            Text(if (installed.state() == ExtensionInstallationState.ENABLED) "Disable" else "Enable")
                        }
                        if (extension.versionCode() > installed.versionCode() && portable) {
                            Button(onClick = update, enabled = !busy) { Text("Update") }
                        }
                        TextButton(onClick = remove) { Text("Remove") }
                    }
                }
                if (extension.artifacts().any { it.format() == ExtensionArtifactFormat.ANIYOMI_APK }
                    && installLegacy != null
                ) {
                    TextButton(onClick = installLegacy, enabled = !busy) { Text("Install APK") }
                }
            }
        }
    }
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
                TextButton(onClick = remove) { Text("Remove") }
            }
        }
    }
}

@Composable
private fun AddRepositoryDialog(dismiss: () -> Unit, add: (String) -> Unit) {
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Add repository") },
        text = {
            Column {
                Text("Paste an HTTPS Aniyomi-compatible index URL from a repository you trust.")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Repository index URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = { add(url.trim()) }, enabled = url.isNotBlank()) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
    )
}

@Composable
private fun TrustKeyDialog(dismiss: () -> Unit, trust: (String, String) -> Unit) {
    var keyId by remember { mutableStateOf("") }
    var publicKey by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Trust publisher key") },
        text = {
            Column {
                Text("Only import an Ed25519 key fingerprinted by a publisher you trust.")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = keyId,
                    onValueChange = { keyId = it },
                    label = { Text("Key ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = publicKey,
                    onValueChange = { publicKey = it },
                    label = { Text("Base64 X.509 public key") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { trust(keyId.trim(), publicKey.trim()) },
                enabled = keyId.isNotBlank() && publicKey.isNotBlank(),
            ) {
                Text("Trust")
            }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
    )
}
