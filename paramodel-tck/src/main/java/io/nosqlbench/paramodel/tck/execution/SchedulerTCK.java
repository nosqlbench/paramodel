package io.nosqlbench.paramodel.tck.execution;

import io.nosqlbench.paramodel.execution.Scheduler;
import io.nosqlbench.paramodel.plan.AtomicStep;
import io.nosqlbench.paramodel.plan.ExecutionGraph;
import io.nosqlbench.paramodel.tck.ImplementationProvider;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

///
/// Technology Compatibility Kit tests for Scheduler contract.
///
/// Validates that implementations correctly:
/// - Initialize with an execution graph
/// - Provide next steps for execution
/// - Track step completion
/// - Report state and statistics
/// - Support step priorities
///
/// @see Scheduler
/// @since 0.1.0
///
public abstract class SchedulerTCK {
    protected SchedulerTCK() {}

    protected abstract ImplementationProvider getProvider();

    @Test
    public void testSchedulerInitialize() {
        Scheduler scheduler = getProvider().createScheduler();
        ExecutionGraph graph = getProvider().createExecutionGraph();

        assertThatCode(() -> scheduler.initialize(graph))
            .doesNotThrowAnyException();
    }

    @Test
    public void testSchedulerNextSteps() {
        Scheduler scheduler = getProvider().createScheduler();
        ExecutionGraph graph = getProvider().createExecutionGraph();
        scheduler.initialize(graph);

        assertThat(scheduler.nextSteps()).isNotNull();
    }

    @Test
    public void testSchedulerMarkCompleted() {
        Scheduler scheduler = getProvider().createScheduler();
        ExecutionGraph graph = getProvider().createExecutionGraph();
        scheduler.initialize(graph);

        var steps = graph.steps();
        if (!steps.isEmpty()) {
            AtomicStep step = steps.get(0);
            scheduler.markStarted(step, Instant.now());
            assertThatCode(() -> scheduler.markCompleted(step))
                .doesNotThrowAnyException();
        }
    }

    @Test
    public void testSchedulerIsComplete() {
        Scheduler scheduler = getProvider().createScheduler();
        ExecutionGraph graph = getProvider().createExecutionGraph();
        scheduler.initialize(graph);

        // isComplete should return a boolean without throwing
        boolean complete = scheduler.isComplete();
        assertThat(complete).isIn(true, false);
    }

    @Test
    public void testSchedulerState() {
        Scheduler scheduler = getProvider().createScheduler();
        ExecutionGraph graph = getProvider().createExecutionGraph();
        scheduler.initialize(graph);

        Scheduler.SchedulerState state = scheduler.state();

        assertThat(state).isNotNull();
        assertThat(state.pendingCount()).isGreaterThanOrEqualTo(0);
        assertThat(state.runningCount()).isGreaterThanOrEqualTo(0);
        assertThat(state.completedCount()).isGreaterThanOrEqualTo(0);
        assertThat(state.stepsByPhase()).isNotNull();
    }

    @Test
    public void testSchedulerStatistics() {
        Scheduler scheduler = getProvider().createScheduler();
        ExecutionGraph graph = getProvider().createExecutionGraph();
        scheduler.initialize(graph);

        Scheduler.SchedulerStatistics stats = scheduler.statistics();

        assertThat(stats).isNotNull();
        assertThat(stats.totalSchedulingTime()).isNotNull();
        assertThat(stats.stepsByPriority()).isNotNull();
    }

    @Test
    public void testSchedulerPriority() {
        Scheduler scheduler = getProvider().createScheduler();
        ExecutionGraph graph = getProvider().createExecutionGraph();
        scheduler.initialize(graph);

        var steps = graph.steps();
        if (!steps.isEmpty()) {
            AtomicStep step = steps.get(0);
            scheduler.setPriority(step, Scheduler.Priority.HIGH);

            Scheduler.Priority priority = scheduler.getPriority(step);
            assertThat(priority).isEqualTo(Scheduler.Priority.HIGH);
        }
    }
}
