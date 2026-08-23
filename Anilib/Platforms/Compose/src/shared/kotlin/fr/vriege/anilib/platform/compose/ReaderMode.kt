package fr.vriege.anilib.platform.compose

import fr.vriege.anilib.feature.reader.ReadingDirection

/**
 * How a reading direction behaves, so the reader shell never branches on [ReadingDirection]
 * itself. Each mode owns its own viewer file and answers these questions for it:
 *
 *  - [ReaderPagedMode] (left-to-right, right-to-left, vertical) shows one page at a time; up/down
 *    turn pages, and horizontal keys follow the reading direction.
 *  - [ReaderContinuousMode] (webtoon) scrolls a chapter window; up/down scroll by a viewport,
 *    horizontal keys move between chapters, and zoom only changes page width.
 */
internal sealed interface ReaderMode {

    /** True when pages flow in one uninterrupted scroll rather than one page at a time. */
    val continuous: Boolean

    /** True when horizontal navigation is mirrored, i.e. left advances instead of going back. */
    val mirrored: Boolean

    /** True when zoom may scale both axes; continuous reading scales width only. */
    val twoAxisZoom: Boolean

    fun keyCommand(key: ReaderKeyStroke): ReaderKeyCommand?

    companion object {
        fun of(direction: ReadingDirection): ReaderMode = when (direction) {
            ReadingDirection.LEFT_TO_RIGHT -> ReaderPagedMode(mirrored = false)
            ReadingDirection.RIGHT_TO_LEFT -> ReaderPagedMode(mirrored = true)
            ReadingDirection.VERTICAL -> ReaderPagedMode(mirrored = false)
            ReadingDirection.WEBTOON -> ReaderContinuousMode
        }
    }
}

/** Direction-neutral description of a key press, so modes never touch Compose key types. */
internal enum class ReaderKeyStroke {
    LEFT,
    RIGHT,
    UP,
    DOWN,
    PAGE_UP,
    PAGE_DOWN,
    HOME,
    END,
    ZOOM_IN,
    ZOOM_OUT,
    ZOOM_RESET,
}
