package fr.vriege.anilib.platform.compose

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.vriege.anilib.feature.library.LibraryCategoryScope
import fr.vriege.anilib.feature.library.LibraryProgress
import fr.vriege.anilib.feature.library.LibraryDisplayDensity
import fr.vriege.anilib.feature.library.LibraryDisplayMode
import fr.vriege.anilib.feature.library.LibraryItemId
import fr.vriege.anilib.feature.library.LibrarySort
import fr.vriege.anilib.feature.library.MediaKind
import fr.vriege.anilib.feature.discovery.ui.DiscoveryPresentation
import fr.vriege.anilib.feature.downloads.ui.DownloadPresentation
import fr.vriege.anilib.feature.library.ui.LibraryCard
import fr.vriege.anilib.feature.library.ui.LibraryNavigator
import fr.vriege.anilib.feature.library.ui.LibraryOverview
import fr.vriege.anilib.feature.library.ui.LibraryPresentation
import fr.vriege.anilib.feature.source.SourceContentKind
import fr.vriege.anilib.feature.source.SourceCatalogueItem
import fr.vriege.anilib.feature.source.SourceId
import java.util.Locale
import java.util.Optional
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryPageContent(
    presentation: LibraryPresentation,
    discovery: DiscoveryPresentation,
    downloads: DownloadPresentation,
    kind: MediaKind,
    navigate: ((LibraryNavigator) -> Unit) -> Unit,
) {
    var revision by remember(presentation) { mutableStateOf(0) }
    val overview = remember(presentation, revision) { presentation.library() }
    val scopedCategories = remember(overview, kind) {
        overview.categoryConfigurations()
            .filter { it.scope().supports(kind) }
            .map { it.name() }
    }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    val searchFocus = rememberSearchFocusRequester(searching)
    var favoritesOnly by remember(kind) { mutableStateOf(false) }
    var category by remember(presentation, kind) {
        mutableStateOf(
            overview.displayPreferences().defaultCategory().orElse(null)
                ?.takeIf { it in scopedCategories },
        )
    }
    var error by remember { mutableStateOf<String?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<LibraryItemId>()) }
    var categoryAction by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var migrating by remember { mutableStateOf(false) }
    fun update(action: () -> Unit) {
        try {
            action()
            error = null
            revision++
        } catch (failure: RuntimeException) {
            error = failure.message ?: "Unable to update the library display."
        }
    }
    val titles = remember(overview, query, kind, favoritesOnly, category) {
        overview.titles()
            .asSequence()
            .filter { query.isBlank() || it.title().contains(query, ignoreCase = true) }
            .filter { it.kind() == kind }
            .filter { !favoritesOnly || it.favorite() }
            .filter {
                when (category) {
                    null -> true
                    "" -> it.categories().isEmpty()
                    else -> category in it.categories()
                }
            }
            .toList()
    }
    val hasLibraryTitles = remember(overview, kind, favoritesOnly) {
        overview.titles().any { it.kind() == kind && (!favoritesOnly || it.favorite()) }
    }
    val selectedCards = remember(overview, selected) {
        overview.titles().filter { it.id() in selected }
    }
    val allSelectedFavorites = selectedCards.isNotEmpty() && selectedCards.all(LibraryCard::favorite)
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searching) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = {
                                Text(if (kind == MediaKind.ANIME) "library.search.anime" else "library.search.manga")
                            },
                            singleLine = true,
                            keyboardOptions = searchKeyboardOptions(),
                            keyboardActions = searchKeyboardActions(),
                            modifier = Modifier.fillMaxWidth().searchFocus(searchFocus),
                        )
                    } else {
                        Text(if (kind == MediaKind.ANIME) "ui.anime" else "ui.manga")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        searching = !searching
                        if (!searching) query = ""
                    }) {
                        Icon(Icons.Default.Search, contentDescription = "ui.search.library")
                    }
                    IconButton(onClick = {
                        update { presentation.setSort(overview.displayPreferences().sort().next()) }
                    }) {
                        Icon(Icons.Default.SortByAlpha, contentDescription = "ui.sort.library")
                    }
                    IconButton(onClick = {
                        update {
                            presentation.setDisplayMode(
                                if (overview.displayPreferences().mode() == LibraryDisplayMode.GRID) {
                                    LibraryDisplayMode.LIST
                                } else {
                                    LibraryDisplayMode.GRID
                                },
                            )
                        }
                    }) {
                        Icon(
                            if (overview.displayPreferences().mode() == LibraryDisplayMode.GRID) {
                                Icons.AutoMirrored.Filled.ViewList
                            } else {
                                Icons.Default.GridView
                            },
                            contentDescription = "ui.change.library.layout",
                        )
                    }
                    IconButton(onClick = {
                        selectionMode = !selectionMode
                        if (!selectionMode) selected = emptySet()
                    }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "ui.select.library.titles")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
        ) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (selectionMode) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        UiTranslations.format("dynamic.selected.count", LocalLanguagePack.current, selected.size),
                        fontWeight = FontWeight.SemiBold,
                    )
                    IconButton(
                        enabled = titles.isNotEmpty(),
                        onClick = {
                            selected = if (selected.size == titles.size) {
                                emptySet()
                            } else {
                                titles.mapTo(linkedSetOf()) { it.id() }
                            }
                        },
                    ) {
                        Icon(Icons.Default.SelectAll, contentDescription = "ui.all")
                    }
                    IconButton(
                        enabled = selected.isNotEmpty(),
                        onClick = {
                            update { presentation.setFavorite(selected, !allSelectedFavorites) }
                        },
                    ) {
                        Icon(
                            if (allSelectedFavorites) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (allSelectedFavorites) {
                                "ui.remove.from.library"
                            } else {
                                "ui.add.to.library"
                            },
                        )
                    }
                    IconButton(
                        enabled = selected.isNotEmpty(),
                        onClick = { categoryAction = true },
                    ) {
                        Icon(Icons.Outlined.Category, contentDescription = "ui.category")
                    }
                    IconButton(
                        enabled = selected.isNotEmpty(),
                        onClick = {
                            selected.forEach { id ->
                                runCatching {
                                    if (downloads.canEnqueue(id)) downloads.enqueue(id)
                                }.onFailure { failure ->
                                    error = failure.message ?: "Unable to enqueue a download."
                                }
                            }
                        },
                    ) {
                        Icon(Icons.Outlined.Download, contentDescription = "ui.download")
                    }
                    IconButton(
                        enabled = selected.isNotEmpty(),
                        onClick = { migrating = true },
                    ) {
                        Icon(Icons.Outlined.Sync, contentDescription = "ui.migrate")
                    }
                    IconButton(
                        enabled = selected.isNotEmpty(),
                        onClick = { confirmingDelete = true },
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "ui.delete")
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = favoritesOnly,
                    onClick = { favoritesOnly = !favoritesOnly },
                    label = { Text("ui.favorites") },
                )
            }
            if (scopedCategories.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = category == null,
                        onClick = {
                            category = null
                            update { presentation.setDefaultCategory(Optional.empty()) }
                        },
                        label = { Text("ui.all.categories") },
                    )
                    FilterChip(
                        selected = category == "",
                        onClick = {
                            category = ""
                            update { presentation.setDefaultCategory(Optional.empty()) }
                        },
                        label = { Text("ui.default") },
                    )
                    scopedCategories.forEach { value ->
                        FilterChip(
                            selected = category == value,
                            onClick = {
                                category = value
                                update { presentation.setDefaultCategory(Optional.of(value)) }
                            },
                            label = { Text(value) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (!hasLibraryTitles) {
                EmptyPage(
                    UiTranslations.format(
                        "dynamic.empty.library.kind",
                        LocalLanguagePack.current,
                        UiTranslations.translate(
                            if (kind == MediaKind.ANIME) "ui.anime" else "ui.manga",
                            LocalLanguagePack.current,
                        ).lowercase(),
                    ),
                )
            } else if (titles.isEmpty()) {
                EmptyPage("ui.no.titles.match.library.filters")
            } else {
                if (overview.displayPreferences().mode() == LibraryDisplayMode.GRID) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(overview.displayPreferences().density().minimumCardWidth()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        gridItems(
                            titles,
                            key = { it.id().value() },
                            contentType = { "library-cover" },
                        ) { card ->
                            LibraryCoverCard(
                                card,
                                null,
                                card.id() in selected,
                                selectionMode,
                                { selected = selected.toggle(card.id()) },
                                { navigate { it.openDetails(card.id()) } },
                            )
                        }
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(titles, key = { it.id().value() }) { card ->
                            LibraryTitleCard(
                                card,
                                null,
                                card.id() in selected,
                                selectionMode,
                                { selected = selected.toggle(card.id()) },
                                { navigate { it.openDetails(card.id()) } },
                            )
                        }
                    }
                }
            }
        }
    }
    if (categoryAction) {
        BulkCategoryDialog(
            categories = scopedCategories,
            dismiss = { categoryAction = false },
            create = { value ->
                try {
                    presentation.createCategory(
                        value,
                        if (kind == MediaKind.ANIME) {
                            LibraryCategoryScope.ANIME
                        } else {
                            LibraryCategoryScope.MANGA
                        },
                    )
                    presentation.addToCategory(selected, value)
                } finally {
                    revision++
                }
            },
            add = { value ->
                try {
                    presentation.addToCategory(selected, value)
                } finally {
                    revision++
                }
            },
            remove = { value ->
                try {
                    presentation.removeFromCategory(selected, value)
                } finally {
                    revision++
                }
            },
            delete = { value ->
                try {
                    presentation.deleteCategory(value)
                } finally {
                    revision++
                }
            },
        )
    }
    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = {
                Text(UiTranslations.format("dynamic.delete.titles", LocalLanguagePack.current, selected.size))
            },
            text = { Text("ui.this.removes.the.selected.titles.from.your.library") },
            confirmButton = {
                TextButton(onClick = {
                    update { presentation.deleteTitles(selected) }
                    selected = emptySet()
                    selectionMode = false
                    confirmingDelete = false
                }) { Text("ui.delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) {
                    Text("ui.cancel")
                }
            },
        )
    }
    if (migrating) {
        BulkMigrationDialog(
            cards = overview.titles().filter { it.id() in selected },
            discovery = discovery,
            dismiss = { migrating = false },
            completed = {
                migrating = false
                selected = emptySet()
                selectionMode = false
                revision++
            },
        )
    }
}

