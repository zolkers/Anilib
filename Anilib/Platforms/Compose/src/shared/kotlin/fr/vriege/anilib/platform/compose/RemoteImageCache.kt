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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
    private val activeReferences = mutableMapOf<String, Int>()
    private var cachedBytes = 0L

    @Synchronized
    fun get(key: String): ImageBitmap? = entries[key]?.image

    @Synchronized
    fun acquire(key: String) {
        activeReferences[key] = activeReferences.getOrDefault(key, 0) + 1
        entries[key]
    }

    fun release(key: String) {
        val unusedLoad = synchronized(this) {
            val references = activeReferences[key] ?: return
            if (references > 1) {
                activeReferences[key] = references - 1
                null
            } else {
                activeReferences.remove(key)
                trimToBudget()
                inFlight.remove(key)
            }
        }
        unusedLoad?.cancel()
    }

    suspend fun load(key: String, loader: suspend () -> ImageBitmap): Result<ImageBitmap> {
        get(key)?.let { return Result.success(it) }
        val pending = synchronized(this) {
            entries[key]?.let { return Result.success(it.image) }
            inFlight[key] ?: createLoad(key, loader).also { inFlight[key] = it }
        }
        pending.start()
        return pending.await()
    }

    private fun createLoad(
        key: String,
        loader: suspend () -> ImageBitmap,
    ): Deferred<Result<ImageBitmap>> {
        lateinit var pending: Deferred<Result<ImageBitmap>>
        pending = scope.async(start = CoroutineStart.LAZY) {
            try {
                permits.withPermit {
                    try {
                        val image = loader()
                        currentCoroutineContext().ensureActive()
                        put(key, image)
                        Result.success(image)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Throwable) {
                        Result.failure(failure)
                    }
                }
            } finally {
                synchronized(this@RemoteImageCache) {
                    if (inFlight[key] === pending) {
                        inFlight.remove(key)
                    }
                }
            }
        }
        return pending
    }

    @Synchronized
    private fun put(key: String, image: ImageBitmap) {
        val bytes = image.width.toLong() * image.height.toLong() * 4L
        if (bytes > MAXIMUM_REMOTE_IMAGE_CACHE_BYTES) return
        entries.put(key, Entry(image, bytes))?.let { cachedBytes -= it.bytes }
        cachedBytes += bytes
        trimToBudget()
    }

    private fun trimToBudget() {
        while (cachedBytes > MAXIMUM_REMOTE_IMAGE_CACHE_BYTES ||
            entries.size > MAXIMUM_REMOTE_IMAGE_CACHE_ENTRIES
        ) {
            val iterator = entries.entries.iterator()
            var removed = false
            while (iterator.hasNext()) {
                val candidate = iterator.next()
                if (activeReferences.containsKey(candidate.key)) {
                    continue
                }
                cachedBytes -= candidate.value.bytes
                iterator.remove()
                removed = true
                break
            }
            if (!removed) {
                return
            }
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
