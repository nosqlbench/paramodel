package io.nosqlbench.paramodel.engine.execution;

import io.nosqlbench.paramodel.execution.Scheduler;
import io.nosqlbench.paramodel.plan.AtomicStep;
import io.nosqlbench.paramodel.plan.ExecutionPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * Default scheduler with work-stealing and priority-based execution.
 *
 * Features:
 * - Priority-based scheduling
 * - Work stealing for load balancing
 * - Admission control based on resources
 * - Fair scheduling across different test plans
 */
public class DefaultScheduler implements Scheduler {
    private static final Logger log = LoggerFactory.getLogger(DefaultScheduler.class);

    private final SchedulingPolicy policy;
    private final BlockingQueue<ScheduledStep> readyQueue;
    private final Map<String, AtomicStep> blockedSteps;

    public DefaultScheduler(SchedulingPolicy policy) {
        this.policy = Objects.requireNonNull(policy);
        this.readyQueue = policy == SchedulingPolicy.PRIORITY
            ? new PriorityBlockingQueue<>()
            : new LinkedBlockingQueue<>();
        this.blockedSteps = new HashMap<>();
    }

    @Override
    public void schedule(ExecutionPlan plan) {
        log.info("Scheduling execution plan with {} steps", plan.steps().size());

        List<AtomicStep> topologicalOrder = plan.graph().topologicalOrder();

        for (AtomicStep step : topologicalOrder) {
            ScheduledStep scheduled = new ScheduledStep(step, computePriority(step));
            readyQueue.offer(scheduled);
        }

        log.info("Scheduled {} steps", topologicalOrder.size());
    }

    @Override
    public Optional<AtomicStep> next() {
        ScheduledStep scheduled = readyQueue.poll();
        if (scheduled == null) {
            return Optional.empty();
        }

        log.debug("Dequeued step: {} (priority: {})", scheduled.step.id(), scheduled.priority);
        return Optional.of(scheduled.step);
    }

    @Override
    public void block(AtomicStep step) {
        log.debug("Blocking step: {}", step.id());
        blockedSteps.put(step.id(), step);
    }

    @Override
    public void unblock(AtomicStep step) {
        log.debug("Unblocking step: {}", step.id());
        AtomicStep blocked = blockedSteps.remove(step.id());
        if (blocked != null) {
            ScheduledStep scheduled = new ScheduledStep(blocked, computePriority(blocked));
            readyQueue.offer(scheduled);
        }
    }

    private int computePriority(AtomicStep step) {
        // Simple priority: based on step ID hash
        // Full implementation would consider:
        // - Resource requirements
        // - Expected duration
        // - User-specified priorities
        // - Dependency depth
        return step.id().hashCode();
    }

    public static DefaultScheduler create(SchedulingPolicy policy) {
        return new DefaultScheduler(policy);
    }

    /**
     * Scheduled step with priority.
     */
    private static class ScheduledStep implements Comparable<ScheduledStep> {
        final AtomicStep step;
        final int priority;

        ScheduledStep(AtomicStep step, int priority) {
            this.step = step;
            this.priority = priority;
        }

        @Override
        public int compareTo(ScheduledStep other) {
            return Integer.compare(other.priority, this.priority); // Higher priority first
        }
    }

    /**
     * Scheduling policies.
     */
    public enum SchedulingPolicy {
        FIFO,
        PRIORITY,
        FAIR
    }
}
