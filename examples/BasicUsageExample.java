package examples;

import io.nosqlbench.paramodel.core.*;
import io.nosqlbench.paramodel.mock.core.*;
import io.nosqlbench.paramodel.mock.plan.*;
import io.nosqlbench.paramodel.plan.*;
import io.nosqlbench.paramodel.sequence.*;

import java.util.List;
import java.util.Map;

/**
 * Basic usage example demonstrating parameter definition,
 * test plan creation, and trial enumeration.
 */
public class BasicUsageExample {

    public static void main(String[] args) {
        // 1. Define domains
        MockDomain<String> operationDomain = MockDomain.of("read", "write", "scan");
        MockDomain<Integer> threadDomain = MockDomain.of(1, 2, 4, 8, 16);

        // 2. Create parameters
        MockParameter<String> operation = MockParameter.of("operation", operationDomain);
        MockParameter<Integer> threads = MockParameter.of("threads", threadDomain);

        System.out.println("Parameters defined:");
        System.out.println("  - operation: " + operation.name());
        System.out.println("  - threads: " + threads.name());
        System.out.println();

        // 3. Build test plan
        MockTestPlan plan = MockTestPlan.builder()
            .parameter(operation)
            .parameter(threads)
            .axis(MockAxis.of("operations",
                MockElement.exhaustive("operation")))
            .axis(MockAxis.of("concurrency",
                MockElement.exhaustive("threads")))
            .build();

        System.out.println("TestPlan created:");
        System.out.println("  - Parameters: " + plan.parameters().size());
        System.out.println("  - Axes: " + plan.axes().size());
        System.out.println();

        // 4. Validate plan
        ValidationResult validation = plan.validate();
        System.out.println("Validation: " + (validation.isValid() ? "PASSED" : "FAILED"));
        System.out.println();

        // 5. Commit to execution plan
        ExecutionPlan execPlan = plan.commit();
        System.out.println("ExecutionPlan created:");
        System.out.println("  - Estimated trials: " + execPlan.estimatedTrialCount());
        System.out.println("  - Plan is committed: " + plan.isCommitted());
        System.out.println();

        // 6. Create sample trials manually for demonstration
        System.out.println("Sample trials:");
        for (String op : List.of("read", "write", "scan")) {
            for (Integer t : List.of(1, 4, 16)) {
                MockValue<String> opVal = MockValue.of(op, "operation");
                MockValue<Integer> threadVal = MockValue.of(t, "threads");

                MockTrial trial = MockTrial.builder()
                    .assignment("operation", opVal)
                    .assignment("threads", threadVal)
                    .build();

                System.out.println("  Trial " + trial.id() + ": " +
                    "operation=" + opVal.value() + ", threads=" + threadVal.value());
            }
        }
    }
}
