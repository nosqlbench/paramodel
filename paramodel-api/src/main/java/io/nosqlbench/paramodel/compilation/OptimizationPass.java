package io.nosqlbench.paramodel.compilation;

import io.nosqlbench.paramodel.plan.AtomicStep;

import java.util.Optional;

///
/// # OptimizationPass
///
/// Represents a single optimization transformation applied during compilation to
/// improve execution efficiency. Optimization passes analyze and transform the
/// execution plan structure to reduce cost, execution time, or resource usage.
///
/// ## Optimization Philosophy
///
/// Optimizations are applied incrementally during the {@link CompilationStage} pipeline:
///
/// ```
/// Optimization Framework:
///
/// ExecutionGraph (unoptimized)
///   │
///   ├─→ BarrierCoalescingPass
///   │     Reduces synchronization overhead
///   │     18 barriers → 12 barriers
///   │
///   ├─→ StepFusionPass
///   │     Combines related steps
///   │     45 steps → 38 steps
///   │
///   ├─→ InstanceSharingPass
///   │     Reuses element instances
///   │     20 instances → 12 instances
///   │
///   ├─→ DeadStepEliminationPass
///   │     Removes unreachable steps
///   │     38 steps → 37 steps
///   │
///   ├─→ CriticalPathPrioritizationPass
///   │     Reorders for faster completion
///   │     Estimated duration: 4h 30m → 4h 10m
///   │
///   └─→ ResourcePackingPass
///         Better resource utilization
///         Avg utilization: 45% → 68%
///         │
///         └─→ ExecutionGraph (optimized)
/// ```
///
/// ## Pass Categories
///
/// Optimization passes fall into several categories:
///
/// ```
/// Pass Categories:
///
/// Reduction Passes:
///   - Barrier coalescing (fewer sync points)
///   - Step fusion (fewer operations)
///   - Instance sharing (fewer deployments)
///   - Redundancy elimination (remove duplicates)
///
/// Reordering Passes:
///   - Critical path prioritization (faster completion)
///   - Resource packing (better utilization)
///   - Cache-aware scheduling (better locality)
///
/// Structural Passes:
///   - Dead code elimination (remove unreachable)
///   - Subgraph extraction (parallel execution)
///   - Pipeline splitting (checkpointing)
///
/// Speculative Passes:
///   - Prefetching (anticipate needs)
///   - Redundant execution (fault tolerance)
///   - Dynamic adaptation (runtime changes)
/// ```
///
/// ## Barrier Coalescing
///
/// Combine multiple barriers into fewer synchronization points:
///
/// ```
/// Barrier Coalescing:
///
/// Before:
///   Step A ──→ BARRIER(b1) ──→ Step D
///   Step B ──→ BARRIER(b2) ──→ Step E
///   Step C ──→ BARRIER(b3) ──→ Step F
///
/// Analysis:
///   - All barriers have same dependencies: {A, B, C}
///   - All barriers release immediately after
///   - No ordering dependencies between D, E, F
///
/// After:
///   Step A ──┐
///   Step B ──┼──→ BARRIER(b_coalesced) ──→ Step D
///   Step C ──┘                          ├──→ Step E
///                                       └──→ Step F
///
/// Savings:
///   - 3 barriers → 1 barrier
///   - Reduced synchronization overhead: ~2ms per barrier × 2 = 4ms saved
/// ```
///
/// ## Step Fusion
///
/// Combine adjacent related steps into single operations:
///
/// ```
/// Step Fusion:
///
/// Before:
///   DEPLOY_ELEMENT(cache)
///      ↓
///   HEALTH_CHECK(cache)
///      ↓
///   WARMUP(cache)
///
/// After:
///   DEPLOY_WITH_WARMUP(cache)
///     - Deploy element
///     - Run health checks
///     - Execute warmup
///
/// Savings:
///   - 3 steps → 1 step
///   - Reduced graph complexity
///   - Eliminated intermediate coordination
///   - Reduced total duration: 30s + 10s + 20s → 55s (5s saved)
/// ```
///
/// ## Instance Sharing
///
/// Reuse element instances across trials when safe:
///
/// ```
/// Instance Sharing:
///
/// Before (no sharing):
///   Trial 1: db_instance_1
///   Trial 2: db_instance_2
///   Trial 3: db_instance_3
///   Total: 3 instances × $5/hour × 2 hours = $30
///
/// After (with sharing):
///   Analysis: All trials use same db configuration
///   Relationship: SHARED allowed
///
///   Trial 1, 2, 3: db_instance_shared
///   Total: 1 instance × $5/hour × 2 hours = $10
///
/// Savings:
///   - 3 instances → 1 instance
///   - Cost: $30 → $10 (67% reduction)
///   - Deployment time: 3 × 5min → 1 × 5min (10 minutes saved)
/// ```
///
/// ## Dead Step Elimination
///
/// Remove unreachable or unnecessary steps:
///
/// ```
/// Dead Step Elimination:
///
/// Before:
///   DEPLOY_ELEMENT(service_A)
///      ↓
///   TRIAL_STEP(t1) [uses service_A]
///      ↓
///   DEPLOY_ELEMENT(service_B)
///      ↓
///   [No steps use service_B]
///      ↓
///   TEARDOWN_ELEMENT(service_B)
///
/// Analysis:
///   - service_B is deployed but never used
///   - Deploy and teardown are dead code
///
/// After:
///   DEPLOY_ELEMENT(service_A)
///      ↓
///   TRIAL_STEP(t1)
///   [service_B steps removed]
///
/// Savings:
///   - 2 steps eliminated
///   - Deployment cost saved: $10
///   - Duration saved: 10 minutes
/// ```
///
/// ## Critical Path Prioritization
///
/// Schedule critical path steps early to minimize total time:
///
/// ```
/// Critical Path Prioritization:
///
/// Before (arbitrary ordering):
///   Wave 1: [A, B, C]     Critical: A
///   Wave 2: [D, E, F]     Critical: D
///   Wave 3: [G, H]        Critical: G
///
/// Critical path: A → D → G (total: 90 minutes)
/// Non-critical can run anytime
///
/// After (prioritized):
///   Wave 1: [A, B, C]     Start A first (critical)
///   Wave 2: [D, E, F]     Start D first (critical)
///   Wave 3: [G, H]        Start G first (critical)
///
/// Result:
///   - Critical path executes with minimal delays
///   - Non-critical fills gaps
///   - Total time: 90min → 85min (5min saved)
/// ```
///
/// ## Resource Packing
///
/// Arrange steps to maximize resource utilization:
///
/// ```
/// Resource Packing:
///
/// Available: 8 CPU cores
///
/// Before (poor packing):
///   t=0:   Step A (4 cores) + Step B (2 cores)  = 6/8 cores (75%)
///   t=10:  Step C (6 cores)                     = 6/8 cores (75%)
///   t=20:  Step D (2 cores)                     = 2/8 cores (25%)
///   Average utilization: 58%
///
/// After (optimized packing):
///   t=0:   Step A (4 cores) + Step B (2 cores) + Step D (2 cores) = 8/8 (100%)
///   t=10:  Step C (6 cores)                     = 6/8 cores (75%)
///   Average utilization: 87%
///
/// Savings:
///   - Duration: 30min → 20min (33% faster)
///   - Better resource utilization: 58% → 87%
/// ```
///
/// ## Usage Examples
///
/// ### Example 1: Implementing a Simple Pass
///
/// ```java
/// public class BarrierCoalescingPass implements OptimizationPass {
///     @Override
///     public String name() {
///         return "BarrierCoalescing";
///     }
///
///     @Override
///     public boolean shouldApply(CompilationContext ctx) {
///         return ctx.barriers().orElse(List.of()).size() > 1;
///     }
///
///     @Override
///     public void apply(CompilationContext ctx) {
///         List<Barrier> barriers = ctx.barriers().orElseThrow();
///
///         // Group barriers with identical dependencies
///         Map<Set<String>, List<Barrier>> grouped = barriers.stream()
///             .collect(Collectors.groupingBy(b -> Set.copyOf(b.dependencies())));
///
///         List<Barrier> coalesced = grouped.values().stream()
///             .map(this::coalesceGroup)
///             .toList();
///
///         int saved = barriers.size() - coalesced.size();
///         ctx.setBarriers(coalesced);
///         ctx.recordMetric("barriers_coalesced", saved);
///     }
/// }
/// ```
///
/// ### Example 2: Pass with Cost Analysis
///
/// ```java
/// public class InstanceSharingPass implements OptimizationPass {
///     @Override
///     public String name() {
///         return "InstanceSharing";
///     }
///
///     @Override
///     public Optional<String> estimateSavings(CompilationContext ctx) {
///         int beforeInstances = ctx.elementInstances().orElseThrow().size();
///         int afterInstances = estimateAfterSharing(ctx);
///         int saved = beforeInstances - afterInstances;
///
///         double costSavings = saved * 5.0 * 2.0; // $5/hr × 2 hours
///         return Optional.of(String.format(
///             "%d instances eliminated, $%.2f saved",
///             saved, costSavings));
///     }
///
///     @Override
///     public void apply(CompilationContext ctx) {
///         // Find instances that can be shared
///         // Merge instance plans
///         // Update step bindings
///     }
/// }
/// ```
///
/// ### Example 3: Multi-Pass Optimization
///
/// ```java
/// public class OptimizationStage implements CompilationStage {
///     private final List<OptimizationPass> passes = List.of(
///         new BarrierCoalescingPass(),
///         new StepFusionPass(),
///         new InstanceSharingPass(),
///         new DeadStepEliminationPass(),
///         new CriticalPathPrioritizationPass(),
///         new ResourcePackingPass()
///     );
///
///     @Override
///     public void execute(CompilationContext ctx) {
///         OptimizationLevel level = ctx.options().optimizationLevel();
///
///         for (OptimizationPass pass : passes) {
///             if (!pass.enabledForLevel(level)) {
///                 continue; // Skip if not enabled for this level
///             }
///
///             if (!pass.shouldApply(ctx)) {
///                 continue; // Skip if not applicable
///             }
///
///             String savings = pass.estimateSavings(ctx)
///                 .orElse("unknown savings");
///
///             ctx.addInfo("Applying " + pass.name() + ": " + savings);
///             pass.apply(ctx);
///         }
///     }
/// }
/// ```
///
/// ### Example 4: Conditional Pass Application
///
/// ```java
/// public class SpeculativePrefetchPass implements OptimizationPass {
///     @Override
///     public boolean shouldApply(CompilationContext ctx) {
///         // Only apply if explicitly enabled
///         return ctx.options().customOptions()
///             .getOrDefault("enable_speculation", false).equals(true);
///     }
///
///     @Override
///     public boolean enabledForLevel(Compiler.OptimizationLevel level) {
///         // Only enable at aggressive level
///         return level == Compiler.OptimizationLevel.AGGRESSIVE;
///     }
///
///     @Override
///     public void apply(CompilationContext ctx) {
///         // Add prefetch steps for anticipated resources
///     }
/// }
/// ```
///
/// ### Example 5: Pass with Validation
///
/// ```java
/// public class CriticalPathPrioritizationPass implements OptimizationPass {
///     @Override
///     public void apply(CompilationContext ctx) {
///         ExecutionGraph graph = ctx.get("executionGraph")
///             .map(g -> (ExecutionGraph) g)
///             .orElseThrow();
///
///         // Find critical path
///         List<AtomicStep> critical = graph.criticalPath();
///
///         // Reorder steps to prioritize critical path
///         List<AtomicStep> reordered = prioritizeCriticalPath(
///             ctx.steps().orElseThrow(),
///             critical);
///
///         // Validate reordering preserves dependencies
///         if (!validateDependencies(reordered)) {
///             ctx.addWarning(
///                 "Critical path prioritization failed validation",
///                 "Skipping optimization");
///             return;
///         }
///
///         ctx.setSteps(reordered);
///
///         // Estimate improvement
///         Duration before = graph.criticalPathDuration();
///         Duration after = estimateNewDuration(reordered);
///         ctx.recordMetric("prioritization_improvement_ms",
///             before.minus(after).toMillis());
///     }
/// }
/// ```
///
/// ## Contract Requirements
///
/// ### Correctness
/// - Passes MUST preserve execution plan semantics
/// - Passes MUST NOT violate dependencies
/// - Passes MUST be idempotent (safe to apply multiple times)
///
/// ### Safety
/// - Passes MUST validate transformations before applying
/// - Passes MUST rollback on validation failures
/// - Passes SHOULD estimate savings before applying
///
/// ### Performance
/// - Passes SHOULD complete in reasonable time
/// - Passes SHOULD provide measurable improvements
/// - Passes MAY skip application if savings are negligible
///
/// @see Compiler
/// @see CompilationContext
/// @see CompilationStage
///
public interface OptimizationPass {