private fun Set<LibraryItemId>.toggle(id: LibraryItemId): Set<LibraryItemId> =
    if (id in this) this - id else this + id

@Composable
internal fun BulkCategoryDialog(
    categories: List<String>,
    dismiss: () -> Unit,
    create: (String) -> Unit,
    add: (String) -> Unit,
    remove: (String) -> Unit,
    delete: (String) -> Unit,
) {
    CategoryAssignmentDialog(
        categories = categories,
        assignedCategories = null,
        dismiss = dismiss,
        create = create,
        add = add,
        remove = remove,
        delete = delete,
    )
}

internal enum class CategoryActionFeedbackType {
    ADDED,
    REMOVED,
}

internal data class CategoryActionFeedback(
    val category: String,
    val type: CategoryActionFeedbackType,
    val sequence: Int,
)

@Composable
internal fun CategoryAssignmentDialog(
    categories: List<String>,
    assignedCategories: Collection<String>?,
    dismiss: () -> Unit,
    create: (String) -> Unit,
    add: (String) -> Unit,
    remove: (String) -> Unit,
    delete: (String) -> Unit,
) {
    var newCategory by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var feedback by remember { mutableStateOf<CategoryActionFeedback?>(null) }
    var feedbackSequence by remember { mutableStateOf(0) }
    var categoryPendingDeletion by remember { mutableStateOf<String?>(null) }
    val reducedMotion = LocalReducedMotion.current
    val normalizedNewCategory = newCategory.trim()
    val categoryAlreadyExists = normalizedNewCategory in categories

    fun showFeedback(category: String, type: CategoryActionFeedbackType) {
        feedbackSequence++
        feedback = CategoryActionFeedback(category, type, feedbackSequence)
    }

    fun perform(action: () -> Unit, completed: () -> Unit = {}) {
        runCatching(action)
            .onSuccess {
                error = null
                completed()
            }
            .onFailure { failure ->
                error = failure.message ?: "ui.unable.to.update.categories"
            }
    }

    fun createNewCategory() {
        if (normalizedNewCategory.isEmpty() || categoryAlreadyExists) return
        val category = normalizedNewCategory
        perform(
            action = { create(category) },
            completed = {
                newCategory = ""
                showFeedback(category, CategoryActionFeedbackType.ADDED)
            },
        )
    }

    LaunchedEffect(feedback) {
        if (feedback != null) {
            if (!reducedMotion) delay(700)
            feedback = null
        }
    }

    AlertDialog(
        onDismissRequest = dismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("ui.update.categories", modifier = Modifier.weight(1f))
                IconButton(onClick = dismiss) {
                    Icon(Icons.Default.Close, contentDescription = "ui.close")
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (assignedCategories == null) {
                        "ui.manage.categories.for.selected.titles"
                    } else {
                        "ui.select.categories.for.this.title"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = newCategory,
                    onValueChange = { newCategory = it },
                    label = { Text("ui.category.name") },
                    supportingText = if (categoryAlreadyExists) {
                        { Text("ui.category.already.exists") }
                    } else {
                        null
                    },
                    isError = categoryAlreadyExists,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { createNewCategory() }),
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(
                            enabled = normalizedNewCategory.isNotEmpty() && !categoryAlreadyExists,
                            onClick = ::createNewCategory,
                        ) {
                            Icon(
                                Icons.Default.CreateNewFolder,
                                contentDescription = "ui.create.category",
                            )
                        }
                    },
                )
                if (categories.isEmpty()) {
                    Text(
                        "ui.no.categories.for.this.library.type",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        UiTranslations.format(
                            "dynamic.categories.count",
                            LocalLanguagePack.current,
                            categories.size,
                        ),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(categories, key = { it }) { category ->
                            val assigned = assignedCategories?.let { category in it }
                            val rowFeedback = feedback?.takeIf { it.category == category }
                            val feedbackColor by animateColorAsState(
                                targetValue = when {
                                    !reducedMotion && rowFeedback?.type == CategoryActionFeedbackType.ADDED -> {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                    }
                                    !reducedMotion && rowFeedback?.type == CategoryActionFeedbackType.REMOVED -> {
                                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
                                    }
                                    assigned == true -> {
                                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                                    }
                                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                },
                                animationSpec = tween(durationMillis = if (reducedMotion) 0 else 220),
                                label = "category-action-feedback",
                            )
                            fun addToCategory() {
                                perform(
                                    action = { add(category) },
                                    completed = {
                                        showFeedback(category, CategoryActionFeedbackType.ADDED)
                                    },
                                )
                            }
                            fun removeFromCategory() {
                                perform(
                                    action = { remove(category) },
                                    completed = {
                                        showFeedback(category, CategoryActionFeedbackType.REMOVED)
                                    },
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(feedbackColor)
                                    .clickable(enabled = assigned != null) {
                                        if (assigned == true) removeFromCategory() else addToCategory()
                                    }
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    category,
                                    modifier = Modifier.weight(1f).padding(vertical = 10.dp),
                                    fontWeight = if (assigned == true) FontWeight.SemiBold else null,
                                )
                                Box(
                                    modifier = Modifier.size(24.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    val indicator = when {
                                        rowFeedback?.type == CategoryActionFeedbackType.REMOVED -> {
                                            CategoryActionFeedbackType.REMOVED
                                        }
                                        assigned == true || rowFeedback != null -> CategoryActionFeedbackType.ADDED
                                        else -> null
                                    }
                                    Crossfade(
                                        targetState = indicator,
                                        animationSpec = tween(if (reducedMotion) 0 else 180),
                                        label = "category-state-indicator",
                                    ) { state ->
                                        if (state != null) {
                                            Icon(
                                                if (state == CategoryActionFeedbackType.REMOVED) {
                                                    Icons.Default.RemoveCircle
                                                } else {
                                                    Icons.Default.CheckCircle
                                                },
                                                contentDescription = null,
                                                tint = if (state == CategoryActionFeedbackType.REMOVED) {
                                                    MaterialTheme.colorScheme.error
                                                } else {
                                                    MaterialTheme.colorScheme.primary
                                                },
                                            )
                                        }
                                    }
                                }
                                if (assigned == null) {
                                    IconButton(onClick = ::addToCategory) {
                                        Icon(Icons.Default.AddCircle, contentDescription = "ui.add")
                                    }
                                    IconButton(onClick = ::removeFromCategory) {
                                        Icon(Icons.Default.RemoveCircle, contentDescription = "ui.remove")
                                    }
                                } else {
                                    IconButton(
                                        onClick = {
                                            if (assigned) removeFromCategory() else addToCategory()
                                        },
                                    ) {
                                        Icon(
                                            if (assigned) Icons.Default.RemoveCircle else Icons.Default.AddCircle,
                                            contentDescription = if (assigned) "ui.remove" else "ui.add",
                                        )
                                    }
                                }
                                IconButton(onClick = { categoryPendingDeletion = category }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "ui.delete.category",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {},
    )
    categoryPendingDeletion?.let { category ->
        AlertDialog(
            onDismissRequest = { categoryPendingDeletion = null },
            title = {
                Text(UiTranslations.format("dynamic.delete.category", LocalLanguagePack.current, category))
            },
            text = { Text("ui.titles.stay.in.the.library.and.lose.this.category.assignment") },
            confirmButton = {
                IconButton(
                    onClick = {
                        perform(
                            action = { delete(category) },
                            completed = { categoryPendingDeletion = null },
                        )
                    },
                ) {
                    Icon(
                        Icons.Default.DeleteForever,
                        contentDescription = "ui.delete",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                IconButton(onClick = { categoryPendingDeletion = null }) {
                    Icon(Icons.Default.Close, contentDescription = "ui.cancel")
                }
            },
        )
    }
}

@Composable
internal fun BulkMigrationDialog(
    cards: List<LibraryCard>,
    discovery: DiscoveryPresentation,
    dismiss: () -> Unit,
    completed: () -> Unit,
) {
    var index by remember(cards) { mutableStateOf(0) }
    var candidates by remember(cards) { mutableStateOf(listOf<SourceCatalogueItem>()) }
    var targetSource by remember(cards) { mutableStateOf<SourceId?>(null) }
    var loading by remember(cards) { mutableStateOf(false) }
    var error by remember(cards) { mutableStateOf<String?>(null) }
    val language = LocalLanguagePack.current
    val scope = rememberCrashSafeCoroutineScope()
    val current = cards.getOrNull(index)
    if (current == null) {
        completed()
        return
    }
    val contentKind = if (current.kind() == MediaKind.ANIME) {
        SourceContentKind.ANIME
    } else {
        SourceContentKind.MANGA
    }
    val sources = remember(current.id()) {
        discovery.sourceSections(contentKind).flatMap { it.sources() }
    }
    fun selectSource(sourceId: SourceId) {
        targetSource = sourceId
        loading = true
        error = null
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    discovery.migrationCandidates(current.id(), sourceId, 20)
                }
            }.onSuccess { values ->
                candidates = values
                if (values.isEmpty()) error = UiTranslations.translate("ui.no.migration.candidates", language)
            }.onFailure { failure ->
                error = failure.message ?: UiTranslations.translate("ui.unable.load.migration.candidates", language)
            }
            loading = false
        }
    }
    fun migrate(target: SourceCatalogueItem) {
        loading = true
        error = null
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { discovery.migrate(current.id(), target) }
            }.onSuccess {
                index++
                targetSource = null
                candidates = emptyList()
                if (index >= cards.size) completed()
            }.onFailure { failure ->
                error = failure.message ?: UiTranslations.format(
                    "dynamic.unable.migrate",
                    language,
                    current.title(),
                )
            }
            loading = false
        }
    }
    AlertDialog(
        onDismissRequest = dismiss,
        title = {
            Text(UiTranslations.format("dynamic.migrate.sequence", language, index + 1, cards.size))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(current.title(), fontWeight = FontWeight.SemiBold)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (loading) {
                    Text("ui.loading")
                } else if (targetSource == null) {
                    Text("ui.choose.a.target.source")
                    sources.take(12).forEach { source ->
                        TextButton(onClick = { selectSource(source.id()) }) {
                            Text("${source.displayName()} · ${source.languageTag()}")
                        }
                    }
                } else {
                    TextButton(onClick = {
                        targetSource = null
                        candidates = emptyList()
                    }) { Text("ui.change.source") }
                    candidates.take(12).forEach { candidate ->
                        TextButton(onClick = { migrate(candidate) }) {
                            Text(candidate.title())
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = dismiss) { Text("ui.cancel") }
        },
    )
}

internal fun LibraryDisplayDensity.next(): LibraryDisplayDensity = when (this) {
    LibraryDisplayDensity.COMPACT -> LibraryDisplayDensity.COMFORTABLE
    LibraryDisplayDensity.COMFORTABLE -> LibraryDisplayDensity.RELAXED
    LibraryDisplayDensity.RELAXED -> LibraryDisplayDensity.COMPACT
}

internal fun LibraryDisplayDensity.label(): String = name.lowercase().replaceFirstChar(Char::uppercase)

internal fun LibraryDisplayDensity.minimumCardWidth() = when (this) {
    LibraryDisplayDensity.COMPACT -> 140.dp
    LibraryDisplayDensity.COMFORTABLE -> 180.dp
    LibraryDisplayDensity.RELAXED -> 240.dp
}

internal fun LibrarySort.next(): LibrarySort = when (this) {
    LibrarySort.TITLE_ASCENDING -> LibrarySort.TITLE_DESCENDING
    LibrarySort.TITLE_DESCENDING -> LibrarySort.ADDED_NEWEST
    LibrarySort.ADDED_NEWEST -> LibrarySort.ADDED_OLDEST
    LibrarySort.ADDED_OLDEST -> LibrarySort.TITLE_ASCENDING
}

internal fun LibrarySort.label(): String = when (this) {
    LibrarySort.TITLE_ASCENDING -> "A-Z"
    LibrarySort.TITLE_DESCENDING -> "Z-A"
    LibrarySort.ADDED_NEWEST -> "Newest"
    LibrarySort.ADDED_OLDEST -> "Oldest"
}

@Composable
internal fun LibraryTitleCard(
    card: LibraryCard,
    remainingEpisodeCount: Int?,
    selected: Boolean,
    selectionMode: Boolean,
    select: () -> Unit,
    openDetails: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = if (selectionMode) select else openDetails),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectionMode) {
                Checkbox(selected, onCheckedChange = { select() })
            }
            RemoteArtwork(
                card.artwork().orElse(null),
                card.title(),
                modifier = Modifier.size(width = 58.dp, height = 82.dp)
                    .clip(RoundedCornerShape(9.dp)),
            )
            Spacer(Modifier.width(14.dp))
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
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "ui.in.library",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp).size(22.dp),
                )
            }
            RemainingEpisodeBadge(remainingEpisodeCount)
        }
    }
}

