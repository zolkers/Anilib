package fr.vriege.anilib.platform.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.vriege.anilib.feature.library.MediaKind
import fr.vriege.anilib.feature.library.PublicationStatus
import fr.vriege.anilib.feature.library.LibraryCategory
import fr.vriege.anilib.feature.library.LibraryCategoryScope
import fr.vriege.anilib.feature.library.LibraryCategoryUpdatePolicy
import fr.vriege.anilib.feature.library.LibraryDisplayDensity
import fr.vriege.anilib.feature.library.LibraryDisplayMode
import fr.vriege.anilib.feature.library.LibraryItemId
import fr.vriege.anilib.feature.library.LibrarySort
import fr.vriege.anilib.feature.discovery.ui.DiscoveryPresentation
import fr.vriege.anilib.feature.library.ui.LibraryPresentation
import fr.vriege.anilib.feature.library.ui.LibraryCard
import fr.vriege.anilib.feature.player.ui.PlayerPresentation
import fr.vriege.anilib.feature.source.SourceId
import fr.vriege.anilib.feature.tracker.ui.TrackerPresentation
import fr.vriege.anilib.feature.applicationupdate.ui.ApplicationUpdatePresentation
import fr.vriege.anilib.feature.applicationupdate.ApplicationUpdateChannel
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.PaddingValues

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CategoriesScreen(presentation: LibraryPresentation, goBack: () -> Unit) {
    var revision by remember(presentation) { mutableStateOf(0) }
    val overview = remember(presentation, revision) { presentation.library() }
    var creating by remember { mutableStateOf(false) }
    var selectedScope by remember { mutableStateOf(LibraryCategoryScope.ANIME) }
    var managingCategory by remember { mutableStateOf<LibraryCategory?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    fun update(action: () -> Unit): Boolean = try {
        action()
        error = null
        revision++
        true
    } catch (failure: RuntimeException) {
        error = failure.message ?: "Unable to update categories."
        false
    }
    MoreScaffold("ui.categories", goBack) { padding ->
        val scopedTitles = overview.titles().filter { selectedScope.supports(it.kind()) }
        val uncategorized = scopedTitles.count { it.categories().isEmpty() }
        val visibleCategories = overview.categoryConfigurations()
            .filter { it.scope() == selectedScope || it.scope() == LibraryCategoryScope.SHARED }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("ui.organize.your.library", fontWeight = FontWeight.SemiBold)
                        Text(
                            "ui.each.category.can.use.its.own.layout.density.sort.and.update.policy",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { creating = true }) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "ui.create.category",
                        )
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedScope == LibraryCategoryScope.ANIME,
                        onClick = { selectedScope = LibraryCategoryScope.ANIME },
                        label = { Text("ui.anime") },
                    )
                    FilterChip(
                        selected = selectedScope == LibraryCategoryScope.MANGA,
                        onClick = { selectedScope = LibraryCategoryScope.MANGA },
                        label = { Text("ui.manga") },
                    )
                }
            }
            item {
                SummaryCard(
                    "ui.default",
                    UiTranslations.format("dynamic.titles.count", LocalLanguagePack.current, uncategorized),
                )
            }
            visibleCategories.forEachIndexed { index, category ->
                val count = scopedTitles.count { category.name() in it.categories() }
                val previousTarget = visibleCategories.getOrNull(index - 1)
                    ?.let(overview.categoryConfigurations()::indexOf)
                val nextTarget = visibleCategories.getOrNull(index + 1)
                    ?.let(overview.categoryConfigurations()::indexOf)
                item(key = category.name()) {
                    CategoryCard(
                        category,
                        count,
                        replace = { next ->
                            update { presentation.replaceCategory(category.name(), next) }
                        },
                        moveUp = previousTarget?.let { target ->
                            { update { presentation.moveCategory(category.name(), target) } }
                        },
                        moveDown = nextTarget?.let { target ->
                            { update { presentation.moveCategory(category.name(), target) } }
                        },
                        manageTitles = { managingCategory = category },
                        delete = { update { presentation.deleteCategory(category.name()) } },
                    )
                }
            }
            if (visibleCategories.isEmpty() && uncategorized == 0) {
                item { EmptyPage("ui.categories.appear.after.adding.titles") }
            }
        }
    }
    if (creating) {
        val preferences = overview.displayPreferences()
        CategoryEditorDialog(
            title = "ui.create.category",
            confirmLabel = "ui.create",
            initial = LibraryCategory(
                "New category",
                selectedScope,
                preferences.mode(),
                preferences.density(),
                preferences.sort(),
                LibraryCategoryUpdatePolicy.DEFAULT,
            ),
            clearInitialName = true,
            dismiss = { creating = false },
            confirm = { category ->
                if (update { presentation.createCategory(category) }) creating = false
            },
        )
    }
    managingCategory?.let { category ->
        CategoryTitlesDialog(
            category = category,
            titles = overview.titles().filter { category.scope().supports(it.kind()) },
            dismiss = { managingCategory = null },
            save = { selected ->
                if (update { presentation.setCategoryTitles(category.name(), selected) }) {
                    managingCategory = null
                }
            },
        )
    }
}

