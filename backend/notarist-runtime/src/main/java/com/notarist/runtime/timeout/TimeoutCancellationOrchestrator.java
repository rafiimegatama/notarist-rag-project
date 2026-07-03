package com.notarist.runtime.timeout;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.*;

/**
 * Central coordinator for timeout-bounded task execution and cancellation.
 *
 * Every external AI call (OCR, embedding, inference, reranker) is submitted here.
 * Tracks active tasks by traceId for on-demand cancellation (e.g., SSE disconnect).
 *
 * Timeout enforcement: CompletableFuture.orTimeout via a dedicated ScheduledExecutor.
 * On timeout: future is completed exceptionally with TimeoutException, active task map cleaned up.
 */
@Component
public class TimeoutCancellationOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(TimeoutCancellationOrchestrator.class);

    public static class TimeoutException extends RuntimeException {
        private final String traceId;
        private final long timeoutMs;

        public TimeoutException(String traceId, long timeoutMs) {
            super("Task timed out: traceId=" + traceId + " timeoutMs=" + timeoutMs);
            this.traceId   = traceId;
            this.timeoutMs = timeoutMs;
        }

        public String getTraceId()  { return traceId; }
        public long   getTimeoutMs(){ return timeoutMs; }
    }

    private final ConcurrentHashMap<String, CompletableFuture<?>> activeTasks = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;
    private final ExecutorService          workerPool;
    private final Counter                  timeoutCounter;
    private final Counter                  cancelCounter;

    public TimeoutCancellationOrchestrator(MeterRegistry meterRegistry) {
        this.scheduler = Executors.newScheduledThreadPool(2,
                r -> { Thread t = new Thread(r, "timeout-scheduler"); t.setDaemon(true); return t; });

        this.workerPool = Executors.newCachedThreadPool(
                r -> { Thread t = new Thread(r, "timeout-worker"); t.setDaemon(true); return t; });

        this.timeoutCounter = Counter.builder("notarist.runtime.timeout.total")
                .description("Total task timeouts across all AI runtimes")
                .register(meterRegistry);

        this.cancelCounter = Counter.builder("notarist.runtime.cancellation.total")
                .description("Total on-demand cancellations")
                .register(meterRegistry);
    }

    /**
     * Submits a callable for bounded execution.
     *
     * @param traceId   unique identifier for this task (used for cancellation lookup)
     * @param task      the work to execute
     * @param timeoutMs maximum allowed duration in milliseconds
     * @return result of the callable
     * @throws TimeoutException  if the task exceeds timeoutMs
     * @throws RuntimeException  if the task throws
     */
    public <T> T submitWithTimeout(String traceId, Callable<T> task, long timeoutMs) {
        CompletableFuture<T> future = new CompletableFuture<>();
        activeTasks.put(traceId, future);

        workerPool.submit(() -> {
            try {
                T result = task.call();
                future.complete(result);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        ScheduledFuture<?> watchdog = scheduler.schedule(() -> {
            if (!future.isDone()) {
                timeoutCounter.increment();
                log.warn("TimeoutCancellationOrchestrator: task TIMED OUT traceId={} timeoutMs={}", traceId, timeoutMs);
                future.completeExceptionally(new TimeoutException(traceId, timeoutMs));
            }
        }, timeoutMs, TimeUnit.MILLISECONDS);

        try {
            T result = future.get(timeoutMs + 100, TimeUnit.MILLISECONDS);
            watchdog.cancel(false);
            return result;
        } catch (java.util.concurrent.TimeoutException e) {
            timeoutCounter.increment();
            throw new TimeoutException(traceId, timeoutMs);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof TimeoutException te) throw te;
            if (cause instanceof RuntimeException re) throw re;
            throw new RuntimeException("Task execution failed: " + traceId, cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted waiting for task: " + traceId, e);
        } finally {
            activeTasks.remove(traceId);
            watchdog.cancel(false);
        }
    }

    /**
     * Cancels an active task by traceId (e.g., when SSE client disconnects).
     * No-op if the task has already completed.
     */
    public boolean cancel(String traceId) {
        CompletableFuture<?> future = activeTasks.remove(traceId);
        if (future != null && !future.isDone()) {
            cancelCounter.increment();
            future.cancel(true);
            log.info("TimeoutCancellationOrchestrator: task CANCELLED traceId={}", traceId);
            return true;
        }
        return false;
    }

    public boolean isActive(String traceId) {
        CompletableFuture<?> f = activeTasks.get(traceId);
        return f != null && !f.isDone();
    }

    public int activeTaskCount() {
        return activeTasks.size();
    }
}