@Composable
internal fun LibraryCoverCard(
    card: LibraryCard,
    remainingEpisodeCount: Int?,
    selected: Boolean,
    selectionMode: Boolean,
    select: () -> Unit,
    openDetails: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = if (selectionMode) select else openDetails),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Box {
            RemoteArtwork(
                card.artwork().orElse(null),
                card.title(),
                modifier = Modifier.fillMaxWidth().aspectRatio(0.68f),
            )
            Surface(
                color = Color.Black.copy(alpha = 0.72f),
                modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
            ) {
                Text(
                    card.title(),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                )
            }
            if (selectionMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { select() },
                    modifier = Modifier.align(Alignment.TopStart),
                )
            }
            Box(modifier = Modifier.align(Alignment.TopEnd)) {
                RemainingEpisodeBadge(remainingEpisodeCount)
            }
        }
    }
}

@Composable
internal fun RemainingEpisodeBadge(remainingEpisodeCount: Int?) {
    if (remainingEpisodeCount == null || remainingEpisodeCount <= 0) return
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            remainingEpisodeCount.toString(),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

internal fun librarySummary(overview: LibraryOverview): String =
    "${overview.titles().size} titles | ${overview.favoriteCount()} favorites | " +
        "${overview.categories().size} categories"

internal fun cardMetadata(card: LibraryCard): String {
    val categoryText = joined(card.categories())
    val progressText = card.progress().map { progress(card.kind(), it) }.orElse("Not started")
    return "${formatEnum(card.kind())} | $categoryText | $progressText"
}

internal fun progress(kind: MediaKind, progress: LibraryProgress): String {
    if (progress.extent() == LibraryProgress.UNKNOWN_EXTENT) {
        return if (kind == MediaKind.ANIME && progress.position() >= 1_000L) {
            formatMediaPosition(progress.position())
        } else {
            progress.position().toString()
        }
    }
    val percentage = (progress.completion().orElse(0.0) * 100.0).roundToInt()
    val position = if (kind == MediaKind.ANIME && progress.extent() >= 1_000L) {
        formatMediaPosition(progress.position())
    } else {
        progress.position().toString()
    }
    val extent = if (kind == MediaKind.ANIME && progress.extent() >= 1_000L) {
        formatMediaPosition(progress.extent())
    } else {
        progress.extent().toString()
    }
    return "$position / $extent ($percentage%)"
}

internal fun joined(values: List<String>): String =
    if (values.isEmpty()) "None" else values.joinToString(", ")

internal fun formatEnum(value: Enum<*>): String = value.name
    .replace('_', ' ')
    .lowercase(Locale.ROOT)
    .replaceFirstChar(Char::uppercase)
