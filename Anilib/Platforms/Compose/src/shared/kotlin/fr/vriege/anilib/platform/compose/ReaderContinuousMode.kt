package fr.vriege.anilib.platform.compose

/**
 * Uninterrupted scrolling over a window of chapters. Vertical keys scroll by a viewport rather
 * than turning pages, and horizontal keys move between chapters since there is no page-by-page
 * navigation to bind them to.
 */
internal data object ReaderContinuousMode : ReaderMode {

    override val continuous: Boolean = true
    override val verticalPager: Boolean = false
    override val mirrored: Boolean = false

    /** Continuous reading scales page width only; height follows from the page aspect ratio. */
    override val twoAxisZoom: Boolean = false

    override fun keyCommand(key: ReaderKeyStroke): ReaderKeyCommand? = when (key) {
        ReaderKeyStroke.LEFT -> ReaderKeyCommand.PREVIOUS_CHAPTER
        ReaderKeyStroke.RIGHT -> ReaderKeyCommand.NEXT_CHAPTER
        ReaderKeyStroke.UP, ReaderKeyStroke.PAGE_UP -> ReaderKeyCommand.SCROLL_UP
        ReaderKeyStroke.DOWN, ReaderKeyStroke.PAGE_DOWN -> ReaderKeyCommand.SCROLL_DOWN
        ReaderKeyStroke.HOME -> ReaderKeyCommand.FIRST_PAGE
        ReaderKeyStroke.END -> ReaderKeyCommand.LAST_PAGE
        ReaderKeyStroke.ZOOM_IN -> ReaderKeyCommand.ZOOM_IN
        ReaderKeyStroke.ZOOM_OUT -> ReaderKeyCommand.ZOOM_OUT
        ReaderKeyStroke.ZOOM_RESET -> ReaderKeyCommand.ZOOM_RESET
    }
}
