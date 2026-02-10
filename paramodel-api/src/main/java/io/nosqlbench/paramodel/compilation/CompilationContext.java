package io.nosqlbench.paramodel.compilation;

import io.nosqlbench.paramodel.elements.Element;
import io.nosqlbench.paramodel.plan.Barrier;
import io.nosqlbench.paramodel.plan.TestPlan;
import io.nosqlbench.paramodel.sequence.Trial;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

///
/// # CompilationContext
///
/// Shared state and services available during the {@link Compiler} compilation process.
/// The context provides access to test plan components, intermediate compilation results,
/// configuration, and utilities needed by compilation stages.
///
/// ## Context Lifecycle
///
/// The compilation context exists throughout the compilation pipeline:
///
/// ```
/// Context Lifecycle:
///
/// create() → Context initialized
///    │
///    ├─→ [Validation Stage] → Access: testPlan, options
///    │
///    ├─→ [Normalization Stage] → Access: testPlan, mutate: normalizedPlan
///    │
///    ├─→ [Enumeration Stage] → Access: normalizedPlan, mutate: trials
///    │
///    ├─→ [Instantiation Stage] → Access: trials, elements, mutate: instances
///    │
///    ├─→ [Step Generation Stage] → Access: trials, instances, mutate: steps
///    │
///    ├─→ [Dependency Stage] → Access: steps, mutate: graph
///    │
///    ├─→ [Optimization Stage] → Access: graph, mutate: optimizedGraph
///    │
///    └─→ [Code Gen Stage] → Access: all, produce: ExecutionPlan
///         │
///         └─→ destroy() → Context cleaned up
/// ```
///
/// ## State Management
///
/// The context manages both immutable inputs and mutable intermediate results:
///
/// ```
/// Context State:
///
/// Immutable Inputs:
///   - testPlan: Original test plan
///   - options: Compiler options
///   - configuration: Environment configuration
///
/// Mutable Intermediate State:
///   - normalizedPlan: Normalized test plan
///   - trials: Enumerated trial instances
///   - elementInstances: Element instance plan
///   - steps: Generated atomic steps
///   - graph: Execution dependency graph
///   - optimizationState: Optimizer working state
///
/// Compilation Artifacts:
///   - errors: Accumulated errors
///   - warnings: Accumulated warnings
///   - metrics: Compilation metrics
///   - metadata: Result metadata
/// ```
///
/// ## Element Instance Registry
///
/// Context tracks planned element instances:
///
/// ```
/// Instance Registry:
///
/// Element: database (type: postgres)
///   Instance db_instance_1:
///     scope: global
///     trials: [all]
///     lifecycle: START → END
///
/// Element: cache (type: redis)
///   Instance cache_instance_10:
///     scope: concurrency=10
///     trials: [t1, t2, t3]
///     lifecycle: after db_instance_1 → before trials done
///
///   Instance cache_instance_50:
///     scope: concurrency=50
///     trials: [t4, t5, t6]
///     lifecycle: after db_instance_1 → before trials done
///
/// Registry Operations:
///   - planInstance(element, scope) → instance_id
///   - getInstance(instance_id) → InstancePlan
///   - getInstancesForElement(element) → List<InstancePlan>
///   - getInstanceForTrial(element, trial) → InstancePlan
/// ```
///
/// ## Trial Enumeration State
///
/// Context maintains trial generation state:
///
/// ```
/// Trial Enumeration:
///
/// Cartesian Product: [cache_size] × [concurrency]
///   Raw space: 3 × 3 = 9 combinations
///
/// After constraint filtering:
///   Filtered: 9 → 8 trials (1 invalid combination removed)
///
/// After ordering:
///   Ordered: [t3, t1, t7, t9, t2, t4, t6, t8, t5] (SHUFFLED)
///
/// Trial Registry:
///   - trials: List<Trial> (ordered)
///   - trialById: Map<String, Trial>
///   - trialsByAxis: Map<String, List<Trial>>
/// ```
///
/// ## Error and Warning Accumulation
///
/// Context collects diagnostics throughout compilation:
///
/// ```
/// Diagnostic Accumulation:
///
/// [Validation Stage]
///   ERROR: Relationship graph contains cycle: db → cache → db
///   WARNING: Trial space size (100,000) is large
///
/// [Enumeration Stage]
///   WARNING: 12 trials filtered due to constraints
///
/// [Instantiation Stage]
///   INFO: Element 'cache' will create 3 instances
///
/// [Optimization Stage]
///   INFO: Applied barrier coalescing: 18 → 12 barriers
///   INFO: Applied step fusion: 45 → 38 steps
///
/// Result:
///   - 1 ERROR → compilation fails
///   - 3 WARNINGS → included in result
///   - 2 INFO → logged for debugging
/// ```
///
/// ## Configuration and Environment
///
/// Context provides access to configuration:
///
/// ```
/// Configuration:
///
/// Compiler Options:
///   - strategy: OPTIMIZE_EXECUTION
///   - optimizationLevel: AGGRESSIVE
///   - maxTrialSpaceSize: 1,000,000
///
/// Environment:
///   - region: us-west-2
///   - account: prod
///   - resourceQuotas: {cpu: 512, memory: 2048GB}
///
/// Feature Flags:
///   - speculativeExecution: false
///   - checkpointCompression: true
///   - experimentalOptimizations: false
/// ```
///
/// ## Metrics Collection
///
/// Context tracks compilation performance:
///
/// ```
/// Compilation Metrics:
///
/// Stage Timings:
///   - Validation: 0.12s
///   - Normalization: 0.08s
///   - Enumeration: 2.34s
///   - Instantiation: 0.45s
///   - Step Generation: 1.87s
///   - Dependency Analysis: 0.92s
///   - Optimization: 3.21s
///   - Code Generation: 0.34s
///   Total: 9.33s
///
/// Counters:
///   - trials_generated: 10,000
///   - trials_filtered: 127
///   - steps_generated: 30,456
///   - barriers_generated: 89
///   - optimizations_applied: 12
///
/// Memory:
///   - peak_usage: 1.2 GB
///   - allocations: 847,291
/// ```
///
/// ## Usage Examples
///
/// ### Example 1: Accessing Context in Compilation Stage
///
/// ```java
/// public class TrialEnumerationStage implements CompilationStage {
///     @Override
///     public void execute(CompilationContext ctx) {
///         TestPlan plan = ctx.testPlan();
///         List<Axis<?>> axes = plan.axes();
///
///         // Generate Cartesian product
///         List<Trial> trials = generateCartesianProduct(axes);
///
///         // Filter by constraints
///         trials = filterByConstraints(trials, plan.constraints());
///
///         // Store in context
///         ctx.setTrials(trials);
///
///         // Record metrics
///         ctx.recordMetric("trials_generated", trials.size());
///     }
/// }
/// ```
///
/// ### Example 2: Element Instance Planning
///
/// ```java
/// public void planElementInstances(CompilationContext ctx) {
///     TestPlan plan = ctx.testPlan();
///     List<Trial> trials = ctx.trials();
///
///     for (Element element : plan.elements()) {
///         // Analyze relationships to determine instance scope
///         InstanceScope scope = analyzeScope(element, plan.relationships());
///
///         if (scope.isGlobal()) {
///             // One instance for all trials
///             ctx.planInstance(element, trials, "global");
///         } else if (scope.isPerAxis()) {
///             // One instance per axis value
///             Axis<?> axis = scope.axis();
///             for (Object value : axis.values()) {
///                 List<Trial> scopedTrials = filterByAxisValue(trials, axis, value);
///                 ctx.planInstance(element, scopedTrials, axis.name() + "=" + value);
///             }
///         } else {
///             // One instance per trial
///             for (Trial trial : trials) {
///                 ctx.planInstance(element, List.of(trial), trial.id());
///             }
///         }
///     }
/// }
/// ```
///
/// ### Example 3: Error Reporting
///
/// ```java
/// public void validateRelationships(CompilationContext ctx) {
///     TestPlan plan = ctx.testPlan();
///     Map<ElementPair, RelationshipType> relationships = plan.relationships();
///
///     // Check for cycles
///     if (hasCycle(relationships)) {
///         ctx.addError(
///             ErrorSeverity.ERROR,
///             "Relationship graph contains cycle",
///             "relationships",
///             "Remove circular dependencies between elements"
///         );
///     }
///
///     // Check for conflicts
///     for (ElementPair pair : relationships.keySet()) {
///         RelationshipType r1 = relationships.get(pair);
///         RelationshipType r2 = relationships.get(pair.reverse());
///
///         if (r1 != null && r2 != null && !r1.compatibleWith(r2)) {
///             ctx.addWarning(
///                 "Asymmetric relationship between " + pair,
///                 "Consider using symmetric relationships"
///             );
///         }
///     }
/// }
/// ```
///
/// ### Example 4: Querying Context State
///
/// ```java
/// public void optimizeSteps(CompilationContext ctx) {
///     List<AtomicStep> steps = ctx.steps();
///     List<Barrier> barriers = ctx.barriers();
///
///     // Find barriers that can be coalesced
///     List<Barrier> coalesced = coalesceBarriers(barriers);
///
///     if (coalesced.size() < barriers.size()) {
///         int saved = barriers.size() - coalesced.size();
///         ctx.setBarriers(coalesced);
///         ctx.recordMetric("barriers_coalesced", saved);
///         ctx.addInfo("Coalesced " + saved + " barriers");
///     }
///
///     // Check trial space size
///     int trialCount = ctx.trials().size();
///     long maxSize = ctx.options().maxTrialSpaceSize();
///
///     if (trialCount > maxSize) {
///         ctx.addError(
///             ErrorSeverity.ERROR,
///             "Trial space size " + trialCount + " exceeds limit " + maxSize,
///             "trial_enumeration",
///             "Reduce axis cardinalities or add constraints"
///         );
///     }
/// }
/// ```
///
/// ### Example 5: Metrics and Timing
///
/// ```java
/// public ExecutionPlan compile(TestPlan plan, CompilationContext ctx) {
///     ctx.startTimer("total_compilation");
///
///     ctx.startTimer("validation");
///     validate(plan, ctx);
///     ctx.stopTimer("validation");
///
///     ctx.startTimer("enumeration");
///     enumerateTrials(plan, ctx);
///     ctx.stopTimer("enumeration");
///
///     // ... other stages ...
///
///     ctx.stopTimer("total_compilation");
///
///     // Retrieve metrics
///     Map<String, Duration> timings = ctx.timings();
///     Map<String, Long> counters = ctx.counters();
///
///     System.out.printf("Compilation took %s%n",
///         timings.get("total_compilation"));
///     System.out.printf("Generated %d trials%n",
///         counters.get("trials_generated"));
/// }
/// ```
///
/// ## Contract Requirements
///
/// ### Thread Safety
/// - Context instances MUST be confined to single thread
/// - Context MUST NOT be shared across concurrent compilation tasks
///
/// ### State Consistency
/// - State transitions MUST follow compilation stage order
/// - Stages MUST NOT access state not yet computed
/// - Errors MUST halt compilation progression
///
/// ### Resource Management
/// - Context MUST release resources after compilation completes
/// - Large intermediate data SHOULD be cleared when no longer needed
/// - Memory usage SHOULD be bounded
///
/// @see Compiler
/// @see CompilationStage
/// @see TestPlan
///
public interface CompilationContext {

