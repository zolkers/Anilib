package fr.vriege.anilib.framework.concurrent.runtime;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public final class ManagedExecutors {
    private static final Duration DEFAULT_SHUTDOWN_TIMEOUT = Duration.ofSeconds(3);
    private static final ThreadPoolExecutor BACKGROUND = bounded(
            "anilib-background",
            Math.max(2, Math.min(8, Runtime.getRuntime().availableProcessors())),
            256);

    private ManagedExecutors() {
    }

    public static ExecutorService single(String name) {
        return Executors.newSingleThreadExecutor(threadFactory(name));
    }

    public static ScheduledExecutorService scheduled(String name) {
        return Executors.newSingleThreadScheduledExecutor(threadFactory(name));
    }

    public static ExecutorService fixed(String name, int parallelism) {
        if (parallelism < 1) {
            throw new IllegalArgumentException("parallelism must be positive");
        }
        return Executors.newFixedThreadPool(parallelism, threadFactory(name));
    }

    public static ThreadPoolExecutor bounded(String name, int parallelism, int queueCapacity) {
        if (parallelism < 1) {
            throw new IllegalArgumentException("parallelism must be positive");
        }
        if (queueCapacity < 1) {
            throw new IllegalArgumentException("queueCapacity must be positive");
        }
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                parallelism,
                parallelism,
                30L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                threadFactory(name),
                new ThreadPoolExecutor.AbortPolicy());
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    public static Thread start(String name, Runnable task) {
        Thread thread = thread(name, task);
        thread.start();
        return thread;
    }

    public static CompletableFuture<Void> run(String name, Runnable task) {
        return supply(name, () -> {
            task.run();
            return null;
        });
    }

    public static <T> CompletableFuture<T> supply(String name, Supplier<T> task) {
        String operationName = requireName(name);
        Supplier<T> operation = Objects.requireNonNull(task, "task");
        CompletableFuture<T> result = new CompletableFuture<>();
        try {
            BACKGROUND.execute(() -> {
                try {
                    result.complete(operation.get());
                } catch (Throwable failure) {
                    result.completeExceptionally(failure);
                }
            });
        } catch (RuntimeException failure) {
            result.completeExceptionally(new IllegalStateException(
                    "Managed background capacity is exhausted for " + operationName,
                    failure));
        }
        return result;
    }

    public static Thread thread(String name, Runnable task) {
        return threadFactory(name).newThread(Objects.requireNonNull(task, "task"));
    }

    public static ThreadFactory threadFactory(String name) {
        String prefix = requireName(name);
        AtomicInteger sequence = new AtomicInteger();
        return task -> {
            Thread thread = new Thread(
                    Objects.requireNonNull(task, "task"),
                    prefix + '-' + sequence.incrementAndGet());
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler((failedThread, failure) ->
                    System.getLogger(ManagedExecutors.class.getName()).log(
                            System.Logger.Level.ERROR,
                            "Managed task failed on " + failedThread.getName(),
                            failure));
            return thread;
        };
    }

    public static void shutdown(ExecutorService executor) {
        shutdown(executor, DEFAULT_SHUTDOWN_TIMEOUT);
    }

    public static void shutdown(ExecutorService executor, Duration timeout) {
        ExecutorService managed = Objects.requireNonNull(executor, "executor");
        Duration wait = Objects.requireNonNull(timeout, "timeout");
        if (wait.isNegative()) {
            throw new IllegalArgumentException("timeout must not be negative");
        }
        managed.shutdown();
        try {
            if (!managed.awaitTermination(wait.toMillis(), TimeUnit.MILLISECONDS)) {
                managed.shutdownNow();
                managed.awaitTermination(wait.toMillis(), TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException exception) {
            managed.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static String requireName(String value) {
        String name = Objects.requireNonNull(value, "name").strip();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        return name;
    }
}
