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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import fr.vriege.anilib.feature.library.ui.LibraryOverview
import fr.vriege.anilib.feature.applicationupdate.ui.ApplicationUpdatePresentation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CategoriesScreen(overview: LibraryOverview, goBack: () -> Unit) {
    MoreScaffold("Categories", goBack) { padding ->
        val counts = overview.categories().associateWith { category ->
            overview.titles().count { category in it.categories() }
        }
        val uncategorized = overview.titles().count { it.categories().isEmpty() }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { SummaryCard("Default", "$uncategorized titles") }
            counts.forEach { (category, count) ->
                item(key = category) { SummaryCard(category, "$count titles") }
            }
            if (counts.isEmpty() && uncategorized == 0) {
                item { EmptyPage("Categories will appear when titles are added to the library.") }
            }
        }
    }
}

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
