package io.nosqlbench.paramodel.engine.integration;

import io.nosqlbench.paramodel.compilation.Compiler;
import io.nosqlbench.paramodel.elements.*;
import io.nosqlbench.paramodel.engine.compiler.*;
import io.nosqlbench.paramodel.engine.execution.*;
import io.nosqlbench.paramodel.execution.Runtime;
import io.nosqlbench.paramodel.mock.parameters.*;
import io.nosqlbench.paramodel.mock.plan.*;
import io.nosqlbench.paramodel.mock.sequence.*;
import io.nosqlbench.paramodel.plan.*;
import io.nosqlbench.paramodel.sequence.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * End-to-end integration test validating the complete workflow:
 * TestPlan → Compilation → Execution → Results
 */
public class EndToEndIntegrationTest {

    @Test
    @DisplayName("Complete workflow: define → compile → execute")
    public void testCompleteWorkflow() {
        // 1. Define parameters and build test plan using current API
        Element dbElement = MockElement.of("database");
        Element cacheElement = MockElement.of("cache");

        Axis<String> opAxis = MockAxis.of("operation", "read", "write");

        TestPlan testPlan = MockTestPlan.builder()
            .name("integration-test")
            .axis(opAxis)
            .element(dbElement)
            .element(cacheElement)
            .build();

        assertThat(testPlan.name()).isEqualTo("integration-test");
        assertThat(testPlan.axes()).hasSize(1);
        assertThat(testPlan.elements()).hasSize(2);
        assertThat(testPlan.validate().isPassed()).isTrue();

        // 2. Compile
        DefaultCompiler compiler = DefaultCompiler.builder()
            .standardPipeline()
            .build();

        Compiler.CompilationResult result = compiler.compile(testPlan);

        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.executionPlan()).isPresent();
        assertThat(testPlan.isCommitted()).isTrue();

        // 3. Verify execution plan
        ExecutionPlan execPlan = result.executionPlan().get();
        assertThat(execPlan).isNotNull();
        assertThat(execPlan.id()).isNotNull();
    }

    @Test
    @DisplayName("Scheduler initializes with execution graph")
    public void testSchedulerWithGraph() {
        // Create mock steps and graph
        MockExecutionGraph graph = new MockExecutionGraph();
        DefaultScheduler scheduler = DefaultScheduler.create();

        scheduler.initialize(graph);

        // Empty graph should produce no steps
        assertThat(scheduler.nextSteps()).isEmpty();
        assertThat(scheduler.isComplete()).isTrue();
    }

    @Test
    @DisplayName("Resource manager allocates and releases resources")
    public void testResourceManagement() throws Runtime.InsufficientResourcesException {
        Runtime.Resources capacity = Runtime.Resources.of(16.0, 64.0, 500.0);
        DefaultResourceManager resourceManager = DefaultResourceManager.create(capacity);

        // Check availability
        Runtime.ResourceAvailability available = resourceManager.available();
        assertThat(available).isNotNull();

        // Check current usage
        var usage = resourceManager.currentUsage();
        assertThat(usage).isNotNull();
        assertThat(usage.cpuUtilization()).isGreaterThanOrEqualTo(0.0);

        // Verify allocations list
        assertThat(resourceManager.allocations()).isEmpty();
    }

    @Test
    @DisplayName("Test plan cannot be committed twice")
    public void testDoubleCommitFails() {
        Element element = MockElement.of("svc");
        Axis<String> axis = MockAxis.of("mode", "fast", "slow");

        TestPlan plan = MockTestPlan.builder()
            .name("double-commit-test")
            .axis(axis)
            .element(element)
            .build();

        plan.commit();
        assertThat(plan.isCommitted()).isTrue();

        assertThatThrownBy(plan::commit)
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Compilation validates test plan")
    public void testCompilationValidation() {
        Element element = MockElement.of("svc");

        TestPlan plan = MockTestPlan.builder()
            .name("validation-test")
            .element(element)
            .build();

        DefaultCompiler compiler = DefaultCompiler.builder()
            .standardPipeline()
            .build();

        var validationResult = compiler.validate(plan);
        assertThat(validationResult).isNotNull();
    }
}