@Composable
private fun CategoryCard(
    category: LibraryCategory,
    count: Int,
    replace: (LibraryCategory) -> Boolean,
    moveUp: (() -> Unit)?,
    moveDown: (() -> Unit)?,
    manageTitles: () -> Unit,
    delete: () -> Unit,
) {
    var editing by remember(category.name()) { mutableStateOf(false) }
    var confirmingDelete by remember(category.name()) { mutableStateOf(false) }
    AnilibGroup {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(category.name(), fontWeight = FontWeight.Medium)
                    Text(
                        category.scope().scopeLabel(),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        UiTranslations.format("dynamic.titles.count", LocalLanguagePack.current, count),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "${category.displayMode().displayLabel()} · " +
                            "${category.density().densityLabel()} · ${category.sort().sortLabel()}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "ui.library.updates",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "·",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            category.updatePolicy().updateLabel(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(enabled = moveUp != null, onClick = { moveUp?.invoke() }) {
                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        contentDescription = "ui.up",
                    )
                }
                IconButton(enabled = moveDown != null, onClick = { moveDown?.invoke() }) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "ui.down",
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = manageTitles) {
                    Icon(
                        Icons.Default.Category,
                        contentDescription = "ui.manage.titles",
                    )
                }
                IconButton(onClick = { editing = true }) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "ui.edit",
                    )
                }
                IconButton(onClick = { confirmingDelete = true }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "ui.delete",
                    )
                }
            }
        }
    }
    if (editing) {
        CategoryEditorDialog(
            title = "ui.edit.category",
            confirmLabel = "ui.save",
            initial = category,
            dismiss = { editing = false },
            confirm = {
                if (replace(it)) editing = false
            },
        )
    }
    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = {
                Text(UiTranslations.format("dynamic.delete.category", LocalLanguagePack.current, category.name()))
            },
            text = { Text("ui.titles.stay.in.the.library.and.lose.this.category.assignment") },
            confirmButton = {
                TextButton(onClick = {
                    delete()
                    confirmingDelete = false
                }) { Text("ui.delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text("ui.cancel") }
            },
        )
    }
}

