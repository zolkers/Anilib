package fr.vriege.anilib.framework.concurrent.runtime;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class ConcurrencyRuntimeSmoke {
    private ConcurrencyRuntimeSmoke() {
    }

    public static void main(String[] arguments) throws Exception {
        verifyLatestTaskWins();
        verifyTimeout();
        verifyClose();
    }

    private static void verifyLatestTaskWins() throws Exception {
        try (var pipeline = new LatestTaskPipeline<String>(
                "latest-task-smoke", 2, 4, Duration.ofSeconds(2))) {
            CountDownLatch started = new CountDownLatch(1);
            CompletableFuture<TaskPipelineException.Reason> obsolete = CompletableFuture.supplyAsync(() -> {
                try {
                    pipeline.execute("search", () -> {
                        started.countDown();
                        Thread.sleep(TimeUnit.SECONDS.toMillis(5));
                        return "obsolete";
                    });
                    throw new IllegalStateException("Obsolete task unexpectedly completed");
                } catch (TaskPipelineException exception) {
                    return exception.reason();
                }
            });
            if (!started.await(1, TimeUnit.SECONDS)) {
                throw new IllegalStateException("First task did not start");
            }
            String result = pipeline.execute("search", () -> "latest");
            if (!result.equals("latest")) {
                throw new IllegalStateException("Latest result was not returned");
            }
            if (obsolete.get(1, TimeUnit.SECONDS) != TaskPipelineException.Reason.SUPERSEDED) {
                throw new IllegalStateException("Obsolete task was not superseded");
            }
        }
    }

    private static void verifyTimeout() {
        try (var pipeline = new LatestTaskPipeline<String>(
                "timeout-smoke", 1, 1, Duration.ofMillis(50))) {
            try {
                pipeline.execute("slow", () -> {
                    Thread.sleep(TimeUnit.SECONDS.toMillis(5));
                    return null;
                });
                throw new IllegalStateException("Slow task unexpectedly completed");
            } catch (TaskPipelineException exception) {
                if (exception.reason() != TaskPipelineException.Reason.TIMED_OUT) {
                    throw new IllegalStateException("Slow task did not time out", exception);
                }
            }
        }
    }

    private static void verifyClose() {
        var pipeline = new LatestTaskPipeline<String>(
                "close-smoke", 1, 1, Duration.ofSeconds(1));
        pipeline.close();
        try {
            pipeline.execute("closed", () -> null);
            throw new IllegalStateException("Closed pipeline accepted a task");
        } catch (TaskPipelineException exception) {
            if (exception.reason() != TaskPipelineException.Reason.CLOSED) {
                throw new IllegalStateException("Closed pipeline returned the wrong reason", exception);
            }
        }
    }
}
