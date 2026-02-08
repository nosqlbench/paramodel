package io.nosqlbench.paramodel.cost;

import io.nosqlbench.paramodel.plan.ExecutionPlan;
import io.nosqlbench.paramodel.plan.TestPlan;

import java.time.Duration;
import java.util.Map;

///
/// # CostEstimator
///
/// Estimates execution costs based on resource usage, element pricing, and duration.
///
/// ## Cost Components
///
/// ```
/// Total Cost = Compute Cost + Storage Cost + Network Cost + Element Cost
///
/// Compute Cost = CPU hours × CPU rate + Memory GB-hours × Memory rate
/// Storage Cost = Storage GB-hours × Storage rate
/// Network Cost = Data transferred GB × Network rate
/// Element Cost = ∑(element hours × element rate)
/// ```
///
/// ## Usage Examples
///
/// ### Example 1: Estimate Test Plan
///
/// ```java
/// CostEstimator estimator = CostEstimator.create();
/// CostEstimate estimate = estimator.estimate(testPlan);
///
/// System.out.printf("Estimated cost: $%.2f%n", estimate.totalCost());
/// System.out.printf("  Compute: $%.2f%n", estimate.computeCost());
/// System.out.printf("  Storage: $%.2f%n", estimate.storageCost());
/// System.out.printf("  Network: $%.2f%n", estimate.networkCost());
/// ```
///
public interface CostEstimator {

    static CostEstimator create() {
        throw new UnsupportedOperationException(
            "CostEstimator.create() requires a concrete implementation");
    }

    CostEstimate estimate(TestPlan plan);

    CostEstimate estimate(ExecutionPlan plan);

    interface CostEstimate {
        double totalCost();
        double computeCost();
        double storageCost();
        double networkCost();
        double elementCost();
        Map<String, Double> costBreakdown();
        Duration estimatedDuration();
    }
}
