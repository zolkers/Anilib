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
import androidx.compose.runtime.LaunchedEffect
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
                runCatching { presentation.authenticate(account.descriptor().id(), credentials) }
                    .onSuccess {
                        error = null
                        login = null
                        revision++
                    }
                    .onFailure { error = it.message ?: "Unable to sign in." }
            },
            error = error,
        )
    }
    logout?.let { account ->
        AlertDialog(
            onDismissRequest = { logout = null },
            title = { Text("Disconnect ${account.descriptor().name()}?") },
            text = { Text("Local progress stays on this device, but synchronization with the service stops.") },
            confirmButton = {
                TextButton(onClick = {
                    runCatching { presentation.logout(account.descriptor().id()) }
                        .onSuccess {
                            error = null
                            logout = null
                            revision++
                        }
                        .onFailure { error = it.message ?: "Unable to sign out." }
                }) { Text("Disconnect") }
            },
            dismissButton = { TextButton(onClick = { logout = null }) { Text("Cancel") } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tracking") },
                navigationIcon = {
                    IconButton(onClick = goBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            item { TrackerSectionHeader("Synchronization") }
            item {
                TrackerSyncSettings(
                    preferences = preferences,
                    synchronize = {
                        runCatching { presentation.synchronizeAll() }
                            .onSuccess {
                                syncSummary = syncSummary(it)
                                error = null
                                revision++
                            }
                            .onFailure { error = it.message ?: "Synchronization failed." }
                    },
                    save = {
                        runCatching { presentation.saveSyncPreferences(it) }
                            .onSuccess {
                                error = null
                                revision++
                            }
                            .onFailure { failure ->
                                error = failure.message ?: "Unable to save synchronization preferences."
                            }
                    },
                )
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
                item { TrackerSectionHeader("Synchronization conflicts") }
                items(conflicts, key = { conflictKey(it) }) { conflict ->
                    TrackerConflictCard(
                        conflict = conflict,
                        resolve = { resolution ->
                            runCatching {
                                presentation.resolveConflict(
                                    conflict.localEntry().libraryItemId(),
                                    conflict.localEntry().trackerId(),
                                    resolution,
                                )
                            }.onSuccess {
                                error = null
                                revision++
                            }.onFailure { error = it.message ?: "Unable to resolve synchronization conflict." }
                        },
                    )
                }
            }
            if (accounts.isEmpty()) {
                item {
                    Text(
                        text = "No tracker bundle is enabled in this product configuration.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
            if (accounts.isNotEmpty()) item { TrackerSectionHeader("Services") }
            items(accounts, key = { it.descriptor().id().value() }) { account ->
                TrackerAccountRow(
                    account = account,
                    activate = {
                        error = null
                        if (account.authenticated()
                            && account.descriptor().authentication() != TrackerAuthentication.NONE
                        ) {
                            logout = account
                        } else if (account.descriptor().authentication() == TrackerAuthentication.OAUTH) {
                            runCatching { presentation.beginAuthorization(account.descriptor().id()) }
                                .onSuccess { webAuthorization = account to it }
                                .onFailure { error = it.message ?: "Unable to open provider sign-in." }
                        } else if (account.descriptor().authentication() != TrackerAuthentication.NONE) {
                            login = account
                        }
                    },
                )
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
                contentDescription = "Connected",
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
    Text(
        label,
        color = MaterialTheme.colorScheme.secondary,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 8.dp),
    )
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
                "Local ${trackerProgress(conflict.localEntry())} · " +
                    "Remote ${trackerProgress(conflict.remoteEntry())}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { resolve(TrackerConflictResolution.KEEP_LOCAL) }) {
                    Text("Keep local")
                }
                TextButton(onClick = { resolve(TrackerConflictResolution.KEEP_REMOTE) }) {
                    Text("Keep remote")
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
        title = { Text("Sign in to ${account.descriptor().name()}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (authentication == TrackerAuthentication.USERNAME_PASSWORD) {
                    OutlinedTextField(
                        value = identity,
                        onValueChange = { identity = it },
                        label = { Text("Username") },
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
                Text("Sign in")
            }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
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
    ObserveTracking(presentation) { revision++ }
    val accounts = remember(revision, kind) {
        presentation.accounts().filter { it.descriptor().supportedKinds().contains(kind) }
    }
    val entries = remember(revision, itemId) {
        presentation.entries(itemId).associateBy(TrackerEntry::trackerId)
    }
    val action: (() -> Unit) -> Unit = { operation ->
        runCatching(operation)
            .onSuccess {
                error = null
                revision++
            }
            .onFailure { error = it.message ?: "Tracking operation failed." }
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
                runCatching { presentation.authenticate(account.descriptor().id(), credentials) }
                    .onSuccess {
                        error = null
                        login = null
                        revision++
                        searching = account
                    }
                    .onFailure { error = it.message ?: "Unable to sign in." }
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
                title = { Text("Tracking") },
                navigationIcon = { TextButton(onClick = goBack) { Text("Back") } },
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
                        Text("Sync")
                    }
                }
            }
            if (accounts.isEmpty()) {
                item { EmptyPage("Sign in to an installed tracking service from More > Tracking.") }
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
                            runCatching { presentation.beginAuthorization(account.descriptor().id()) }
                                .onSuccess { webAuthorization = account to it }
                                .onFailure { error = it.message ?: "Unable to open provider sign-in." }
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
            TextButton(onClick = search) { Text("Add tracking") }
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
            title = { Text("Remove ${descriptor.name()} tracking?") },
            text = { Text("The remote list entry will be deleted before the local binding is removed.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmingRemove = false
                    remove()
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmingRemove = false }) { Text("Cancel") } },
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
            Text("Status: ${readable(entry.status())}")
            Text("Progress: ${trackerProgress(entry)}")
            Text("Score: ${entry.score().let { if (it.isPresent) it.orElse(0.0).toString() else "Not set" }}")
            if (descriptor.supportsDates()) {
                Text("Started: ${entry.startDate().map(LocalDate::toString).orElse("Not set")}")
                Text("Finished: ${entry.finishDate().map(LocalDate::toString).orElse("Not set")}")
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { editing = true }) { Text("Edit") }
                TextButton(onClick = refresh) { Text("Refresh") }
                TextButton(onClick = { confirmingRemove = true }) { Text("Remove") }
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
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Edit ${descriptor.name()} tracking") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(entry.title(), fontWeight = FontWeight.SemiBold)
                Text("Status", style = MaterialTheme.typography.labelLarge)
                EnumChoiceRow(
                    values = descriptor.statuses(),
                    selected = status,
                    label = ::readable,
                    choose = { status = it },
                )
                OutlinedTextField(
                    value = progress,
                    onValueChange = { progress = it },
                    label = {
                        Text(if (entry.totalUnits() >= 0) "Progress / ${entry.totalUnits()}" else "Progress")
                    },
                    singleLine = true,
                )
                if (descriptor.scores().isNotEmpty()) {
                    OutlinedTextField(
                        value = score,
                        onValueChange = { score = it },
                        label = { Text("Score (blank or ${scoreRange(descriptor.scores())})") },
                        singleLine = true,
                    )
                }
                if (descriptor.supportsDates()) {
                    OutlinedTextField(
                        value = startDate,
                        onValueChange = { startDate = it },
                        label = { Text("Start date (YYYY-MM-DD, optional)") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = finishDate,
                        onValueChange = { finishDate = it },
                        label = { Text("Finish date (YYYY-MM-DD, optional)") },
                        singleLine = true,
                    )
                }
                if (descriptor.supportsPrivateEntries()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Private entry", modifier = Modifier.weight(1f))
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
                        "Progress must not exceed ${entry.totalUnits()}."
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
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
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
    selected?.let { result ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text("Track ${result.title()}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Provider: ${account.descriptor().name()}")
                    Text("Media: ${readableEnum(result.kind())}")
                    Text(
                        if (result.totalUnits() >= 0) "Length: ${result.totalUnits()} units"
                        else "Length: unknown",
                    )
                    result.remoteUri().orElse(null)?.let { Text("Remote page: $it") }
                }
            },
            confirmButton = {
                Button(onClick = {
                    runCatching { presentation.bind(itemId, result) }
                        .onSuccess { bound() }
                        .onFailure { error = it.message ?: "Unable to add tracking." }
                }) { Text("Add tracking") }
            },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("Cancel") } },
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(account.descriptor().name()) },
                navigationIcon = { TextButton(onClick = close) { Text("Back") } },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Button(
                enabled = query.isNotBlank(),
                onClick = {
                    runCatching { presentation.search(account.descriptor().id(), query, kind) }
                        .onSuccess {
                            results = it
                            error = null
                        }
                        .onFailure { error = it.message ?: "Search failed." }
                },
            ) {
                Text("Search")
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
    if (!runtimeStatus.available) {
        BrowserUnavailable(runtimeStatus.message, close)
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
    val intercept: (String) -> Boolean = { value ->
        val callback = runCatching { URI.create(value) }.getOrNull()
        val accepted = callback != null && authorization.accepts(callback)
        if (accepted && !completing) {
            completing = true
            runCatching { presentation.completeAuthorization(account.descriptor().id(), callback) }
                .onSuccess { authorized() }
                .onFailure {
                    val message = it.message ?: "Provider sign-in failed."
                    localError = message
                    failed(message)
                    completing = false
                }
        }
        accepted
    }
    val platformBridge = LocalBrowserPlatformController.current.rememberBridge(
        policy = policy,
        report = { localError = it },
        interceptNavigation = intercept,
    )
    LaunchedEffect(state.lastLoadedUrl) {
        state.lastLoadedUrl?.let(intercept)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sign in to ${account.descriptor().name()}") },
                navigationIcon = {
                    IconButton(onClick = close) {
                        Icon(Icons.Default.Close, contentDescription = "Close browser")
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

private fun readable(value: TrackerStatus): String = value.name
    .replace('_', ' ')
    .lowercase()
    .replaceFirstChar(Char::uppercase)

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