    ///
    /// Returns the test plan being compiled.
    ///
    /// @return Source test plan
    ///
    TestPlan testPlan();

    ///
    /// Returns the compiler options.
    ///
    /// @return Compiler options
    ///
    Compiler.CompilerOptions options();

    ///
    /// Returns environment configuration.
    ///
    /// @return Environment configuration (unmodifiable)
    ///
    Map<String, Object> environment();

    ///
    /// Returns the enumerated trials.
    ///
    /// Available after enumeration stage.
    ///
    /// @return Trials if enumeration completed
    ///
    Optional<List<Trial>> trials();

    ///
    /// Sets the enumerated trials.
    ///
    /// @param trials Trial list
    ///
    void setTrials(List<Trial> trials);

    ///
    /// Returns all planned element instances.
    ///
    /// Available after instantiation stage.
    ///
    /// @return Element instances if instantiation completed
    ///
    Optional<List<ElementInstance>> elementInstances();

    ///
    /// Plans a new element instance with dependencies.
    ///
    /// @param element Element to instantiate
    /// @param trials Trials using this instance
    /// @param scopeDescription Human-readable scope description
    /// @param dependsOn Set of instance IDs this instance depends on
    /// @return Instance identifier
    ///
    String planInstance(Element element, List<Trial> trials, String scopeDescription, Set<String> dependsOn);

