package fr.vriege.anilib.platform.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.vriege.anilib.feature.library.MediaKind
import fr.vriege.anilib.feature.library.LibraryCategory
import fr.vriege.anilib.feature.library.LibraryCategoryUpdatePolicy
import fr.vriege.anilib.feature.library.ui.LibraryOverview
import fr.vriege.anilib.feature.library.ui.LibraryPresentation
import fr.vriege.anilib.feature.applicationupdate.ui.ApplicationUpdatePresentation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CategoriesScreen(presentation: LibraryPresentation, goBack: () -> Unit) {
    var revision by remember(presentation) { mutableStateOf(0) }
    val overview = remember(presentation, revision) { presentation.library() }
    var newCategory by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    fun update(action: () -> Unit) {
        try {
            action()
            error = null
            revision++
        } catch (failure: RuntimeException) {
            error = failure.message ?: "Unable to update categories."
        }
    }
    MoreScaffold("Categories", goBack) { padding ->
        val uncategorized = overview.titles().count { it.categories().isEmpty() }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = newCategory,
                        onValueChange = { newCategory = it },
                        label = { Text("New category") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        enabled = newCategory.isNotBlank(),
                        onClick = {
                            update { presentation.createCategory(newCategory.trim()) }
                            if (error == null) newCategory = ""
                        },
                    ) { Text("Add") }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            item { SummaryCard("Default", "$uncategorized titles") }
            overview.categoryConfigurations().forEachIndexed { index, category ->
                val count = overview.titles().count { category.name() in it.categories() }
                item(key = category.name()) {
                    CategoryCard(
                        category,
                        count,
                        index,
                        overview.categoryConfigurations().lastIndex,
                        rename = { next ->
                            update { presentation.renameCategory(category.name(), next) }
                        },
                        move = { target ->
                            update { presentation.moveCategory(category.name(), target) }
                        },
                        setUpdatePolicy = { policy ->
                            update { presentation.setCategoryUpdatePolicy(category.name(), policy) }
                        },
                        delete = { update { presentation.deleteCategory(category.name()) } },
                    )
                }
            }
            if (overview.categories().isEmpty() && uncategorized == 0) {
                item { EmptyPage("Categories will appear when titles are added to the library.") }
            }
        }
    }
}

@Composable
private fun CategoryCard(
    category: LibraryCategory,
    count: Int,
    index: Int,
    lastIndex: Int,
    rename: (String) -> Unit,
    move: (Int) -> Unit,
    setUpdatePolicy: (LibraryCategoryUpdatePolicy) -> Unit,
    delete: () -> Unit,
) {
    var editing by remember(category.name()) { mutableStateOf(false) }
    var confirmingDelete by remember(category.name()) { mutableStateOf(false) }
    var nextName by remember(category.name()) { mutableStateOf(category.name()) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(category.name(), fontWeight = FontWeight.Medium)
                    Text("$count titles", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(enabled = index > 0, onClick = { move(index - 1) }) { Text("Up") }
                TextButton(enabled = index < lastIndex, onClick = { move(index + 1) }) { Text("Down") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { editing = true }) { Text("Rename") }
                TextButton(onClick = {
                    setUpdatePolicy(category.updatePolicy().next())
                }) { Text("Updates: ${category.updatePolicy().label()}") }
                TextButton(onClick = { confirmingDelete = true }) { Text("Delete") }
            }
        }
    }
    if (editing) {
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Rename category") },
            text = {
                OutlinedTextField(
                    value = nextName,
                    onValueChange = { nextName = it },
                    label = { Text("Category name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = nextName.isNotBlank(),
                    onClick = {
                        rename(nextName.trim())
                        editing = false
                    },
                ) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancel") } },
        )
    }
    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete ${category.name()}?") },
            text = { Text("Titles stay in the library and lose this category assignment.") },
            confirmButton = {
                TextButton(onClick = {
                    delete()
                    confirmingDelete = false
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") }
            },
        )
    }
}

private fun LibraryCategoryUpdatePolicy.next(): LibraryCategoryUpdatePolicy = when (this) {
    LibraryCategoryUpdatePolicy.DEFAULT -> LibraryCategoryUpdatePolicy.INCLUDE
    LibraryCategoryUpdatePolicy.INCLUDE -> LibraryCategoryUpdatePolicy.EXCLUDE
    LibraryCategoryUpdatePolicy.EXCLUDE -> LibraryCategoryUpdatePolicy.DEFAULT
}

private fun LibraryCategoryUpdatePolicy.label(): String =
    name.lowercase().replaceFirstChar(Char::uppercase)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StatisticsScreen(overview: LibraryOverview, goBack: () -> Unit) {
    val anime = overview.titles().count { it.kind() == MediaKind.ANIME }
    val manga = overview.titles().count { it.kind() == MediaKind.MANGA }
    val started = overview.titles().count { it.progress().isPresent }
    MoreScaffold("Statistics", goBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { SummaryCard("Library", "${overview.titles().size} titles") }
            item { SummaryCard("Anime", "$anime titles") }
            item { SummaryCard("Manga", "$manga titles") }
            item { SummaryCard("Favorites", "${overview.favoriteCount()} titles") }
            item { SummaryCard("Started", "$started titles") }
            item { SummaryCard("Categories", "${overview.categories().size} custom categories") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AboutScreen(
    componentCount: Int,
    updates: ApplicationUpdatePresentation,
    goBack: () -> Unit,
) {
    var snapshot by remember(updates) { mutableStateOf(updates.snapshot()) }
    var checking by remember(updates) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val available = snapshot.availableRelease().orElse(null)
    MoreScaffold("About", goBack) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Anilib", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "A cross-platform anime and manga library built from explicit, removable feature bundles.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SummaryCard("Runtime", "$componentCount feature bundles active")
            SummaryCard("Version", snapshot.currentVersion().display())
            SummaryCard("Update channel", "Stable · ${snapshot.platform().name.lowercase()}")
            SummaryCard("Source format", "Signed portable Anilib Bundles")
            SummaryCard("Platforms", "Android and desktop")
            snapshot.error().orElse(null)?.let { error ->
                Text(error, color = MaterialTheme.colorScheme.error)
            }
            if (available == null) {
                Button(
                    enabled = !checking,
                    onClick = {
                        checking = true
                        scope.launch {
                            snapshot = withContext(Dispatchers.IO) { updates.checkNow() }
                            checking = false
                        }
                    },
                ) {
                    Text(if (checking) "Checking…" else "Check for updates")
                }
            } else {
                Text(
                    "Anilib ${available.version().display()} is available.",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Button(onClick = { uriHandler.openUri(available.releasePage().toString()) }) {
                    Text("Open release")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { uriHandler.openUri("https://github.com/zolkers/Anilib") }) {
                    Text("Project")
                }
                TextButton(onClick = { uriHandler.openUri("https://github.com/zolkers/Anilib/issues") }) {
                    Text("Help")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoreScaffold(
    title: String,
    goBack: () -> Unit,
    content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = goBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        content = content,
    )
}

@Composable
private fun SummaryCard(title: String, summary: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
            Text(title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
            Text(summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