    ///
    /// Returns the name of this optimization pass.
    ///
    /// @return Pass name
    ///
    String name();

    ///
    /// Returns a description of what this pass optimizes.
    ///
    /// @return Pass description
    ///
    default String description() {
        return "Optimization pass: " + name();
    }

    ///
    /// Checks if this pass should be applied to the given context.
    ///
    /// @param context Compilation context
    /// @return True if pass is applicable
    ///
    boolean shouldApply(CompilationContext context);

    ///
    /// Applies this optimization to the execution plan.
    ///
    /// @param context Compilation context
    ///
    void apply(CompilationContext context);

    ///
    /// Estimates the savings this pass would provide.
    ///
    /// @param context Compilation context
    /// @return Human-readable savings estimate if calculable
    ///
    default Optional<String> estimateSavings(CompilationContext context) {
        return Optional.empty();
    }

    ///
    /// Checks if this pass is enabled for the given optimization level.
    ///
    /// @param level Optimization level
    /// @return True if enabled
    ///
    default boolean enabledForLevel(Compiler.OptimizationLevel level) {
        return level != Compiler.OptimizationLevel.NONE;
    }

    ///
    /// Returns the optimization category.
    ///
    /// @return Category
    ///
    default OptimizationCategory category() {
        return OptimizationCategory.OTHER;
    }

    ///
    /// Optimization categories.
    ///
    enum OptimizationCategory {
        REDUCTION,      // Reduce number of operations
        REORDERING,     // Change execution order
        STRUCTURAL,     // Modify graph structure
        SPECULATIVE,    // Anticipate future needs
        OTHER           // Other optimization types
    }
}
