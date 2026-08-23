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
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
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
import androidx.compose.material.icons.automirrored.outlined.ChromeReaderMode
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorFilter as ComposeColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
import fr.vriege.anilib.feature.reader.ui.ReaderWindowChapter
import fr.vriege.anilib.feature.settings.LanguagePack
import fr.vriege.anilib.feature.source.SourceContentUnit
import fr.vriege.anilib.feature.source.SourceContentUnitId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val READER_CONTROLS_HIDE_DELAY_MILLIS = 3_500L
private const val CONTINUOUS_PREFETCH_MARGIN = 5
private val READER_ZOOM_STEPS = floatArrayOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f, 4f, 5f)

@Composable
internal fun ReaderScreen(
    controller: ReaderController,
    pageDecoder: (ByteArray) -> ImageBitmap?,
    applyOrientationPolicy: (ReaderOrientationPolicy) -> Unit,
    downloadContent: (LibraryItemId, SourceContentUnitId) -> Unit,
    closeReader: () -> Unit,
) {
    val scope = rememberCrashSafeCoroutineScope()
    var revision by remember(controller) { mutableIntStateOf(0) }
    var controlsVisible by remember(controller) { mutableStateOf(true) }
    var zoomScale by remember(controller) { mutableFloatStateOf(1f) }
    var zoomOffset by remember(controller) { mutableStateOf(Offset.Zero) }
    var settingsMenu by remember(controller) { mutableStateOf(false) }
    var chapterMenu by remember(controller) { mutableStateOf(false) }
    var readerMenu by remember(controller) { mutableStateOf(false) }
    var readingModeMenu by remember(controller) { mutableStateOf(false) }
    var actionMessage by remember(controller) { mutableStateOf<String?>(null) }
    var splitSecondHalf by remember(controller) { mutableStateOf(false) }
    var interactions by remember(controller) { mutableStateOf(controller.interactions()) }
    var display by remember(controller) { mutableStateOf(controller.display()) }
    var titleDisplayOverride by remember(controller) { mutableStateOf(controller.hasDisplayOverride()) }
    var readContentIds by remember(controller) { mutableStateOf(controller.readContentIds()) }
    var contentUnits by remember(controller) { mutableStateOf<List<SourceContentUnit>?>(null) }
    var decodedPage by remember(controller) { mutableStateOf<Result<ImageBitmap>?>(null) }
    var decodedAdjacentPage by remember(controller) { mutableStateOf<Result<ImageBitmap>?>(null) }
    var readerBusy by remember(controller) { mutableStateOf(false) }
    var continuousScrollTarget by remember(controller) { mutableStateOf<Int?>(null) }
    var continuousScrollStep by remember(controller) { mutableStateOf<Int?>(null) }
    var verticalScrollTarget by remember(controller) { mutableStateOf<Int?>(null) }
    val focusRequester = remember(controller) { FocusRequester() }
    val snapshot = remember(controller, revision) { controller.snapshot() }
    var windowPageIndex by remember(controller, snapshot.contentUnit().id()) {
        mutableIntStateOf(snapshot.currentPageIndex())
    }
    val mode = remember(snapshot.direction()) { ReaderMode.of(snapshot.direction()) }
    val continuous = mode.continuous
    val verticalPager = mode.verticalPager
    val windowed = continuous || verticalPager
    var readerWindow by remember(controller) {
        mutableStateOf<List<ReaderWindowChapter>>(emptyList())
    }
    var windowInitialPage by remember(controller) { mutableIntStateOf(0) }
    // The window is published as soon as the current chapter is known, so opening a chapter costs
    // one source call. Neighbours are pulled in afterwards, in the background, and only once the
    // reader is close to a chapter edge - the same trade-off Aniyomi makes.
    CrashSafeLaunchedEffect(controller, windowed, revision) {
        if (!windowed) return@CrashSafeLaunchedEffect
        val resolved = withContext(Dispatchers.IO) {
            runCatching { controller.window() to controller.windowPageIndex() }.getOrNull()
        } ?: return@CrashSafeLaunchedEffect
        if (readerWindow.isEmpty()) windowInitialPage = resolved.second
        readerWindow = resolved.first
    }
    CrashSafeLaunchedEffect(controller, windowed, snapshot.contentUnit().id(), windowPageIndex) {
        if (!windowed) return@CrashSafeLaunchedEffect
        val pageCount = snapshot.pageCount()
        val nearEdge = windowPageIndex <= CONTINUOUS_PREFETCH_MARGIN ||
            windowPageIndex >= pageCount - 1 - CONTINUOUS_PREFETCH_MARGIN
        if (!nearEdge) return@CrashSafeLaunchedEffect
        val grown = withContext(Dispatchers.IO) {
            runCatching {
                controller.prefetchNeighbours()
                controller.window()
            }.getOrNull()
        } ?: return@CrashSafeLaunchedEffect
        if (grown.size != readerWindow.size) readerWindow = grown
    }
    CrashSafeLaunchedEffect(controller, snapshot.contentUnit().id()) {
        contentUnits = null
        contentUnits = withContext(Dispatchers.IO) {
            runCatching { controller.contentUnits() }.getOrDefault(emptyList())
        }
    }
    CrashSafeLaunchedEffect(
        controller,
        snapshot.contentUnit().id(),
        snapshot.currentPageIndex(),
        display.dualPage(),
        windowed,
        revision,
    ) {
        decodedPage = null
        decodedAdjacentPage = null
        zoomScale = 1f
        zoomOffset = Offset.Zero
        if (windowed) return@CrashSafeLaunchedEffect
        val pageIndex = snapshot.currentPageIndex()
        val loadAdjacent = display.dualPage() && pageIndex + 1 < snapshot.pageCount()
        val pages = withContext(Dispatchers.IO) {
            val primary = runCatching {
                requireNotNull(pageDecoder(controller.page(pageIndex))) { "Unsupported page image format" }
            }
            val adjacent = if (loadAdjacent) {
                runCatching {
                    requireNotNull(pageDecoder(controller.page(pageIndex + 1))) {
                        "Unsupported adjacent page image format"
                    }
                }
            } else {
                null
            }
            primary to adjacent
        }
        decodedPage = pages.first
        decodedAdjacentPage = pages.second
    }
    CrashSafeLaunchedEffect(
        controlsVisible,
        settingsMenu,
        chapterMenu,
        readerMenu,
        readingModeMenu,
        snapshot.currentPageIndex(),
    ) {
        if (controlsVisible && !settingsMenu && !chapterMenu && !readerMenu && !readingModeMenu) {
            delay(READER_CONTROLS_HIDE_DELAY_MILLIS)
            controlsVisible = false
        }
    }
    DisposableEffect(display.orientationPolicy(), applyOrientationPolicy) {
        applyOrientationPolicy(display.orientationPolicy())
        onDispose { applyOrientationPolicy(ReaderOrientationPolicy.SYSTEM) }
    }
    CrashSafeLaunchedEffect(controller, settingsMenu, chapterMenu, readerMenu, readingModeMenu) {
        if (!settingsMenu && !chapterMenu && !readerMenu && !readingModeMenu) {
            runCatching { focusRequester.requestFocus() }
        }
    }

    fun moveContentUnit(next: Boolean) {
        if (readerBusy) return
        scope.launch {
            readerBusy = true
            withContext(Dispatchers.IO) {
                runCatching { if (next) controller.nextContentUnit() else controller.previousContentUnit() }
            }.onSuccess { moved ->
                if (moved) {
                    splitSecondHalf = false
                    verticalScrollTarget = null
                    readerWindow = emptyList()
                    actionMessage = null
                    revision++
                }
            }.onFailure { actionMessage = it.message ?: "The chapter could not be opened." }
            readerBusy = false
        }
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
        if (moved) {
            splitSecondHalf = previous && display.splitPages()
            revision++
        } else {
            moveContentUnit(!previous)
        }
    }

    fun moveVertical(previous: Boolean) {
        val target = snapshot.currentPageIndex() + if (previous) -1 else 1
        if (target in 0 until snapshot.pageCount()) {
            verticalScrollTarget = target
        } else {
            moveContentUnit(!previous)
        }
    }

    fun setZoom(scale: Float) {
        zoomScale = scale
        if (scale <= 1.01f) zoomOffset = Offset.Zero
    }

    fun execute(action: ReaderInteractionAction) {
        when (action) {
            ReaderInteractionAction.PREVIOUS_PAGE -> if (verticalPager) moveVertical(true) else move(true)
            ReaderInteractionAction.NEXT_PAGE -> if (verticalPager) moveVertical(false) else move(false)
            ReaderInteractionAction.TOGGLE_CONTROLS -> controlsVisible = !controlsVisible
            ReaderInteractionAction.TOGGLE_ZOOM -> {
                setZoom(if (zoomScale > 1.01f) 1f else 2f)
                controlsVisible = false
            }
            ReaderInteractionAction.ZOOM_IN -> setZoom(stepZoom(zoomScale, 1))
            ReaderInteractionAction.ZOOM_OUT -> setZoom(stepZoom(zoomScale, -1))
            ReaderInteractionAction.ZOOM_RESET -> setZoom(1f)
            ReaderInteractionAction.OPEN_MENU -> settingsMenu = true
            ReaderInteractionAction.NONE -> Unit
        }
    }

    fun openContentUnit(contentUnitId: SourceContentUnitId) {
        if (readerBusy) return
        scope.launch {
            readerBusy = true
            withContext(Dispatchers.IO) { runCatching { controller.openContentUnit(contentUnitId) } }
                .onSuccess {
                    splitSecondHalf = false
                    readerWindow = emptyList()
                    actionMessage = null
                    revision++
                }
                .onFailure { actionMessage = it.message ?: "The chapter could not be opened." }
            readerBusy = false
        }
    }

    fun handleKeyCommand(command: ReaderKeyCommand) {
        when (command) {
            ReaderKeyCommand.PREVIOUS_PAGE -> execute(ReaderInteractionAction.PREVIOUS_PAGE)
            ReaderKeyCommand.NEXT_PAGE -> execute(ReaderInteractionAction.NEXT_PAGE)
            ReaderKeyCommand.SCROLL_UP -> continuousScrollStep = -1
            ReaderKeyCommand.SCROLL_DOWN -> continuousScrollStep = 1
            ReaderKeyCommand.PREVIOUS_CHAPTER -> moveContentUnit(false)
            ReaderKeyCommand.NEXT_CHAPTER -> moveContentUnit(true)
            ReaderKeyCommand.FIRST_PAGE -> when {
                continuous -> continuousScrollTarget = 0
                verticalPager -> verticalScrollTarget = 0
                else -> {
                    controller.goToPage(0)
                    splitSecondHalf = false
                    revision++
                }
            }
            ReaderKeyCommand.LAST_PAGE -> when {
                continuous -> continuousScrollTarget = (snapshot.pageCount() - 1).coerceAtLeast(0)
                verticalPager -> verticalScrollTarget = (snapshot.pageCount() - 1).coerceAtLeast(0)
                else -> {
                    controller.goToPage((snapshot.pageCount() - 1).coerceAtLeast(0))
                    splitSecondHalf = false
                    revision++
                }
            }
            ReaderKeyCommand.ZOOM_IN -> execute(ReaderInteractionAction.ZOOM_IN)
            ReaderKeyCommand.ZOOM_OUT -> execute(ReaderInteractionAction.ZOOM_OUT)
            ReaderKeyCommand.ZOOM_RESET -> execute(ReaderInteractionAction.ZOOM_RESET)
        }
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

    fun transformZoom(pan: Offset, gestureZoom: Float) {
        val nextScale = (zoomScale * gestureZoom).coerceIn(READER_ZOOM_STEPS.first(), READER_ZOOM_STEPS.last())
        zoomScale = nextScale
        if (nextScale <= 1.01f) {
            zoomOffset = Offset.Zero
        } else {
            zoomOffset += pan
            controlsVisible = false
        }
    }

    var drag by remember(controller) { mutableStateOf(Offset.Zero) }
    val readerModifier = Modifier
        .fillMaxSize()
        .background(Color.Black)
        .testTag("reader-canvas")
        .semantics { stateDescription = "${(zoomScale * 100f).roundToInt()}%" }
        .focusRequester(focusRequester)
        .focusable()
        .onPreviewKeyEvent { event ->
            val command = resolveReaderKeyCommand(event, mode) ?: return@onPreviewKeyEvent false
            handleKeyCommand(command)
            true
        }
        .readerPlatformZoom { transformZoom(Offset.Zero, it) }
        .pointerInput(controller, snapshot.contentUnit().id(), snapshot.currentPageIndex()) {
            detectReaderPinchGestures(::transformZoom)
        }
    Box(
        modifier = if (windowed && zoomScale <= 1.01f) {
            readerModifier
        } else {
            readerModifier.pointerInput(interactions, snapshot.direction(), zoomScale) {
                detectDragGestures(
                    onDragStart = { drag = Offset.Zero },
                    onDrag = { change, amount ->
                        change.consume()
                        if (zoomScale > 1.01f) {
                            val maximumX = size.width * (zoomScale - 1f) / 2f
                            val maximumY = size.height * (zoomScale - 1f) / 2f
                            zoomOffset = Offset(
                                (zoomOffset.x + amount.x).coerceIn(-maximumX, maximumX),
                                (zoomOffset.y + amount.y).coerceIn(-maximumY, maximumY),
                            )
                        } else {
                            drag += amount
                        }
                    },
                    onDragEnd = {
                        if (zoomScale > 1.01f) return@detectDragGestures
                        val horizontal = kotlin.math.abs(drag.x) > kotlin.math.abs(drag.y)
                        val action = if (horizontal && kotlin.math.abs(drag.x) >= 48f) {
                            if (drag.x < 0f) interactions.swipeLeft() else interactions.swipeRight()
                        } else if (!horizontal && kotlin.math.abs(drag.y) >= 48f) {
                            if (drag.y < 0f) interactions.swipeUp() else interactions.swipeDown()
                        } else {
                            ReaderInteractionAction.NONE
                        }
                        execute(horizontalAction(action, mode))
                    },
                )
            }
        },
    ) {
        val reducedMotion = LocalReducedMotion.current
        Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
            Box(
                modifier = if (!mode.twoAxisZoom) {
                    // Continuous reading zooms the page width only; height follows from the page
                    // aspect ratio, so the scroll keeps its natural feel at any zoom level.
                    Modifier.fillMaxSize()
                } else {
                    Modifier.fillMaxSize().graphicsLayer(
                        scaleX = zoomScale,
                        scaleY = zoomScale,
                        translationX = zoomOffset.x,
                        translationY = zoomOffset.y,
                    )
                },
            ) {
                if (continuous && readerWindow.isNotEmpty()) {
                    // Deliberately not keyed on the chapter: the window spans neighbouring
                    // chapters, so rebuilding here would undo the seamless scroll.
                    ReaderContinuousPages(
                        controller = controller,
                        pageDecoder = pageDecoder,
                        window = readerWindow,
                        initialGlobalPage = windowInitialPage,
                        direction = snapshot.direction(),
                        display = display,
                        revision = revision,
                        widthScale = zoomScale,
                        scrollTarget = continuousScrollTarget,
                        consumeScrollTarget = { continuousScrollTarget = null },
                        scrollStep = continuousScrollStep,
                        consumeScrollStep = { continuousScrollStep = null },
                        pageSelected = { windowPageIndex = it },
                        chapterChanged = { revision++ },
                        toggleControls = { controlsVisible = !controlsVisible },
                        toggleZoom = { execute(ReaderInteractionAction.TOGGLE_ZOOM) },
                    )
                } else if (verticalPager && readerWindow.isNotEmpty()) {
                    ReaderVerticalPages(
                        controller = controller,
                        pageDecoder = pageDecoder,
                        window = readerWindow,
                        initialGlobalPage = windowInitialPage,
                        display = display,
                        zoomScale = zoomScale,
                        scrollTarget = verticalScrollTarget,
                        consumeScrollTarget = { verticalScrollTarget = null },
                        pageSelected = { windowPageIndex = it },
                        chapterChanged = { revision++ },
                        toggleControls = { controlsVisible = !controlsVisible },
                        toggleZoom = { execute(ReaderInteractionAction.TOGGLE_ZOOM) },
                    )
                } else decodedPage?.getOrNull()?.let { image ->
                    val frame = ReaderPageFrame(
                        image,
                        decodedAdjacentPage?.getOrNull(),
                        snapshot.currentPageIndex(),
                        splitSecondHalf,
                    )
                    AnimatedContent(
                        targetState = frame,
                        transitionSpec = {
                            readerTransition(
                                if (reducedMotion) ReaderPageTransition.NONE else display.transition(),
                            )
                        },
                        label = "reader-page",
                    ) { current ->
                        ReaderPages(
                            primary = current.primary,
                            adjacent = current.adjacent,
                            pageIndex = current.pageIndex,
                            direction = snapshot.direction(),
                            display = display,
                            zoomed = false,
                            splitSecondHalf = current.splitSecondHalf,
                        )
                    }
                } ?: if (decodedPage == null) {
                    ReaderPageLoading()
                } else {
                    ReaderPageError(decodedPage?.exceptionOrNull()?.message) { revision++ }
                }
            }
        }

        if (!windowed) {
            ReaderTapZones(
                mode = mode,
                interactions = interactions,
                execute = ::execute,
            )
        }

        if (controlsVisible) {
            ReaderTopBar(
                title = snapshot.title(),
                contentUnit = snapshot.contentUnit().title(),
                closeReader = closeReader,
                openSettings = { settingsMenu = true },
                openReadingMode = { readingModeMenu = true },
                openChapters = { chapterMenu = true },
                openMenu = { readerMenu = true },
            )
            ReaderBottomBar(
                pageIndex = if (windowed) windowPageIndex else snapshot.currentPageIndex(),
                pageCount = snapshot.pageCount(),
                goToPage = { index ->
                    when {
                        continuous -> continuousScrollTarget = index
                        verticalPager -> verticalScrollTarget = index
                        else -> {
                            controller.goToPage(index)
                            splitSecondHalf = false
                            revision++
                        }
                    }
                },
                splitSecondHalf = splitSecondHalf,
                dualPage = display.dualPage(),
                splitPages = display.splitPages(),
                previousChapter = { moveContentUnit(false) },
                nextChapter = { moveContentUnit(true) },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        } else {
            ReaderControlsHandle(
                showControls = { controlsVisible = true },
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
        if (settingsMenu) {
            ReaderSettingsDialog(
                interactions = interactions,
                display = display,
                direction = snapshot.direction(),
                titleOverride = titleDisplayOverride,
                updateInteractions = {
                    controller.setInteractions(it)
                    interactions = controller.interactions()
                },
                updateDisplay = {
                    controller.setDisplay(it, titleDisplayOverride)
                    display = it
                    splitSecondHalf = false
                },
                updateDirection = {
                    controller.setDirection(it)
                    display = display.withReadingDirection(it)
                    splitSecondHalf = false
                    revision++
                },
                setTitleOverride = { enabled ->
                    if (enabled) {
                        controller.setDisplay(display, true)
                        titleDisplayOverride = true
                    } else {
                        controller.clearDisplayOverride()
                        titleDisplayOverride = false
                        display = controller.display()
                        controller.setDirection(display.readingDirection())
                        splitSecondHalf = false
                        readerWindow = emptyList()
                        continuousScrollTarget = null
                        verticalScrollTarget = null
                        revision++
                    }
                },
                close = { settingsMenu = false },
            )
        }
        if (readingModeMenu) {
            ReaderModeDialog(
                current = snapshot.direction(),
                select = {
                    windowPageIndex = snapshot.currentPageIndex()
                    readerWindow = emptyList()
                    controller.setDirection(it)
                    display = display.withReadingDirection(it)
                    splitSecondHalf = false
                    continuousScrollTarget = null
                    verticalScrollTarget = null
                    readingModeMenu = false
                    revision++
                },
                close = { readingModeMenu = false },
            )
        }
        if (chapterMenu) {
            ReaderChapterDialog(
                units = contentUnits.orEmpty(),
                loading = contentUnits == null,
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
                    moveContentUnit(false)
                    readerMenu = false
                },
                nextChapter = {
                    moveContentUnit(true)
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
        if (readerBusy) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.36f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }
}

private suspend fun PointerInputScope.detectReaderPinchGestures(
    transform: (pan: Offset, zoom: Float) -> Unit,
) {
    awaitEachGesture {
        var previousCentroid: Offset? = null
        var previousDistance = 0f
        var active: Boolean
        do {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }
            if (pressed.size >= 2) {
                val first = pressed[0].position
                val second = pressed[1].position
                val centroid = Offset((first.x + second.x) / 2f, (first.y + second.y) / 2f)
                val horizontal = first.x - second.x
                val vertical = first.y - second.y
                val distance = sqrt(horizontal * horizontal + vertical * vertical)
                if (previousDistance > 0f && distance > 0f) {
                    transform(centroid - (previousCentroid ?: centroid), distance / previousDistance)
                }
                previousCentroid = centroid
                previousDistance = distance
                pressed.forEach { it.consume() }
            } else {
                previousCentroid = null
                previousDistance = 0f
            }
            active = event.changes.any { it.pressed }
        } while (active)
    }
}

@Composable
private fun ReaderControlsHandle(showControls: () -> Unit, modifier: Modifier = Modifier) {
    val description = UiTranslations.translate("ui.show.reader.controls", LocalLanguagePack.current)
    IconButton(
        onClick = showControls,
        modifier = modifier
            .padding(top = 8.dp)
            .background(Color.Black.copy(alpha = 0.54f), MaterialTheme.shapes.large),
    ) {
        Icon(Icons.Default.MoreVert, contentDescription = description, tint = Color.White)
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
internal fun ReaderPageImage(
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
            contentDescription = UiTranslations.format(
                "dynamic.page",
                LocalLanguagePack.current,
                pageIndex + 1,
            ),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = splitScale * cropScale * zoomScale,
                    scaleY = splitScale * cropScale * zoomScale,
                    rotationZ = display.rotation().degrees().toFloat(),
                    transformOrigin = origin,
                ),
            contentScale = contentScale(display.scaleMode(), direction),
            colorFilter = readerColorFilter(display.colorFilter(), display.brightnessPercent()),
        )
    }
}

private fun contentScale(mode: ReaderScaleMode, direction: ReadingDirection): ContentScale {
    if (direction == ReadingDirection.WEBTOON) {
        return ContentScale.FillWidth
    }
    return when (mode) {
        ReaderScaleMode.FIT -> ContentScale.Fit
        ReaderScaleMode.FILL -> ContentScale.Crop
        ReaderScaleMode.FIT_WIDTH -> ContentScale.FillWidth
        ReaderScaleMode.FIT_HEIGHT -> ContentScale.FillHeight
        ReaderScaleMode.ORIGINAL -> ContentScale.None
    }
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
    mode: ReaderMode,
    interactions: ReaderInteractionPreferences,
    execute: (ReaderInteractionAction) -> Unit,
) {
    if (mode.continuous) {
        Column(modifier = Modifier.fillMaxSize()) {
            ReaderTapZone(Modifier.weight(0.36f).fillMaxWidth(), interactions.topTap(), interactions, execute)
            ReaderTapZone(Modifier.weight(0.28f).fillMaxWidth(), interactions.centerTap(), interactions, execute)
            ReaderTapZone(Modifier.weight(0.36f).fillMaxWidth(), interactions.bottomTap(), interactions, execute)
        }
    } else {
        val left = horizontalAction(interactions.leftTap(), mode)
        val right = horizontalAction(interactions.rightTap(), mode)
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
    openReadingMode: () -> Unit,
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
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ui.close.reader", tint = Color.White)
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
            Icon(Icons.Default.Settings, contentDescription = "ui.reader.settings", tint = Color.White)
        }
        IconButton(onClick = openChapters) {
            Icon(Icons.AutoMirrored.Filled.List, contentDescription = "ui.chapters", tint = Color.White)
        }
        IconButton(onClick = openMenu) {
            Icon(Icons.Default.MoreVert, contentDescription = "ui.reader.menu", tint = Color.White)
        }
        IconButton(onClick = openReadingMode) {
            Icon(
                Icons.AutoMirrored.Outlined.ChromeReaderMode,
                contentDescription = "ui.reading.mode",
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun ReaderModeDialog(
    current: ReadingDirection,
    select: (ReadingDirection) -> Unit,
    close: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = close,
        title = { Text("ui.reading.mode") },
        text = {
            Column {
                ReadingDirection.entries.forEach { direction ->
                    TextButton(onClick = { select(direction) }) {
                        Text(
                            (if (direction == current) "✓ " else "") +
                                UiTranslations.translate(readingDirectionKey(direction), LocalLanguagePack.current),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = close) { Text("ui.close") } },
    )
}

private fun readingDirectionKey(direction: ReadingDirection): String = when (direction) {
    ReadingDirection.LEFT_TO_RIGHT -> "ui.left.to.right"
    ReadingDirection.RIGHT_TO_LEFT -> "ui.right.to.left"
    ReadingDirection.VERTICAL -> "ui.vertical"
    ReadingDirection.WEBTOON -> "ui.webtoon"
}

private enum class InteractionSlot(val labelKey: String) {
    LEFT_TAP("ui.left.tap"),
    CENTER_TAP("ui.center.tap"),
    RIGHT_TAP("ui.right.tap"),
    TOP_TAP("ui.top.tap"),
    BOTTOM_TAP("ui.bottom.tap"),
    SWIPE_LEFT("ui.swipe.left"),
    SWIPE_RIGHT("ui.swipe.right"),
    SWIPE_UP("ui.swipe.up"),
    SWIPE_DOWN("ui.swipe.down"),
    DOUBLE_TAP("ui.double.tap"),
    LONG_PRESS("ui.long.press"),
}

@Composable
private fun ReaderChapterDialog(
    units: List<SourceContentUnit>,
    loading: Boolean,
    current: SourceContentUnitId,
    readContentIds: Set<String>,
    open: (SourceContentUnitId) -> Unit,
    setRead: (SourceContentUnitId, Boolean) -> Unit,
    download: (SourceContentUnitId) -> Unit,
    close: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = close,
        title = { Text("ui.chapters") },
        text = {
            if (loading) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(96.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else if (units.isEmpty()) {
                Text("ui.no.chapter.list.is.available")
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
                                TextButton(onClick = { download(unit.id()) }) { Text("ui.download") }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = close) { Text("ui.close") } },
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
                TextButton(onClick = previousChapter) { Text("ui.previous.chapter") }
                TextButton(onClick = nextChapter) { Text("ui.next.chapter") }
                TextButton(onClick = { setRead(!read) }) {
                    Text(if (read) "Mark chapter unread" else "Mark chapter read")
                }
                TextButton(onClick = download) { Text("ui.download.chapter") }
                TextButton(onClick = openChapters) { Text("ui.all.chapters") }
                TextButton(onClick = openSettings) { Text("ui.reader.settings") }
                if (!message.isNullOrBlank()) {
                    Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = close) { Text("ui.close") } },
    )
}

@Composable
private fun ReaderSettingsDialog(
    interactions: ReaderInteractionPreferences,
    display: ReaderDisplayPreferences,
    direction: ReadingDirection,
    titleOverride: Boolean,
    updateInteractions: (ReaderInteractionPreferences) -> Unit,
    updateDisplay: (ReaderDisplayPreferences) -> Unit,
    updateDirection: (ReadingDirection) -> Unit,
    setTitleOverride: (Boolean) -> Unit,
    close: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = close,
        title = { Text("ui.reader.settings") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 520.dp)) {
                item { Text("ui.display", fontWeight = FontWeight.SemiBold) }
                item {
                    TextButton(onClick = { setTitleOverride(!titleOverride) }) {
                        Text(
                            UiTranslations.format(
                                "dynamic.apply.to.title",
                                LocalLanguagePack.current,
                                enabledLabel(titleOverride, LocalLanguagePack.current),
                            ),
                        )
                    }
                }
                item {
                    TextButton(onClick = { updateDirection(nextDirection(direction)) }) {
                        Text(
                            UiTranslations.format(
                                "dynamic.reading.mode",
                                LocalLanguagePack.current,
                                UiTranslations.translate(readingDirectionKey(direction), LocalLanguagePack.current),
                            ),
                        )
                    }
                }
                item {
                    TextButton(onClick = {
                        updateDisplay(withScaleMode(display, nextScaleMode(display.scaleMode())))
                    }) {
                        Text(
                            UiTranslations.format(
                                "dynamic.scale",
                                LocalLanguagePack.current,
                                display.scaleMode().name.replace('_', ' ').lowercase(),
                            ),
                        )
                    }
                }
                item {
                    TextButton(onClick = { updateDisplay(withCropBorders(display, !display.cropBorders())) }) {
                        Text(
                            UiTranslations.format(
                                "dynamic.crop.borders",
                                LocalLanguagePack.current,
                                enabledLabel(display.cropBorders(), LocalLanguagePack.current),
                            ),
                        )
                    }
                }
                item {
                    TextButton(onClick = { updateDisplay(withSplitPages(display, !display.splitPages())) }) {
                        Text(
                            UiTranslations.format(
                                "dynamic.split.pages",
                                LocalLanguagePack.current,
                                enabledLabel(display.splitPages(), LocalLanguagePack.current),
                            ),
                        )
                    }
                }
                item {
                    TextButton(onClick = { updateDisplay(withRotation(display, nextRotation(display.rotation()))) }) {
                        Text(
                            UiTranslations.format(
                                "dynamic.rotation.degrees",
                                LocalLanguagePack.current,
                                display.rotation().degrees(),
                            ),
                        )
                    }
                }
                item {
                    TextButton(onClick = { updateDisplay(withDualPage(display, !display.dualPage())) }) {
                        Text(
                            UiTranslations.format(
                                "dynamic.dual.page",
                                LocalLanguagePack.current,
                                enabledLabel(display.dualPage(), LocalLanguagePack.current),
                            ),
                        )
                    }
                }
                item {
                    TextButton(onClick = {
                        updateDisplay(withWebtoonSpacing(display, nextSpacing(display.webtoonSpacingDp())))
                    }) {
                        Text(
                            UiTranslations.format(
                                "dynamic.webtoon.spacing",
                                LocalLanguagePack.current,
                                display.webtoonSpacingDp(),
                            ),
                        )
                    }
                }
                item {
                    TextButton(onClick = {
                        updateDisplay(withColorFilter(display, nextColorFilter(display.colorFilter())))
                    }) {
                        Text(
                            UiTranslations.format(
                                "dynamic.color.filter",
                                LocalLanguagePack.current,
                                display.colorFilter().name.lowercase(),
                            ),
                        )
                    }
                }
                item {
                    TextButton(onClick = {
                        updateDisplay(withBrightness(display, nextBrightness(display.brightnessPercent())))
                    }) {
                        Text(
                            UiTranslations.format(
                                "dynamic.brightness.percent",
                                LocalLanguagePack.current,
                                display.brightnessPercent(),
                            ),
                        )
                    }
                }
                item {
                    TextButton(onClick = {
                        updateDisplay(withTransition(display, nextTransition(display.transition())))
                    }) {
                        Text(
                            UiTranslations.format(
                                "dynamic.transition",
                                LocalLanguagePack.current,
                                display.transition().name.lowercase(),
                            ),
                        )
                    }
                }
                item {
                    TextButton(onClick = {
                        updateDisplay(withOrientation(display, nextOrientation(display.orientationPolicy())))
                    }) {
                        Text(
                            UiTranslations.format(
                                "dynamic.orientation",
                                LocalLanguagePack.current,
                                display.orientationPolicy().name.lowercase(),
                            ),
                        )
                    }
                }
                item { Text("ui.interactions", fontWeight = FontWeight.SemiBold) }
                items(InteractionSlot.entries.size) { index ->
                    val slot = InteractionSlot.entries[index]
                    val action = interaction(interactions, slot)
                    TextButton(onClick = {
                        updateInteractions(withInteraction(interactions, slot, nextAction(action)))
                    }) {
                        Text(
                            UiTranslations.format(
                                "dynamic.interaction.action",
                                LocalLanguagePack.current,
                                UiTranslations.translate(slot.labelKey, LocalLanguagePack.current),
                                UiTranslations.translate(readerActionKey(action), LocalLanguagePack.current),
                            ),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = close) { Text("ui.done") } },
        dismissButton = {
            TextButton(onClick = {
                updateDisplay(ReaderDisplayPreferences.defaults())
                updateInteractions(ReaderInteractionPreferences.defaults())
                updateDirection(ReaderDisplayPreferences.defaults().readingDirection())
            }) { Text("ui.reset") }
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
    current.readingDirection(),
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
    current.readingDirection(),
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
    current.readingDirection(),
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
    current.readingDirection(),
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
    current.readingDirection(),
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
    current.readingDirection(),
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
    current.readingDirection(),
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
    current.readingDirection(),
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
    current.readingDirection(),
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
    current.readingDirection(),
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

private fun stepZoom(current: Float, direction: Int): Float {
    val index = READER_ZOOM_STEPS.indexOfFirst { it >= current - 0.001f }.let { if (it < 0) READER_ZOOM_STEPS.size - 1 else it }
    val target = (index + direction).coerceIn(0, READER_ZOOM_STEPS.size - 1)
    return READER_ZOOM_STEPS[target]
}

private fun nextTransition(value: ReaderPageTransition): ReaderPageTransition {
    val values = ReaderPageTransition.entries
    return values[(value.ordinal + 1) % values.size]
}

private fun nextOrientation(value: ReaderOrientationPolicy): ReaderOrientationPolicy {
    val values = ReaderOrientationPolicy.entries
    return values[(value.ordinal + 1) % values.size]
}

private fun nextDirection(value: ReadingDirection): ReadingDirection {
    val values = ReadingDirection.entries
    return values[(value.ordinal + 1) % values.size]
}

private fun enabledLabel(value: Boolean, language: LanguagePack): String =
    UiTranslations.translate(if (value) "ui.on" else "ui.off", language)

private fun readerActionKey(action: ReaderInteractionAction): String = when (action) {
    ReaderInteractionAction.PREVIOUS_PAGE -> "ui.previous.page"
    ReaderInteractionAction.NEXT_PAGE -> "ui.next.page"
    ReaderInteractionAction.TOGGLE_CONTROLS -> "ui.toggle.controls"
    ReaderInteractionAction.TOGGLE_ZOOM -> "ui.toggle.zoom"
    ReaderInteractionAction.ZOOM_IN -> "ui.zoom.in"
    ReaderInteractionAction.ZOOM_OUT -> "ui.zoom.out"
    ReaderInteractionAction.ZOOM_RESET -> "ui.zoom.reset"
    ReaderInteractionAction.OPEN_MENU -> "ui.open.menu"
    ReaderInteractionAction.NONE -> "ui.none"
}

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
    mode: ReaderMode,
): ReaderInteractionAction {
    if (!mode.mirrored) return action
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
    goToPage: (Int) -> Unit,
    splitSecondHalf: Boolean,
    dualPage: Boolean,
    splitPages: Boolean,
    previousChapter: () -> Unit,
    nextChapter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var sliderValue by remember(pageIndex, pageCount) { mutableFloatStateOf(pageIndex.toFloat()) }
    val language = LocalLanguagePack.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.82f))
            .padding(horizontal = 18.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = previousChapter) {
                Icon(
                    Icons.Default.ChevronLeft,
                    contentDescription = UiTranslations.translate("ui.previous.chapter", language),
                    tint = Color.White,
                )
            }
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = { goToPage(sliderValue.roundToInt()) },
                valueRange = 0f..(pageCount - 1).coerceAtLeast(1).toFloat(),
                steps = (pageCount - 2).coerceAtLeast(0),
                enabled = pageCount > 1,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = nextChapter) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = UiTranslations.translate("ui.next.chapter", language),
                    tint = Color.White,
                )
            }
        }
        Text(
            text = readerPageLabel(
                sliderValue.roundToInt(),
                pageCount,
                splitSecondHalf,
                dualPage,
                splitPages,
            ),
            color = Color.White,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
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
private fun ReaderPageError(message: String?, retry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("ui.this.page.could.not.be.displayed", color = Color.White)
        if (!message.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(message, color = Color.White.copy(alpha = 0.68f))
        }
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = retry) { Text("ui.retry", color = Color.White) }
    }
}

@Composable
private fun ReaderPageLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Color.White)
    }
}
