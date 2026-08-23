package fr.vriege.anilib.platform.compose

/** A page-snapping vertical pager, distinct from the free-scrolling webtoon viewer. */
internal data object ReaderVerticalMode : ReaderMode {

    override val continuous: Boolean = false
    override val verticalPager: Boolean = true
    override val mirrored: Boolean = false
    override val twoAxisZoom: Boolean = true

    override fun keyCommand(key: ReaderKeyStroke): ReaderKeyCommand? = when (key) {
        ReaderKeyStroke.LEFT, ReaderKeyStroke.UP, ReaderKeyStroke.PAGE_UP -> ReaderKeyCommand.PREVIOUS_PAGE
        ReaderKeyStroke.RIGHT, ReaderKeyStroke.DOWN, ReaderKeyStroke.PAGE_DOWN -> ReaderKeyCommand.NEXT_PAGE
        ReaderKeyStroke.HOME -> ReaderKeyCommand.FIRST_PAGE
        ReaderKeyStroke.END -> ReaderKeyCommand.LAST_PAGE
        ReaderKeyStroke.ZOOM_IN -> ReaderKeyCommand.ZOOM_IN
        ReaderKeyStroke.ZOOM_OUT -> ReaderKeyCommand.ZOOM_OUT
        ReaderKeyStroke.ZOOM_RESET -> ReaderKeyCommand.ZOOM_RESET
    }
}
