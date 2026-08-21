package fr.vriege.anilib.platform.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import fr.vriege.anilib.feature.reader.ReaderDisplayPreferences
import fr.vriege.anilib.feature.reader.ReadingDirection
import fr.vriege.anilib.feature.reader.ui.ReaderController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

private const val CONTINUOUS_SCROLL_STEP_FRACTION = 0.85f

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ReaderContinuousPages(
    controller: ReaderController,
    pageDecoder: (ByteArray) -> ImageBitmap?,
    pageCount: Int,
    initialPage: Int,
    direction: ReadingDirection,
    display: ReaderDisplayPreferences,
    revision: Int,
    scrollTarget: Int?,
    consumeScrollTarget: () -> Unit,
    scrollStep: Int?,
    consumeScrollStep: () -> Unit,
    pageSelected: (Int) -> Unit,
    toggleControls: () -> Unit,
    toggleZoom: () -> Unit,
    previousChapter: () -> Unit,
    nextChapter: () -> Unit,
) {
    val firstPage = initialPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
    // +1 because index 0 is the "previous chapter" boundary item
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = firstPage + 1)
    val pageAspectRatios = remember(controller, pageCount) { mutableStateMapOf<Int, Float>() }
    val spacing = if (direction == ReadingDirection.WEBTOON) display.webtoonSpacingDp().dp else 15.dp

    CrashSafeLaunchedEffect(listState, controller) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { rawIndex ->
                // rawIndex 0 = "previous chapter" boundary; page items start at rawIndex 1
                val pageIndex = (rawIndex - 1).coerceIn(0, pageCount - 1)
                withContext(Dispatchers.IO) { controller.goToPage(pageIndex) }
                pageSelected(pageIndex)
            }
    }
    CrashSafeLaunchedEffect(scrollTarget) {
        scrollTarget?.let { target ->
            listState.animateScrollToItem(target.coerceIn(0, pageCount - 1) + 1)
            consumeScrollTarget()
        }
    }
    CrashSafeLaunchedEffect(scrollStep) {
        scrollStep?.let { step ->
            val viewportHeight = listState.layoutInfo.viewportSize.height.toFloat()
            listState.animateScrollBy(step * viewportHeight * CONTINUOUS_SCROLL_STEP_FRACTION)
            consumeScrollStep()
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().testTag("reader-continuous-pages").combinedClickable(
            onClick = toggleControls,
            onDoubleClick = toggleZoom,
        ),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        item(key = "chapter-start") {
            ReaderChapterBoundary(previous = true, onClick = previousChapter)
        }
        items(pageCount, key = { it }) { pageIndex ->
            ReaderContinuousPage(
                controller = controller,
                pageDecoder = pageDecoder,
                pageIndex = pageIndex,
                direction = direction,
                display = display,
                revision = revision,
                knownAspectRatio = pageAspectRatios[pageIndex],
                rememberAspectRatio = { pageAspectRatios[pageIndex] = it },
            )
        }
        item(key = "chapter-end") {
            ReaderChapterBoundary(previous = false, onClick = nextChapter)
        }
    }
}

@Composable
private fun ReaderContinuousPage(
    controller: ReaderController,
    pageDecoder: (ByteArray) -> ImageBitmap?,
    pageIndex: Int,
    direction: ReadingDirection,
    display: ReaderDisplayPreferences,
    revision: Int,
    knownAspectRatio: Float?,
    rememberAspectRatio: (Float) -> Unit,
) {
    var retry by remember(controller, pageIndex) { mutableIntStateOf(0) }
    var decoded by remember(controller, pageIndex, revision, retry) {
        mutableStateOf<Result<ImageBitmap>?>(null)
    }
    CrashSafeLaunchedEffect(controller, pageIndex, revision, retry) {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                requireNotNull(pageDecoder(controller.page(pageIndex))) {
                    "Unsupported page image format"
                }
            }
        }
        result.getOrNull()?.let { rememberAspectRatio(it.width.toFloat() / it.height) }
        decoded = result
    }

    when (val result = decoded) {
        null -> Box(
            modifier = stablePageSize(knownAspectRatio, 520),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = Color.White)
        }
        else -> result.fold(
            onSuccess = { image ->
                ReaderPageImage(
                    image = image,
                    pageIndex = pageIndex,
                    display = display,
                    zoomed = false,
                    splitSecondHalf = false,
                    direction = direction,
                    modifier = Modifier.fillMaxWidth().aspectRatio(image.width.toFloat() / image.height),
                )
            },
            onFailure = { failure ->
                Column(
                    modifier = stablePageSize(knownAspectRatio, 240).padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("ui.this.page.could.not.be.displayed", color = Color.White)
                    failure.message?.takeIf(String::isNotBlank)?.let {
                        Text(it, color = Color.White.copy(alpha = 0.68f))
                    }
                    TextButton(onClick = { retry++ }) { Text("ui.retry", color = Color.White) }
                }
            },
        )
    }
}

@Composable
private fun ReaderChapterBoundary(previous: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(72.dp),
        color = Color.Black,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = if (previous) Icons.AutoMirrored.Filled.ArrowBack else Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.72f),
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = if (previous) UiTranslations.translate("ui.previous.chapter", LocalLanguagePack.current)
                    else UiTranslations.translate("ui.next.chapter", LocalLanguagePack.current),
                color = Color.White.copy(alpha = 0.72f),
            )
        }
    }
}

private fun stablePageSize(knownAspectRatio: Float?, fallbackHeightDp: Int): Modifier =
    if (knownAspectRatio == null) {
        Modifier.fillMaxWidth().height(fallbackHeightDp.dp)
    } else {
        Modifier.fillMaxWidth().aspectRatio(knownAspectRatio)
    }
