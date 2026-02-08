package io.nosqlbench.paramodel.compilation;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

///
/// # CompilationStage
///
/// Represents a single stage in the {@link Compiler} pipeline, encapsulating a discrete
/// transformation or validation step. Stages execute sequentially, each consuming and
/// producing state via the {@link CompilationContext}.
///
/// ## Stage Pipeline Architecture
///
/// Compilation proceeds through a sequence of stages:
///
/// ```
/// Stage Pipeline:
///
/// CompilationContext
///   │
///   ├─→ ValidationStage
///   │     Input: testPlan
///   │     Action: Validate syntax, semantics, policies
///   │     Output: errors/warnings or proceed
///   │
///   ├─→ NormalizationStage
///   │     Input: testPlan
///   │     Action: Apply defaults, canonicalize
///   │     Output: normalizedPlan
///   │
///   ├─→ TrialEnumerationStage
///   │     Input: normalizedPlan
///   │     Action: Generate Cartesian product, filter
///   │     Output: trials
///   │
///   ├─→ InstantiationStage
///   │     Input: trials, elements, relationships
///   │     Action: Compute instance scopes
///   │     Output: elementInstances
///   │
///   ├─→ StepGenerationStage
///   │     Input: trials, elementInstances
///   │     Action: Generate deploy/execute/teardown steps
///   │     Output: steps, barriers
///   │
///   ├─→ DependencyAnalysisStage
///   │     Input: steps, barriers
///   │     Action: Build dependency graph, detect cycles
///   │     Output: executionGraph
///   │
///   ├─→ OptimizationStage
///   │     Input: executionGraph
///   │     Action: Apply optimization passes
///   │     Output: optimizedGraph
///   │
///   └─→ CodeGenerationStage
///         Input: optimizedGraph, all metadata
///         Action: Construct ExecutionPlan
///         Output: ExecutionPlan
/// ```
///
/// ## Stage Contract
///
/// Each stage follows a standard contract:
///
/// ```
/// Stage Execution Pattern:
///
/// execute(context):
///   1. Check preconditions
///      - Verify required inputs exist in context
///      - Validate no errors from previous stages
///
///   2. Perform transformation
///      - Read inputs from context
///      - Execute stage logic
///      - Produce outputs
///
///   3. Update context
///      - Store outputs in context
///      - Record metrics
///      - Report errors/warnings
///
///   4. Check postconditions
///      - Verify outputs are valid
///      - Update stage completion status
///
/// Example:
///
///   class TrialEnumerationStage implements CompilationStage {
///       public void execute(CompilationContext ctx) {
///           // 1. Preconditions
///           TestPlan plan = ctx.testPlan();
///           if (ctx.hasErrors()) return; // Abort if previous errors
///
///           // 2. Transformation
///           List<Trial> trials = enumerateTrials(plan);
///
///           // 3. Update context
///           ctx.setTrials(trials);
///           ctx.recordMetric("trials_enumerated", trials.size());
///
///           // 4. Postconditions
///           if (trials.isEmpty()) {
///               ctx.addError(ERROR, "No trials generated", null, null);
///           }
///       }
///   }
/// ```
///
/// ## Stage Ordering and Dependencies
///
/// Stages have explicit dependencies:
///
/// ```
/// Stage Dependencies:
///
/// ValidationStage
///   depends on: [none]
///   produces: validation status
///
/// NormalizationStage
///   depends on: [ValidationStage]
///   produces: normalizedPlan
///
/// TrialEnumerationStage
///   depends on: [NormalizationStage]
///   produces: trials
///
/// InstantiationStage
///   depends on: [TrialEnumerationStage]
///   produces: elementInstances
///
/// StepGenerationStage
///   depends on: [InstantiationStage]
///   produces: steps, barriers
///
/// DependencyAnalysisStage
///   depends on: [StepGenerationStage]
///   produces: executionGraph
///
/// OptimizationStage
///   depends on: [DependencyAnalysisStage]
///   produces: optimizedGraph
///
/// CodeGenerationStage
///   depends on: [OptimizationStage]
///   produces: ExecutionPlan
/// ```
///
/// ## Error Handling
///
/// Stages report errors that control pipeline flow:
///
/// ```
/// Error Handling Strategy:
///
/// Stage executes:
///   ├─ Success → Continue to next stage
///   ├─ Warning → Continue but log warning
///   └─ Error → Abort pipeline, return error result
///
/// Example Error Scenarios:
///
/// ValidationStage:
///   ERROR: "Relationship graph contains cycle"
///   → Abort compilation immediately
///
/// TrialEnumerationStage:
///   WARNING: "Trial space size (100,000) is large"
///   → Continue but warn user
///
///   ERROR: "Trial space exceeds limit (1,000,000)"
///   → Abort compilation
///
/// OptimizationStage:
///   INFO: "Applied barrier coalescing"
///   → Continue, record optimization
/// ```
///
/// ## Stage Skipping and Conditional Execution
///
/// Some stages may be skipped based on options:
///
/// ```
/// Conditional Execution:
///
/// if (options.skipValidation()) {
///   skip(ValidationStage)
/// }
///
/// if (options.optimizationLevel() == NONE) {
///   skip(OptimizationStage)
/// }
///
/// if (options.dryRun()) {
///   execute all stages
///   skip(CodeGenerationStage) // Don't create final plan
///   return analysis results only
/// }
/// ```
///
/// ## Stage Performance Tracking
///
/// Each stage tracks its own performance:
///
/// ```
/// Performance Tracking:
///
/// Stage: TrialEnumerationStage
///   Start: T0
///   Duration: 2.34s
///   Metrics:
///     - cartesian_product_size: 10,000
///     - trials_filtered: 127
///     - trials_output: 9,873
///   Memory:
///     - allocated: 48 MB
///     - peak: 52 MB
///
/// Stage: OptimizationStage
///   Start: T1
///   Duration: 3.21s
///   Metrics:
///     - optimizations_attempted: 12
///     - optimizations_applied: 8
///     - barriers_before: 89
///     - barriers_after: 67
///     - steps_before: 30,456
///     - steps_after: 29,102
/// ```
///
/// ## Usage Examples
///
/// ### Example 1: Implementing a Simple Stage
///
/// ```java
/// public class ValidationStage implements CompilationStage {
///     @Override
///     public String name() {
///         return "Validation";
///     }
///
///     @Override
///     public void execute(CompilationContext ctx) {
///         ctx.startTimer("validation");
///
///         TestPlan plan = ctx.testPlan();
///
///         // Validate axes
///         for (Axis<?> axis : plan.axes()) {
///             if (axis.values().isEmpty()) {
///                 ctx.addError(ERROR,
///                     "Axis '" + axis.name() + "' has no values",
///                     "axes." + axis.name(),
///                     "Add at least one value to the axis");
///             }
///         }
///
///         // Validate relationships
///         if (hasRelationshipCycle(plan.relationships())) {
///             ctx.addError(ERROR,
///                 "Relationship graph contains cycle",
///                 "relationships",
///                 "Remove circular dependencies");
///         }
///
///         ctx.stopTimer("validation");
///     }
/// }
/// ```
///
/// ### Example 2: Stage with Precondition Checks
///
/// ```java
/// public class StepGenerationStage implements CompilationStage {
///     @Override
///     public String name() {
///         return "StepGeneration";
///     }
///
///     @Override
///     public void execute(CompilationContext ctx) {
///         // Check preconditions
///         if (ctx.hasErrors()) {
///             return; // Abort if previous errors
///         }
///
///         if (ctx.trials().isEmpty()) {
///             ctx.addError(ERROR,
///                 "Cannot generate steps without trials",
///                 "step_generation",
///                 "Fix trial enumeration errors first");
///             return;
///         }
///
///         if (ctx.elementInstances().isEmpty()) {
///             ctx.addError(ERROR,
///                 "Cannot generate steps without element instances",
///                 "step_generation",
///                 "Fix instantiation errors first");
///             return;
///         }
///
///         // Execute stage logic
///         List<AtomicStep> steps = generateSteps(
///             ctx.trials().get(),
///             ctx.elementInstances().get());
///
///         ctx.setSteps(steps);
///         ctx.recordMetric("steps_generated", steps.size());
///     }
/// }
/// ```
///
/// ### Example 3: Stage with Conditional Logic
///
/// ```java
/// public class OptimizationStage implements CompilationStage {
///     @Override
///     public void execute(CompilationContext ctx) {
///         OptimizationLevel level = ctx.options().optimizationLevel();
///
///         if (level == OptimizationLevel.NONE) {
///             ctx.addInfo("Optimizations disabled, skipping");
///             return;
///         }
///
///         List<OptimizationPass> passes = selectPasses(level);
///
///         for (OptimizationPass pass : passes) {
///             if (pass.shouldApply(ctx)) {
///                 pass.apply(ctx);
///                 ctx.recordMetric("optimization_" + pass.name(), 1);
///             }
///         }
///     }
/// }
/// ```
///
/// ### Example 4: Pipeline Execution
///
/// ```java
/// public class CompilerImpl implements Compiler {
///     private final List<CompilationStage> stages = List.of(
///         new ValidationStage(),
///         new NormalizationStage(),
///         new TrialEnumerationStage(),
///         new InstantiationStage(),
///         new StepGenerationStage(),
///         new DependencyAnalysisStage(),
///         new OptimizationStage(),
///         new CodeGenerationStage()
///     );
///
///     @Override
///     public CompilationResult compile(TestPlan testPlan) {
///         CompilationContext ctx = createContext(testPlan);
///
///         for (CompilationStage stage : stages) {
///             ctx.startTimer(stage.name());
///
///             stage.execute(ctx);
///
///             ctx.stopTimer(stage.name());
///
///             // Abort on errors
///             if (ctx.hasErrors()) {
///                 return CompilationResult.failure(
///                     ctx.errors(),
///                     ctx.warnings(),
///                     ctx.timings());
///             }
///         }
///
///         return CompilationResult.success(
///             ctx.executionPlan(),
///             ctx.warnings(),
///             ctx.timings());
///     }
/// }
/// ```
///
/// ### Example 5: Stage with Progress Reporting
///
/// ```java
/// public class TrialEnumerationStage implements CompilationStage {
///     @Override
///     public void execute(CompilationContext ctx) {
///         TestPlan plan = ctx.testPlan();
///         List<Axis<?>> axes = plan.axes();
///
///         // Calculate total space
///         long totalSpace = axes.stream()
///             .mapToLong(a -> a.cardinality())
///             .reduce(1L, (a, b) -> a * b);
///
///         ctx.recordMetric("cartesian_product_size", totalSpace);
///
///         if (totalSpace > ctx.options().maxTrialSpaceSize()) {
///             ctx.addError(ERROR,
///                 "Trial space (" + totalSpace + ") exceeds limit",
///                 "trial_enumeration",
///                 "Reduce axis cardinalities");
///             return;
///         }
///
///         // Generate with progress reporting
///         List<Trial> trials = new ArrayList<>();
///         long generated = 0;
///
///         for (Trial trial : generateCartesianProduct(axes)) {
///             if (++generated % 1000 == 0) {
///                 ctx.addInfo("Enumerated " + generated + " trials...");
///             }
///             trials.add(trial);
///         }
///
///         ctx.setTrials(trials);
///         ctx.recordMetric("trials_generated", trials.size());
///     }
/// }
/// ```
///
/// ## Contract Requirements
///
/// ### Execution Semantics
/// - Stages MUST be idempotent (safe to retry)
/// - Stages MUST NOT modify inputs, only context outputs
/// - Stages MUST report errors via context, not throw exceptions
///
/// ### Dependencies
/// - Stages MUST check for required inputs before executing
/// - Stages MUST abort if previous stages reported errors
/// - Stages MUST produce declared outputs or report errors
///
/// ### Performance
/// - Stages SHOULD track execution time
/// - Stages SHOULD record relevant metrics
/// - Stages SHOULD minimize memory usage
///
/// @see Compiler
/// @see CompilationContext
///
public interface CompilationStage {

