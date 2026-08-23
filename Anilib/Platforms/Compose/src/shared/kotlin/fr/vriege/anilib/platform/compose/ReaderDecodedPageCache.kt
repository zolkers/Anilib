package fr.vriege.anilib.platform.compose

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async

private const val DEFAULT_DECODED_PAGE_CACHE_BYTES = 128L * 1024L * 1024L
private const val DEFAULT_DECODED_PAGE_CACHE_ENTRIES = 24

/*
 * Reader-scoped cache for decoded images.
 *
 * Lazy lists dispose off-screen composables. Keeping the decoded bitmap in an item-local
 * `remember` therefore makes a page flash back to loading whenever the user scrolls away and
 * returns. This cache lives at reader scope, deduplicates concurrent loads, and evicts the least
 * recently used images by both decoded size and entry count.
 */
internal class ReaderDecodedPageCache(
    private val scope: CoroutineScope,
    private val maximumBytes: Long = DEFAULT_DECODED_PAGE_CACHE_BYTES,
    private val maximumEntries: Int = DEFAULT_DECODED_PAGE_CACHE_ENTRIES,
) {
    private data class Entry(val image: ImageBitmap, val bytes: Long)

    private val entries = LinkedHashMap<String, Entry>(32, 0.75f, true)
    private val inFlight = mutableMapOf<String, Deferred<Result<ImageBitmap>>>()
    private var cachedBytes = 0L

    init {
        require(maximumBytes > 0) { "maximumBytes must be positive" }
        require(maximumEntries > 0) { "maximumEntries must be positive" }
    }

    @Synchronized
    fun get(key: String): ImageBitmap? = entries[key]?.image

    suspend fun load(key: String, loader: suspend () -> ImageBitmap): Result<ImageBitmap> {
        get(key)?.let { return Result.success(it) }
        val pending = synchronized(this) {
            entries[key]?.let { return Result.success(it.image) }
            inFlight[key] ?: scope.async(Dispatchers.IO) {
                try {
                    val result = try {
                        Result.success(loader())
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Throwable) {
                        Result.failure(failure)
                    }
                    result.getOrNull()?.let { put(key, it) }
                    result
                } finally {
                    synchronized(this@ReaderDecodedPageCache) { inFlight.remove(key) }
                }
            }.also { inFlight[key] = it }
        }
        return pending.await()
    }

    @Synchronized
    private fun put(key: String, image: ImageBitmap) {
        val bytes = image.width.toLong() * image.height.toLong() * 4L
        if (bytes > maximumBytes) return
        entries.put(key, Entry(image, bytes))?.let { cachedBytes -= it.bytes }
        cachedBytes += bytes
        val iterator = entries.entries.iterator()
        while ((cachedBytes > maximumBytes || entries.size > maximumEntries) && iterator.hasNext()) {
            val oldest = iterator.next()
            cachedBytes -= oldest.value.bytes
            iterator.remove()
        }
    }
}
