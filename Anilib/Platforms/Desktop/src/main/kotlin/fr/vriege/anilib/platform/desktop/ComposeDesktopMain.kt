package fr.vriege.anilib.platform.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import fr.vriege.anilib.configuration.standard.StandardAnilib
import fr.vriege.anilib.feature.covercache.bundle.CoverCachePlugin
import fr.vriege.anilib.feature.library.LibraryItemId
import fr.vriege.anilib.feature.library.LibraryProgress
import fr.vriege.anilib.feature.library.ui.LibraryCard
import fr.vriege.anilib.feature.library.ui.LibraryDetails
import fr.vriege.anilib.feature.library.ui.LibraryHistoryRow
import fr.vriege.anilib.feature.library.ui.LibraryNavigationState
import fr.vriege.anilib.feature.library.ui.LibraryNavigator
import fr.vriege.anilib.feature.library.ui.LibraryOverview
import fr.vriege.anilib.feature.library.ui.LibraryPage
import fr.vriege.anilib.feature.library.ui.LibraryPresentation
import fr.vriege.anilib.feature.library.ui.LibraryUiCapabilities
import fr.vriege.anilib.kernel.StartedAnilib
import java.awt.GraphicsEnvironment
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.roundToInt

private val dateTimeFormatter = DateTimeFormatter
    .ofLocalizedDateTime(FormatStyle.MEDIUM)
    .withLocale(Locale.getDefault())
    .withZone(ZoneId.systemDefault())

fun main() {
    val dataDirectory = DesktopDataDirectory.resolve()
    val started = StandardAnilib.start(
        dataDirectory,
        listOf(CoverCachePlugin(dataDirectory.resolve("cache").resolve("covers"))),
    )
    if (GraphicsEnvironment.isHeadless()) {
        printHeadlessSummary(started)
        started.close()
        return
    }
    val presentation = started.capability(LibraryUiCapabilities.PRESENTATION)
    application {
        Window(
            onCloseRequest = {
                started.close()
                exitApplication()
            },
            title = "Anilib",
        ) {
            AnilibDesktopApp(presentation, started.components().size)
        }
    }
}

private fun printHeadlessSummary(started: StartedAnilib) {
    val count = started.capability(LibraryUiCapabilities.PRESENTATION).library().titles().size
    println(
        "Anilib started headlessly with ${started.components().size} bundles and $count library items.",
    )
}

@Composable
private fun AnilibDesktopApp(presentation: LibraryPresentation, componentCount: Int) {
    val navigator = remember { LibraryNavigator() }
    var destination by remember { mutableStateOf(navigator.state()) }
    val navigate: ((LibraryNavigator) -> Unit) -> Unit = { transition ->
        transition(navigator)
        destination = navigator.state()
    }

    MaterialTheme(
        colorScheme = if (isDarkEnvironment()) darkColorScheme() else lightColorScheme(),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxSize()) {
                AnilibNavigation(destination, navigate)
                VerticalDivider()
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    LibraryDestination(
                        presentation = presentation,
                        destination = destination,
                        componentCount = componentCount,
                        navigate = navigate,
                    )
                }
            }
        }
    }
}

@Composable
private fun AnilibNavigation(
    destination: LibraryNavigationState,
    navigate: ((LibraryNavigator) -> Unit) -> Unit,
) {
    NavigationRail(header = {
        Text(
            text = "Anilib",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 22.dp),
        )
    }) {
        NavigationRailItem(
            selected = destination.page() == LibraryPage.LIBRARY,
            onClick = { navigate(LibraryNavigator::openLibrary) },
            icon = { Icon(Icons.Default.CollectionsBookmark, contentDescription = null) },
            label = { Text("Library") },
        )
        NavigationRailItem(
            selected = destination.page() == LibraryPage.HISTORY,
            onClick = { navigate(LibraryNavigator::openHistory) },
            icon = { Icon(Icons.Default.History, contentDescription = null) },
            label = { Text("History") },
        )
    }
}

