package fr.vriege.anilib.platform.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.vriege.anilib.feature.reader.ReadingDirection
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
    val snapshot = remember(controller, revision) { controller.snapshot() }
    val decodedPage = remember(controller, snapshot.currentPageIndex()) {
        runCatching { pageDecoder(controller.currentPage()) }
    }

    fun move(previous: Boolean) {
        val moved = if (previous) controller.previousPage() else controller.nextPage()
        if (moved) revision++
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        decodedPage.getOrNull()?.let { image ->
            Image(
                bitmap = image,
                contentDescription = "Page ${snapshot.currentPageIndex() + 1}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        } ?: ReaderPageError(decodedPage.exceptionOrNull()?.message) { revision++ }

        ReaderTapZones(
            direction = snapshot.direction(),
            previous = { move(true) },
            next = { move(false) },
            toggleControls = { controlsVisible = !controlsVisible },
        )

        if (controlsVisible) {
            ReaderTopBar(
                title = snapshot.title(),
                contentUnit = snapshot.contentUnit().title(),
                closeReader = closeReader,
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
    }
}

@Composable
private fun ReaderTapZones(
    direction: ReadingDirection,
    previous: () -> Unit,
    next: () -> Unit,
    toggleControls: () -> Unit,
) {
    if (direction == ReadingDirection.VERTICAL || direction == ReadingDirection.WEBTOON) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(0.36f).fillMaxWidth().clickable(onClick = previous))
            Box(modifier = Modifier.weight(0.28f).fillMaxWidth().clickable(onClick = toggleControls))
            Box(modifier = Modifier.weight(0.36f).fillMaxWidth().clickable(onClick = next))
        }
    } else {
        val left = if (direction == ReadingDirection.RIGHT_TO_LEFT) next else previous
        val right = if (direction == ReadingDirection.RIGHT_TO_LEFT) previous else next
        Row(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(0.36f).fillMaxHeight().clickable(onClick = left))
            Box(modifier = Modifier.weight(0.28f).fillMaxHeight().clickable(onClick = toggleControls))
            Box(modifier = Modifier.weight(0.36f).fillMaxHeight().clickable(onClick = right))
        }
    }
}

@Composable
private fun ReaderTopBar(
    title: String,
    contentUnit: String,
    closeReader: () -> Unit,
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
