package fr.vriege.anilib.platform.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import fr.vriege.anilib.feature.downloads.DownloadQueueSnapshot
import fr.vriege.anilib.feature.downloads.ui.DownloadPresentation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
internal fun rememberDownloadQueueSnapshot(
    presentation: DownloadPresentation,
): DownloadQueueSnapshot? {
    val queue = LocalDownloadQueueState.current?.value
        ?: rememberDownloadQueueState(presentation).value
    val preparation = LocalDownloadPreparationState.current
    SideEffect { preparation?.reconcile(queue) }
    return queue
}

internal val LocalDownloadQueueState = staticCompositionLocalOf<State<DownloadQueueSnapshot?>?> { null }

@Composable
internal fun rememberDownloadQueueState(
    presentation: DownloadPresentation,
): State<DownloadQueueSnapshot?> {
    val report = LocalUiFailureHandler.current
    return produceState<DownloadQueueSnapshot?>(null, presentation) {
        val changes = Channel<Unit>(Channel.CONFLATED)
        val observation = withContext(Dispatchers.IO) {
            presentation.observe { changes.trySend(Unit) }
        }
        try {
            changes.trySend(Unit)
            for (ignored in changes) {
                value = withContext(Dispatchers.IO) { presentation.queue() }
                delay(DOWNLOAD_UI_REFRESH_MILLIS)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            reportRecoverable(failure, report)
        } finally {
            changes.close()
            withContext(Dispatchers.IO) { runCatching { observation.close() } }
        }
    }
}

private const val DOWNLOAD_UI_REFRESH_MILLIS = 150L
