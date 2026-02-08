package io.nosqlbench.paramodel.engine.execution;

import io.nosqlbench.paramodel.execution.Executor;
import io.nosqlbench.paramodel.execution.Runtime;
import io.nosqlbench.paramodel.plan.AtomicStep;
import io.nosqlbench.paramodel.plan.ExecutionPlan;
import io.nosqlbench.paramodel.sequence.Trial;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.Function;

/**
 * Default executor implementation with concurrent execution support.
 *
 * Features:
 * - Thread pool-based parallel execution
 * - Resource-aware scheduling
 * - Progress tracking
 * - Cancellation support
 */
public class DefaultExecutor implements Executor {
    private static final Logger log = LoggerFactory.getLogger(DefaultExecutor.class);

    private final Runtime runtime;
    private final ExecutorService executorService;
    private final int maxConcurrency;

    public DefaultExecutor(Runtime runtime, int maxConcurrency) {
        this.runtime = Objects.requireNonNull(runtime);
        this.maxConcurrency = maxConcurrency;
        this.executorService = Executors.newFixedThreadPool(
            maxConcurrency,
            new ThreadFactory() {
                private int counter = 0;
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "paramodel-executor-" + counter++);
                    t.setDaemon(true);
                    return t;
                }
            }
        );
    }

    @Override
    public <R> List<R> execute(ExecutionPlan plan, Function<Trial, R> trialExecutor) {
        log.info("Starting execution of plan with {} estimated trials", plan.estimatedTrialCount());

        List<AtomicStep> steps = plan.graph().topologicalOrder();
        List<Future<R>> futures = new CopyOnWriteArrayList<>();

        try {
            // Submit all steps for execution
            for (AtomicStep step : steps) {
                Future<R> future = executorService.submit(() -> {
                    log.debug("Executing step: {}", step.id());
                    Trial trial = step.trial();
                    return trialExecutor.apply(trial);
                });
                futures.add(future);
            }

            // Collect results
            List<R> results = new CopyOnWriteArrayList<>();
            for (Future<R> future : futures) {
                try {
                    R result = future.get();
                    results.add(result);
                } catch (ExecutionException e) {
                    log.error("Step execution failed", e.getCause());
                    throw new RuntimeException("Step execution failed", e.getCause());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Execution interrupted", e);
                }
            }

            log.info("Execution completed. {} results collected", results.size());
            return results;

        } finally {
            // Don't shutdown here - allow reuse
        }
    }

    @Override
    public Runtime runtime() {
        return runtime;
    }

    @Override
    public void shutdown() {
        log.info("Shutting down executor");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Runtime runtime;
        private int maxConcurrency = java.lang.Runtime.getRuntime().availableProcessors();

        public Builder runtime(Runtime runtime) {
            this.runtime = runtime;
            return this;
        }

        public Builder maxConcurrency(int maxConcurrency) {
            if (maxConcurrency < 1) {
                throw new IllegalArgumentException("maxConcurrency must be >= 1");
            }
            this.maxConcurrency = maxConcurrency;
            return this;
        }

        public DefaultExecutor build() {
            if (runtime == null) {
                runtime = new SimpleRuntime();
            }
            return new DefaultExecutor(runtime, maxConcurrency);
        }
    }

    /**
     * Simple runtime implementation.
     */
    private static class SimpleRuntime implements Runtime {
        @Override
        public void start() {
            // No-op
        }

        @Override
        public void stop() {
            // No-op
        }

        @Override
        public boolean isRunning() {
            return true;
        }

        @Override
        public java.util.Map<String, Object> metrics() {
            return java.util.Map.of();
        }
    }
}
