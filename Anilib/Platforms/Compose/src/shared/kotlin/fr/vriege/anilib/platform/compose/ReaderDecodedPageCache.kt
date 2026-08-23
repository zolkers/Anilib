package fr.vriege.anilib.platform.compose

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private const val DEFAULT_DECODED_PAGE_CACHE_BYTES = 128L * 1024L * 1024L
private const val DEFAULT_DECODED_PAGE_CACHE_ENTRIES = 24
private const val DEFAULT_PREFETCH_PARALLELISM = 2

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
    prefetchParallelism: Int = DEFAULT_PREFETCH_PARALLELISM,
) {
    private data class Entry(val image: ImageBitmap, val bytes: Long)

    private val entries = LinkedHashMap<String, Entry>(32, 0.75f, true)
    private val inFlight = mutableMapOf<String, Deferred<Result<ImageBitmap>>>()
    private val prefetchJobs = mutableMapOf<String, Job>()
    private val prefetchPermits = Semaphore(prefetchParallelism)
    private var cachedBytes = 0L

    init {
        require(maximumBytes > 0) { "maximumBytes must be positive" }
        require(maximumEntries > 0) { "maximumEntries must be positive" }
        require(prefetchParallelism > 0) { "prefetchParallelism must be positive" }
    }

    @Synchronized
    fun get(key: String): ImageBitmap? = entries[key]?.image

    // Moves nearby decoded pages to the hot end of the LRU without copying their bitmaps.
    @Synchronized
    fun touch(keys: Iterable<String>) {
        keys.forEach(entries::get)
    }

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

    /*
     * Warms a decoded page without blocking the scroll observer. Only a small number of decodes
     * may run together; queued pages outside the latest viewport window can be cancelled with
     * [retainPrefetch]. A visible page still calls [load] and promotes itself by sharing the same
     * in-flight result.
     */
    fun prefetch(key: String, loader: suspend () -> ImageBitmap) {
        synchronized(this) {
            if (entries.containsKey(key) || inFlight.containsKey(key) || prefetchJobs.containsKey(key)) return
            lateinit var job: Job
            job = scope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
                try {
                    prefetchPermits.withPermit { load(key, loader) }
                } finally {
                    synchronized(this@ReaderDecodedPageCache) {
                        if (prefetchJobs[key] === job) prefetchJobs.remove(key)
                    }
                }
            }
            prefetchJobs[key] = job
            job.start()
        }
    }

    /* Cancels queued decode work that is no longer close to the viewport. */
    fun retainPrefetch(keys: Set<String>) {
        val obsolete = synchronized(this) {
            prefetchJobs.entries
                .filter { it.key !in keys }
                .map { it.key to it.value }
                .also { jobs -> jobs.forEach { prefetchJobs.remove(it.first) } }
        }
        obsolete.forEach { it.second.cancel() }
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
