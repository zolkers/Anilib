package fr.vriege.anilib.platform.compose

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
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.vriege.anilib.feature.reader.ReadingDirection
import fr.vriege.anilib.feature.reader.ReaderInteractionAction
import fr.vriege.anilib.feature.reader.ReaderInteractionPreferences
import fr.vriege.anilib.feature.reader.ui.ReaderController
import kotlin.math.roundToInt

@Composable
internal fun ReaderScreen(
    controller: ReaderController,
    pageDecoder: (ByteArray) -> ImageBitmap?,
    closeReader: () -> Unit,
) {
    var revision by remember(controller) { mutableIntStateOf(0) }
    var controlsVisible by remember(controller) { mutableStateOf(true) }
    var zoomed by remember(controller) { mutableStateOf(false) }
    var interactionMenu by remember(controller) { mutableStateOf(false) }
    var interactions by remember(controller) { mutableStateOf(controller.interactions()) }
    val snapshot = remember(controller, revision) { controller.snapshot() }
    val decodedPage = remember(controller, snapshot.currentPageIndex()) {
        runCatching { pageDecoder(controller.currentPage()) }
    }

    fun move(previous: Boolean) {
        val moved = if (previous) controller.previousPage() else controller.nextPage()
        if (moved) revision++
    }

    fun execute(action: ReaderInteractionAction) {
        when (action) {
            ReaderInteractionAction.PREVIOUS_PAGE -> move(true)
            ReaderInteractionAction.NEXT_PAGE -> move(false)
            ReaderInteractionAction.TOGGLE_CONTROLS -> controlsVisible = !controlsVisible
            ReaderInteractionAction.TOGGLE_ZOOM -> zoomed = !zoomed
            ReaderInteractionAction.OPEN_MENU -> interactionMenu = true
            ReaderInteractionAction.NONE -> Unit
        }
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
        decodedPage.getOrNull()?.let { image ->
            Image(
                bitmap = image,
                contentDescription = "Page ${snapshot.currentPageIndex() + 1}",
                modifier = Modifier.fillMaxSize().scale(if (zoomed) 2f else 1f),
                contentScale = ContentScale.Fit,
            )
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
                openInteractions = { interactionMenu = true },
            )
            ReaderBottomBar(
                pageIndex = snapshot.currentPageIndex(),
                pageCount = snapshot.pageCount(),
                direction = snapshot.direction(),
                goToPage = { index ->
                    controller.goToPage(index)
                    revision++
                },
                changeDirection = { direction ->
                    controller.setDirection(direction)
                    revision++
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        if (interactionMenu) {
            ReaderInteractionDialog(
                preferences = interactions,
                update = {
                    controller.setInteractions(it)
                    interactions = it
                },
                close = { interactionMenu = false },
            )
        }
    }
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
    openInteractions: () -> Unit,
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
        IconButton(onClick = openInteractions) {
            Icon(Icons.Default.Settings, contentDescription = "Reader interactions", tint = Color.White)
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
private fun ReaderInteractionDialog(
    preferences: ReaderInteractionPreferences,
    update: (ReaderInteractionPreferences) -> Unit,
    close: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Reader interactions") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 520.dp)) {
                items(InteractionSlot.entries.size) { index ->
                    val slot = InteractionSlot.entries[index]
                    val action = interaction(preferences, slot)
                    TextButton(onClick = { update(withInteraction(preferences, slot, nextAction(action))) }) {
                        Text("${slot.label}: ${action.name.replace('_', ' ').lowercase()}")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = close) { Text("Done") } },
        dismissButton = {
            TextButton(onClick = { update(ReaderInteractionPreferences.defaults()) }) { Text("Reset") }
        },
    )
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
            text = "${pageIndex + 1} / $pageCount",
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