    ///
    /// Plans a new element instance.
    ///
    /// @param element Element to instantiate
    /// @param trials Trials using this instance
    /// @param scopeDescription Human-readable scope description
    /// @return Instance identifier
    ///
    String planInstance(Element element, List<Trial> trials, String scopeDescription);

    ///
    /// Finds the instance for a specific trial and element.
    ///
    /// @param elementName Element name
    /// @param trial Trial
    /// @return Element instance
    ///
    Optional<ElementInstance> getInstanceForTrial(String elementName, Trial trial);

    ///
    /// Returns all instances for an element.
    ///
    /// @param elementName Element name
    /// @return Element instances
    ///
    List<ElementInstance> getInstancesForElement(String elementName);

    ///
    /// Returns generated atomic steps.
    ///
    /// Available after step generation stage.
    ///
    /// @return Steps if generation completed
    ///
    Optional<List<io.nosqlbench.paramodel.plan.AtomicStep>> steps();

    ///
    /// Sets the generated atomic steps.
    ///
    /// @param steps Step list
    ///
    void setSteps(List<io.nosqlbench.paramodel.plan.AtomicStep> steps);

    ///
    /// Returns generated barriers.
    ///
    /// Available after step generation stage.
    ///
    /// @return Barriers if generation completed
    ///
    Optional<List<Barrier>> barriers();

