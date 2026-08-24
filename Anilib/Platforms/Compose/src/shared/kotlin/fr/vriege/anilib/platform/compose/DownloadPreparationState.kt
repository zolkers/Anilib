package fr.vriege.anilib.platform.compose

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.staticCompositionLocalOf
import fr.vriege.anilib.feature.downloads.DownloadId
import fr.vriege.anilib.feature.downloads.ui.DownloadPresentation
import fr.vriege.anilib.feature.library.LibraryItemId
import fr.vriege.anilib.feature.source.SourceContentUnitId

internal class DownloadPreparationState {
    var pendingCount by mutableIntStateOf(0)
        private set

    fun <T> track(action: () -> T): T {
        update(1)
        return try {
            action()
        } finally {
            update(-1)
        }
    }

    @Synchronized
    private fun update(delta: Int) {
        Snapshot.withMutableSnapshot {
            pendingCount = (pendingCount + delta).coerceAtLeast(0)
        }
    }
}

internal class PreparingDownloadPresentation(
    private val delegate: DownloadPresentation,
    private val preparation: DownloadPreparationState,
) : DownloadPresentation by delegate {
    override fun enqueue(libraryItemId: LibraryItemId): DownloadId = preparation.track {
        delegate.enqueue(libraryItemId)
    }

    override fun enqueue(
        libraryItemId: LibraryItemId,
        contentUnitId: SourceContentUnitId,
    ): DownloadId = preparation.track {
        delegate.enqueue(libraryItemId, contentUnitId)
    }

    override fun enqueue(libraryItemId: LibraryItemId, sourceContentId: String): DownloadId = preparation.track {
        delegate.enqueue(libraryItemId, sourceContentId)
    }
}

internal val LocalDownloadPreparationState = staticCompositionLocalOf<DownloadPreparationState?> { null }
