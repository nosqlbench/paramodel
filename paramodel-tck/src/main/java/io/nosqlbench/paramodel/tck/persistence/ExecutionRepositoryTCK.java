package io.nosqlbench.paramodel.tck.persistence;

import io.nosqlbench.paramodel.execution.Executor;
import io.nosqlbench.paramodel.persistence.ExecutionRepository;
import io.nosqlbench.paramodel.plan.ExecutionPlan;
import io.nosqlbench.paramodel.plan.TestPlan;
import io.nosqlbench.paramodel.tck.ImplementationProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

///
/// TCK tests for {@link ExecutionRepository} implementations.
///
/// Validates save, get, and list operations for execution plan
/// and execution result persistence.
///
/// @since 0.1.0
///
public abstract class ExecutionRepositoryTCK {

    /// Creates a new TCK test instance.
    protected ExecutionRepositoryTCK() {}

    /// Returns the implementation provider under test.
    protected abstract ImplementationProvider getProvider();

    private ExecutionRepository repository;

    @BeforeEach
    void setUp() {
        repository = getProvider().createExecutionRepository();
    }

    @Test
    void testSaveAndGetPlan() {
        TestPlan testPlan = getProvider().createTestPlan();
        ExecutionPlan plan = getProvider().createExecutionPlan(testPlan);
        repository.savePlan(plan);

        Optional<ExecutionPlan> retrieved = repository.getPlan(plan.id());
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().id()).isEqualTo(plan.id());
    }

    @Test
    void testSaveAndGetExecution() {
        TestPlan testPlan = getProvider().createTestPlan();
        ExecutionPlan plan = getProvider().createExecutionPlan(testPlan);

        Executor executor = getProvider().createExecutor();
        Executor.ExecutionResult result;
        try {
            result = executor.execute(plan);
        } catch (Executor.ExecutionFailedException e) {
            throw new RuntimeException("Execution should not fail in mock", e);
        }

        repository.saveExecution(result);

        Optional<Executor.ExecutionResult> retrieved = repository.getExecution(result.executionId());
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().executionId()).isEqualTo(result.executionId());
    }

    @Test
    void testListExecutions() {
        TestPlan testPlan = getProvider().createTestPlan();
        ExecutionPlan plan = getProvider().createExecutionPlan(testPlan);

        Executor executor = getProvider().createExecutor();
        try {
            Executor.ExecutionResult result1 = executor.execute(plan);
            Executor.ExecutionResult result2 = executor.execute(plan);
            repository.saveExecution(result1);
            repository.saveExecution(result2);
        } catch (Executor.ExecutionFailedException e) {
            throw new RuntimeException("Execution should not fail in mock", e);
        }

        List<Executor.ExecutionResult> results = repository.listExecutions(plan.id());
        assertThat(results).hasSize(2);
    }

    @Test
    void testGetNonExistentPlan() {
        Optional<ExecutionPlan> result = repository.getPlan("nonexistent");
        assertThat(result).isEmpty();
    }
}
