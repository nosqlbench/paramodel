package io.nosqlbench.paramodel.mock.persistence;

import io.nosqlbench.paramodel.execution.Executor;
import io.nosqlbench.paramodel.persistence.ExecutionRepository;
import io.nosqlbench.paramodel.plan.ExecutionPlan;

import java.util.*;

///
/// In-memory execution repository for testing.
///
/// Stores execution plans and execution results in separate maps,
/// keyed by plan ID and execution ID respectively.
///
/// @see ExecutionRepository
/// @since 0.1.0
///
public class MockExecutionRepository implements ExecutionRepository {
    private final Map<String, ExecutionPlan> plans = new LinkedHashMap<>();
    private final Map<String, Executor.ExecutionResult> executions = new LinkedHashMap<>();

    /// Creates a new empty execution repository.
    public MockExecutionRepository() {}

    @Override
    public void savePlan(ExecutionPlan plan) {
        Objects.requireNonNull(plan, "plan must not be null");
        plans.put(plan.id(), plan);
    }

    @Override
    public Optional<ExecutionPlan> getPlan(String planId) {
        return Optional.ofNullable(plans.get(planId));
    }

    @Override
    public void saveExecution(Executor.ExecutionResult result) {
        Objects.requireNonNull(result, "result must not be null");
        executions.put(result.executionId(), result);
    }

    @Override
    public Optional<Executor.ExecutionResult> getExecution(String executionId) {
        return Optional.ofNullable(executions.get(executionId));
    }

    @Override
    public List<Executor.ExecutionResult> listExecutions(String planId) {
        return executions.values().stream()
            .filter(e -> planId.equals(e.plan().id()))
            .toList();
    }
}
