package fr.vriege.anilib.platform.compose

/** Platform-owned WebView cache and site-storage maintenance boundary. */
fun interface BrowserDataController {
    fun clearData(): BrowserDataClearResult
}

data class BrowserDataClearResult(
    val successful: Boolean,
    val message: String,
)
