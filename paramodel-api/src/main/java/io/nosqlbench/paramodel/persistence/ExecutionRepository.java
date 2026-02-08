package io.nosqlbench.paramodel.persistence;

import io.nosqlbench.paramodel.plan.ExecutionPlan;
import io.nosqlbench.paramodel.execution.Executor;

import java.util.List;
import java.util.Optional;

///
/// # ExecutionRepository
///
/// Persists execution plans, execution history, and execution metadata.
///
public interface ExecutionRepository {

    static ExecutionRepository create() {
        throw new UnsupportedOperationException(
            "ExecutionRepository.create() requires a concrete implementation");
    }

    void savePlan(ExecutionPlan plan);

    Optional<ExecutionPlan> getPlan(String planId);

    void saveExecution(Executor.ExecutionResult result);

    Optional<Executor.ExecutionResult> getExecution(String executionId);

    List<Executor.ExecutionResult> listExecutions(String planId);
}