@Composable
private fun LibraryDestination(
    presentation: LibraryPresentation,
    destination: LibraryNavigationState,
    componentCount: Int,
    navigate: ((LibraryNavigator) -> Unit) -> Unit,
) {
    when (destination.page()) {
        LibraryPage.LIBRARY -> LibraryPage(presentation.library(), componentCount, navigate)
        LibraryPage.HISTORY -> HistoryPage(presentation, navigate)
        LibraryPage.DETAILS -> DetailsDestination(presentation, destination, navigate)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryPage(
    overview: LibraryOverview,
    componentCount: Int,
    navigate: ((LibraryNavigator) -> Unit) -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Library") }) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
        ) {
            Text(
                text = librarySummary(overview),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            if (overview.titles().isEmpty()) {
                EmptyPage("Your library is empty. Add local content to begin.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(overview.titles(), key = { it.id().value() }) { card ->
                        LibraryTitleCard(card) { navigate { it.openDetails(card.id()) } }
                    }
                    item {
                        Text(
                            text = "$componentCount feature bundles active",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryTitleCard(card: LibraryCard, openDetails: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = openDetails),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = card.title(),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = cardMetadata(card),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (card.favorite()) {
                Text(
                    text = "Favorite",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryPage(
    presentation: LibraryPresentation,
    navigate: ((LibraryNavigator) -> Unit) -> Unit,
) {
    val history = presentation.history()
    Scaffold(topBar = { TopAppBar(title = { Text("History") }) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
        ) {
            Text(
                text = "${history.entries().size} recent entries",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            if (history.entries().isEmpty()) {
                EmptyPage("Titles you open will appear here.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(history.entries()) { row ->
                        HistoryCard(row) { navigate { it.openDetails(row.libraryItemId()) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(row: LibraryHistoryRow, openDetails: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = openDetails),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
            Text(row.title(), fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            Spacer(Modifier.height(5.dp))
            Text(
                text = "${row.contentId()} | Position ${row.position()}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = dateTimeFormatter.format(row.openedAt()),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DetailsDestination(
    presentation: LibraryPresentation,
    destination: LibraryNavigationState,
    navigate: ((LibraryNavigator) -> Unit) -> Unit,
) {
    val id = destination.selectedTitle().orElse(null)
    val details = id?.let { presentation.details(it).orElse(null) }
    if (details == null) {
        MissingDetails { navigate(LibraryNavigator::openLibrary) }
    } else {
        DetailsPage(details) { navigate(LibraryNavigator::back) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailsPage(details: LibraryDetails, goBack: () -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(details.title()) }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { Text(formatEnum(details.kind()), color = MaterialTheme.colorScheme.primary) }
            item {
                DetailsFacts(details)
            }
            item {
                Text("Description", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    details.description().ifBlank { "No description available." },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Button(onClick = goBack, modifier = Modifier.padding(vertical = 12.dp)) {
                    Text("Back")
                }
            }
        }
    }
}

@Composable
private fun DetailsFacts(details: LibraryDetails) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Fact("Favorite", if (details.favorite()) "Yes" else "No")
            Fact("Status", formatEnum(details.publicationStatus()))
            Fact("Added", dateTimeFormatter.format(details.addedAt()))
            Fact("Categories", joined(details.categories()))
            Fact("Authors", joined(details.authors()))
            Fact("Artists", joined(details.artists()))
            Fact("Progress", details.progress().map(::progress).orElse("Not started"))
            Fact("History", "${details.historyEntryCount()} entries")
        }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, fontWeight = FontWeight.Medium, modifier = Modifier.width(130.dp))
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyPage(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MissingDetails(openLibrary: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("This title is no longer in the library.")
            Spacer(Modifier.height(12.dp))
            Button(onClick = openLibrary) { Text("Back to library") }
        }
    }
}

private fun librarySummary(overview: LibraryOverview): String =
    "${overview.titles().size} titles | ${overview.favoriteCount()} favorites | " +
        "${overview.categories().size} categories"

private fun cardMetadata(card: LibraryCard): String {
    val categoryText = joined(card.categories())
    val progressText = card.progress().map(::progress).orElse("Not started")
    return "${formatEnum(card.kind())} | $categoryText | $progressText"
}

private fun progress(progress: LibraryProgress): String {
    if (progress.extent() == LibraryProgress.UNKNOWN_EXTENT) {
        return progress.position().toString()
    }
    val percentage = (progress.completion().orElse(0.0) * 100.0).roundToInt()
    return "${progress.position()} / ${progress.extent()} ($percentage%)"
}

private fun joined(values: List<String>): String =
    if (values.isEmpty()) "None" else values.joinToString(", ")

private fun formatEnum(value: Enum<*>): String = value.name
    .replace('_', ' ')
    .lowercase(Locale.ROOT)
    .replaceFirstChar(Char::uppercase)

@Composable
private fun isDarkEnvironment(): Boolean {
    val theme = System.getProperty("anilib.theme", "system")
    if (theme.equals("dark", ignoreCase = true)) {
        return true
    }
    if (theme.equals("light", ignoreCase = true)) {
        return false
    }
    return isSystemInDarkTheme()
}