    ///
    /// Sets the generated barriers.
    ///
    /// @param barriers Barrier list
    ///
    void setBarriers(List<Barrier> barriers);

    ///
    /// Adds a compilation error.
    ///
    /// @param severity Error severity
    /// @param message Error message
    /// @param location Optional location identifier
    /// @param suggestion Optional suggestion for fixing
    ///
    void addError(Compiler.ErrorSeverity severity, String message,
                  String location, String suggestion);

    ///
    /// Adds a compilation warning.
    ///
    /// @param message Warning message
    /// @param suggestion Optional suggestion
    ///
    void addWarning(String message, String suggestion);

    ///
    /// Adds an info message.
    ///
    /// @param message Info message
    ///
    void addInfo(String message);

    ///
    /// Returns accumulated errors.
    ///
    /// @return Compilation errors (unmodifiable)
    ///
    List<Compiler.CompilationError> errors();

    ///
    /// Returns accumulated warnings.
    ///
    /// @return Compilation warnings (unmodifiable)
    ///
    List<Compiler.CompilationWarning> warnings();

    ///
    /// Checks if any errors have been reported.
    ///
    /// @return True if errors exist
    ///
    boolean hasErrors();

    ///
    /// Records a metric counter.
    ///
    /// @param name Metric name
    /// @param value Metric value
    ///
    void recordMetric(String name, long value);

    ///
    /// Records a metric with double value.
    ///
    /// @param name Metric name
    /// @param value Metric value
    ///
    void recordMetric(String name, double value);

    ///
    /// Starts a named timer.
    ///
    /// @param name Timer name
    ///
    void startTimer(String name);

    ///
    /// Stops a named timer.
    ///
    /// @param name Timer name
    ///
    void stopTimer(String name);

    ///
    /// Returns all recorded timings.
    ///
    /// @return Timings map (unmodifiable)
    ///
    Map<String, java.time.Duration> timings();

    ///
    /// Returns all recorded counters.
    ///
    /// @return Counters map (unmodifiable)
    ///
    Map<String, Long> counters();

    ///
    /// Stores arbitrary data in context.
    ///
    /// @param key Data key
    /// @param value Data value
    ///
    void put(String key, Object value);

    ///
    /// Retrieves arbitrary data from context.
    ///
    /// @param key Data key
    /// @return Data value if exists
    ///
    Optional<Object> get(String key);

    ///
    /// Element instance plan.
    ///
    record ElementInstance(
        String instanceId,
        Element element,
        List<Trial> trials,
        String scopeDescription,
        Set<String> dependsOn
    ) {}
}
