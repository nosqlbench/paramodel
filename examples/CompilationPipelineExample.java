package examples;

import io.nosqlbench.paramodel.compilation.*;
import io.nosqlbench.paramodel.engine.compiler.*;
import io.nosqlbench.paramodel.mock.parameters.*;
import io.nosqlbench.paramodel.mock.plan.*;
import io.nosqlbench.paramodel.plan.*;

/**
 * Example demonstrating the 8-stage compilation pipeline.
 *
 * Shows how to:
 * - Build a custom compiler
 * - Configure compilation stages
 * - Compile TestPlan to ExecutionPlan
 * - Access compilation artifacts
 */
public class CompilationPipelineExample {

    public static void main(String[] args) {
        System.out.println("=== Compilation Pipeline Example ===\n");

        // 1. Create test plan
        TestPlan testPlan = createTestPlan();
        System.out.println("1. TestPlan created");
        System.out.println("   Parameters: " + testPlan.parameters().size());
        System.out.println("   Axes: " + testPlan.axes().size());
        System.out.println();

        // 2. Build compiler with standard pipeline
        Compiler compiler = DefaultCompiler.builder()
            .standardPipeline()
            .build();
        System.out.println("2. Compiler built with 8-stage pipeline:");
        System.out.println("   - Validation");
        System.out.println("   - Normalization");
        System.out.println("   - Trial Enumeration");
        System.out.println("   - Instantiation");
        System.out.println("   - Step Generation");
        System.out.println("   - Dependency Analysis");
        System.out.println("   - Optimization");
        System.out.println("   - Code Generation");
        System.out.println();

        // 3. Compile
        System.out.println("3. Compiling...");
        try {
            ExecutionPlan executionPlan = compiler.compile(testPlan);

            System.out.println("   ✓ Compilation successful!");
            System.out.println();

            // 4. Inspect execution plan
            System.out.println("4. ExecutionPlan details:");
            System.out.println("   Estimated trials: " + executionPlan.estimatedTrialCount());
            System.out.println("   Steps: " + executionPlan.steps().size());
            System.out.println("   Graph nodes: " + executionPlan.graph().nodes().size());
            System.out.println("   Compilation version: " + executionPlan.metadata().compilationVersion());
            System.out.println("   Compiled at: " + executionPlan.metadata().compiledAt());
            System.out.println("   Fingerprint: " + executionPlan.metadata().fingerprint());
            System.out.println();

            // 5. Show optimization strategy applied
            System.out.println("5. Optimization:");
            System.out.println("   Strategy: " + testPlan.optimizationStrategy());
            System.out.println("   Metrics: " + executionPlan.metadata().optimizationMetrics());

        } catch (DefaultCompiler.CompilationException e) {
            System.err.println("   ✗ Compilation failed!");
            System.err.println("   Errors:");
            for (String error : e.getErrors()) {
                System.err.println("     - " + error);
            }
        }
    }

    private static TestPlan createTestPlan() {
        // Create parameters
        MockDomain<String> dbDomain = MockDomain.of("cassandra", "mongodb", "postgres");
        MockDomain<String> queryDomain = MockDomain.of("point", "range", "aggregate");
        MockDomain<Integer> batchDomain = MockDomain.of(1, 10, 100, 1000);

        MockParameter<String> database = MockParameter.of("database", dbDomain);
        MockParameter<String> queryType = MockParameter.of("queryType", queryDomain);
        MockParameter<Integer> batchSize = MockParameter.of("batchSize", batchDomain);

        // Build test plan
        return MockTestPlan.builder()
            .parameter(database)
            .parameter(queryType)
            .parameter(batchSize)
            .axis(MockAxis.of("databases",
                MockElement.exhaustive("database")))
            .axis(MockAxis.of("queries",
                MockElement.exhaustive("queryType")))
            .axis(MockAxis.of("batching",
                MockElement.boundary("batchSize")))
            .optimizationStrategy(OptimizationStrategy.PRUNE_REDUNDANT)
            .build();
    }
}
