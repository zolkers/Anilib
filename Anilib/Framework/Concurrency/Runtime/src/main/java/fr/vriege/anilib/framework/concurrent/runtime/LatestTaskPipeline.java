package fr.vriege.anilib.framework.concurrent.runtime;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class LatestTaskPipeline<K> implements AutoCloseable {
    private final ThreadPoolExecutor executor;
    private final Duration timeout;
    private final Map<K, FutureTask<?>> active = new ConcurrentHashMap<>();
    private volatile boolean closed;

    public LatestTaskPipeline(String name, int parallelism, int queueCapacity, Duration timeout) {
        this.executor = ManagedExecutors.bounded(name, parallelism, queueCapacity);
        this.timeout = positive(timeout, "timeout");
    }

    public <T> T execute(K key, Callable<T> task) {
        return submit(Objects.requireNonNull(key, "key"), task, true);
    }

    public <T> T executeIndependent(K key, Callable<T> task) {
        return submit(Objects.requireNonNull(key, "key"), task, false);
    }

    private <T> T submit(K key, Callable<T> task, boolean replace) {
        if (closed) {
            throw new TaskPipelineException(TaskPipelineException.Reason.CLOSED, "Task pipeline is closed");
        }
        FutureTask<T> current = new FutureTask<>(Objects.requireNonNull(task, "task"));
        if (replace) {
            FutureTask<?> previous = active.put(key, current);
            cancel(previous);
        } else if (active.putIfAbsent(key, current) != null) {
            throw new TaskPipelineException(
                    TaskPipelineException.Reason.BUSY,
                    "Another task is already active for " + key);
        }
        try {
            executor.execute(current);
            return await(current);
        } catch (RejectedExecutionException exception) {
            throw new TaskPipelineException(
                    TaskPipelineException.Reason.BUSY,
                    "Task pipeline capacity is exhausted",
                    exception);
        } finally {
            active.remove(key, current);
        }
    }

    private <T> T await(FutureTask<T> task) {
        try {
            return task.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            cancel(task);
            throw new TaskPipelineException(
                    TaskPipelineException.Reason.TIMED_OUT,
                    "Task exceeded " + timeout.toSeconds() + " seconds",
                    exception);
        } catch (CancellationException exception) {
            throw new TaskPipelineException(
                    TaskPipelineException.Reason.SUPERSEDED,
                    "Task was replaced by a newer request",
                    exception);
        } catch (InterruptedException exception) {
            cancel(task);
            Thread.currentThread().interrupt();
            throw new TaskPipelineException(
                    TaskPipelineException.Reason.INTERRUPTED,
                    "Task was interrupted",
                    exception);
        } catch (ExecutionException exception) {
            throw propagate(exception.getCause());
        }
    }

    private void cancel(FutureTask<?> task) {
        if (task == null) {
            return;
        }
        task.cancel(true);
        executor.remove(task);
        executor.purge();
    }

    @Override
    public void close() {
        closed = true;
        new ArrayList<>(active.values()).forEach(this::cancel);
        active.clear();
        ManagedExecutors.shutdown(executor);
    }

    private static Duration positive(Duration value, String name) {
        Duration duration = Objects.requireNonNull(value, name);
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return duration;
    }

    private static RuntimeException propagate(Throwable failure) {
        if (failure instanceof RuntimeException runtime) {
            return runtime;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        return new IllegalStateException("Managed task failed", failure);
    }
}
