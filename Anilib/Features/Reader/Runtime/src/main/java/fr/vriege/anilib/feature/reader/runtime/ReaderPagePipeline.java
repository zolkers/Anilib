package fr.vriege.anilib.feature.reader.runtime;

import fr.vriege.anilib.feature.reader.ReaderException;
import fr.vriege.anilib.feature.reader.ReaderPolicy;
import fr.vriege.anilib.feature.source.SourcePageResource;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;

/**
 * Loads the pages of one chapter against the reader's shared cache and priority queue.
 *
 * <p>Prefetching is forward-biased and bounded: reading moves in one direction, so spending the
 * queue on pages behind the reader delays the ones ahead. Backwards lookahead is kept to a single
 * page so stepping back is still instant.
 */
final class ReaderPagePipeline implements AutoCloseable {
    /** Pages fetched ahead of the reader. Matches the lookahead mainstream readers settle on. */
    private static final int FORWARD_PREFETCH = 4;

    /** Pages kept behind the reader, enough to make a single step back instant. */
    private static final int BACKWARD_PREFETCH = 1;

    private final Object owner = new Object();
    private final Function<SourcePageResource, byte[]> pageReader;
    private final List<SourcePageResource> pages;
    private final ReaderPolicy policy;
    private final ReaderPageCache cache;
    private final ReaderPageLoadQueue queue;
    private volatile boolean closed;

    ReaderPagePipeline(
            Function<SourcePageResource, byte[]> pageReader,
            List<SourcePageResource> pages,
            ReaderPolicy policy,
            ReaderPageCache cache,
            ReaderPageLoadQueue queue) {
        this.pageReader = Objects.requireNonNull(pageReader, "pageReader must not be null");
        this.pages = List.copyOf(pages);
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
        this.cache = Objects.requireNonNull(cache, "cache must not be null");
        this.queue = Objects.requireNonNull(queue, "queue must not be null");
    }

    /**
     * Begins fetching around {@code index} without waiting, so a chapter starts downloading as
     * soon as it opens rather than only once the first blocking read returns.
     */
    void warmUp(int index) {
        if (pages.isEmpty() || closed) {
            return;
        }
        int start = Math.max(0, Math.min(index, pages.size() - 1));
        request(start, ReaderPageLoadQueue.Priority.DEMAND);
        prefetchAround(start);
    }

    byte[] load(int index) {
        validateIndex(index);
        ensureOpen();
        byte[] cached = cache.get(new ReaderPageCache.Key(owner, index));
        if (cached != null) {
            prefetchAround(index);
            return cached;
        }
        CompletableFuture<byte[]> pageFuture = request(index, ReaderPageLoadQueue.Priority.DEMAND);
        try {
            byte[] loaded = pageFuture.join();
            prefetchAround(index);
            return loaded;
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            if (cause instanceof ReaderException readerException) {
                throw readerException;
            }
            throw new ReaderException("Could not load reader page " + index, cause);
        }
    }

    private CompletableFuture<byte[]> request(int index, ReaderPageLoadQueue.Priority priority) {
        ReaderPageCache.Key key = new ReaderPageCache.Key(owner, index);
        byte[] cached = cache.get(key);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return queue.submit(key, priority, () -> read(index, key));
    }

    private byte[] read(int index, ReaderPageCache.Key key) {
        byte[] cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        SourcePageResource resource = pages.get(index);
        if (resource.estimatedBytes() > policy.maximumPageBytes()) {
            throw new ReaderException("Reader page " + index + " exceeds the configured size limit");
        }
        byte[] bytes = Objects.requireNonNull(
                pageReader.apply(resource),
                "page reader returned null page bytes");
        if (bytes.length > policy.maximumPageBytes()) {
            throw new ReaderException("Reader page " + index + " exceeds the configured size limit");
        }
        if (!closed) {
            cache.put(key, bytes);
        }
        return bytes;
    }

    private void prefetchAround(int index) {
        if (closed) {
            return;
        }
        for (int distance = 1; distance <= FORWARD_PREFETCH; distance++) {
            prefetch(index + distance);
        }
        for (int distance = 1; distance <= BACKWARD_PREFETCH; distance++) {
            prefetch(index - distance);
        }
    }

    private void prefetch(int index) {
        if (index >= 0 && index < pages.size()) {
            request(index, ReaderPageLoadQueue.Priority.PREFETCH);
        }
    }

    private void validateIndex(int index) {
        if (index < 0 || index >= pages.size()) {
            throw new IllegalArgumentException("index must address an existing reader page");
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new ReaderException("Reader page pipeline is closed");
        }
    }

    @Override
    public void close() {
        closed = true;
        for (int index = 0; index < pages.size(); index++) {
            queue.cancel(new ReaderPageCache.Key(owner, index));
        }
        cache.evictOwner(owner);
    }
}
