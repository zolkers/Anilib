package fr.vriege.anilib.platform.compose

data class BrowserRuntimeStatus(val available: Boolean, val message: String) {
    companion object {
        fun ready(): BrowserRuntimeStatus = BrowserRuntimeStatus(true, "")

        fun unavailable(message: String): BrowserRuntimeStatus = BrowserRuntimeStatus(false, message)
    }
}
