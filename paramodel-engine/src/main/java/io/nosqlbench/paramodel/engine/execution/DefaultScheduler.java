package io.nosqlbench.paramodel.engine.execution;

import io.nosqlbench.paramodel.execution.Runtime;
import io.nosqlbench.paramodel.execution.Scheduler;
import io.nosqlbench.paramodel.plan.AtomicStep;
import io.nosqlbench.paramodel.plan.ExecutionGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Default scheduler with priority-based execution.
 *
 * This is a stub implementation.
 */
public class DefaultScheduler implements Scheduler {
    private static final Logger log = LoggerFactory.getLogger(DefaultScheduler.class);

    private ExecutionGraph graph;
    private final Queue<AtomicStep> readyQueue;
    private final Set<String> completedStepIds;
    private final Set<String> failedStepIds;
    private final Map<String, Priority> priorities;

    public DefaultScheduler() {
        this.readyQueue = new LinkedList<>();
        this.completedStepIds = new HashSet<>();
        this.failedStepIds = new HashSet<>();
        this.priorities = new HashMap<>();
    }

    @Override
    public void initialize(ExecutionGraph graph) {
        log.info("Initializing scheduler with execution graph");
        this.graph = graph;

        // Initialize ready queue with steps that have no dependencies
        for (AtomicStep step : graph.topologicalSort()) {
            if (graph.dependencies(step).isEmpty()) {
                readyQueue.offer(step);
            }
        }
    }

    @Override
    public List<AtomicStep> nextSteps() {
        List<AtomicStep> steps = new ArrayList<>();
        AtomicStep step = readyQueue.poll();
        if (step != null) {
            steps.add(step);
        }
        return steps;
    }

    @Override
    public List<AtomicStep> nextSteps(Runtime.ResourceAvailability available) {
        return nextSteps();
    }

    @Override
    public Optional<AtomicStep> nextStep(int workerId) {
        AtomicStep step = readyQueue.poll();
        if (step != null) {
            log.debug("Dequeued step for worker {}: {}", workerId, step.id());
        }
        return Optional.ofNullable(step);
    }

    @Override
    public void markStarted(AtomicStep step, Instant startTime) {
        log.debug("Marking step started: {} at {}", step.id(), startTime);
    }

    @Override
    public void markCompleted(AtomicStep step) {
        log.debug("Marking step completed: {}", step.id());
        completedStepIds.add(step.id());
    }

    @Override
    public void markFailed(AtomicStep step, Throwable error) {
        log.debug("Marking step failed: {} - {}", step.id(), error.getMessage());
        failedStepIds.add(step.id());
    }

    @Override
    public void setPriority(AtomicStep step, Priority priority) {
        priorities.put(step.id(), priority);
    }

    @Override
    public Priority getPriority(AtomicStep step) {
        return priorities.getOrDefault(step.id(), Priority.NORMAL);
    }

    @Override
    public boolean isComplete() {
        return readyQueue.isEmpty();
    }

    @Override
    public SchedulerState state() {
        return new StubSchedulerState();
    }

    @Override
    public SchedulerStatistics statistics() {
        return new StubSchedulerStatistics();
    }

    public static DefaultScheduler create() {
        return new DefaultScheduler();
    }

    private class StubSchedulerState implements SchedulerState {
        @Override
        public int pendingCount() {
            return readyQueue.size();
        }

        @Override
        public int runningCount() {
            return 0;
        }

        @Override
        public int completedCount() {
            return completedStepIds.size();
        }

        @Override
        public int failedCount() {
            return failedStepIds.size();
        }

        @Override
        public int queueDepth() {
            return readyQueue.size();
        }

        @Override
        public double resourceUtilization() {
            return 0.0;
        }

        @Override
        public Map<String, Integer> stepsByPhase() {
            return Map.of();
        }
    }

    private static class StubSchedulerStatistics implements SchedulerStatistics {
        @Override
        public Duration totalSchedulingTime() {
            return Duration.ZERO;
        }

        @Override
        public int totalStepsScheduled() {
            return 0;
        }

        @Override
        public int totalStepsCompleted() {
            return 0;
        }

        @Override
        public int totalStepsFailed() {
            return 0;
        }

        @Override
        public double averageQueueWaitTime() {
            return 0.0;
        }

        @Override
        public double averageResourceUtilization() {
            return 0.0;
        }

        @Override
        public Map<Priority, Integer> stepsByPriority() {
            return Map.of();
        }
    }
}
