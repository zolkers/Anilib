package fr.vriege.anilib.platform.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import fr.vriege.anilib.feature.reader.ui.ReaderWindowChapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

private const val VERTICAL_DECODE_PREFETCH_FORWARD = 3
private const val VERTICAL_DECODE_PREFETCH_BACKWARD = 1

/**
 * A true vertical pager: every gesture settles on exactly one page while the underlying chapter
 * window keeps the neighbouring chapters ready. This is intentionally separate from webtoon,
 * whose scroll is continuous and whose page heights form one long strip.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ReaderVerticalPages(
    controller: ReaderController,
    pageDecoder: (ByteArray) -> ImageBitmap?,
    window: List<ReaderWindowChapter>,
    initialGlobalPage: Int,
    display: ReaderDisplayPreferences,
    zoomScale: Float,
    scrollTarget: Int?,
    consumeScrollTarget: () -> Unit,
    pageSelected: (Int) -> Unit,
    chapterChanged: () -> Unit,
    toggleControls: () -> Unit,
    toggleZoom: () -> Unit,
) {
    val current = window.firstOrNull(ReaderWindowChapter::current)
    val entries = remember(window) { readerWindowEntries(window) }
    val initialPage = remember(controller) {
        entries.indexOfFirst { it is ReaderWindowEntry.Page && it.globalPage == initialGlobalPage }
            .coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(initialPage = initialPage) { entries.size }
    val decodeScope = rememberCrashSafeCoroutineScope()
    val decodedPages = remember(controller) { ReaderDecodedPageCache(decodeScope) }

    CrashSafeLaunchedEffect(pagerState, controller, entries) {
        snapshotFlow { pagerState.settledPage }
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
            if (index >= 0) pagerState.animateScrollToPage(index)
            consumeScrollTarget()
        }
    }
    CrashSafeLaunchedEffect(pagerState, controller, entries) {
        var previousPage = pagerState.currentPage
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { index ->
                val movingForward = index >= previousPage
                previousPage = index
                val forward = if (movingForward) {
                    VERTICAL_DECODE_PREFETCH_FORWARD
                } else {
                    VERTICAL_DECODE_PREFETCH_BACKWARD
                }
                val backward = if (movingForward) {
                    VERTICAL_DECODE_PREFETCH_BACKWARD
                } else {
                    VERTICAL_DECODE_PREFETCH_FORWARD
                }
                val targets = buildList {
                    for (candidate in (index + 1)..(index + forward)) add(candidate)
                    for (candidate in (index - 1) downTo (index - backward)) add(candidate)
                }.mapNotNull { entries.getOrNull(it) as? ReaderWindowEntry.Page }
                decodedPages.retainPrefetch(targets.mapTo(mutableSetOf(), ReaderWindowEntry.Page::key))
                targets.forEach { page ->
                    decodedPages.prefetch(page.key) {
                        requireNotNull(pageDecoder(controller.windowPage(page.globalPage))) {
                            "Unsupported page image format"
                        }
                    }
                }
            }
    }

    VerticalPager(
        state = pagerState,
        modifier = Modifier
            .fillMaxSize()
            .testTag("reader-vertical-pages")
            .combinedClickable(onClick = toggleControls, onDoubleClick = toggleZoom),
        beyondViewportPageCount = 1,
        userScrollEnabled = zoomScale <= 1.01f,
        key = { entries[it].key },
    ) { index ->
        when (val entry = entries[index]) {
            is ReaderWindowEntry.Transition -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                ReaderChapterTransition(entry.label)
            }
            is ReaderWindowEntry.Page -> ReaderVerticalPage(
                controller = controller,
                pageDecoder = pageDecoder,
                entry = entry,
                display = display,
                decodedPages = decodedPages,
            )
        }
    }
}

@Composable
private fun ReaderVerticalPage(
    controller: ReaderController,
    pageDecoder: (ByteArray) -> ImageBitmap?,
    entry: ReaderWindowEntry.Page,
    display: ReaderDisplayPreferences,
    decodedPages: ReaderDecodedPageCache,
) {
    var retry by remember(controller, entry.key) { mutableIntStateOf(0) }
    var decoded by remember(controller, entry.key, retry) {
        mutableStateOf<Result<ImageBitmap>?>(decodedPages.get(entry.key)?.let { Result.success(it) })
    }
    CrashSafeLaunchedEffect(controller, entry.key, retry) {
        if (decoded != null) return@CrashSafeLaunchedEffect
        decoded = decodedPages.load(entry.key) {
            requireNotNull(pageDecoder(controller.windowPage(entry.globalPage))) {
                "Unsupported page image format"
            }
        }
    }

    when (val result = decoded) {
        null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
        else -> result.fold(
            onSuccess = { image ->
                ReaderPageImage(
                    image = image,
                    pageIndex = entry.localPage,
                    display = display,
                    zoomed = false,
                    splitSecondHalf = false,
                    direction = ReadingDirection.VERTICAL,
                    modifier = Modifier.fillMaxSize(),
                )
            },
            onFailure = { failure ->
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
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
