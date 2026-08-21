package fr.vriege.anilib.platform.compose

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import fr.vriege.anilib.feature.reader.ReadingDirection

/**
 * Keyboard commands the reader understands, resolved per reading mode: paged modes (LTR/RTL)
 * turn pages, continuous modes (vertical/webtoon) scroll and move between chapters instead,
 * since they have no page-by-page navigation of their own.
 */
internal enum class ReaderKeyCommand {
    PREVIOUS_PAGE,
    NEXT_PAGE,
    SCROLL_UP,
    SCROLL_DOWN,
    PREVIOUS_CHAPTER,
    NEXT_CHAPTER,
    FIRST_PAGE,
    LAST_PAGE,
    ZOOM_IN,
    ZOOM_OUT,
    ZOOM_RESET,
}

/**
 * Resolves a key-down event to a reader command, or null when the key isn't bound.
 * Left/right are mirrored for right-to-left reading, matching tap-zone mirroring elsewhere.
 */
internal fun resolveReaderKeyCommand(
    event: KeyEvent,
    direction: ReadingDirection,
    continuous: Boolean,
): ReaderKeyCommand? {
    if (event.type != KeyEventType.KeyDown) return null
    val rtl = direction == ReadingDirection.RIGHT_TO_LEFT
    return when (event.key) {
        Key.DirectionLeft -> if (continuous) {
            ReaderKeyCommand.PREVIOUS_CHAPTER
        } else if (rtl) {
            ReaderKeyCommand.NEXT_PAGE
        } else {
            ReaderKeyCommand.PREVIOUS_PAGE
        }
        Key.DirectionRight -> if (continuous) {
            ReaderKeyCommand.NEXT_CHAPTER
        } else if (rtl) {
            ReaderKeyCommand.PREVIOUS_PAGE
        } else {
            ReaderKeyCommand.NEXT_PAGE
        }
        Key.DirectionUp, Key.PageUp, Key.Backspace ->
            if (continuous) ReaderKeyCommand.SCROLL_UP else ReaderKeyCommand.PREVIOUS_PAGE
        Key.DirectionDown, Key.PageDown, Key.Spacebar ->
            if (continuous) ReaderKeyCommand.SCROLL_DOWN else ReaderKeyCommand.NEXT_PAGE
        Key.MoveHome -> ReaderKeyCommand.FIRST_PAGE
        Key.MoveEnd -> ReaderKeyCommand.LAST_PAGE
        Key.Plus, Key.Equals, Key.NumPadAdd -> ReaderKeyCommand.ZOOM_IN
        Key.Minus, Key.NumPadSubtract -> ReaderKeyCommand.ZOOM_OUT
        Key.Zero, Key.NumPad0 -> ReaderKeyCommand.ZOOM_RESET
        else -> null
    }
}
