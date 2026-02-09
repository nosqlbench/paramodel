package io.nosqlbench.paramodel.tck.execution;

import io.nosqlbench.paramodel.execution.Executor;
import io.nosqlbench.paramodel.plan.ExecutionPlan;
import io.nosqlbench.paramodel.tck.ImplementationProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

///
/// Technology Compatibility Kit tests for Executor contract.
///
/// Validates that implementations correctly:
/// - Execute plans synchronously
/// - Provide configuration
/// - Manage checkpoints
/// - Return execution results with expected metadata
///
/// @see Executor
/// @since 0.1.0
///
public abstract class ExecutorTCK {
    protected ExecutorTCK() {}

    protected abstract ImplementationProvider getProvider();

    @Test
    public void testExecutorConfig() {
        Executor executor = getProvider().createExecutor();

        Executor.ExecutorConfig config = executor.config();

        assertThat(config).isNotNull();
        assertThat(config.maxConcurrentTrials()).isGreaterThan(0);
        assertThat(config.maxCpu()).isGreaterThan(0);
        assertThat(config.maxMemoryGb()).isGreaterThan(0);
        assertThat(config.customConfig()).isNotNull();
    }

    @Test
    public void testExecutorExecute() throws Exception {
        Executor executor = getProvider().createExecutor();
        var testPlan = getProvider().createTestPlan();
        ExecutionPlan plan = getProvider().createExecutionPlan(testPlan);

        Executor.ExecutionResult result = executor.execute(plan);

        assertThat(result).isNotNull();
        assertThat(result.executionId()).isNotNull();
        assertThat(result.plan()).isNotNull();
        assertThat(result.finalStatus()).isNotNull();
        assertThat(result.startedAt()).isNotNull();
        assertThat(result.completedAt()).isNotNull();
        assertThat(result.duration()).isNotNull();
        assertThat(result.trialResults()).isNotNull();
    }

    @Test
    public void testExecutorExecuteResult() throws Exception {
        Executor executor = getProvider().createExecutor();
        var testPlan = getProvider().createTestPlan();
        ExecutionPlan plan = getProvider().createExecutionPlan(testPlan);

        Executor.ExecutionResult result = executor.execute(plan);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.metrics()).isNotNull();
        assertThat(result.metadata()).isNotNull();
    }

    @Test
    public void testExecutorCheckpoints() throws Exception {
        Executor executor = getProvider().createExecutor();
        var testPlan = getProvider().createTestPlan();
        ExecutionPlan plan = getProvider().createExecutionPlan(testPlan);

        assertThat(executor.checkpoints(plan)).isNotNull();
    }

    @Test
    public void testExecutorLatestCheckpoint() throws Exception {
        Executor executor = getProvider().createExecutor();
        var testPlan = getProvider().createTestPlan();
        ExecutionPlan plan = getProvider().createExecutionPlan(testPlan);

        assertThat(executor.latestCheckpoint(plan)).isNotNull();
    }

    @Test
    public void testExecutorExecuteAsync() throws Exception {
        Executor executor = getProvider().createExecutor();
        var testPlan = getProvider().createTestPlan();
        ExecutionPlan plan = getProvider().createExecutionPlan(testPlan);

        Executor.ExecutionHandle handle = executor.executeAsync(plan);

        assertThat(handle).isNotNull();
        assertThat(handle.executionId()).isNotNull();
        assertThat(handle.isDone()).isTrue();

        Executor.ExecutionResult result = handle.await();
        assertThat(result).isNotNull();
    }
}
