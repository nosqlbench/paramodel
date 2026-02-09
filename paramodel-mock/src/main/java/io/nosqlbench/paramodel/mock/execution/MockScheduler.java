package io.nosqlbench.paramodel.mock.execution;

import io.nosqlbench.paramodel.execution.Runtime;
import io.nosqlbench.paramodel.execution.Scheduler;
import io.nosqlbench.paramodel.plan.AtomicStep;
import io.nosqlbench.paramodel.plan.ExecutionGraph;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

///
/// Simple scheduler implementation for testing.
///
/// Provides FIFO scheduling with in-memory step tracking
/// and basic priority support.
///
/// @see Scheduler
/// @since 0.1.0
///
public class MockScheduler implements Scheduler {
    private final List<AtomicStep> pending = new ArrayList<>();
    private final List<AtomicStep> running = new ArrayList<>();
    private final List<AtomicStep> completed = new ArrayList<>();
    private final List<AtomicStep> failed = new ArrayList<>();
    private final Map<String, Priority> priorities = new HashMap<>();
    private boolean initialized = false;

    ///
    /// Creates a mock scheduler.
    ///
    public MockScheduler() {}

    @Override
    public void initialize(ExecutionGraph graph) {
        pending.addAll(graph.steps());
        initialized = true;
    }

    @Override
    public List<AtomicStep> nextSteps() {
        if (pending.isEmpty()) return List.of();
        AtomicStep next = pending.get(0);
        return List.of(next);
    }

    @Override
    public List<AtomicStep> nextSteps(Runtime.ResourceAvailability available) {
        return nextSteps();
    }

    @Override
    public Optional<AtomicStep> nextStep(int workerId) {
        if (pending.isEmpty()) return Optional.empty();
        return Optional.of(pending.get(0));
    }

    @Override
    public void markStarted(AtomicStep step, Instant startTime) {
        pending.remove(step);
        running.add(step);
    }

    @Override
    public void markCompleted(AtomicStep step) {
        running.remove(step);
        pending.remove(step);
        completed.add(step);
    }

    @Override
    public void markFailed(AtomicStep step, Throwable error) {
        running.remove(step);
        pending.remove(step);
        failed.add(step);
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
        return initialized && pending.isEmpty() && running.isEmpty();
    }

    @Override
    public SchedulerState state() {
        return new MockSchedulerState(
            pending.size(), running.size(), completed.size(), failed.size(),
            pending.size(), 0.0, Map.of()
        );
    }

    @Override
    public SchedulerStatistics statistics() {
        return new MockSchedulerStatistics(
            Duration.ZERO, completed.size() + failed.size(),
            completed.size(), failed.size(),
            0.0, 0.0, Map.of()
        );
    }

    private record MockSchedulerState(
        int pendingCount,
        int runningCount,
        int completedCount,
        int failedCount,
        int queueDepth,
        double resourceUtilization,
        Map<String, Integer> stepsByPhase
    ) implements SchedulerState {}

    private record MockSchedulerStatistics(
        Duration totalSchedulingTime,
        int totalStepsScheduled,
        int totalStepsCompleted,
        int totalStepsFailed,
        double averageQueueWaitTime,
        double averageResourceUtilization,
        Map<Priority, Integer> stepsByPriority
    ) implements SchedulerStatistics {}
}
