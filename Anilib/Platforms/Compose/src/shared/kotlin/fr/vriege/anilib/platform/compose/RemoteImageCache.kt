package fr.vriege.anilib.platform.compose

import androidx.compose.ui.graphics.ImageBitmap
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private const val MAXIMUM_REMOTE_IMAGE_CACHE_BYTES = 96L * 1024L * 1024L
private const val MAXIMUM_REMOTE_IMAGE_CACHE_ENTRIES = 160
private const val REMOTE_IMAGE_LOAD_PARALLELISM = 4

internal object RemoteImageCache {
    private data class Entry(val image: ImageBitmap, val bytes: Long)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val permits = Semaphore(REMOTE_IMAGE_LOAD_PARALLELISM)
    private val entries = LinkedHashMap<String, Entry>(64, 0.75f, true)
    private val inFlight = mutableMapOf<String, Deferred<Result<ImageBitmap>>>()
    private var cachedBytes = 0L

    @Synchronized
    fun get(key: String): ImageBitmap? = entries[key]?.image

    suspend fun load(key: String, loader: suspend () -> ImageBitmap): Result<ImageBitmap> {
        get(key)?.let { return Result.success(it) }
        val pending = synchronized(this) {
            entries[key]?.let { return Result.success(it.image) }
            inFlight[key] ?: scope.async(start = CoroutineStart.LAZY) {
                try {
                    permits.withPermit {
                        try {
                            Result.success(loader()).also { result ->
                                result.getOrNull()?.let { put(key, it) }
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Throwable) {
                            Result.failure(failure)
                        }
                    }
                } finally {
                    synchronized(this@RemoteImageCache) { inFlight.remove(key) }
                }
            }.also { inFlight[key] = it }
        }
        pending.start()
        return pending.await()
    }

    @Synchronized
    private fun put(key: String, image: ImageBitmap) {
        val bytes = image.width.toLong() * image.height.toLong() * 4L
        if (bytes > MAXIMUM_REMOTE_IMAGE_CACHE_BYTES) return
        entries.put(key, Entry(image, bytes))?.let { cachedBytes -= it.bytes }
        cachedBytes += bytes
        val iterator = entries.entries.iterator()
        while ((cachedBytes > MAXIMUM_REMOTE_IMAGE_CACHE_BYTES ||
                entries.size > MAXIMUM_REMOTE_IMAGE_CACHE_ENTRIES) && iterator.hasNext()) {
            cachedBytes -= iterator.next().value.bytes
            iterator.remove()
        }
    }
}

internal fun remoteImageCacheKey(
    purpose: String,
    uri: URI,
    environment: ExtensionIconEnvironment,
): String = buildString {
    append(purpose)
    append(':')
    append(System.identityHashCode(environment.httpClient))
    append(':')
    append(System.identityHashCode(environment.decode))
    append(':')
    append(uri.toASCIIString())
}