    ///
    /// Returns the name of this compilation stage.
    ///
    /// @return Stage name
    ///
    String name();

    ///
    /// Returns a description of what this stage does.
    ///
    /// @return Stage description
    ///
    default String description() {
        return "Compilation stage: " + name();
    }

    ///
    /// Executes this stage using the provided context.
    ///
    /// The stage should:
    /// 1. Check preconditions
    /// 2. Perform its transformation
    /// 3. Update context with outputs
    /// 4. Report errors/warnings
    ///
    /// @param context Compilation context
    ///
    void execute(CompilationContext context);

    ///
    /// Returns the stages this stage depends on.
    ///
    /// @return Prerequisite stage names (empty if no dependencies)
    ///
    default List<String> dependencies() {
        return List.of();
    }

    ///
    /// Checks if this stage can be skipped based on context/options.
    ///
    /// @param context Compilation context
    /// @return True if stage can be skipped
    ///
    default boolean canSkip(CompilationContext context) {
        return false;
    }

    ///
    /// Returns estimated duration for this stage.
    ///
    /// Used for progress estimation. Empty if duration cannot be estimated.
    ///
    /// @param context Compilation context
    /// @return Estimated duration if known
    ///
    default Optional<Duration> estimatedDuration(CompilationContext context) {
        return Optional.empty();
    }
}
