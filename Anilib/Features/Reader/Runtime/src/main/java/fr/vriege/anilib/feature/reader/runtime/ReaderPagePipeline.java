package fr.vriege.anilib.feature.reader.runtime;

import fr.vriege.anilib.feature.reader.ReaderException;
import fr.vriege.anilib.feature.reader.ReaderPolicy;
import fr.vriege.anilib.feature.source.SourcePageResource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Function;

/** Per-session bounded LRU cache and asynchronous neighboring-page loader. */
final class ReaderPagePipeline implements AutoCloseable {
    private final Function<SourcePageResource, byte[]> pageReader;
    private final List<SourcePageResource> pages;
    private final ReaderPolicy policy;
    private final Executor executor;
    private final Map<Integer, byte[]> cache = new LinkedHashMap<>(16, 0.75f, true);
    private final Map<Integer, CompletableFuture<byte[]>> loading = new LinkedHashMap<>();
    private long cachedBytes;
    private boolean closed;

    ReaderPagePipeline(
            Function<SourcePageResource, byte[]> pageReader,
            List<SourcePageResource> pages,
            ReaderPolicy policy,
            Executor executor) {
        this.pageReader = Objects.requireNonNull(pageReader, "pageReader must not be null");
        this.pages = List.copyOf(pages);
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    byte[] load(int index) {
        validateIndex(index);
        CompletableFuture<byte[]> pageFuture;
        synchronized (this) {
            ensureOpen();
            byte[] cached = cache.get(index);
            if (cached != null) {
                prefetchAround(index);
                return cached.clone();
            }
            pageFuture = schedule(index);
        }

        try {
            byte[] loaded = pageFuture.join();
            synchronized (this) {
                ensureOpen();
                prefetchAround(index);
            }
            return loaded.clone();
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            if (cause instanceof ReaderException readerException) {
                throw readerException;
            }
            throw new ReaderException("Could not load reader page " + index, cause);
        }
    }

    private synchronized CompletableFuture<byte[]> schedule(int index) {
        byte[] cached = cache.get(index);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        CompletableFuture<byte[]> existing = loading.get(index);
        if (existing != null) {
            return existing;
        }

        CompletableFuture<byte[]> scheduled = new CompletableFuture<>();
        loading.put(index, scheduled);
        executor.execute(() -> read(index, scheduled));
        return scheduled;
    }

    private void read(int index, CompletableFuture<byte[]> scheduled) {
        try {
            SourcePageResource resource = pages.get(index);
            if (resource.estimatedBytes() > policy.maximumPageBytes()) {
                throw new ReaderException("Reader page " + index + " exceeds the configured size limit");
            }
            byte[] bytes = Objects.requireNonNull(
                    pageReader.apply(resource),
                    "page reader returned null page bytes").clone();
            if (bytes.length > policy.maximumPageBytes()) {
                throw new ReaderException("Reader page " + index + " exceeds the configured size limit");
            }
            synchronized (this) {
                loading.remove(index);
                if (!closed) {
                    put(index, bytes);
                }
            }
            scheduled.complete(bytes);
        } catch (RuntimeException exception) {
            synchronized (this) {
                loading.remove(index);
            }
            scheduled.completeExceptionally(exception);
        }
    }

    private void prefetchAround(int index) {
        for (int distance = 1; distance <= policy.prefetchDistance(); distance++) {
            prefetch(index + distance);
            prefetch(index - distance);
        }
    }

    private void prefetch(int index) {
        if (index >= 0 && index < pages.size()) {
            schedule(index);
        }
    }

    private void put(int index, byte[] bytes) {
        byte[] previous = cache.put(index, bytes);
        if (previous != null) {
            cachedBytes -= previous.length;
        }
        cachedBytes += bytes.length;
        while (cachedBytes > policy.maximumCacheBytes() && !cache.isEmpty()) {
            Map.Entry<Integer, byte[]> oldest = cache.entrySet().iterator().next();
            cache.remove(oldest.getKey());
            cachedBytes -= oldest.getValue().length;
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
    public synchronized void close() {
        closed = true;
        loading.values().forEach(future -> future.cancel(true));
        loading.clear();
        cache.clear();
        cachedBytes = 0;
    }
}
