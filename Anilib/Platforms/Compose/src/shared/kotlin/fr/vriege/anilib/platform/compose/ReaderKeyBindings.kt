package fr.vriege.anilib.platform.compose

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

/**
 * Commands the reader understands. What each key means is decided by the active [ReaderMode],
 * never here, so adding a reading mode never touches this file.
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

/** Translates a Compose key event into a direction-neutral stroke, or null when unbound. */
private fun readerKeyStroke(event: KeyEvent): ReaderKeyStroke? = when (event.key) {
    Key.DirectionLeft -> ReaderKeyStroke.LEFT
    Key.DirectionRight -> ReaderKeyStroke.RIGHT
    Key.DirectionUp -> ReaderKeyStroke.UP
    Key.DirectionDown -> ReaderKeyStroke.DOWN
    Key.PageUp, Key.Backspace -> ReaderKeyStroke.PAGE_UP
    Key.PageDown, Key.Spacebar -> ReaderKeyStroke.PAGE_DOWN
    Key.MoveHome -> ReaderKeyStroke.HOME
    Key.MoveEnd -> ReaderKeyStroke.END
    Key.Plus, Key.Equals, Key.NumPadAdd -> ReaderKeyStroke.ZOOM_IN
    Key.Minus, Key.NumPadSubtract -> ReaderKeyStroke.ZOOM_OUT
    Key.Zero, Key.NumPad0 -> ReaderKeyStroke.ZOOM_RESET
    else -> null
}

/** Resolves a key-down event to a command using the active mode's bindings. */
internal fun resolveReaderKeyCommand(event: KeyEvent, mode: ReaderMode): ReaderKeyCommand? {
    if (event.type != KeyEventType.KeyDown) return null
    return readerKeyStroke(event)?.let(mode::keyCommand)
}
