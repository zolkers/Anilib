package fr.vriege.anilib.platform.compose

/** Platform browser-engine readiness without leaking an SDK type into shared UI. */
data class BrowserRuntimeStatus(val available: Boolean, val message: String) {
    companion object {
        fun ready(): BrowserRuntimeStatus = BrowserRuntimeStatus(true, "")

        fun unavailable(message: String): BrowserRuntimeStatus = BrowserRuntimeStatus(false, message)
    }
}
