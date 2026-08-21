package fr.vriege.anilib.platform.compose

/**
 * One page at a time. Vertical keys turn pages too, because there is nothing to scroll; horizontal
 * keys are mirrored for right-to-left titles so "forward" always follows the reading direction.
 */
internal data class ReaderPagedMode(override val mirrored: Boolean) : ReaderMode {

    override val continuous: Boolean = false
    override val twoAxisZoom: Boolean = true

    override fun keyCommand(key: ReaderKeyStroke): ReaderKeyCommand? = when (key) {
        ReaderKeyStroke.LEFT -> if (mirrored) ReaderKeyCommand.NEXT_PAGE else ReaderKeyCommand.PREVIOUS_PAGE
        ReaderKeyStroke.RIGHT -> if (mirrored) ReaderKeyCommand.PREVIOUS_PAGE else ReaderKeyCommand.NEXT_PAGE
        ReaderKeyStroke.UP, ReaderKeyStroke.PAGE_UP -> ReaderKeyCommand.PREVIOUS_PAGE
        ReaderKeyStroke.DOWN, ReaderKeyStroke.PAGE_DOWN -> ReaderKeyCommand.NEXT_PAGE
        ReaderKeyStroke.HOME -> ReaderKeyCommand.FIRST_PAGE
        ReaderKeyStroke.END -> ReaderKeyCommand.LAST_PAGE
        ReaderKeyStroke.ZOOM_IN -> ReaderKeyCommand.ZOOM_IN
        ReaderKeyStroke.ZOOM_OUT -> ReaderKeyCommand.ZOOM_OUT
        ReaderKeyStroke.ZOOM_RESET -> ReaderKeyCommand.ZOOM_RESET
    }
}
