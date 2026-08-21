package fr.vriege.anilib.feature.reader.runtime;

import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Priority dispatch for page reads, shared by every open chapter.
 *
 * <p>A plain executor makes the page the reader is actually looking at queue behind whatever
 * prefetching was scheduled first, which is what makes a reader feel slow: the work is happening,
 * just not the work anyone is waiting for. Demand reads therefore outrank prefetches, and a
 * prefetch that later becomes a demand read is promoted rather than read twice.
 */
final class ReaderPageLoadQueue implements AutoCloseable {

    /** Higher ordinal wins. */
    enum Priority {
        PREFETCH,
        DEMAND,
        RETRY,
    }

    private final PriorityBlockingQueue<Task> queue = new PriorityBlockingQueue<>(
            64,
            Comparator.<Task>comparingInt(task -> -task.priority.get().ordinal())
                    .thenComparingLong(task -> task.sequence));
    private final Map<Object, Task> pending = new ConcurrentHashMap<>();
    private final AtomicLong sequences = new AtomicLong();
    private final Thread[] workers;
    private volatile boolean closed;

    ReaderPageLoadQueue(int workerCount, String threadPrefix) {
        this.workers = new Thread[Math.max(1, workerCount)];
        for (int index = 0; index < workers.length; index++) {
            Thread worker = new Thread(this::drain, threadPrefix + "-" + index);
            worker.setDaemon(true);
            workers[index] = worker;
            worker.start();
        }
    }

    /**
     * Submits work for {@code key}, reusing any in-flight request for the same key. Resubmitting a
     * queued key at a higher priority promotes the existing task instead of reading twice.
     */
    CompletableFuture<byte[]> submit(Object key, Priority priority, Supplier<byte[]> work) {
        Objects.requireNonNull(key, "key must not be null");
        if (closed) {
            return CompletableFuture.failedFuture(new IllegalStateException("queue is closed"));
        }
        Task task = pending.computeIfAbsent(key, ignored -> {
            Task created = new Task(key, priority, work, sequences.incrementAndGet());
            queue.add(created);
            return created;
        });
        if (task.promoteTo(priority)) {
            // Re-queue so the comparator sees the new priority; whichever entry is taken first
            // claims the task and the duplicate is skipped.
            queue.add(task);
        }
        return task.result;
    }

    /** Drops queued work for a key that is no longer wanted. Work already started still finishes. */
    void cancel(Object key) {
        Task task = pending.get(key);
        if (task != null && task.claimed.compareAndSet(false, true)) {
            pending.remove(key, task);
            queue.remove(task);
            task.result.cancel(false);
        }
    }

    private void drain() {
        while (!closed) {
            Task task;
            try {
                task = queue.take();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
            if (!task.claimed.compareAndSet(false, true)) {
                continue;
            }
            pending.remove(task.key, task);
            try {
                task.result.complete(task.work.get());
            } catch (RuntimeException exception) {
                task.result.completeExceptionally(exception);
            }
        }
    }

    @Override
    public void close() {
        closed = true;
        queue.clear();
        pending.clear();
        for (Thread worker : workers) {
            worker.interrupt();
        }
    }

    private static final class Task {
        private final Object key;
        private final AtomicReference<Priority> priority;
        private final Supplier<byte[]> work;
        private final long sequence;
        private final AtomicBoolean claimed = new AtomicBoolean();
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();

        private Task(Object key, Priority priority, Supplier<byte[]> work, long sequence) {
            this.key = key;
            this.priority = new AtomicReference<>(priority);
            this.work = work;
            this.sequence = sequence;
        }

        private boolean promoteTo(Priority candidate) {
            Priority current = priority.get();
            while (candidate.ordinal() > current.ordinal()) {
                if (priority.compareAndSet(current, candidate)) {
                    return true;
                }
                current = priority.get();
            }
            return false;
        }
    }
}
