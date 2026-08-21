package fr.vriege.anilib.platform.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.vriege.anilib.feature.reader.ReaderDisplayPreferences
import fr.vriege.anilib.feature.reader.ReadingDirection
import fr.vriege.anilib.feature.reader.ui.ReaderController
import fr.vriege.anilib.feature.reader.ui.ReaderWindowChapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

private const val CONTINUOUS_SCROLL_STEP_FRACTION = 0.85f

/**
 * Continuous viewer over the reader's chapter window. Previous, current and next chapter pages
 * live in one scroll sequence, so crossing a chapter never rebuilds the list - it only reports the
 * new position, the way Aniyomi's webtoon viewer does. Item keys are chapter-scoped so the window
 * can shift underneath without disturbing the scroll offset.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ReaderContinuousPages(
    controller: ReaderController,
    pageDecoder: (ByteArray) -> ImageBitmap?,
    window: List<ReaderWindowChapter>,
    initialGlobalPage: Int,
    direction: ReadingDirection,
    display: ReaderDisplayPreferences,
    revision: Int,
    widthScale: Float,
    scrollTarget: Int?,
    consumeScrollTarget: () -> Unit,
    scrollStep: Int?,
    consumeScrollStep: () -> Unit,
    pageSelected: (Int) -> Unit,
    chapterChanged: () -> Unit,
    toggleControls: () -> Unit,
    toggleZoom: () -> Unit,
) {
    val current = window.firstOrNull { it.current() }
    val entries = remember(window) { continuousEntries(window) }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = entries
            .indexOfFirst { it is ContinuousEntry.Page && it.globalPage == initialGlobalPage }
            .coerceAtLeast(0),
    )
    val pageAspectRatios = remember(controller) { mutableStateMapOf<String, Float>() }
    val spacing = if (direction == ReadingDirection.WEBTOON) display.webtoonSpacingDp().dp else 15.dp

    CrashSafeLaunchedEffect(listState, controller, entries) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { index ->
                val page = entries.getOrNull(index) as? ContinuousEntry.Page ?: return@collect
                val moved = withContext(Dispatchers.IO) { controller.selectWindowPage(page.globalPage) }
                pageSelected(page.localPage)
                if (moved) chapterChanged()
            }
    }
    CrashSafeLaunchedEffect(scrollTarget, entries) {
        val target = scrollTarget
        val chapter = current
        if (target != null && chapter != null) {
            val global = chapter.firstGlobalPage() + target.coerceIn(0, chapter.pageCount() - 1)
            val index = entries.indexOfFirst { it is ContinuousEntry.Page && it.globalPage == global }
            if (index >= 0) listState.animateScrollToItem(index)
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
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        items(entries.size, key = { entries[it].key }) { index ->
            when (val entry = entries[index]) {
                is ContinuousEntry.Transition -> ReaderChapterTransition(entry.label)
                is ContinuousEntry.Page -> ReaderContinuousPage(
                    controller = controller,
                    pageDecoder = pageDecoder,
                    globalPage = entry.globalPage,
                    pageIndex = entry.localPage,
                    direction = direction,
                    display = display,
                    revision = revision,
                    widthScale = widthScale,
                    knownAspectRatio = pageAspectRatios[entry.key],
                    rememberAspectRatio = { pageAspectRatios[entry.key] = it },
                )
            }
        }
    }
}

private sealed interface ContinuousEntry {
    val key: String

    data class Page(
        override val key: String,
        val globalPage: Int,
        val localPage: Int,
    ) : ContinuousEntry

    data class Transition(override val key: String, val label: String) : ContinuousEntry
}

/**
 * Flattens the window into scroll entries. A slim transition sits between adjacent chapters so the
 * hand-off stays legible without ever interrupting the scroll.
 */
private fun continuousEntries(window: List<ReaderWindowChapter>): List<ContinuousEntry> {
    val entries = mutableListOf<ContinuousEntry>()
    window.forEachIndexed { chapterIndex, chapter ->
        val chapterId = chapter.contentUnit().id().value()
        if (chapterIndex > 0) {
            entries += ContinuousEntry.Transition("transition:$chapterId", chapter.contentUnit().title())
        }
        repeat(chapter.pageCount()) { local ->
            entries += ContinuousEntry.Page(
                key = "$chapterId#$local",
                globalPage = chapter.firstGlobalPage() + local,
                localPage = local,
            )
        }
    }
    return entries
}

@Composable
private fun ReaderChapterTransition(title: String) {
    Text(
        text = title,
        color = Color.White.copy(alpha = 0.38f),
        style = MaterialTheme.typography.labelMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
    )
}

@Composable
private fun ReaderContinuousPage(
    controller: ReaderController,
    pageDecoder: (ByteArray) -> ImageBitmap?,
    globalPage: Int,
    pageIndex: Int,
    direction: ReadingDirection,
    display: ReaderDisplayPreferences,
    revision: Int,
    widthScale: Float,
    knownAspectRatio: Float?,
    rememberAspectRatio: (Float) -> Unit,
) {
    var retry by remember(controller, globalPage) { mutableIntStateOf(0) }
    var decoded by remember(controller, globalPage, revision, retry) {
        mutableStateOf<Result<ImageBitmap>?>(null)
    }
    CrashSafeLaunchedEffect(controller, globalPage, revision, retry) {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                requireNotNull(pageDecoder(controller.windowPage(globalPage))) {
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
                    modifier = Modifier
                        .fillMaxWidth(widthScale.coerceIn(0.25f, 1f))
                        .aspectRatio(image.width.toFloat() / image.height),
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

private fun stablePageSize(knownAspectRatio: Float?, fallbackHeightDp: Int): Modifier =
    if (knownAspectRatio == null) {
        Modifier.fillMaxWidth().height(fallbackHeightDp.dp)
    } else {
        Modifier.fillMaxWidth().aspectRatio(knownAspectRatio)
    }
