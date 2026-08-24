package fr.vriege.anilib.platform.compose

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.staticCompositionLocalOf
import fr.vriege.anilib.feature.downloads.DownloadId
import fr.vriege.anilib.feature.downloads.DownloadQueueSnapshot
import fr.vriege.anilib.feature.downloads.ui.DownloadPresentation
import fr.vriege.anilib.feature.library.LibraryItemId
import fr.vriege.anilib.feature.source.SourceContentUnitId

internal data class DownloadPreparationKey(
    val libraryItemId: LibraryItemId,
    val contentId: String?,
)

private data class PendingDownloadPreparation(
    val token: Long,
    val key: DownloadPreparationKey,
    val registeredId: DownloadId?,
)

internal class DownloadPreparationState {
    private var pending by mutableStateOf<List<PendingDownloadPreparation>>(emptyList())
    private var nextToken = 0L

    val pendingCount: Int
        get() = pending.size

    fun pendingKeys(): List<DownloadPreparationKey> = pending.map(PendingDownloadPreparation::key)

    fun track(key: DownloadPreparationKey, action: () -> DownloadId): DownloadId {
        val token = begin(key)
        return try {
            action().also { registered(token, it) }
        } catch (failure: Throwable) {
            remove(token)
            throw failure
        }
    }

    @Synchronized
    fun reconcile(queue: DownloadQueueSnapshot?) {
        if (queue == null || pending.none { it.registeredId != null }) return
        val visibleIds = queue.jobs().mapTo(mutableSetOf()) { it.id() }
        update { entries ->
            entries.filterNot { it.registeredId != null && it.registeredId in visibleIds }
        }
    }

    @Synchronized
    private fun begin(key: DownloadPreparationKey): Long {
        val token = nextToken++
        update { it + PendingDownloadPreparation(token, key, null) }
        return token
    }

    @Synchronized
    private fun registered(token: Long, id: DownloadId) {
        update { entries ->
            entries.map { entry -> if (entry.token == token) entry.copy(registeredId = id) else entry }
        }
    }

    @Synchronized
    private fun remove(token: Long) {
        update { entries -> entries.filterNot { it.token == token } }
    }

    private fun update(transform: (List<PendingDownloadPreparation>) -> List<PendingDownloadPreparation>) {
        Snapshot.withMutableSnapshot {
            pending = transform(pending)
        }
    }
}

internal class PreparingDownloadPresentation(
    private val delegate: DownloadPresentation,
    private val preparation: DownloadPreparationState,
) : DownloadPresentation by delegate {
    override fun enqueue(libraryItemId: LibraryItemId): DownloadId = preparation.track(
        DownloadPreparationKey(libraryItemId, null),
    ) { delegate.enqueue(libraryItemId) }

    override fun enqueue(
        libraryItemId: LibraryItemId,
        contentUnitId: SourceContentUnitId,
    ): DownloadId = preparation.track(
        DownloadPreparationKey(libraryItemId, contentUnitId.value()),
    ) { delegate.enqueue(libraryItemId, contentUnitId) }

    override fun enqueue(libraryItemId: LibraryItemId, sourceContentId: String): DownloadId = preparation.track(
        DownloadPreparationKey(libraryItemId, sourceContentId),
    ) { delegate.enqueue(libraryItemId, sourceContentId) }
}

internal val LocalDownloadPreparationState = staticCompositionLocalOf<DownloadPreparationState?> { null }
