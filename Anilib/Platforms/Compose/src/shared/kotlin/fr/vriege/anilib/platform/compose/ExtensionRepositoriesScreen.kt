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
import fr.vriege.anilib.feature.extensionrepository.ExtensionPackageMetadata
import fr.vriege.anilib.feature.extensionrepository.ui.ExtensionRepositoryPresentation
import fr.vriege.anilib.feature.extensionrepository.ui.ExtensionRepositoryRow

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
    DisposableEffect(presentation) {
        val observation = presentation.observe { view = presentation.snapshot() }
        onDispose { observation.close() }
    }

    fun refresh() {
        loading = true
        error = null
        presentation.refreshAll().whenComplete { refreshed, failure ->
            if (failure == null) {
                view = refreshed
            } else {
                error = failure.cause?.message ?: failure.message ?: "Repository refresh failed."
            }
            loading = false
        }
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
                    "Anilib ships with no source catalogue. Add only repository URLs you trust.",
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
            error?.let { message ->
                item { Text(message, color = MaterialTheme.colorScheme.error) }
            }
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
                item {
                    Text(
                        "Available extensions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                items(view.packages(), key = { it.packageName() }) { extension ->
                    ExtensionPackageCard(extension)
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
private fun ExtensionPackageCard(extension: ExtensionPackageMetadata) {
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
