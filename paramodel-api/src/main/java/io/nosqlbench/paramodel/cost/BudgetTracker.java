package io.nosqlbench.paramodel.cost;

import java.util.Optional;

///
/// # BudgetTracker
///
/// Tracks actual costs against budgets and sends alerts when limits approached.
///
public interface BudgetTracker {

    static BudgetTracker create() {
        throw new UnsupportedOperationException(
            "BudgetTracker.create() requires a concrete implementation");
    }

    void setBudget(double budget);

    void recordCost(double cost);

    double totalCost();

    double remainingBudget();

    boolean isOverBudget();

    Optional<Double> projectedCost();
}
