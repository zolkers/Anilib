package fr.vriege.anilib.platform.compose

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import fr.vriege.anilib.feature.library.LibraryItemId
import fr.vriege.anilib.feature.library.MediaKind
import fr.vriege.anilib.feature.tracker.TrackerAccount
import fr.vriege.anilib.feature.tracker.TrackerAuthentication
import fr.vriege.anilib.feature.tracker.TrackerCredentials
import fr.vriege.anilib.feature.tracker.TrackerEntry
import fr.vriege.anilib.feature.tracker.TrackerId
import fr.vriege.anilib.feature.tracker.TrackerSearchResult
import fr.vriege.anilib.feature.tracker.TrackerStatus
import fr.vriege.anilib.feature.tracker.ui.TrackerPresentation
import java.time.LocalDate
import java.util.Optional
import java.util.OptionalDouble

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrackerAccountsScreen(
    presentation: TrackerPresentation,
    goBack: () -> Unit,
) {
    var revision by remember { mutableStateOf(0) }
    var login by remember { mutableStateOf<TrackerAccount?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    ObserveTracking(presentation) { revision++ }
    val accounts = remember(revision) { presentation.accounts() }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tracking") },
                navigationIcon = { TextButton(onClick = goBack) { Text("Back") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    text = "Tracking services",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
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
            items(accounts, key = { it.descriptor().id().value() }) { account ->
                TrackerAccountRow(
                    account = account,
                    activate = {
                        error = null
                        if (account.authenticated()
                            && account.descriptor().authentication() != TrackerAuthentication.NONE
                        ) {
                            runCatching { presentation.logout(account.descriptor().id()) }
                                .onFailure { error = it.message ?: "Unable to sign out." }
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
        authentication == TrackerAuthentication.OAUTH -> "Connect with authorization code"
        authentication == TrackerAuthentication.TOKEN -> "Connect with access token"
        else -> "Sign in"
    }
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = activate).padding(24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(account.descriptor().name(), fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text(summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (account.authenticated() && authentication != TrackerAuthentication.NONE) {
            Text("Sign out", color = MaterialTheme.colorScheme.error)
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
    itemId: LibraryItemId,
    title: String,
    kind: MediaKind,
    goBack: () -> Unit,
) {
    var revision by remember(itemId) { mutableStateOf(0) }
    var searching by remember { mutableStateOf<TrackerAccount?>(null) }
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
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
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
                        } else {
                            error = "Sign in to ${account.descriptor().name()} from More > Tracking first."
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(descriptor.name(), color = MaterialTheme.colorScheme.primary)
            Text(entry.title(), fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Text("Status: ${readable(entry.status())}")
            Text("Progress: ${trackerProgress(entry)}")
            Text("Score: ${entry.score().let { if (it.isPresent) it.orElse(0.0).toString() else "Not set" }}")
            if (descriptor.supportsDates()) {
                Text("Started: ${entry.startDate().map(LocalDate::toString).orElse("Not set")}")
                Text("Finished: ${entry.finishDate().map(LocalDate::toString).orElse("Not set")}")
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = { update(entry.withStatus(nextStatus(entry, descriptor.statuses()))) }) {
                    Text("Status")
                }
                TextButton(onClick = { update(entry.withProgress(nextProgress(entry))) }) {
                    Text("+ Progress")
                }
                if (descriptor.scores().isNotEmpty()) {
                    TextButton(onClick = { update(entry.withScore(nextScore(entry, descriptor.scores()))) }) {
                        Text("Score")
                    }
                }
            }
            if (descriptor.supportsDates()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(
                        onClick = { update(entry.withDates(Optional.of(LocalDate.now()), entry.finishDate())) },
                    ) {
                        Text("Start today")
                    }
                    TextButton(
                        onClick = { update(entry.withDates(entry.startDate(), Optional.of(LocalDate.now()))) },
                    ) {
                        Text("Finish today")
                    }
                }
            }
            if (descriptor.supportsPrivateEntries()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Private", modifier = Modifier.weight(1f))
                    Switch(
                        checked = entry.privateEntry(),
                        onCheckedChange = { update(entry.withPrivateEntry(it)) },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = refresh) { Text("Refresh") }
                TextButton(onClick = remove) { Text("Remove") }
            }
        }
    }
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
    var error by remember { mutableStateOf<String?>(null) }
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
                        modifier = Modifier.fillMaxWidth().clickable {
                            runCatching { presentation.bind(itemId, result) }
                                .onSuccess { bound() }
                                .onFailure { error = it.message ?: "Unable to add tracking." }
                        },
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

private fun nextStatus(entry: TrackerEntry, statuses: List<TrackerStatus>): TrackerStatus {
    val index = statuses.indexOf(entry.status())
    return statuses[(index + 1).mod(statuses.size)]
}

private fun nextProgress(entry: TrackerEntry): Double {
    val next = entry.progress() + 1.0
    return if (entry.totalUnits() >= 0) next.coerceAtMost(entry.totalUnits().toDouble()) else next
}

private fun nextScore(entry: TrackerEntry, scores: List<Double>): OptionalDouble {
    if (!entry.score().isPresent) {
        return OptionalDouble.of(scores.first())
    }
    val index = scores.indexOf(entry.score().orElse(0.0))
    return OptionalDouble.of(scores[(index + 1).mod(scores.size)])
}

private fun trackerProgress(entry: TrackerEntry): String =
    if (entry.totalUnits() >= 0) "${entry.progress()} / ${entry.totalUnits()}" else entry.progress().toString()

private fun readable(value: TrackerStatus): String = value.name
    .replace('_', ' ')
    .lowercase()
    .replaceFirstChar(Char::uppercase)
