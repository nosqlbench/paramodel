package io.nosqlbench.paramodel.engine.execution;

import io.nosqlbench.paramodel.execution.Scheduler;
import io.nosqlbench.paramodel.plan.AtomicStep;
import io.nosqlbench.paramodel.plan.ExecutionGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Default scheduler with work-stealing and priority-based execution.
 *
 * This is a stub implementation.
 */
public class DefaultScheduler implements Scheduler {
    private static final Logger log = LoggerFactory.getLogger(DefaultScheduler.class);

    private ExecutionGraph graph;
    private final Queue<AtomicStep> readyQueue;

    public DefaultScheduler() {
        this.readyQueue = new LinkedList<>();
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
    public Optional<AtomicStep> nextReady() {
        AtomicStep step = readyQueue.poll();
        if (step != null) {
            log.debug("Dequeued step: {}", step.id());
        }
        return Optional.ofNullable(step);
    }

    @Override
    public void markCompleted(AtomicStep step) {
        log.debug("Marking step completed: {}", step.id());

        // Stub - would check if any blocked steps can now be unblocked
    }

    @Override
    public int pendingCount() {
        return readyQueue.size();
    }

    public static DefaultScheduler create() {
        return new DefaultScheduler();
    }
}
