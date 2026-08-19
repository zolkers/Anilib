package fr.vriege.anilib.platform.compose

class BrowserRuntimeStatus private constructor(
    val available: Boolean,
    val message: String,
    private val initializer: (() -> BrowserRuntimeStatus)?,
) {
    @Volatile
    private var resolved: BrowserRuntimeStatus? = null

    fun currentOrNull(): BrowserRuntimeStatus? = resolved ?: if (initializer == null) this else null

    fun resolve(): BrowserRuntimeStatus {
        currentOrNull()?.let { return it }
        return synchronized(this) {
            currentOrNull() ?: initializer!!().also { resolved = it }
        }
    }

    companion object {
        fun ready(): BrowserRuntimeStatus = BrowserRuntimeStatus(true, "", null)

        fun unavailable(message: String): BrowserRuntimeStatus =
            BrowserRuntimeStatus(false, message, null)

        fun deferred(initializer: () -> BrowserRuntimeStatus): BrowserRuntimeStatus =
            BrowserRuntimeStatus(false, "", initializer)
    }
}
