package fr.vriege.anilib.platform.compose

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorFilter as ComposeColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.vriege.anilib.feature.library.LibraryItemId
import fr.vriege.anilib.feature.reader.ReadingDirection
import fr.vriege.anilib.feature.reader.ReaderColorFilter
import fr.vriege.anilib.feature.reader.ReaderDisplayPreferences
import fr.vriege.anilib.feature.reader.ReaderInteractionAction
import fr.vriege.anilib.feature.reader.ReaderInteractionPreferences
import fr.vriege.anilib.feature.reader.ReaderOrientationPolicy
import fr.vriege.anilib.feature.reader.ReaderPageTransition
import fr.vriege.anilib.feature.reader.ReaderRotation
import fr.vriege.anilib.feature.reader.ReaderScaleMode
import fr.vriege.anilib.feature.reader.ui.ReaderController
import fr.vriege.anilib.feature.source.SourceContentUnit
import fr.vriege.anilib.feature.source.SourceContentUnitId
import kotlin.math.roundToInt

@Composable
internal fun ReaderScreen(
    controller: ReaderController,
    pageDecoder: (ByteArray) -> ImageBitmap?,
    applyOrientationPolicy: (ReaderOrientationPolicy) -> Unit,
    downloadContent: (LibraryItemId, SourceContentUnitId) -> Unit,
    closeReader: () -> Unit,
) {
    var revision by remember(controller) { mutableIntStateOf(0) }
    var controlsVisible by remember(controller) { mutableStateOf(true) }
    var zoomed by remember(controller) { mutableStateOf(false) }
    var settingsMenu by remember(controller) { mutableStateOf(false) }
    var chapterMenu by remember(controller) { mutableStateOf(false) }
    var readerMenu by remember(controller) { mutableStateOf(false) }
    var actionMessage by remember(controller) { mutableStateOf<String?>(null) }
    var splitSecondHalf by remember(controller) { mutableStateOf(false) }
    var interactions by remember(controller) { mutableStateOf(controller.interactions()) }
    var display by remember(controller) { mutableStateOf(controller.display()) }
    var titleDisplayOverride by remember(controller) { mutableStateOf(controller.hasDisplayOverride()) }
    var readContentIds by remember(controller) { mutableStateOf(controller.readContentIds()) }
    val snapshot = remember(controller, revision) { controller.snapshot() }
    val contentUnits = remember(controller, revision) {
        runCatching { controller.contentUnits() }.getOrDefault(emptyList())
    }
    val decodedPage = remember(controller, snapshot.currentPageIndex()) {
        runCatching { pageDecoder(controller.currentPage()) }
    }
    val decodedAdjacentPage = remember(controller, snapshot.currentPageIndex(), display.dualPage()) {
        if (display.dualPage() && snapshot.currentPageIndex() + 1 < snapshot.pageCount()) {
            runCatching { pageDecoder(controller.page(snapshot.currentPageIndex() + 1)) }
        } else {
            null
        }
    }
    DisposableEffect(display.orientationPolicy(), applyOrientationPolicy) {
        applyOrientationPolicy(display.orientationPolicy())
        onDispose { applyOrientationPolicy(ReaderOrientationPolicy.SYSTEM) }
    }

    fun move(previous: Boolean) {
        if (display.splitPages()) {
            if (previous && splitSecondHalf) {
                splitSecondHalf = false
                return
            }
            if (!previous && !splitSecondHalf) {
                splitSecondHalf = true
                return
            }
        }
        val moved = if (display.dualPage()) {
            val delta = if (previous) -2 else 2
            val target = snapshot.currentPageIndex() + delta
            if (target in 0 until snapshot.pageCount()) {
                controller.goToPage(target)
                true
            } else {
                false
            }
        } else if (previous) {
            controller.previousPage()
        } else {
            controller.nextPage()
        }
        if (moved) splitSecondHalf = previous && display.splitPages()
        if (moved) revision++
    }

    fun execute(action: ReaderInteractionAction) {
        when (action) {
            ReaderInteractionAction.PREVIOUS_PAGE -> move(true)
            ReaderInteractionAction.NEXT_PAGE -> move(false)
            ReaderInteractionAction.TOGGLE_CONTROLS -> controlsVisible = !controlsVisible
            ReaderInteractionAction.TOGGLE_ZOOM -> zoomed = !zoomed
            ReaderInteractionAction.OPEN_MENU -> settingsMenu = true
            ReaderInteractionAction.NONE -> Unit
        }
    }

    fun openContentUnit(contentUnitId: SourceContentUnitId) {
        controller.openContentUnit(contentUnitId)
        splitSecondHalf = false
        actionMessage = null
        revision++
    }

    fun setRead(contentUnitId: SourceContentUnitId, read: Boolean) {
        controller.setContentRead(contentUnitId, read)
        readContentIds = controller.readContentIds()
    }

    fun download(contentUnitId: SourceContentUnitId) {
        runCatching { downloadContent(snapshot.libraryItemId(), contentUnitId) }
            .onSuccess { actionMessage = "Download queued." }
            .onFailure { actionMessage = it.message ?: "The download could not be queued." }
    }

    var drag by remember(controller) { mutableStateOf(Offset.Zero) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(interactions, snapshot.direction()) {
                detectDragGestures(
                    onDragStart = { drag = Offset.Zero },
                    onDrag = { change, amount ->
                        change.consume()
                        drag += amount
                    },
                    onDragEnd = {
                        val horizontal = kotlin.math.abs(drag.x) > kotlin.math.abs(drag.y)
                        val action = if (horizontal && kotlin.math.abs(drag.x) >= 48f) {
                            if (drag.x < 0f) interactions.swipeLeft() else interactions.swipeRight()
                        } else if (!horizontal && kotlin.math.abs(drag.y) >= 48f) {
                            if (drag.y < 0f) interactions.swipeUp() else interactions.swipeDown()
                        } else {
                            ReaderInteractionAction.NONE
                        }
                        execute(horizontalAction(action, snapshot.direction()))
                    },
                )
            },
    ) {
        val reducedMotion = LocalReducedMotion.current
        decodedPage.getOrNull()?.let { image ->
            val frame = ReaderPageFrame(
                image,
                decodedAdjacentPage?.getOrNull(),
                snapshot.currentPageIndex(),
                splitSecondHalf,
            )
            AnimatedContent(
                targetState = frame,
                transitionSpec = {
                    readerTransition(if (reducedMotion) ReaderPageTransition.NONE else display.transition())
                },
                label = "reader-page",
            ) { current ->
                ReaderPages(
                    primary = current.primary,
                    adjacent = current.adjacent,
                    pageIndex = current.pageIndex,
                    direction = snapshot.direction(),
                    display = display,
                    zoomed = zoomed,
                    splitSecondHalf = current.splitSecondHalf,
                )
            }
        } ?: ReaderPageError(decodedPage.exceptionOrNull()?.message) { revision++ }

        ReaderTapZones(
            direction = snapshot.direction(),
            interactions = interactions,
            execute = ::execute,
        )

        if (controlsVisible) {
            ReaderTopBar(
                title = snapshot.title(),
                contentUnit = snapshot.contentUnit().title(),
                closeReader = closeReader,
                openSettings = { settingsMenu = true },
                openChapters = { chapterMenu = true },
                openMenu = { readerMenu = true },
            )
            ReaderBottomBar(
                pageIndex = snapshot.currentPageIndex(),
                pageCount = snapshot.pageCount(),
                direction = snapshot.direction(),
                goToPage = { index ->
                    controller.goToPage(index)
                    splitSecondHalf = false
                    revision++
                },
                changeDirection = { direction ->
                    controller.setDirection(direction)
                    revision++
                },
                splitSecondHalf = splitSecondHalf,
                dualPage = display.dualPage(),
                splitPages = display.splitPages(),
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        if (settingsMenu) {
            ReaderSettingsDialog(
                interactions = interactions,
                display = display,
                titleOverride = titleDisplayOverride,
                updateInteractions = {
                    controller.setInteractions(it)
                    interactions = it
                },
                updateDisplay = {
                    controller.setDisplay(it, titleDisplayOverride)
                    display = it
                    splitSecondHalf = false
                },
                setTitleOverride = { enabled ->
                    if (enabled) {
                        controller.setDisplay(display, true)
                        titleDisplayOverride = true
                    } else {
                        controller.clearDisplayOverride()
                        titleDisplayOverride = false
                        display = controller.display()
                    }
                },
                close = { settingsMenu = false },
            )
        }
        if (chapterMenu) {
            ReaderChapterDialog(
                units = contentUnits,
                current = snapshot.contentUnit().id(),
                readContentIds = readContentIds,
                open = {
                    openContentUnit(it)
                    chapterMenu = false
                },
                setRead = ::setRead,
                download = ::download,
                close = { chapterMenu = false },
            )
        }
        if (readerMenu) {
            ReaderMenuDialog(
                current = snapshot.contentUnit(),
                read = readContentIds.contains(snapshot.contentUnit().id().value()),
                message = actionMessage,
                previousChapter = {
                    if (controller.previousContentUnit()) revision++
                    readerMenu = false
                },
                nextChapter = {
                    if (controller.nextContentUnit()) revision++
                    readerMenu = false
                },
                setRead = { setRead(snapshot.contentUnit().id(), it) },
                download = { download(snapshot.contentUnit().id()) },
                openChapters = {
                    readerMenu = false
                    chapterMenu = true
                },
                openSettings = {
                    readerMenu = false
                    settingsMenu = true
                },
                close = { readerMenu = false },
            )
        }
    }
}

private data class ReaderPageFrame(
    val primary: ImageBitmap,
    val adjacent: ImageBitmap?,
    val pageIndex: Int,
    val splitSecondHalf: Boolean,
)

private fun readerTransition(transition: ReaderPageTransition): ContentTransform = when (transition) {
    ReaderPageTransition.NONE -> EnterTransition.None togetherWith ExitTransition.None
    ReaderPageTransition.FADE -> fadeIn(tween(180)) togetherWith fadeOut(tween(180))
    ReaderPageTransition.SLIDE -> {
        (slideInHorizontally(tween(220)) { it / 3 } + fadeIn(tween(180))) togetherWith
            (slideOutHorizontally(tween(220)) { -it / 3 } + fadeOut(tween(180)))
    }
}

@Composable
private fun ReaderPages(
    primary: ImageBitmap,
    adjacent: ImageBitmap?,
    pageIndex: Int,
    direction: ReadingDirection,
    display: ReaderDisplayPreferences,
    zoomed: Boolean,
    splitSecondHalf: Boolean,
) {
    val spacing = if (direction == ReadingDirection.WEBTOON) display.webtoonSpacingDp().dp else 0.dp
    val modifier = Modifier.fillMaxSize().padding(vertical = spacing / 2)
    if (display.dualPage() && adjacent != null) {
        val pages = if (direction == ReadingDirection.RIGHT_TO_LEFT) {
            listOf(adjacent to pageIndex + 1, primary to pageIndex)
        } else {
            listOf(primary to pageIndex, adjacent to pageIndex + 1)
        }
        Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(spacing)) {
            pages.forEach { (image, index) ->
                ReaderPageImage(
                    image = image,
                    pageIndex = index,
                    display = display,
                    zoomed = zoomed,
                    splitSecondHalf = false,
                    direction = direction,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
    } else {
        ReaderPageImage(
            image = primary,
            pageIndex = pageIndex,
            display = display,
            zoomed = zoomed,
            splitSecondHalf = splitSecondHalf,
            direction = direction,
            modifier = modifier,
        )
    }
}

@Composable
private fun ReaderPageImage(
    image: ImageBitmap,
    pageIndex: Int,
    display: ReaderDisplayPreferences,
    zoomed: Boolean,
    splitSecondHalf: Boolean,
    direction: ReadingDirection,
    modifier: Modifier,
) {
    val splitScale = if (display.splitPages()) 2f else 1f
    val cropScale = if (display.cropBorders()) 1.06f else 1f
    val zoomScale = if (zoomed) 2f else 1f
    val secondVisualHalf = splitSecondHalf.xor(direction == ReadingDirection.RIGHT_TO_LEFT)
    val origin = when {
        !display.splitPages() -> TransformOrigin.Center
        secondVisualHalf -> TransformOrigin(1f, 0.5f)
        else -> TransformOrigin(0f, 0.5f)
    }
    Box(modifier = modifier.clipToBounds()) {
        Image(
            bitmap = image,
            contentDescription = "Page ${pageIndex + 1}",
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = splitScale * cropScale * zoomScale,
                    scaleY = splitScale * cropScale * zoomScale,
                    rotationZ = display.rotation().degrees().toFloat(),
                    transformOrigin = origin,
                ),
            contentScale = contentScale(display.scaleMode()),
            colorFilter = readerColorFilter(display.colorFilter(), display.brightnessPercent()),
        )
    }
}

private fun contentScale(mode: ReaderScaleMode): ContentScale = when (mode) {
    ReaderScaleMode.FIT -> ContentScale.Fit
    ReaderScaleMode.FILL -> ContentScale.Crop
    ReaderScaleMode.FIT_WIDTH -> ContentScale.FillWidth
    ReaderScaleMode.FIT_HEIGHT -> ContentScale.FillHeight
    ReaderScaleMode.ORIGINAL -> ContentScale.None
}

private fun readerColorFilter(
    filter: ReaderColorFilter,
    brightnessPercent: Int,
): ComposeColorFilter? {
    if (filter == ReaderColorFilter.NONE && brightnessPercent == 100) {
        return null
    }
    val values = when (filter) {
        ReaderColorFilter.NONE -> floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
        ReaderColorFilter.GRAYSCALE -> floatArrayOf(
            0.213f, 0.715f, 0.072f, 0f, 0f,
            0.213f, 0.715f, 0.072f, 0f, 0f,
            0.213f, 0.715f, 0.072f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
        ReaderColorFilter.SEPIA -> floatArrayOf(
            0.393f, 0.769f, 0.189f, 0f, 0f,
            0.349f, 0.686f, 0.168f, 0f, 0f,
            0.272f, 0.534f, 0.131f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
        ReaderColorFilter.INVERTED -> floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f,
        )
    }
    val brightness = brightnessPercent / 100f
    for (row in 0..2) {
        for (column in 0..2) {
            val index = row * 5 + column
            values[index] *= brightness
        }
    }
    return ComposeColorFilter.colorMatrix(ColorMatrix(values))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReaderTapZones(
    direction: ReadingDirection,
    interactions: ReaderInteractionPreferences,
    execute: (ReaderInteractionAction) -> Unit,
) {
    if (direction == ReadingDirection.VERTICAL || direction == ReadingDirection.WEBTOON) {
        Column(modifier = Modifier.fillMaxSize()) {
            ReaderTapZone(Modifier.weight(0.36f).fillMaxWidth(), interactions.topTap(), interactions, execute)
            ReaderTapZone(Modifier.weight(0.28f).fillMaxWidth(), interactions.centerTap(), interactions, execute)
            ReaderTapZone(Modifier.weight(0.36f).fillMaxWidth(), interactions.bottomTap(), interactions, execute)
        }
    } else {
        val left = horizontalAction(interactions.leftTap(), direction)
        val right = horizontalAction(interactions.rightTap(), direction)
        Row(modifier = Modifier.fillMaxSize()) {
            ReaderTapZone(Modifier.weight(0.36f).fillMaxHeight(), left, interactions, execute)
            ReaderTapZone(Modifier.weight(0.28f).fillMaxHeight(), interactions.centerTap(), interactions, execute)
            ReaderTapZone(Modifier.weight(0.36f).fillMaxHeight(), right, interactions, execute)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReaderTapZone(
    modifier: Modifier,
    action: ReaderInteractionAction,
    interactions: ReaderInteractionPreferences,
    execute: (ReaderInteractionAction) -> Unit,
) {
    Box(
        modifier = modifier.combinedClickable(
            onClick = { execute(action) },
            onDoubleClick = { execute(interactions.doubleTap()) },
            onLongClick = { execute(interactions.longPress()) },
        ),
    )
}

@Composable
private fun ReaderTopBar(
    title: String,
    contentUnit: String,
    closeReader: () -> Unit,
    openSettings: () -> Unit,
    openChapters: () -> Unit,
    openMenu: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.78f))
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = closeReader) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close reader", tint = Color.White)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                contentUnit,
                color = Color.White.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = openSettings) {
            Icon(Icons.Default.Settings, contentDescription = "Reader settings", tint = Color.White)
        }
        IconButton(onClick = openChapters) {
            Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Chapters", tint = Color.White)
        }
        IconButton(onClick = openMenu) {
            Icon(Icons.Default.MoreVert, contentDescription = "Reader menu", tint = Color.White)
        }
    }
}

private enum class InteractionSlot(val label: String) {
    LEFT_TAP("Left tap"),
    CENTER_TAP("Center tap"),
    RIGHT_TAP("Right tap"),
    TOP_TAP("Top tap"),
    BOTTOM_TAP("Bottom tap"),
    SWIPE_LEFT("Swipe left"),
    SWIPE_RIGHT("Swipe right"),
    SWIPE_UP("Swipe up"),
    SWIPE_DOWN("Swipe down"),
    DOUBLE_TAP("Double tap"),
    LONG_PRESS("Long press"),
}

@Composable
private fun ReaderChapterDialog(
    units: List<SourceContentUnit>,
    current: SourceContentUnitId,
    readContentIds: Set<String>,
    open: (SourceContentUnitId) -> Unit,
    setRead: (SourceContentUnitId, Boolean) -> Unit,
    download: (SourceContentUnitId) -> Unit,
    close: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Chapters") },
        text = {
            if (units.isEmpty()) {
                Text("No chapter list is available.")
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 520.dp)) {
                    items(units.size) { index ->
                        val unit = units[index]
                        val read = readContentIds.contains(unit.id().value())
                        Column(modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = { open(unit.id()) }) {
                                Text(if (unit.id() == current) "▶ ${unit.title()}" else unit.title())
                            }
                            Row {
                                TextButton(onClick = { setRead(unit.id(), !read) }) {
                                    Text(if (read) "Mark unread" else "Mark read")
                                }
                                TextButton(onClick = { download(unit.id()) }) { Text("Download") }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = close) { Text("Close") } },
    )
}

@Composable
private fun ReaderMenuDialog(
    current: SourceContentUnit,
    read: Boolean,
    message: String?,
    previousChapter: () -> Unit,
    nextChapter: () -> Unit,
    setRead: (Boolean) -> Unit,
    download: () -> Unit,
    openChapters: () -> Unit,
    openSettings: () -> Unit,
    close: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = close,
        title = { Text(current.title()) },
        text = {
            Column {
                TextButton(onClick = previousChapter) { Text("Previous chapter") }
                TextButton(onClick = nextChapter) { Text("Next chapter") }
                TextButton(onClick = { setRead(!read) }) {
                    Text(if (read) "Mark chapter unread" else "Mark chapter read")
                }
                TextButton(onClick = download) { Text("Download chapter") }
                TextButton(onClick = openChapters) { Text("All chapters") }
                TextButton(onClick = openSettings) { Text("Reader settings") }
                if (!message.isNullOrBlank()) {
                    Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = close) { Text("Close") } },
    )
}

@Composable
private fun ReaderSettingsDialog(
    interactions: ReaderInteractionPreferences,
    display: ReaderDisplayPreferences,
    titleOverride: Boolean,
    updateInteractions: (ReaderInteractionPreferences) -> Unit,
    updateDisplay: (ReaderDisplayPreferences) -> Unit,
    setTitleOverride: (Boolean) -> Unit,
    close: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Reader settings") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 520.dp)) {
                item { Text("Display", fontWeight = FontWeight.SemiBold) }
                item {
                    TextButton(onClick = { setTitleOverride(!titleOverride) }) {
                        Text("Apply to this title: ${enabledLabel(titleOverride)}")
                    }
                }
                item {
                    TextButton(onClick = {
                        updateDisplay(withScaleMode(display, nextScaleMode(display.scaleMode())))
                    }) {
                        Text("Scale: ${display.scaleMode().name.replace('_', ' ').lowercase()}")
                    }
                }
                item {
                    TextButton(onClick = { updateDisplay(withCropBorders(display, !display.cropBorders())) }) {
                        Text("Crop borders: ${enabledLabel(display.cropBorders())}")
                    }
                }
                item {
                    TextButton(onClick = { updateDisplay(withSplitPages(display, !display.splitPages())) }) {
                        Text("Split pages: ${enabledLabel(display.splitPages())}")
                    }
                }
                item {
                    TextButton(onClick = { updateDisplay(withRotation(display, nextRotation(display.rotation()))) }) {
                        Text("Rotation: ${display.rotation().degrees()}°")
                    }
                }
                item {
                    TextButton(onClick = { updateDisplay(withDualPage(display, !display.dualPage())) }) {
                        Text("Dual page: ${enabledLabel(display.dualPage())}")
                    }
                }
                item {
                    TextButton(onClick = {
                        updateDisplay(withWebtoonSpacing(display, nextSpacing(display.webtoonSpacingDp())))
                    }) {
                        Text("Webtoon spacing: ${display.webtoonSpacingDp()} dp")
                    }
                }
                item {
                    TextButton(onClick = {
                        updateDisplay(withColorFilter(display, nextColorFilter(display.colorFilter())))
                    }) {
                        Text("Color filter: ${display.colorFilter().name.lowercase()}")
                    }
                }
                item {
                    TextButton(onClick = {
                        updateDisplay(withBrightness(display, nextBrightness(display.brightnessPercent())))
                    }) {
                        Text("Brightness: ${display.brightnessPercent()}%")
                    }
                }
                item {
                    TextButton(onClick = {
                        updateDisplay(withTransition(display, nextTransition(display.transition())))
                    }) {
                        Text("Transition: ${display.transition().name.lowercase()}")
                    }
                }
                item {
                    TextButton(onClick = {
                        updateDisplay(withOrientation(display, nextOrientation(display.orientationPolicy())))
                    }) {
                        Text("Orientation: ${display.orientationPolicy().name.lowercase()}")
                    }
                }
                item { Text("Interactions", fontWeight = FontWeight.SemiBold) }
                items(InteractionSlot.entries.size) { index ->
                    val slot = InteractionSlot.entries[index]
                    val action = interaction(interactions, slot)
                    TextButton(onClick = {
                        updateInteractions(withInteraction(interactions, slot, nextAction(action)))
                    }) {
                        Text("${slot.label}: ${action.name.replace('_', ' ').lowercase()}")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = close) { Text("Done") } },
        dismissButton = {
            TextButton(onClick = {
                updateDisplay(ReaderDisplayPreferences.defaults())
                updateInteractions(ReaderInteractionPreferences.defaults())
            }) { Text("Reset") }
        },
    )
}

private fun withScaleMode(current: ReaderDisplayPreferences, value: ReaderScaleMode) = ReaderDisplayPreferences(
    value,
    current.cropBorders(),
    current.splitPages(),
    current.rotation(),
    current.dualPage(),
    current.webtoonSpacingDp(),
    current.colorFilter(),
    current.brightnessPercent(),
    current.transition(),
    current.orientationPolicy(),
)

private fun withCropBorders(current: ReaderDisplayPreferences, value: Boolean) = ReaderDisplayPreferences(
    current.scaleMode(),
    value,
    current.splitPages(),
    current.rotation(),
    current.dualPage(),
    current.webtoonSpacingDp(),
    current.colorFilter(),
    current.brightnessPercent(),
    current.transition(),
    current.orientationPolicy(),
)

private fun withSplitPages(current: ReaderDisplayPreferences, value: Boolean) = ReaderDisplayPreferences(
    current.scaleMode(),
    current.cropBorders(),
    value,
    current.rotation(),
    if (value) false else current.dualPage(),
    current.webtoonSpacingDp(),
    current.colorFilter(),
    current.brightnessPercent(),
    current.transition(),
    current.orientationPolicy(),
)

private fun withRotation(current: ReaderDisplayPreferences, value: ReaderRotation) = ReaderDisplayPreferences(
    current.scaleMode(),
    current.cropBorders(),
    current.splitPages(),
    value,
    current.dualPage(),
    current.webtoonSpacingDp(),
    current.colorFilter(),
    current.brightnessPercent(),
    current.transition(),
    current.orientationPolicy(),
)

private fun withDualPage(current: ReaderDisplayPreferences, value: Boolean) = ReaderDisplayPreferences(
    current.scaleMode(),
    current.cropBorders(),
    if (value) false else current.splitPages(),
    current.rotation(),
    value,
    current.webtoonSpacingDp(),
    current.colorFilter(),
    current.brightnessPercent(),
    current.transition(),
    current.orientationPolicy(),
)

private fun withWebtoonSpacing(current: ReaderDisplayPreferences, value: Int) = ReaderDisplayPreferences(
    current.scaleMode(),
    current.cropBorders(),
    current.splitPages(),
    current.rotation(),
    current.dualPage(),
    value,
    current.colorFilter(),
    current.brightnessPercent(),
    current.transition(),
    current.orientationPolicy(),
)

private fun withColorFilter(current: ReaderDisplayPreferences, value: ReaderColorFilter) = ReaderDisplayPreferences(
    current.scaleMode(),
    current.cropBorders(),
    current.splitPages(),
    current.rotation(),
    current.dualPage(),
    current.webtoonSpacingDp(),
    value,
    current.brightnessPercent(),
    current.transition(),
    current.orientationPolicy(),
)

private fun withBrightness(current: ReaderDisplayPreferences, value: Int) = ReaderDisplayPreferences(
    current.scaleMode(),
    current.cropBorders(),
    current.splitPages(),
    current.rotation(),
    current.dualPage(),
    current.webtoonSpacingDp(),
    current.colorFilter(),
    value,
    current.transition(),
    current.orientationPolicy(),
)

private fun withTransition(
    current: ReaderDisplayPreferences,
    value: ReaderPageTransition,
) = ReaderDisplayPreferences(
    current.scaleMode(),
    current.cropBorders(),
    current.splitPages(),
    current.rotation(),
    current.dualPage(),
    current.webtoonSpacingDp(),
    current.colorFilter(),
    current.brightnessPercent(),
    value,
    current.orientationPolicy(),
)

private fun withOrientation(
    current: ReaderDisplayPreferences,
    value: ReaderOrientationPolicy,
) = ReaderDisplayPreferences(
    current.scaleMode(),
    current.cropBorders(),
    current.splitPages(),
    current.rotation(),
    current.dualPage(),
    current.webtoonSpacingDp(),
    current.colorFilter(),
    current.brightnessPercent(),
    current.transition(),
    value,
)

private fun nextScaleMode(value: ReaderScaleMode): ReaderScaleMode {
    val values = ReaderScaleMode.entries
    return values[(value.ordinal + 1) % values.size]
}

private fun nextRotation(value: ReaderRotation): ReaderRotation {
    val values = ReaderRotation.entries
    return values[(value.ordinal + 1) % values.size]
}

private fun nextSpacing(value: Int): Int {
    val values = intArrayOf(0, 4, 8, 16, 24, 32, 48, 64, 96)
    val index = values.indexOf(value)
    return values[(index + 1) % values.size]
}

private fun nextColorFilter(value: ReaderColorFilter): ReaderColorFilter {
    val values = ReaderColorFilter.entries
    return values[(value.ordinal + 1) % values.size]
}

private fun nextBrightness(value: Int): Int {
    val values = intArrayOf(25, 50, 75, 100, 125, 150, 175, 200)
    val index = values.indexOf(value)
    return values[(index + 1) % values.size]
}

private fun nextTransition(value: ReaderPageTransition): ReaderPageTransition {
    val values = ReaderPageTransition.entries
    return values[(value.ordinal + 1) % values.size]
}

private fun nextOrientation(value: ReaderOrientationPolicy): ReaderOrientationPolicy {
    val values = ReaderOrientationPolicy.entries
    return values[(value.ordinal + 1) % values.size]
}

private fun enabledLabel(value: Boolean) = if (value) "on" else "off"

private fun interaction(
    preferences: ReaderInteractionPreferences,
    slot: InteractionSlot,
): ReaderInteractionAction = when (slot) {
    InteractionSlot.LEFT_TAP -> preferences.leftTap()
    InteractionSlot.CENTER_TAP -> preferences.centerTap()
    InteractionSlot.RIGHT_TAP -> preferences.rightTap()
    InteractionSlot.TOP_TAP -> preferences.topTap()
    InteractionSlot.BOTTOM_TAP -> preferences.bottomTap()
    InteractionSlot.SWIPE_LEFT -> preferences.swipeLeft()
    InteractionSlot.SWIPE_RIGHT -> preferences.swipeRight()
    InteractionSlot.SWIPE_UP -> preferences.swipeUp()
    InteractionSlot.SWIPE_DOWN -> preferences.swipeDown()
    InteractionSlot.DOUBLE_TAP -> preferences.doubleTap()
    InteractionSlot.LONG_PRESS -> preferences.longPress()
}

private fun withInteraction(
    current: ReaderInteractionPreferences,
    slot: InteractionSlot,
    action: ReaderInteractionAction,
) = ReaderInteractionPreferences(
    if (slot == InteractionSlot.LEFT_TAP) action else current.leftTap(),
    if (slot == InteractionSlot.CENTER_TAP) action else current.centerTap(),
    if (slot == InteractionSlot.RIGHT_TAP) action else current.rightTap(),
    if (slot == InteractionSlot.TOP_TAP) action else current.topTap(),
    if (slot == InteractionSlot.BOTTOM_TAP) action else current.bottomTap(),
    if (slot == InteractionSlot.SWIPE_LEFT) action else current.swipeLeft(),
    if (slot == InteractionSlot.SWIPE_RIGHT) action else current.swipeRight(),
    if (slot == InteractionSlot.SWIPE_UP) action else current.swipeUp(),
    if (slot == InteractionSlot.SWIPE_DOWN) action else current.swipeDown(),
    if (slot == InteractionSlot.DOUBLE_TAP) action else current.doubleTap(),
    if (slot == InteractionSlot.LONG_PRESS) action else current.longPress(),
)

private fun nextAction(action: ReaderInteractionAction): ReaderInteractionAction {
    val values = ReaderInteractionAction.entries
    return values[(action.ordinal + 1) % values.size]
}

private fun horizontalAction(
    action: ReaderInteractionAction,
    direction: ReadingDirection,
): ReaderInteractionAction {
    if (direction != ReadingDirection.RIGHT_TO_LEFT) return action
    return when (action) {
        ReaderInteractionAction.PREVIOUS_PAGE -> ReaderInteractionAction.NEXT_PAGE
        ReaderInteractionAction.NEXT_PAGE -> ReaderInteractionAction.PREVIOUS_PAGE
        else -> action
    }
}

@Composable
private fun ReaderBottomBar(
    pageIndex: Int,
    pageCount: Int,
    direction: ReadingDirection,
    goToPage: (Int) -> Unit,
    changeDirection: (ReadingDirection) -> Unit,
    splitSecondHalf: Boolean,
    dualPage: Boolean,
    splitPages: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.82f))
            .padding(horizontal = 18.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = Color.White)
            Slider(
                value = pageIndex.toFloat(),
                onValueChange = { goToPage(it.roundToInt()) },
                valueRange = 0f..(pageCount - 1).coerceAtLeast(1).toFloat(),
                steps = (pageCount - 2).coerceAtLeast(0),
                enabled = pageCount > 1,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.White)
        }
        Text(
            text = readerPageLabel(pageIndex, pageCount, splitSecondHalf, dualPage, splitPages),
            color = Color.White,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            DirectionButton("LTR", ReadingDirection.LEFT_TO_RIGHT, direction, changeDirection)
            DirectionButton("RTL", ReadingDirection.RIGHT_TO_LEFT, direction, changeDirection)
            DirectionButton("Vertical", ReadingDirection.VERTICAL, direction, changeDirection)
            DirectionButton("Webtoon", ReadingDirection.WEBTOON, direction, changeDirection)
        }
    }
}

private fun readerPageLabel(
    pageIndex: Int,
    pageCount: Int,
    splitSecondHalf: Boolean,
    dualPage: Boolean,
    splitPages: Boolean,
): String = when {
    splitPages -> "${pageIndex + 1}${if (splitSecondHalf) "b" else "a"} / $pageCount"
    dualPage && pageIndex + 1 < pageCount -> "${pageIndex + 1}-${pageIndex + 2} / $pageCount"
    else -> "${pageIndex + 1} / $pageCount"
}

@Composable
private fun DirectionButton(
    label: String,
    value: ReadingDirection,
    selected: ReadingDirection,
    changeDirection: (ReadingDirection) -> Unit,
) {
    TextButton(onClick = { changeDirection(value) }) {
        Text(
            label,
            color = if (value == selected) Color(0xFF90CAF9) else Color.White,
            fontWeight = if (value == selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun ReaderPageError(message: String?, retry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("This page could not be displayed.", color = Color.White)
        if (!message.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(message, color = Color.White.copy(alpha = 0.68f))
        }
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = retry) { Text("Retry", color = Color.White) }
    }
}
