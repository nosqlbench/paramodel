package examples;

import io.nosqlbench.paramodel.parameters.*;
import io.nosqlbench.paramodel.mock.parameters.*;
import io.nosqlbench.paramodel.mock.plan.*;
import io.nosqlbench.paramodel.plan.*;

import java.util.Map;

/**
 * Example demonstrating constraint usage.
 *
 * Shows:
 * - Simple predicates
 * - Constraint composition (AND, OR, NOT)
 * - Cross-parameter constraints
 * - TestPlan with constraints
 */
public class ConstraintsExample {

    public static void main(String[] args) {
        System.out.println("=== Constraints Example ===\n");

        // 1. Simple constraints
        System.out.println("1. Simple constraints:");

        Constraint<Integer> positive = n -> n > 0;
        Constraint<Integer> lessThan100 = n -> n < 100;
        Constraint<Integer> even = n -> n % 2 == 0;

        System.out.println("   positive.test(5): " + positive.test(5));
        System.out.println("   positive.test(-5): " + positive.test(-5));
        System.out.println("   even.test(4): " + even.test(4));
        System.out.println("   even.test(5): " + even.test(5));
        System.out.println();

        // 2. Constraint composition
        System.out.println("2. Constraint composition:");

        Constraint<Integer> positiveEven = positive.and(even);
        Constraint<Integer> inRange = positive.and(lessThan100);
        Constraint<Integer> odd = even.negate();

        System.out.println("   positiveEven.test(4): " + positiveEven.test(4));
        System.out.println("   positiveEven.test(3): " + positiveEven.test(3));
        System.out.println("   inRange.test(50): " + inRange.test(50));
        System.out.println("   inRange.test(150): " + inRange.test(150));
        System.out.println("   odd.test(5): " + odd.test(5));
        System.out.println();

        // 3. Cross-parameter constraints
        System.out.println("3. Cross-parameter constraints:");

        // Constraint: threads must be power of 2
        Constraint<Map<String, Value<?>>> powerOf2Threads = assignment -> {
            Value<?> threadsValue = assignment.get("threads");
            if (threadsValue == null) return true;
            Integer threads = (Integer) threadsValue.value();
            return threads > 0 && (threads & (threads - 1)) == 0;
        };

        // Constraint: batchSize must be <= threads * 100
        Constraint<Map<String, Value<?>>> batchSizeLimit = assignment -> {
            Value<?> threadsValue = assignment.get("threads");
            Value<?> batchValue = assignment.get("batchSize");
            if (threadsValue == null || batchValue == null) return true;
            Integer threads = (Integer) threadsValue.value();
            Integer batch = (Integer) batchValue.value();
            return batch <= threads * 100;
        };

        // Test constraints
        MockValue<Integer> threads4 = MockValue.of(4, "threads");
        MockValue<Integer> threads5 = MockValue.of(5, "threads");
        MockValue<Integer> batch100 = MockValue.of(100, "batchSize");
        MockValue<Integer> batch1000 = MockValue.of(1000, "batchSize");

        Map<String, Value<?>> valid = Map.of(
            "threads", threads4,
            "batchSize", batch100
        );
        Map<String, Value<?>> invalidThreads = Map.of(
            "threads", threads5,
            "batchSize", batch100
        );
        Map<String, Value<?>> invalidBatch = Map.of(
            "threads", threads4,
            "batchSize", batch1000
        );

        System.out.println("   threads=4, batch=100 (valid): " +
            powerOf2Threads.test(valid) + " && " + batchSizeLimit.test(valid));
        System.out.println("   threads=5, batch=100 (invalid threads): " +
            powerOf2Threads.test(invalidThreads));
        System.out.println("   threads=4, batch=1000 (invalid batch): " +
            batchSizeLimit.test(invalidBatch));
        System.out.println();

        // 4. TestPlan with constraints
        System.out.println("4. TestPlan with constraints:");

        MockDomain<Integer> threadDomain = MockDomain.of(1, 2, 4, 8, 16);
        MockDomain<Integer> batchDomain = MockDomain.of(100, 500, 1000, 5000);

        MockParameter<Integer> threads = MockParameter.of("threads", threadDomain);
        MockParameter<Integer> batchSize = MockParameter.of("batchSize", batchDomain);

        TestPlan plan = MockTestPlan.builder()
            .parameter(threads)
            .parameter(batchSize)
            .axis(MockAxis.of("concurrency", MockElement.exhaustive("threads")))
            .axis(MockAxis.of("batching", MockElement.exhaustive("batchSize")))
            .constraint(batchSizeLimit)
            .build();

        ValidationResult validation = plan.validate();
        System.out.println("   TestPlan validation: " +
            (validation.isValid() ? "PASSED" : "FAILED"));
        System.out.println("   Parameters: " + plan.parameters().size());
        System.out.println("   Constraints: " + plan.constraints().size());
        System.out.println();

        // 5. Complex constraint example
        System.out.println("5. Complex constraint:");

        // Constraint: (threads is power of 2) AND (batch <= threads * 100)
        //          OR (threads == 1)
        Constraint<Map<String, Value<?>>> complexConstraint =
            powerOf2Threads.and(batchSizeLimit)
                .or(assignment -> {
                    Value<?> t = assignment.get("threads");
                    return t != null && ((Integer) t.value()) == 1;
                });

        System.out.println("   Complex constraint defined:");
        System.out.println("   (powerOf2 AND batchLimit) OR (threads == 1)");
        System.out.println("   Applied to trial validation");
    }
}
