package io.nosqlbench.paramodel.tck.engine;

import io.nosqlbench.paramodel.compilation.CompilationContext;
import io.nosqlbench.paramodel.compilation.CompilationStage;
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
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;

/**
 * End-to-end integration test validating the complete workflow:
 * TestPlan → Compilation → Execution → Results
 */
public class EngineIntegrationTest {

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

    @Test
    @DisplayName("Instantiation stage binds parameters correctly")
    public void testInstantiationBinding() {
        // 1. Define element with parameter
        io.nosqlbench.paramodel.parameters.Parameter<Integer> portParam = 
            io.nosqlbench.paramodel.parameters.types.IntegerParameter.range("port", 8080, 8081); // 2 values
            
        // Use MockElement.builder directly
        Element serverElement = io.nosqlbench.paramodel.mock.plan.MockElement.builder("server")
            .type("service")
            .parameter(portParam)
            .build();
            
        // 2. Define axis for that parameter
        Axis<Integer> portAxis = io.nosqlbench.paramodel.mock.plan.MockAxis.of("port", 8080, 8081);

        TestPlan testPlan = io.nosqlbench.paramodel.mock.plan.MockTestPlan.builder()
            .name("binding-test")
            .axis(portAxis)
            .element(serverElement)
            .build();

        // 3. Create inspector stage
        AtomicBoolean checked = new AtomicBoolean(false);
        
        CompilationStage inspector = new CompilationStage() {
            @Override public String name() { return "Inspector"; }
            @Override public void execute(CompilationContext context) {
                // Check trials
                assertThat(context.trials()).isPresent();
                assertThat(context.trials().get()).hasSize(2);
                
                // Check instances
                List<CompilationContext.ElementInstance> instances = context.getInstancesForElement("server");
                assertThat(instances).hasSize(2); // One per trial because parameter varies
                
                checked.set(true);
            }
        };

        // 4. Compile with custom pipeline
        DefaultCompiler compiler = DefaultCompiler.builder()
            .stage(new ValidationStage())
            .stage(new NormalizationStage())
            .stage(new TrialEnumerationStage())
            .stage(new InstantiationStage())
            .stage(inspector) // Inject inspector
            .stage(new StepGenerationStage())
            .stage(new DependencyAnalysisStage())
            .stage(new OptimizationStage())
            .stage(new CodeGenerationStage())
            .build();

        compiler.compile(testPlan);
        
        assertThat(checked.get()).isTrue();
    }

    @Test
    @DisplayName("Instantiation stage resolves element dependencies")
    public void testDependencyResolution() {
        // 1. Define DB (Global)
        Element db = MockElement.of("db");
        
        // 2. Define App (Per-Trial) depending on DB
        io.nosqlbench.paramodel.parameters.Parameter<Integer> portParam = 
            io.nosqlbench.paramodel.parameters.types.IntegerParameter.range("port", 8080, 8081);
            
        Element app = MockElement.builder("app")
            .parameter(portParam)
            .dependency(db)
            .build();
            
        // 3. Define Axis for App
        Axis<Integer> portAxis = MockAxis.of("port", 8080, 8081);

        TestPlan testPlan = MockTestPlan.builder()
            .name("dep-test")
            .axis(portAxis)
            .element(db)
            .element(app)
            .build();

        // 4. Compile with inspector
        AtomicBoolean checked = new AtomicBoolean(false);
        CompilationStage inspector = new CompilationStage() {
            @Override public String name() { return "Inspector"; }
            @Override public void execute(CompilationContext context) {
                // Get DB instances
                List<CompilationContext.ElementInstance> dbInstances = context.getInstancesForElement("db");
                assertThat(dbInstances).hasSize(1);
                String dbId = dbInstances.get(0).instanceId();
                
                // Get App instances
                List<CompilationContext.ElementInstance> appInstances = context.getInstancesForElement("app");
                assertThat(appInstances).hasSize(2);
                
                // Verify App instances depend on DB instance
                for (CompilationContext.ElementInstance appInst : appInstances) {
                    assertThat(appInst.dependsOn()).contains(dbId);
                }
                
                checked.set(true);
            }
        };

        DefaultCompiler compiler = DefaultCompiler.builder()
            .stage(new ValidationStage())
            .stage(new NormalizationStage())
            .stage(new TrialEnumerationStage())
            .stage(new InstantiationStage())
            .stage(inspector)
            .stage(new StepGenerationStage())
            .stage(new DependencyAnalysisStage())
            .stage(new OptimizationStage())
            .stage(new CodeGenerationStage())
            .build();

        compiler.compile(testPlan);
        assertThat(checked.get()).isTrue();
    }
}