@Composable
private fun CategoryEditorDialog(
    title: String,
    confirmLabel: String,
    initial: LibraryCategory,
    clearInitialName: Boolean = false,
    dismiss: () -> Unit,
    confirm: (LibraryCategory) -> Unit,
) {
    var name by remember(initial.name(), clearInitialName) {
        mutableStateOf(if (clearInitialName) "" else initial.name())
    }
    var displayMode by remember(initial) { mutableStateOf(initial.displayMode()) }
    var density by remember(initial) { mutableStateOf(initial.density()) }
    var sort by remember(initial) { mutableStateOf(initial.sort()) }
    var updatePolicy by remember(initial) { mutableStateOf(initial.updatePolicy()) }
    var scope by remember(initial) { mutableStateOf(initial.scope()) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("ui.category.name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                CategoryOption("ui.library.type", scope.scopeLabel()) {
                    scope = if (scope == LibraryCategoryScope.ANIME) {
                        LibraryCategoryScope.MANGA
                    } else {
                        LibraryCategoryScope.ANIME
                    }
                }
                CategoryOption("ui.display.mode", displayMode.displayLabel()) {
                    displayMode = nextValue(displayMode, DISPLAY_MODES)
                }
                CategoryOption("ui.density", density.densityLabel()) {
                    density = nextValue(density, DISPLAY_DENSITIES)
                }
                CategoryOption("ui.sort", sort.sortLabel()) {
                    sort = nextValue(sort, SORTS)
                }
                CategoryOption("ui.library.updates", updatePolicy.updateLabel()) {
                    updatePolicy = nextValue(updatePolicy, UPDATE_POLICIES)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    confirm(LibraryCategory(name.trim(), scope, displayMode, density, sort, updatePolicy))
                },
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("ui.cancel") } },
    )
}

@Composable
private fun CategoryTitlesDialog(
    category: LibraryCategory,
    titles: List<LibraryCard>,
    dismiss: () -> Unit,
    save: (Set<LibraryItemId>) -> Unit,
) {
    var selected by remember(category.name(), titles) {
        mutableStateOf<Set<LibraryItemId>>(
            titles.filter { category.name() in it.categories() }.mapTo(linkedSetOf()) { it.id() },
        )
    }
    AlertDialog(
        onDismissRequest = dismiss,
        title = {
            Text(UiTranslations.format("dynamic.manage.category.titles", LocalLanguagePack.current, category.name()))
        },
        text = {
            if (titles.isEmpty()) {
                Text("ui.no.titles.in.this.library")
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    items(titles, key = { it.id().value() }) { title ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selected = selected.toggle(title.id()) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = title.id() in selected,
                                onCheckedChange = { selected = selected.toggle(title.id()) },
                            )
                            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                                Text(title.title(), fontWeight = FontWeight.Medium)
                                Text(
                                    title.kind().kindLabel(),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { save(selected) }) { Text("ui.save") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("ui.cancel") } },
    )
}

private fun Set<LibraryItemId>.toggle(id: LibraryItemId): Set<LibraryItemId> =
    if (id in this) this - id else this + id

private fun LibraryCategoryScope.scopeLabel(): String = when (this) {
    LibraryCategoryScope.ANIME -> "ui.anime"
    LibraryCategoryScope.MANGA -> "ui.manga"
    LibraryCategoryScope.SHARED -> "ui.anime.and.manga"
}

private fun MediaKind.kindLabel(): String =
    if (this == MediaKind.ANIME) "ui.anime" else "ui.manga"

@Composable
private fun CategoryOption(label: String, value: String, selectNext: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = selectNext, modifier = Modifier.fillMaxWidth()) { Text(value) }
    }
}

private val DISPLAY_MODES = listOf(LibraryDisplayMode.GRID, LibraryDisplayMode.LIST)
private val DISPLAY_DENSITIES = listOf(
    LibraryDisplayDensity.COMPACT,
    LibraryDisplayDensity.COMFORTABLE,
    LibraryDisplayDensity.RELAXED,
)
private val SORTS = listOf(
    LibrarySort.TITLE_ASCENDING,
    LibrarySort.TITLE_DESCENDING,
    LibrarySort.ADDED_NEWEST,
    LibrarySort.ADDED_OLDEST,
)
private val UPDATE_POLICIES = listOf(
    LibraryCategoryUpdatePolicy.DEFAULT,
    LibraryCategoryUpdatePolicy.INCLUDE,
    LibraryCategoryUpdatePolicy.EXCLUDE,
)

