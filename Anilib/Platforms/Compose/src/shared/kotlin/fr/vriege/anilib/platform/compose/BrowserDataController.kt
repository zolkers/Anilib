package fr.vriege.anilib.platform.compose

fun interface BrowserDataController {
    fun clearData(): BrowserDataClearResult
}

data class BrowserDataClearResult(
    val successful: Boolean,
    val message: String,
)
