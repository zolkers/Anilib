package fr.vriege.anilib.platform.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.vriege.anilib.feature.downloads.DownloadJobSnapshot
import fr.vriege.anilib.feature.downloads.DownloadQueueSnapshot
import fr.vriege.anilib.feature.downloads.DownloadStatus
import fr.vriege.anilib.feature.library.LibraryItemId

internal data class DownloadUiProgress(
    val status: DownloadStatus,
    val fraction: Float,
)

internal class DownloadProgressIndex(
    queue: DownloadQueueSnapshot?,
    private val preparing: List<DownloadPreparationKey>,
) {
    private val jobsByContent = queue?.jobs().orEmpty()
        .asSequence()
        .filter { it.status() != DownloadStatus.CANCELLED }
        .groupBy { it.libraryItemId() to it.contentUnit().id().value() }
        .mapValues { (_, jobs) -> jobs.maxBy(DownloadJobSnapshot::updatedAt) }
    private val jobsByTitle = jobsByContent.values.groupBy(DownloadJobSnapshot::libraryItemId)

    fun title(libraryItemId: LibraryItemId): DownloadUiProgress? = progress(
        jobsByTitle[libraryItemId].orEmpty(),
        preparing.any { it.libraryItemId == libraryItemId },
    )

    fun titles(libraryItemIds: Set<LibraryItemId>): DownloadUiProgress? = progress(
        libraryItemIds.flatMap { jobsByTitle[it].orEmpty() },
        preparing.any { it.libraryItemId in libraryItemIds },
    )

    fun content(libraryItemId: LibraryItemId, contentId: String): DownloadUiProgress? = progress(
        listOfNotNull(jobsByContent[libraryItemId to contentId]),
        preparing.any { it.libraryItemId == libraryItemId && it.contentId == contentId },
    )

    private fun progress(jobs: List<DownloadJobSnapshot>, isPreparing: Boolean): DownloadUiProgress? {
        if (jobs.isEmpty() && !isPreparing) return null
        val total = jobs.sumOf(DownloadJobSnapshot::totalPages).coerceAtLeast(1)
        val completed = jobs.sumOf(DownloadJobSnapshot::completedPages)
        return DownloadUiProgress(
            status = when {
                jobs.any { it.status() == DownloadStatus.DOWNLOADING } -> DownloadStatus.DOWNLOADING
                isPreparing -> DownloadStatus.QUEUED
                else -> aggregateDownloadStatus(jobs)
            },
            fraction = (completed.toFloat() / total).coerceIn(0f, 1f),
        )
    }
}

@Composable
internal fun rememberDownloadProgressIndex(queue: DownloadQueueSnapshot?): DownloadProgressIndex {
    val pending = LocalDownloadPreparationState.current?.pendingKeys().orEmpty()
    return remember(queue, pending) { DownloadProgressIndex(queue, pending) }
}

private fun aggregateDownloadStatus(jobs: List<DownloadJobSnapshot>): DownloadStatus = when {
    jobs.any { it.status() == DownloadStatus.DOWNLOADING } -> DownloadStatus.DOWNLOADING
    jobs.any { it.status() == DownloadStatus.QUEUED } -> DownloadStatus.QUEUED
    jobs.any { it.status() == DownloadStatus.PAUSED } -> DownloadStatus.PAUSED
    jobs.any { it.status() == DownloadStatus.FAILED } -> DownloadStatus.FAILED
    jobs.all { it.status() == DownloadStatus.COMPLETED } -> DownloadStatus.COMPLETED
    else -> DownloadStatus.CANCELLED
}

@Composable
internal fun DownloadActionButton(
    progress: DownloadUiProgress?,
    enabled: Boolean,
    action: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = action,
        enabled = enabled && progress == null,
        modifier = modifier,
    ) {
        when (progress?.status) {
            DownloadStatus.DOWNLOADING -> CircularProgressIndicator(
                progress = { progress.fraction },
                modifier = Modifier.size(23.dp),
                strokeWidth = 2.5.dp,
            )
            DownloadStatus.QUEUED -> CircularProgressIndicator(
                modifier = Modifier.size(23.dp),
                strokeWidth = 2.5.dp,
            )
            DownloadStatus.PAUSED -> Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { progress.fraction },
                    modifier = Modifier.size(23.dp),
                    strokeWidth = 2.5.dp,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Icon(
                    Icons.Default.Pause,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.secondary,
                )
            }
            DownloadStatus.COMPLETED -> Icon(
                Icons.Default.CheckCircle,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.primary,
            )
            DownloadStatus.FAILED -> Icon(
                Icons.Default.Error,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.error,
            )
            DownloadStatus.CANCELLED, null -> Icon(
                Icons.Outlined.Download,
                contentDescription = contentDescription,
            )
        }
    }
}
