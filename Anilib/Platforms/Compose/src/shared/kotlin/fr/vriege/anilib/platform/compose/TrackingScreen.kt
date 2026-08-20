package fr.vriege.anilib.platform.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Sync
import com.multiplatform.webview.web.LoadingState
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.rememberWebViewState
import fr.vriege.anilib.feature.library.LibraryItemId
import fr.vriege.anilib.feature.library.MediaKind
import fr.vriege.anilib.feature.settings.LanguagePack
import fr.vriege.anilib.feature.tracker.TrackerAccount
import fr.vriege.anilib.feature.tracker.TrackerAuthentication
import fr.vriege.anilib.feature.tracker.TrackerAuthorization
import fr.vriege.anilib.feature.tracker.TrackerCredentials
import fr.vriege.anilib.feature.tracker.TrackerConflictPolicy
import fr.vriege.anilib.feature.tracker.TrackerConflictResolution
import fr.vriege.anilib.feature.tracker.TrackerEntry
import fr.vriege.anilib.feature.tracker.TrackerId
import fr.vriege.anilib.feature.tracker.TrackerSearchResult
import fr.vriege.anilib.feature.tracker.TrackerStatus
import fr.vriege.anilib.feature.tracker.TrackerSyncConflict
import fr.vriege.anilib.feature.tracker.TrackerSyncDirection
import fr.vriege.anilib.feature.tracker.TrackerSyncPreferences
import fr.vriege.anilib.feature.tracker.TrackerSyncReport
import fr.vriege.anilib.feature.tracker.ui.TrackerPresentation
import java.net.URI
import java.time.LocalDate
import java.util.Optional
import java.util.OptionalDouble
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrackerAccountsScreen(
    presentation: TrackerPresentation,
    browserRuntimeStatus: BrowserRuntimeStatus,
    goBack: () -> Unit,
) {
    var revision by remember { mutableStateOf(0) }
    var login by remember { mutableStateOf<TrackerAccount?>(null) }
    var webAuthorization by remember { mutableStateOf<Pair<TrackerAccount, TrackerAuthorization>?>(null) }
    var logout by remember { mutableStateOf<TrackerAccount?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCrashSafeCoroutineScope()
    ObserveTracking(presentation) { revision++ }
    val accounts = remember(revision) { presentation.accounts() }
    val preferences = remember(revision) { presentation.syncPreferences() }
    val conflicts = remember(revision) { presentation.conflicts() }
    var syncSummary by remember { mutableStateOf<String?>(null) }

    webAuthorization?.let { (account, authorization) ->
        TrackerAuthorizationScreen(
            account = account,
            authorization = authorization,
            presentation = presentation,
            runtimeStatus = browserRuntimeStatus,
            close = { webAuthorization = null },
            authorized = {
                webAuthorization = null
                error = null
                revision++
            },
            failed = { error = it },
        )
        return
    }

    login?.let { account ->
        TrackerLoginDialog(
            account = account,
            dismiss = { login = null },
            submit = { credentials ->
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            presentation.authenticate(account.descriptor().id(), credentials)
                        }
                    }.onSuccess {
                        error = null
                        login = null
                        revision++
                    }.onFailure { error = it.message ?: "Unable to sign in." }
                }
            },
            error = error,
        )
    }
    logout?.let { account ->
        AlertDialog(
            onDismissRequest = { logout = null },
            title = {
                Text(
                    UiTranslations.format(
                        "dynamic.disconnect",
                        LocalLanguagePack.current,
                        account.descriptor().name(),
                    ),
                )
            },
            text = { Text("ui.local.progress.stays.on.this.device.but.synchronization.with.the.service.stops") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                presentation.logout(account.descriptor().id())
                            }
                        }.onSuccess {
                            error = null
                            logout = null
                            revision++
                        }.onFailure { error = it.message ?: "Unable to sign out." }
                    }
                }) { Text("ui.disconnect") }
            },
            dismissButton = { TextButton(onClick = { logout = null }) { Text("ui.cancel") } },
        )
    }

    AnilibSubScreenScaffold(title = "Tracking", goBack = goBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            item { TrackerSectionHeader("ui.synchronization") }
            item {
                AnilibGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
                    TrackerSyncSettings(
                        preferences = preferences,
                        synchronize = {
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) { presentation.synchronizeAll() }
                                }.onSuccess {
                                    syncSummary = syncSummary(it)
                                    error = null
                                    revision++
                                }.onFailure { error = it.message ?: "Synchronization failed." }
                            }
                        },
                        save = {
                            val replacement = it
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        presentation.saveSyncPreferences(replacement)
                                    }
                                }.onSuccess {
                                    error = null
                                    revision++
                                }.onFailure { failure ->
                                    error = failure.message ?: "Unable to save synchronization preferences."
                                }
                            }
                        },
                    )
                }
            }
            syncSummary?.let { summary ->
                item {
                    Text(
                        summary,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            }
            if (conflicts.isNotEmpty()) {
                item { TrackerSectionHeader("ui.synchronization.conflicts") }
                items(conflicts, key = { conflictKey(it) }) { conflict ->
                    TrackerConflictCard(
                        conflict = conflict,
                        resolve = { resolution ->
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        presentation.resolveConflict(
                                            conflict.localEntry().libraryItemId(),
                                            conflict.localEntry().trackerId(),
                                            resolution,
                                        )
                                    }
                                }.onSuccess {
                                    error = null
                                    revision++
                                }.onFailure {
                                    error = it.message ?: "Unable to resolve synchronization conflict."
                                }
                            }
                        },
                    )
                }
            }
            if (accounts.isEmpty()) {
                item {
                    Text(
                        text = "ui.no.tracker.bundle.enabled",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
            if (accounts.isNotEmpty()) item { TrackerSectionHeader("ui.services") }
            if (accounts.isNotEmpty()) item {
                AnilibGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
                    accounts.forEach { account ->
                        TrackerAccountRow(
                            account = account,
                            activate = {
                                error = null
                                if (account.authenticated()
                                    && account.descriptor().authentication() != TrackerAuthentication.NONE
                                ) {
                                    logout = account
                                } else if (account.descriptor().authentication() == TrackerAuthentication.OAUTH) {
                                    scope.launch {
                                        runCatching {
                                            withContext(Dispatchers.IO) {
                                                presentation.beginAuthorization(account.descriptor().id())
                                            }
                                        }.onSuccess { webAuthorization = account to it }
                                            .onFailure {
                                                error = it.message ?: "Unable to open provider sign-in."
                                            }
                                    }
                                } else if (
                                    account.descriptor().authentication() != TrackerAuthentication.NONE
                                ) {
                                    login = account
                                }
                            },
                        )
                    }
                }
            }
            error?.let { message ->
                item {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackerAccountRow(account: TrackerAccount, activate: () -> Unit) {
    val authentication = account.descriptor().authentication()
    val summary = when {
        authentication == TrackerAuthentication.NONE -> "Ready"
        account.authenticated() -> account.accountName().ifBlank { "Signed in" }
        authentication == TrackerAuthentication.OAUTH -> "Sign in on ${account.descriptor().name()}"
        authentication == TrackerAuthentication.TOKEN -> "Connect with access token"
        else -> "Sign in"
    }
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = activate).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TrackerProviderIcon(account)
        Spacer(Modifier.size(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(account.descriptor().name(), fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text(summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (account.authenticated() && authentication != TrackerAuthentication.NONE) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = "ui.connected",
                tint = Color(0xFF4CAF50),
            )
        }
    }
}

@Composable
private fun TrackerProviderIcon(account: TrackerAccount) {
    val icon = account.descriptor().icon()
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF000000 or icon.colorRgb().toLong())),
        contentAlignment = Alignment.Center,
    ) {
        Text(icon.monogram(), color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TrackerSyncSettings(
    preferences: TrackerSyncPreferences,
    synchronize: () -> Unit,
    save: (TrackerSyncPreferences) -> Unit,
) {
    TrackerPreferenceRow(
        title = "Automatic synchronization",
        summary = "Refresh linked titles after library activity and sign-in.",
        action = { save(preferences.withAutomatic(!preferences.automatic())) },
        trailing = { Switch(checked = preferences.automatic(), onCheckedChange = null) },
    )
    TrackerPreferenceRow(
        title = "Synchronization direction",
        summary = readableEnum(preferences.direction()),
        action = {
            save(preferences.withDirection(nextValue(TrackerSyncDirection.entries, preferences.direction())))
        },
    )
    TrackerPreferenceRow(
        title = "When both sides changed",
        summary = readableEnum(preferences.conflictPolicy()),
        action = {
            save(
                preferences.withConflictPolicy(
                    nextValue(TrackerConflictPolicy.entries, preferences.conflictPolicy()),
                ),
            )
        },
    )
    TrackerPreferenceRow(
        title = "Synchronize now",
        summary = "Push and pull progress for every linked title.",
        action = synchronize,
        trailing = {
            Icon(Icons.Outlined.Sync, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
    )
}

@Composable
private fun TrackerSectionHeader(label: String) {
    AnilibSection(label)
}

@Composable
private fun TrackerPreferenceRow(
    title: String,
    summary: String,
    action: () -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = action).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(3.dp))
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailing()
    }
}

private fun <T> nextValue(values: List<T>, current: T): T =
    values[(values.indexOf(current) + 1).mod(values.size)]

@Composable
private fun <T> EnumChoiceRow(
    values: List<T>,
    selected: T,
    label: (T) -> String,
    choose: (T) -> Unit,
) {
    Column {
        values.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { value ->
                    TextButton(onClick = { choose(value) }) {
                        Text(if (value == selected) "• ${label(value)}" else label(value))
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackerConflictCard(
    conflict: TrackerSyncConflict,
    resolve: (TrackerConflictResolution) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(conflict.localEntry().title(), fontWeight = FontWeight.SemiBold)
            Text(
                UiTranslations.format(
                    "dynamic.tracker.conflict.progress",
                    LocalLanguagePack.current,
                    trackerProgress(conflict.localEntry()),
                    trackerProgress(conflict.remoteEntry()),
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { resolve(TrackerConflictResolution.KEEP_LOCAL) }) {
                    Text("ui.keep.local")
                }
                TextButton(onClick = { resolve(TrackerConflictResolution.KEEP_REMOTE) }) {
                    Text("ui.keep.remote")
                }
            }
        }
    }
}

@Composable
private fun TrackerLoginDialog(
    account: TrackerAccount,
    dismiss: () -> Unit,
    submit: (TrackerCredentials) -> Unit,
    error: String?,
) {
    val authentication = account.descriptor().authentication()
    var identity by remember(account) { mutableStateOf("") }
    var secret by remember(account) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = dismiss,
        title = {
            Text(UiTranslations.format("dynamic.sign.in", LocalLanguagePack.current, account.descriptor().name()))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (authentication == TrackerAuthentication.USERNAME_PASSWORD) {
                    OutlinedTextField(
                        value = identity,
                        onValueChange = { identity = it },
                        label = { Text("ui.username") },
                        singleLine = true,
                    )
                }
                OutlinedTextField(
                    value = secret,
                    onValueChange = { secret = it },
                    label = { Text(secretLabel(authentication)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                enabled = secret.isNotBlank()
                    && (authentication != TrackerAuthentication.USERNAME_PASSWORD || identity.isNotBlank()),
                onClick = { submit(credentials(authentication, identity, secret)) },
            ) {
                Text("ui.sign.in")
            }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("ui.cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TitleTrackingScreen(
    presentation: TrackerPresentation,
    browserRuntimeStatus: BrowserRuntimeStatus,
    itemId: LibraryItemId,
    title: String,
    kind: MediaKind,
    goBack: () -> Unit,
) {
    var revision by remember(itemId) { mutableStateOf(0) }
    var searching by remember { mutableStateOf<TrackerAccount?>(null) }
    var login by remember { mutableStateOf<TrackerAccount?>(null) }
    var webAuthorization by remember { mutableStateOf<Pair<TrackerAccount, TrackerAuthorization>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCrashSafeCoroutineScope()
    ObserveTracking(presentation) { revision++ }
    val accounts = remember(revision, kind) {
        presentation.accounts().filter { it.descriptor().supportedKinds().contains(kind) }
    }
    val entries = remember(revision, itemId) {
        presentation.entries(itemId).associateBy(TrackerEntry::trackerId)
    }
    val action: (() -> Unit) -> Unit = { operation ->
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { operation() } }
                .onSuccess {
                    error = null
                    revision++
                }
                .onFailure { error = it.message ?: "Tracking operation failed." }
        }
    }

    webAuthorization?.let { (account, authorization) ->
        TrackerAuthorizationScreen(
            account = account,
            authorization = authorization,
            presentation = presentation,
            runtimeStatus = browserRuntimeStatus,
            close = { webAuthorization = null },
            authorized = {
                webAuthorization = null
                error = null
                revision++
                searching = presentation.accounts().firstOrNull {
                    it.descriptor().id() == account.descriptor().id()
                }
            },
            failed = { error = it },
        )
        return
    }

    login?.let { account ->
        TrackerLoginDialog(
            account = account,
            dismiss = { login = null },
            submit = { credentials ->
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            presentation.authenticate(account.descriptor().id(), credentials)
                        }
                    }.onSuccess {
                        error = null
                        login = null
                        revision++
                        searching = account
                    }.onFailure { error = it.message ?: "Unable to sign in." }
                }
            },
            error = error,
        )
    }

    searching?.let { account ->
        TrackerSearchScreen(
            presentation = presentation,
            account = account,
            itemId = itemId,
            title = title,
            kind = kind,
            close = { searching = null },
            bound = {
                searching = null
                revision++
            },
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ui.tracking") },
                navigationIcon = { TextButton(onClick = goBack) { Text("ui.back") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { action { presentation.synchronize(itemId) } }) {
                        Text("ui.sync")
                    }
                }
            }
            if (accounts.isEmpty()) {
                item { EmptyPage("ui.sign.in.tracking.from.more") }
            }
            items(accounts, key = { it.descriptor().id().value() }) { account ->
                val entry = entries[account.descriptor().id()]
                if (entry == null) {
                    UnboundTrackerCard(account) {
                        if (account.authenticated()
                            || account.descriptor().authentication() == TrackerAuthentication.NONE
                        ) {
                            searching = account
                        } else if (account.descriptor().authentication() == TrackerAuthentication.OAUTH) {
                            error = null
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        presentation.beginAuthorization(account.descriptor().id())
                                    }
                                }.onSuccess { webAuthorization = account to it }
                                    .onFailure { error = it.message ?: "Unable to open provider sign-in." }
                            }
                        } else {
                            error = null
                            login = account
                        }
                    }
                } else {
                    TrackerEntryCard(
                        account = account,
                        entry = entry,
                        update = { replacement -> action { presentation.update(replacement) } },
                        refresh = { action { presentation.refresh(itemId, entry.trackerId()) } },
                        remove = { action { presentation.remove(itemId, entry.trackerId()) } },
                    )
                }
            }
            error?.let { message ->
                item { Text(message, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun UnboundTrackerCard(account: TrackerAccount, search: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrackerProviderIcon(account)
            Spacer(Modifier.size(12.dp))
            Text(account.descriptor().name(), fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            TextButton(onClick = search) { Text("ui.add.tracking") }
        }
    }
}

@Composable
private fun TrackerEntryCard(
    account: TrackerAccount,
    entry: TrackerEntry,
    update: (TrackerEntry) -> Unit,
    refresh: () -> Unit,
    remove: () -> Unit,
) {
    val descriptor = account.descriptor()
    var editing by remember(entry) { mutableStateOf(false) }
    var confirmingRemove by remember(entry) { mutableStateOf(false) }
    var editError by remember(entry) { mutableStateOf<String?>(null) }
    if (editing) {
        TrackerEditDialog(
            account = account,
            entry = entry,
            error = editError,
            dismiss = {
                editing = false
                editError = null
            },
            submit = { replacement ->
                runCatching { update(replacement) }
                    .onSuccess {
                        editing = false
                        editError = null
                    }
                    .onFailure { editError = it.message ?: "Unable to update tracking." }
            },
        )
    }
    if (confirmingRemove) {
        AlertDialog(
            onDismissRequest = { confirmingRemove = false },
            title = {
                Text(UiTranslations.format("dynamic.remove.tracking", LocalLanguagePack.current, descriptor.name()))
            },
            text = { Text("ui.the.remote.list.entry.will.be.deleted.before.the.local.binding.is.removed") },
            confirmButton = {
                TextButton(onClick = {
                    confirmingRemove = false
                    remove()
                }) { Text("ui.remove") }
            },
            dismissButton = { TextButton(onClick = { confirmingRemove = false }) { Text("ui.cancel") } },
        )
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TrackerProviderIcon(account)
                Spacer(Modifier.size(12.dp))
                Column {
                    Text(descriptor.name(), color = MaterialTheme.colorScheme.primary)
                    Text(entry.title(), fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                UiTranslations.format(
                    "dynamic.status",
                    LocalLanguagePack.current,
                    UiTranslations.translate(trackerStatusKey(entry.status()), LocalLanguagePack.current),
                ),
            )
            Text(UiTranslations.format("dynamic.progress", LocalLanguagePack.current, trackerProgress(entry)))
            Text(
                UiTranslations.format(
                    "dynamic.score",
                    LocalLanguagePack.current,
                    entry.score().let {
                        if (it.isPresent) {
                            it.orElse(0.0).toString()
                        } else {
                            UiTranslations.translate("ui.not.set", LocalLanguagePack.current)
                        }
                    },
                ),
            )
            if (descriptor.supportsDates()) {
                Text(
                    UiTranslations.format(
                        "dynamic.started",
                        LocalLanguagePack.current,
                        entry.startDate().map(LocalDate::toString).orElse(
                            UiTranslations.translate("ui.not.set", LocalLanguagePack.current),
                        ),
                    ),
                )
                Text(
                    UiTranslations.format(
                        "dynamic.finished",
                        LocalLanguagePack.current,
                        entry.finishDate().map(LocalDate::toString).orElse(
                            UiTranslations.translate("ui.not.set", LocalLanguagePack.current),
                        ),
                    ),
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { editing = true }) { Text("ui.edit") }
                TextButton(onClick = refresh) { Text("ui.refresh") }
                TextButton(onClick = { confirmingRemove = true }) { Text("ui.remove") }
            }
        }
    }
}

@Composable
private fun TrackerEditDialog(
    account: TrackerAccount,
    entry: TrackerEntry,
    error: String?,
    dismiss: () -> Unit,
    submit: (TrackerEntry) -> Unit,
) {
    val descriptor = account.descriptor()
    var status by remember(entry) { mutableStateOf(entry.status()) }
    var progress by remember(entry) { mutableStateOf(entry.progress().toString()) }
    var score by remember(entry) {
        mutableStateOf(if (entry.score().isPresent) entry.score().orElse(0.0).toString() else "")
    }
    var startDate by remember(entry) { mutableStateOf(entry.startDate().map(LocalDate::toString).orElse("")) }
    var finishDate by remember(entry) { mutableStateOf(entry.finishDate().map(LocalDate::toString).orElse("")) }
    var privateEntry by remember(entry) { mutableStateOf(entry.privateEntry()) }
    var validationError by remember(entry) { mutableStateOf<String?>(null) }
    val language = LocalLanguagePack.current
    AlertDialog(
        onDismissRequest = dismiss,
        title = {
            Text(UiTranslations.format("dynamic.edit.tracking", LocalLanguagePack.current, descriptor.name()))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(entry.title(), fontWeight = FontWeight.SemiBold)
                Text("ui.status", style = MaterialTheme.typography.labelLarge)
                EnumChoiceRow(
                    values = descriptor.statuses(),
                    selected = status,
                    label = { UiTranslations.translate(trackerStatusKey(it), language) },
                    choose = { status = it },
                )
                OutlinedTextField(
                    value = progress,
                    onValueChange = { progress = it },
                    label = {
                        Text(
                            if (entry.totalUnits() >= 0) {
                                UiTranslations.format(
                                    "dynamic.progress.total",
                                    LocalLanguagePack.current,
                                    entry.totalUnits(),
                                )
                            } else {
                                UiTranslations.translate("ui.progress", LocalLanguagePack.current)
                            },
                        )
                    },
                    singleLine = true,
                )
                if (descriptor.scores().isNotEmpty()) {
                    OutlinedTextField(
                        value = score,
                        onValueChange = { score = it },
                        label = {
                            Text(
                                UiTranslations.format(
                                    "dynamic.score.range",
                                    LocalLanguagePack.current,
                                    scoreRange(descriptor.scores()),
                                ),
                            )
                        },
                        singleLine = true,
                    )
                }
                if (descriptor.supportsDates()) {
                    OutlinedTextField(
                        value = startDate,
                        onValueChange = { startDate = it },
                        label = { Text("ui.start.date.yyyy.mm.dd.optional") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = finishDate,
                        onValueChange = { finishDate = it },
                        label = { Text("ui.finish.date.yyyy.mm.dd.optional") },
                        singleLine = true,
                    )
                }
                if (descriptor.supportsPrivateEntries()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("ui.private.entry", modifier = Modifier.weight(1f))
                        Switch(checked = privateEntry, onCheckedChange = { privateEntry = it })
                    }
                }
                (validationError ?: error)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                runCatching {
                    val progressValue = progress.toDouble()
                    require(progressValue >= 0.0 && progressValue.isFinite()) { "Progress must be non-negative." }
                    require(entry.totalUnits() < 0 || progressValue <= entry.totalUnits()) {
                        UiTranslations.format(
                            "dynamic.progress.maximum",
                            language,
                            entry.totalUnits(),
                        )
                    }
                    val scoreValue = if (score.isBlank()) {
                        OptionalDouble.empty()
                    } else {
                        OptionalDouble.of(score.toDouble())
                    }
                    require(!scoreValue.isPresent || descriptor.scores().contains(scoreValue.orElse(0.0))) {
                        "Choose a score supported by ${descriptor.name()}."
                    }
                    entry.withProgress(progressValue)
                        .withStatus(status)
                        .withScore(scoreValue)
                        .withDates(optionalDate(startDate), optionalDate(finishDate))
                        .withPrivateEntry(privateEntry)
                }.onSuccess {
                    validationError = null
                    submit(it)
                }.onFailure {
                    validationError = it.message ?: "Invalid tracking values."
                }
            }) { Text("ui.save") }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("ui.cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackerSearchScreen(
    presentation: TrackerPresentation,
    account: TrackerAccount,
    itemId: LibraryItemId,
    title: String,
    kind: MediaKind,
    close: () -> Unit,
    bound: () -> Unit,
) {
    var query by remember { mutableStateOf(title) }
    var results by remember { mutableStateOf<List<TrackerSearchResult>>(emptyList()) }
    var selected by remember { mutableStateOf<TrackerSearchResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCrashSafeCoroutineScope()
    selected?.let { result ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(UiTranslations.format("dynamic.track", LocalLanguagePack.current, result.title())) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        UiTranslations.format(
                            "dynamic.provider",
                            LocalLanguagePack.current,
                            account.descriptor().name(),
                        ),
                    )
                    Text(
                        UiTranslations.format(
                            "dynamic.media",
                            LocalLanguagePack.current,
                            UiTranslations.translate(
                                if (result.kind().name == "ANIME") "ui.anime" else "ui.manga",
                                LocalLanguagePack.current,
                            ),
                        ),
                    )
                    Text(
                        if (result.totalUnits() >= 0) {
                            UiTranslations.format(
                                "dynamic.length.units",
                                LocalLanguagePack.current,
                                result.totalUnits(),
                            )
                        } else {
                            UiTranslations.translate("ui.length.unknown", LocalLanguagePack.current)
                        },
                    )
                    result.remoteUri().orElse(null)?.let {
                        Text(UiTranslations.format("dynamic.remote.page", LocalLanguagePack.current, it))
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) { presentation.bind(itemId, result) }
                        }.onSuccess { bound() }
                            .onFailure { error = it.message ?: "Unable to add tracking." }
                    }
                }) { Text("ui.add.tracking") }
            },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("ui.cancel") } },
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(account.descriptor().name()) },
                navigationIcon = { TextButton(onClick = close) { Text("ui.back") } },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("ui.search.title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Button(
                enabled = query.isNotBlank(),
                onClick = {
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                presentation.search(account.descriptor().id(), query, kind)
                            }
                        }.onSuccess {
                            results = it
                            error = null
                        }.onFailure { error = it.message ?: "Search failed." }
                    }
                },
            ) {
                Text("ui.search")
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(results, key = TrackerSearchResult::remoteId) { result ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { selected = result },
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(result.title(), fontWeight = FontWeight.Medium)
                            Text(
                                if (result.totalUnits() >= 0) "${result.totalUnits()} units" else "Unknown length",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackerAuthorizationScreen(
    account: TrackerAccount,
    authorization: TrackerAuthorization,
    presentation: TrackerPresentation,
    runtimeStatus: BrowserRuntimeStatus,
    close: () -> Unit,
    authorized: () -> Unit,
    failed: (String) -> Unit,
) {
    var resolvedRuntime by remember(runtimeStatus) {
        mutableStateOf(runtimeStatus.currentOrNull())
    }
    CrashSafeLaunchedEffect(runtimeStatus) {
        if (resolvedRuntime == null) {
            resolvedRuntime = withContext(Dispatchers.IO) { runtimeStatus.resolve() }
        }
    }
    val currentRuntime = resolvedRuntime
    if (currentRuntime == null) {
        BrowserInitializing(close)
        return
    }
    if (!currentRuntime.available) {
        BrowserUnavailable(currentRuntime.message, close)
        return
    }
    val policy = LocalBrowserPolicy.current
    val state = rememberWebViewState(authorization.authorizationUri().toString()) {
        isJavaScriptEnabled = true
        androidWebSettings.domStorageEnabled = true
        desktopWebSettings.disablePopupWindows = false
    }
    var completing by remember(authorization) { mutableStateOf(false) }
    var localError by remember(authorization) { mutableStateOf<String?>(null) }
    val scope = rememberCrashSafeCoroutineScope()
    val intercept: (String) -> Boolean = { value ->
        val callback = runCatching { URI.create(value) }.getOrNull()
        val accepted = callback != null && authorization.accepts(callback)
        if (accepted && !completing) {
            completing = true
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        presentation.completeAuthorization(account.descriptor().id(), callback)
                    }
                }.onSuccess { authorized() }
                    .onFailure {
                        val message = it.message ?: "Provider sign-in failed."
                        localError = message
                        failed(message)
                        completing = false
                    }
            }
        }
        accepted
    }
    val platformBridge = LocalBrowserPlatformController.current.rememberBridge(
        policy = policy,
        report = { localError = it },
        interceptNavigation = intercept,
    )
    CrashSafeLaunchedEffect(state.lastLoadedUrl) {
        state.lastLoadedUrl?.let(intercept)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        UiTranslations.format(
                            "dynamic.sign.in",
                            LocalLanguagePack.current,
                            account.descriptor().name(),
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = close) {
                        Icon(Icons.Default.Close, contentDescription = "ui.close.browser")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            val loading = state.loadingState
            if (loading is LoadingState.Loading || completing) {
                LinearProgressIndicator(
                    progress = {
                        if (loading is LoadingState.Loading) loading.progress else 1f
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            localError?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            WebView(
                state = state,
                captureBackPresses = true,
                platformWebViewParams = platformBridge.parameters,
                onCreated = platformBridge.onCreated,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun ObserveTracking(presentation: TrackerPresentation, changed: () -> Unit) {
    DisposableEffect(presentation) {
        val registration = presentation.observe(changed)
        onDispose { registration.close() }
    }
}

private fun credentials(
    authentication: TrackerAuthentication,
    identity: String,
    secret: String,
): TrackerCredentials = when (authentication) {
    TrackerAuthentication.USERNAME_PASSWORD -> TrackerCredentials.password(identity, secret)
    TrackerAuthentication.TOKEN -> TrackerCredentials.token(secret)
    TrackerAuthentication.OAUTH -> TrackerCredentials.authorizationCode(secret)
    TrackerAuthentication.NONE -> error("NONE does not accept credentials")
}

private fun secretLabel(authentication: TrackerAuthentication): String = when (authentication) {
    TrackerAuthentication.USERNAME_PASSWORD -> "Password"
    TrackerAuthentication.TOKEN -> "Access token"
    TrackerAuthentication.OAUTH -> "Authorization code"
    TrackerAuthentication.NONE -> "Credential"
}

private fun trackerProgress(entry: TrackerEntry): String =
    if (entry.totalUnits() >= 0) "${entry.progress()} / ${entry.totalUnits()}" else entry.progress().toString()

private fun trackerStatusKey(value: TrackerStatus): String = when (value) {
    TrackerStatus.WATCHING -> "ui.tracker.status.watching"
    TrackerStatus.READING -> "ui.tracker.status.reading"
    TrackerStatus.COMPLETED -> "ui.tracker.status.completed"
    TrackerStatus.ON_HOLD -> "ui.tracker.status.on.hold"
    TrackerStatus.PLANNING -> "ui.tracker.status.planning"
    TrackerStatus.DROPPED -> "ui.tracker.status.dropped"
    TrackerStatus.REWATCHING -> "ui.tracker.status.rewatching"
    TrackerStatus.REREADING -> "ui.tracker.status.rereading"
}

private fun readableEnum(value: Enum<*>): String = value.name
    .replace('_', ' ')
    .lowercase()
    .replaceFirstChar(Char::uppercase)

private fun optionalDate(value: String): Optional<LocalDate> =
    if (value.isBlank()) Optional.empty() else Optional.of(LocalDate.parse(value.trim()))

private fun scoreRange(values: List<Double>): String = "${values.min()}–${values.max()}"

private fun conflictKey(conflict: TrackerSyncConflict): String =
    "${conflict.localEntry().libraryItemId().value()}:${conflict.localEntry().trackerId().value()}"

private fun syncSummary(report: TrackerSyncReport): String = buildString {
    append("Synchronization: ${report.pushed()} pushed, ${report.pulled()} pulled")
    if (report.conflicts().isNotEmpty()) append(", ${report.conflicts().size} conflict(s)")
    if (report.failures().isNotEmpty()) append(", ${report.failures().size} failure(s)")
    append('.')
}
