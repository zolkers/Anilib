package fr.vriege.anilib.platform.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
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
    val report = LocalUiFailureHandler.current
    val snapshot by produceState<DownloadQueueSnapshot?>(null, presentation) {
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
    return snapshot
}

private const val DOWNLOAD_UI_REFRESH_MILLIS = 150L
