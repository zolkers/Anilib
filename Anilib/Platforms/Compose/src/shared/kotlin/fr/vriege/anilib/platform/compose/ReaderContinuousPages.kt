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
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
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
import kotlin.math.abs

private const val CONTINUOUS_SCROLL_STEP_FRACTION = 0.85f
private const val DECODE_PREFETCH_FORWARD = 5
private const val DECODE_PREFETCH_BACKWARD = 3
private const val DECODE_PREFETCH_FAST_FORWARD = 8
private const val DECODE_PREFETCH_FAST_BACKWARD = 1
private const val COMPOSE_CACHE_AHEAD_FRACTION = 1.5f
private const val COMPOSE_CACHE_BEHIND_FRACTION = 1f

/*
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
    val entries = remember(window) { readerWindowEntries(window) }
    val cacheWindow = remember {
        LazyLayoutCacheWindow(
            aheadFraction = COMPOSE_CACHE_AHEAD_FRACTION,
            behindFraction = COMPOSE_CACHE_BEHIND_FRACTION,
        )
    }
    val listState = rememberLazyListState(
        cacheWindow = cacheWindow,
        initialFirstVisibleItemIndex = entries
            .indexOfFirst { it is ReaderWindowEntry.Page && it.globalPage == initialGlobalPage }
            .coerceAtLeast(0),
    )
    val pageAspectRatios = remember(controller) { mutableStateMapOf<String, Float>() }
    val decodeScope = rememberCrashSafeCoroutineScope()
    val decodedPages = remember(controller) { ReaderDecodedPageCache(decodeScope) }
    val spacing = if (direction == ReadingDirection.WEBTOON) display.webtoonSpacingDp().dp else 15.dp

    CrashSafeLaunchedEffect(listState, controller, entries) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { index ->
                val page = entries.getOrNull(index) as? ReaderWindowEntry.Page ?: return@collect
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
            val index = entries.indexOfFirst { it is ReaderWindowEntry.Page && it.globalPage == global }
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
    CrashSafeLaunchedEffect(listState, controller, entries, revision) {
        var previousFirstVisible = listState.firstVisibleItemIndex
        snapshotFlow {
            val visible = listState.layoutInfo.visibleItemsInfo
            if (visible.isEmpty()) null else visible.first().index to visible.last().index
        }
            .distinctUntilChanged()
            .collect { range ->
                if (range == null) return@collect
                val movement = range.first - previousFirstVisible
                val scrollingForward = range.first >= previousFirstVisible
                previousFirstVisible = range.first
                val fastScroll = abs(movement) > 1
                val forwardDistance = if (scrollingForward) {
                    if (fastScroll) DECODE_PREFETCH_FAST_FORWARD else DECODE_PREFETCH_FORWARD
                } else {
                    if (fastScroll) DECODE_PREFETCH_FAST_BACKWARD else DECODE_PREFETCH_BACKWARD
                }
                val backwardDistance = if (scrollingForward) {
                    if (fastScroll) DECODE_PREFETCH_FAST_BACKWARD else DECODE_PREFETCH_BACKWARD
                } else {
                    if (fastScroll) DECODE_PREFETCH_FAST_FORWARD else DECODE_PREFETCH_FORWARD
                }
                val targets = buildList {
                    for (index in (range.second + 1)..(range.second + forwardDistance)) add(index)
                    for (index in (range.first - 1) downTo (range.first - backwardDistance)) add(index)
                }
                    .mapNotNull { entries.getOrNull(it) as? ReaderWindowEntry.Page }
                val visibleKeys = (range.first..range.second)
                    .mapNotNull { entries.getOrNull(it) as? ReaderWindowEntry.Page }
                    .map(ReaderWindowEntry.Page::key)
                val targetKeys = targets.mapTo(linkedSetOf(), ReaderWindowEntry.Page::key)
                decodedPages.touch(visibleKeys + targetKeys)
                decodedPages.retainPrefetch(targetKeys)
                targets.forEach { page ->
                    decodedPages.prefetch(page.key) {
                        decodeWindowPage(controller, page.globalPage, pageDecoder)
                    }
                }
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
        items(
            count = entries.size,
            key = { entries[it].key },
            contentType = {
                when (entries[it]) {
                    is ReaderWindowEntry.Page -> "page"
                    is ReaderWindowEntry.Transition -> "transition"
                }
            },
        ) { index ->
            when (val entry = entries[index]) {
                is ReaderWindowEntry.Transition -> ReaderChapterTransition(entry.label)
                is ReaderWindowEntry.Page -> ReaderContinuousPage(
                    controller = controller,
                    pageDecoder = pageDecoder,
                    globalPage = entry.globalPage,
                    pageIndex = entry.localPage,
                    direction = direction,
                    display = display,
                    revision = revision,
                    widthScale = widthScale,
                    pageKey = entry.key,
                    decodedPages = decodedPages,
                    knownAspectRatio = pageAspectRatios[entry.key],
                    rememberAspectRatio = { pageAspectRatios[entry.key] = it },
                )
            }
        }
    }
}

internal sealed interface ReaderWindowEntry {
    val key: String

    data class Page(
        override val key: String,
        val globalPage: Int,
        val localPage: Int,
    ) : ReaderWindowEntry

    data class Transition(override val key: String, val label: String) : ReaderWindowEntry
}

/*
 * Flattens the window into scroll entries. A slim transition sits between adjacent chapters so the
 * hand-off stays legible without ever interrupting the scroll.
 */
internal fun readerWindowEntries(window: List<ReaderWindowChapter>): List<ReaderWindowEntry> {
    val entries = mutableListOf<ReaderWindowEntry>()
    window.forEachIndexed { chapterIndex, chapter ->
        val chapterId = chapter.contentUnit().id().value()
        if (chapterIndex > 0) {
            entries += ReaderWindowEntry.Transition("transition:$chapterId", chapter.contentUnit().title())
        }
        repeat(chapter.pageCount()) { local ->
            entries += ReaderWindowEntry.Page(
                key = "$chapterId#$local",
                globalPage = chapter.firstGlobalPage() + local,
                localPage = local,
            )
        }
    }
    return entries
}

@Composable
internal fun ReaderChapterTransition(title: String) {
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
    pageKey: String,
    decodedPages: ReaderDecodedPageCache,
    knownAspectRatio: Float?,
    rememberAspectRatio: (Float) -> Unit,
) {
    var retry by remember(controller, globalPage) { mutableIntStateOf(0) }
    var decoded by remember(controller, pageKey, revision, retry) {
        mutableStateOf(decodedPages.get(pageKey)?.let { Result.success(it) })
    }
    CrashSafeLaunchedEffect(controller, pageKey, revision, retry) {
        if (decoded != null) return@CrashSafeLaunchedEffect
        val result = decodedPages.load(pageKey) {
            decodeWindowPage(controller, globalPage, pageDecoder)
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

private suspend fun decodeWindowPage(
    controller: ReaderController,
    globalPage: Int,
    pageDecoder: (ByteArray) -> ImageBitmap?,
): ImageBitmap {
    val bytes = controller.windowPage(globalPage)
    return withContext(Dispatchers.Default) {
        requireNotNull(pageDecoder(bytes)) { "Unsupported page image format" }
    }
}