private fun <T> nextValue(current: T, values: List<T>): T {
    val index = values.indexOf(current)
    return values[(index + 1).mod(values.size)]
}

private fun LibraryDisplayMode.displayLabel(): String =
    if (this == LibraryDisplayMode.GRID) "ui.grid" else "ui.list"

private fun LibraryDisplayDensity.densityLabel(): String = when (this) {
    LibraryDisplayDensity.COMPACT -> "ui.compact"
    LibraryDisplayDensity.COMFORTABLE -> "ui.comfortable"
    LibraryDisplayDensity.RELAXED -> "ui.relaxed"
}

private fun LibrarySort.sortLabel(): String {
    if (this == LibrarySort.TITLE_ASCENDING) return "ui.title.az"
    if (this == LibrarySort.TITLE_DESCENDING) return "ui.title.za"
    if (this == LibrarySort.ADDED_NEWEST) return "ui.recently.added"
    return "ui.oldest.added"
}

private fun LibraryCategoryUpdatePolicy.updateLabel(): String {
    if (this == LibraryCategoryUpdatePolicy.DEFAULT) return "ui.use.global.policy"
    if (this == LibraryCategoryUpdatePolicy.INCLUDE) return "ui.always.include"
    return "ui.exclude"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StatisticsScreen(
    library: LibraryPresentation,
    discovery: DiscoveryPresentation,
    player: PlayerPresentation,
    tracking: TrackerPresentation,
    goBack: () -> Unit,
) {
    var snapshot by remember(library, discovery, player, tracking) {
        mutableStateOf<StatisticsSnapshot?>(null)
    }
    var error by remember(library, discovery, player, tracking) { mutableStateOf<String?>(null) }
    CrashSafeLaunchedEffect(library, discovery, player, tracking) {
        runCatching {
            withContext(Dispatchers.IO) {
                statisticsSnapshot(library, discovery, player, tracking, Instant.now())
            }
        }.onSuccess {
            snapshot = it
            error = null
        }.onFailure {
            error = it.message ?: "Unable to calculate library statistics."
        }
    }
    MoreScaffold("ui.statistics", goBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            val value = snapshot
            if (value == null && error == null) {
                item { Text("ui.calculating.statistics") }
            } else if (value != null) {
                item { StatisticsHeading("ui.library") }
                item {
                    SummaryCard(
                        "ui.titles",
                        UiTranslations.format("dynamic.total.count", LocalLanguagePack.current, value.totalTitles),
                    )
                }
                item { SummaryCard("ui.anime.manga", "${value.animeTitles} / ${value.mangaTitles}") }
                item {
                    SummaryCard(
                        "ui.favorites",
                        UiTranslations.format("dynamic.titles.count", LocalLanguagePack.current, value.favoriteTitles),
                    )
                }
                item {
                    SummaryCard(
                        "ui.categories",
                        UiTranslations.format(
                            "dynamic.custom.categories",
                            LocalLanguagePack.current,
                            value.categoryCount,
                        ),
                    )
                }
                item { StatisticsHeading("ui.publication.status") }
                value.statuses.forEach { (label, count) ->
                    item(key = "status-$label") {
                        SummaryCard(
                            label,
                            UiTranslations.format("dynamic.titles.count", LocalLanguagePack.current, count),
                        )
                    }
                }
                item { StatisticsHeading("ui.sources.and.languages") }
                value.sources.forEach { (label, count) ->
                    item(key = "source-$label") {
                        SummaryCard(
                            label,
                            UiTranslations.format("dynamic.titles.count", LocalLanguagePack.current, count),
                        )
                    }
                }
                value.languages.forEach { (label, count) ->
                    item(key = "language-$label") {
                        SummaryCard(
                            UiTranslations.format("dynamic.language", LocalLanguagePack.current, label),
                            UiTranslations.format("dynamic.titles.count", LocalLanguagePack.current, count),
                        )
                    }
                }
                item { StatisticsHeading("ui.scores") }
                item {
                    SummaryCard(
                        "ui.average.tracker.score",
                        value.averageScore?.let { String.format(Locale.ROOT, "%.2f / 10", it) }
                            ?: UiTranslations.translate("ui.no.scored.entries", LocalLanguagePack.current),
                    )
                }
                value.scoreBuckets.forEach { (label, count) ->
                    item(key = "score-$label") {
                        SummaryCard(
                            label,
                            UiTranslations.format("dynamic.entries.count", LocalLanguagePack.current, count),
                        )
                    }
                }
                item { StatisticsHeading("ui.duration.and.progress") }
                item { SummaryCard("ui.watched", durationLabel(value.watchedMillis)) }
                item { SummaryCard("ui.known.episode.duration", durationLabel(value.knownDurationMillis)) }
                item {
                    SummaryCard(
                        "ui.average.title.progress",
                        value.averageProgress?.let { "${(it * 100.0).toInt()}%" }
                            ?: UiTranslations.translate("ui.no.measurable.progress", LocalLanguagePack.current),
                    )
                }
                item {
                    SummaryCard(
                        "ui.started",
                        UiTranslations.format("dynamic.titles.count", LocalLanguagePack.current, value.startedTitles),
                    )
                }
                item { StatisticsHeading("ui.activity") }
                item {
                    SummaryCard(
                        "ui.last.7.days",
                        UiTranslations.format("dynamic.visits.count", LocalLanguagePack.current, value.activity7Days),
                    )
                }
                item {
                    SummaryCard(
                        "ui.last.30.days",
                        UiTranslations.format("dynamic.visits.count", LocalLanguagePack.current, value.activity30Days),
                    )
                }
                item {
                    SummaryCard(
                        "ui.last.365.days",
                        UiTranslations.format("dynamic.visits.count", LocalLanguagePack.current, value.activity365Days),
                    )
                }
                if (value.unavailableEpisodeSources > 0) {
                    item {
                        Text(
                            UiTranslations.format(
                                "dynamic.duration.excludes.sources",
                                LocalLanguagePack.current,
                                value.unavailableEpisodeSources,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatisticsHeading(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp),
    )
}

private data class StatisticsSnapshot(
    val totalTitles: Int,
    val animeTitles: Int,
    val mangaTitles: Int,
    val favoriteTitles: Int,
    val categoryCount: Int,
    val statuses: List<Pair<String, Int>>,
    val sources: List<Pair<String, Int>>,
    val languages: List<Pair<String, Int>>,
    val averageScore: Double?,
    val scoreBuckets: List<Pair<String, Int>>,
    val watchedMillis: Long,
    val knownDurationMillis: Long,
    val averageProgress: Double?,
    val startedTitles: Int,
    val activity7Days: Int,
    val activity30Days: Int,
    val activity365Days: Int,
    val unavailableEpisodeSources: Int,
)

private fun statisticsSnapshot(
    library: LibraryPresentation,
    discovery: DiscoveryPresentation,
    player: PlayerPresentation,
    tracking: TrackerPresentation,
    now: Instant,
): StatisticsSnapshot {
    val overview = library.library()
    val details = overview.titles().mapNotNull { library.details(it.id()).orElse(null) }
    val descriptors = details.mapNotNull { detail -> detail.origin().orElse(null)?.sourceId() }
        .distinct()
        .associateWith { sourceId ->
            runCatching { discovery.source(SourceId.of(sourceId)).orElse(null) }.getOrNull()
        }
    val sources = details.groupingBy { detail ->
        detail.origin().map { origin ->
            descriptors[origin.sourceId()]?.displayName() ?: origin.sourceId()
        }.orElse("Local")
    }.eachCount().sortedCounts()
    val languages = details.groupingBy { detail ->
        detail.origin().map { origin ->
            descriptors[origin.sourceId()]?.languageTag() ?: "Unknown"
        }.orElse("Local")
    }.eachCount().sortedCounts()
    val statuses = PublicationStatus.entries.mapNotNull { status ->
        details.count { it.publicationStatus() == status }
            .takeIf { it > 0 }
            ?.let { status.name.lowercase().replaceFirstChar(Char::uppercase) to it }
    }
    val scores = details.flatMap { detail ->
        runCatching { tracking.entries(detail.id()) }.getOrDefault(emptyList())
    }.map { entry -> entry.score().orElse(Double.NaN) }.filter(Double::isFinite)
    val scoreBuckets = listOf(
        "Score 0–3" to scores.count { it < 4.0 },
        "Score 4–6" to scores.count { it >= 4.0 && it < 7.0 },
        "Score 7–8" to scores.count { it >= 7.0 && it < 9.0 },
        "Score 9–10" to scores.count { it >= 9.0 },
    ).filter { it.second > 0 }
    var watchedMillis = 0L
    var knownDurationMillis = 0L
    var unavailableEpisodeSources = 0
    details.filter { it.kind() == MediaKind.ANIME }.forEach { detail ->
        runCatching { player.episodes(detail.id()) }
            .onSuccess { episodes ->
                episodes.mapNotNull { it.playback().orElse(null) }.forEach { playback ->
                    watchedMillis = saturatedAdd(watchedMillis, playback.positionMillis())
                    if (playback.durationMillis() >= 0) {
                        knownDurationMillis = saturatedAdd(
                            knownDurationMillis,
                            playback.durationMillis(),
                        )
                    }
                }
            }
            .onFailure { unavailableEpisodeSources++ }
    }
    val progress = details.mapNotNull { detail ->
        detail.progress().orElse(null)?.completion()?.orElse(Double.NaN)
            ?.takeIf(Double::isFinite)
    }
    val history = library.history().entries()
    return StatisticsSnapshot(
        totalTitles = details.size,
        animeTitles = details.count { it.kind() == MediaKind.ANIME },
        mangaTitles = details.count { it.kind() == MediaKind.MANGA },
        favoriteTitles = overview.favoriteCount(),
        categoryCount = overview.categories().size,
        statuses = statuses,
        sources = sources,
        languages = languages,
        averageScore = scores.takeIf { it.isNotEmpty() }?.average(),
        scoreBuckets = scoreBuckets,
        watchedMillis = watchedMillis,
        knownDurationMillis = knownDurationMillis,
        averageProgress = progress.takeIf { it.isNotEmpty() }?.average(),
        startedTitles = details.count { it.progress().isPresent },
        activity7Days = history.count { !it.openedAt().isBefore(now.minus(Duration.ofDays(7))) },
        activity30Days = history.count { !it.openedAt().isBefore(now.minus(Duration.ofDays(30))) },
        activity365Days = history.count { !it.openedAt().isBefore(now.minus(Duration.ofDays(365))) },
        unavailableEpisodeSources = unavailableEpisodeSources,
    )
}

private fun Map<String, Int>.sortedCounts(): List<Pair<String, Int>> = entries
    .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
    .map { it.key to it.value }

private fun saturatedAdd(left: Long, right: Long): Long =
    if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right

private fun durationLabel(milliseconds: Long): String {
    val minutes = milliseconds / 60_000L
    val hours = minutes / 60L
    val remaining = minutes % 60L
    return if (hours > 0) "$hours h $remaining min" else "$remaining min"
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
    var installing by remember(updates) { mutableStateOf(false) }
    var downloadedBytes by remember(updates) { mutableStateOf(0L) }
    var updateMessage by remember(updates) { mutableStateOf<String?>(null) }
    val scope = rememberCrashSafeCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val platformController = LocalApplicationUpdatePlatformController.current
    val available = snapshot.availableRelease().orElse(null)
    MoreScaffold("ui.about", goBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Anilib", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "ui.a.cross.platform.anime.and.manga.library.built.from.explicit.removable.feature.bundles",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SummaryCard(
                "ui.runtime",
                UiTranslations.format("dynamic.feature.bundles.active", LocalLanguagePack.current, componentCount),
            )
            SummaryCard("ui.version", snapshot.currentVersion().display())
            SummaryCard(
                "ui.update.channel",
                "${snapshot.channel().name.lowercase().replaceFirstChar(Char::uppercase)} · " +
                    snapshot.platform().name.lowercase(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ApplicationUpdateChannel.entries.forEach { channel ->
                    TextButton(
                        enabled = !checking && !installing && snapshot.channel() != channel,
                        onClick = {
                            snapshot = updates.setChannel(channel)
                            updateMessage = "${channel.name.lowercase().replaceFirstChar(Char::uppercase)} " +
                                "channel selected"
                        },
                    ) {
                        Text(channel.name.lowercase().replaceFirstChar(Char::uppercase))
                    }
                }
            }
            SummaryCard("ui.source.format", "ui.signed.portable.anilib.bundles")
            SummaryCard("ui.platforms", "ui.desktop")
            snapshot.error().orElse(null)?.let { error ->
                Text(error, color = MaterialTheme.colorScheme.error)
            }
            updateMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
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
                    Text(if (checking) "ui.checking" else "ui.check.for.updates")
                }
            } else {
                Text(
                    UiTranslations.format(
                        "dynamic.app.version.available",
                        LocalLanguagePack.current,
                        available.version().display(),
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                if (available.changelog().isNotBlank()) {
                    SummaryCard("ui.changelog", available.changelog())
                }
                available.artifact().orElse(null)?.let { artifact ->
                    if (installing) {
                        Text(
                            UiTranslations.format(
                                "dynamic.downloaded.bytes",
                                LocalLanguagePack.current,
                                downloadedBytes,
                                artifact.sizeBytes(),
                            ),
                        )
                    }
                    Button(
                        enabled = !installing,
                        onClick = {
                            installing = true
                            downloadedBytes = 0L
                            updateMessage = "Downloading the signed installer…"
                            scope.launch {
                                runCatching {
                                    val path = platformController.download(artifact) {
                                        scope.launch { downloadedBytes = it }
                                    }
                                    val verification = withContext(Dispatchers.IO) {
                                        updates.verifyDownloadedArtifact(path)
                                    }
                                    platformController.install(verification)
                                }.onSuccess {
                                    updateMessage = "Installer verified; complete installation in the system prompt."
                                }.onFailure {
                                    updateMessage = it.message ?: "Application update failed"
                                }
                                installing = false
                            }
                        },
                    ) {
                        Text(if (installing) "Preparing…" else "Download and install")
                    }
                }
                Button(onClick = { uriHandler.openUri(available.releasePage().toString()) }) {
                    Text("ui.open.release")
                }
                TextButton(onClick = { uriHandler.openUri(available.licensePage().toString()) }) {
                    Text("ui.licence")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { uriHandler.openUri("https://github.com/zolkers/Anilib") }) {
                    Text("ui.project")
                }
                TextButton(onClick = { uriHandler.openUri("https://github.com/zolkers/Anilib/issues") }) {
                    Text("ui.help")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    uriHandler.openUri("https://github.com/zolkers/Anilib/blob/main/THIRD_PARTY.md")
                }) {
                    Text("ui.third.party.notices")
                }
                TextButton(onClick = {
                    uriHandler.openUri("https://github.com/zolkers/Anilib/blob/main/PRIVACY.md")
                }) {
                    Text("ui.privacy")
                }
            }
            Text(
                "ui.copyright.2026.victor.riegert.apache.license.2.0",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoreScaffold(
    title: String,
    goBack: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    AnilibSubScreenScaffold(title = title, goBack = goBack, content = content)
}

@Composable
private fun SummaryCard(title: String, summary: String) {
    AnilibGroup {
        Row(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
            Text(title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
            Text(summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
