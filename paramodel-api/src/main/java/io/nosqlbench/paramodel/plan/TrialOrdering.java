package io.nosqlbench.paramodel.plan;

import io.nosqlbench.paramodel.sequence.Trial;

import java.util.Comparator;
import java.util.List;
import java.util.Random;

///
/// # TrialOrdering
///
/// Defines strategies for ordering trials within an {@link ExecutionPlan}, determining
/// the sequence in which parameter combinations are explored. Trial ordering affects
/// progressive disclosure, early error detection, resource utilization, and result
/// interpretation.
///
/// ## Ordering Philosophy
///
/// The order in which trials execute has significant impact on study efficiency:
///
/// ```
/// Trial Space (2D example):
///
///   concurrency →
///   10    50    100
/// ┌─────┬─────┬─────┐
/// │ t1  │ t2  │ t3  │  cache=128
/// ├─────┼─────┼─────┤
/// │ t4  │ t5  │ t6  │  cache=256
/// ├─────┼─────┼─────┤
/// │ t7  │ t8  │ t9  │  cache=512
/// └─────┴─────┴─────┘
///
/// Different orderings serve different goals:
///
/// SEQUENTIAL (row-major):
///   t1 → t2 → t3 → t4 → t5 → t6 → t7 → t8 → t9
///   Goal: Systematic coverage, predictable debugging
///
/// EDGE_FIRST (boundaries then interior):
///   t1 → t3 → t7 → t9 → t2 → t4 → t6 → t8 → t5
///   Goal: Early boundary detection, progressive refinement
///
/// SHUFFLED (randomized):
///   t5 → t1 → t8 → t3 → t7 → t2 → t9 → t4 → t6
///   Goal: Avoid systematic bias, uniform sampling
///
/// COST_OPTIMIZED (expensive first):
///   t9 → t8 → t7 → t6 → t5 → t4 → t3 → t2 → t1
///   Goal: Fail-fast on costly configurations
/// ```
///
/// ## Sequential Ordering
///
/// Systematic traversal in lexicographic order:
///
/// ```
/// Sequential Strategy:
///
/// Given axes: [A={1,2,3}, B={x,y}]
///
/// Ordering: Cartesian product in axis order
///   (1,x) → (1,y) → (2,x) → (2,y) → (3,x) → (3,y)
///
/// Properties:
///   ✓ Deterministic (same every execution)
///   ✓ Simple to understand and debug
///   ✓ Maintains locality (adjacent trials share parameters)
///   ✗ May exhibit systematic bias
///   ✗ Boundary cases not prioritized
///
/// Best for:
///   - Regression testing (reproducible order)
///   - Debugging (predictable sequence)
///   - Small trial spaces (order matters less)
/// ```
///
/// ## Edge-First Ordering
///
/// Prioritizes boundary values for early anomaly detection:
///
/// ```
/// Edge-First Strategy:
///
/// Given axes: [cache={128,256,512}, concurrency={10,50,100}]
///
/// Phase 1: Boundaries of each axis
///   cache boundaries: 128, 512
///   concurrency boundaries: 10, 100
///
/// Phase 2: Boundary combinations
///   (128,10) → (128,100) → (512,10) → (512,100)
///
/// Phase 3: Boundary of one axis, interior of others
///   (128,50) → (512,50)
///   (256,10) → (256,100)
///
/// Phase 4: Interior combinations
///   (256,50)
///
/// Full ordering:
///   (128,10) → (128,100) → (512,10) → (512,100)  [corners]
///   → (128,50) → (512,50)                        [edges]
///   → (256,10) → (256,100)                       [edges]
///   → (256,50)                                   [interior]
///
/// Properties:
///   ✓ Early detection of boundary issues
///   ✓ Progressive refinement (coarse → fine)
///   ✓ Can stop early with boundary coverage
///   ✗ More complex to implement
///   ✗ May miss interior-only issues initially
///
/// Best for:
///   - Exploratory studies (unknown behavior)
///   - Time-limited testing (maximize early coverage)
///   - Systems with boundary sensitivities
/// ```
///
/// ## Shuffled Ordering
///
/// Randomized traversal to avoid systematic biases:
///
/// ```
/// Shuffled Strategy:
///
/// Given trials: [t1, t2, t3, t4, t5]
///
/// Shuffle with seed: 42
///   → [t3, t1, t5, t2, t4]
///
/// Properties:
///   ✓ Eliminates systematic bias
///   ✓ Uniform sampling over time (for early termination)
///   ✓ Reproducible with fixed seed
///   ✗ Loses locality (poor cache utilization)
///   ✗ Harder to debug (unpredictable order)
///
/// Seed Strategies:
///   - Fixed seed: Reproducible across runs
///   - Time-based seed: Different each run
///   - Plan fingerprint seed: Consistent for same plan
///
/// Best for:
///   - Statistical studies (avoid order effects)
///   - Long-running studies (early termination safety)
///   - Avoiding worst-case ordering pathologies
/// ```
///
/// ## Dependency-Optimized Ordering
///
/// Minimizes element deployment churn by grouping trials:
///
/// ```
/// Dependency-Optimized Strategy:
///
/// Given:
///   - Trials: t1, t2, t3, t4
///   - Element bindings:
///       t1, t2 → cache_instance_A
///       t3, t4 → cache_instance_B
///
/// Naive ordering: t1 → t3 → t2 → t4
///   deploy(cache_A) → run(t1) → teardown(cache_A)
///   → deploy(cache_B) → run(t3) → teardown(cache_B)
///   → deploy(cache_A) → run(t2) → teardown(cache_A)
///   → deploy(cache_B) → run(t4) → teardown(cache_B)
///   Total: 4 deploys, 4 teardowns
///
/// Optimized ordering: t1 → t2 → t3 → t4
///   deploy(cache_A) → run(t1) → run(t2) → teardown(cache_A)
///   → deploy(cache_B) → run(t3) → run(t4) → teardown(cache_B)
///   Total: 2 deploys, 2 teardowns
///
/// Properties:
///   ✓ Minimizes infrastructure churn
///   ✓ Reduces total execution time
///   ✓ Lower cost (fewer deploy/teardown cycles)
///   ✗ May reduce parallelism opportunities
///   ✗ More complex to compute
///
/// Algorithm:
///   1. Build element dependency graph
///   2. Group trials by element bindings
///   3. Order groups to maximize instance reuse
///   4. Within groups, use secondary ordering (e.g., sequential)
///
/// Best for:
///   - Expensive infrastructure (databases, clusters)
///   - Large trial spaces with shared elements
///   - Cost-sensitive studies
/// ```
///
/// ## Cost-Optimized Ordering
///
/// Executes expensive trials first for fail-fast behavior:
///
/// ```
/// Cost-Optimized Strategy:
///
/// Given trials with estimated costs:
///   t1: $1.20  (10 min, large instance)
///   t2: $0.30  (5 min, small instance)
///   t3: $2.50  (20 min, GPU instance)
///   t4: $0.15  (2 min, small instance)
///   t5: $1.80  (15 min, large instance)
///
/// Ordering by cost (descending):
///   t3 ($2.50) → t5 ($1.80) → t1 ($1.20) → t2 ($0.30) → t4 ($0.15)
///
/// Rationale:
///   - Expensive trials likely stress system more
///   - Early failures on expensive configs save money
///   - Remaining cheap trials confirm basic functionality
///
/// Properties:
///   ✓ Early detection of expensive failures
///   ✓ Saves money if study fails early
///   ✓ Stresses system aggressively upfront
///   ✗ May mask issues only visible in cheap configs
///   ✗ Requires accurate cost estimates
///
/// Best for:
///   - Budget-constrained studies
///   - Systems with known expensive failure modes
///   - Exploratory phase before full run
/// ```
///
/// ## Custom Ordering
///
/// User-defined ordering via comparator:
///
/// ```
/// Custom Strategy Example: Diagonal Traversal
///
/// Given 3×3 trial space:
///   (1,x) (1,y) (1,z)
///   (2,x) (2,y) (2,z)
///   (3,x) (3,y) (3,z)
///
/// Custom diagonal ordering:
///   (1,x) → (2,y) → (3,z)  [main diagonal]
///   → (1,y) → (2,z)        [upper diagonal]
///   → (2,x) → (3,y)        [lower diagonal]
///   → (1,z)                [corner]
///   → (3,x)                [corner]
///
/// Implementation:
///   comparator = (t1, t2) -> {
///       int sum1 = axisSumOf(t1);
///       int sum2 = axisSumOf(t2);
///       return Integer.compare(sum1, sum2);
///   }
/// ```
///
/// ## Usage Examples
///
/// ### Example 1: Specifying Ordering in TestPlan
///
/// ```java
/// TestPlan plan = TestPlanBuilder.create()
///     .name("cache-study")
///     .withAxis(Axis.of("cache_size", List.of(128, 256, 512, 1024)))
///     .withAxis(Axis.of("concurrency", List.of(10, 50, 100, 500)))
///     .withElement(Element.redis("cache"))
///     .policies(ExecutionPolicies.builder()
///         .trialOrdering(TrialOrdering.EDGE_FIRST)
///         .build())
///     .build();
///
/// ExecutionPlan execPlan = plan.commit();
/// // Trials will execute in edge-first order
/// ```
///
/// ### Example 2: Shuffled with Fixed Seed
///
/// ```java
/// TrialOrdering ordering = TrialOrdering.shuffled(42L); // Reproducible
///
/// TestPlan plan = TestPlanBuilder.create()
///     .name("randomized-study")
///     .withAxes(/* ... */)
///     .policies(ExecutionPolicies.builder()
///         .trialOrdering(ordering)
///         .build())
///     .build();
/// ```
///
/// ### Example 3: Custom Ordering by Trial Cost
///
/// ```java
/// Comparator<Trial> costComparator = (t1, t2) -> {
///     double cost1 = estimateCost(t1);
///     double cost2 = estimateCost(t2);
///     return Double.compare(cost2, cost1); // Descending (expensive first)
/// };
///
/// TrialOrdering ordering = TrialOrdering.custom(costComparator);
///
/// TestPlan plan = TestPlanBuilder.create()
///     .name("cost-optimized-study")
///     .withAxes(/* ... */)
///     .policies(ExecutionPolicies.builder()
///         .trialOrdering(ordering)
///         .build())
///     .build();
/// ```
///
/// ### Example 4: Analyzing Ordering Impact
///
/// ```java
/// List<Trial> trials = /* ... */;
///
/// // Compare different orderings
/// List<Trial> sequential = TrialOrdering.SEQUENTIAL.order(trials);
/// List<Trial> edgeFirst = TrialOrdering.EDGE_FIRST.order(trials);
/// List<Trial> shuffled = TrialOrdering.shuffled(42L).order(trials);
///
/// System.out.printf("Sequential first trial: %s%n",
///     sequential.get(0).id());
/// System.out.printf("Edge-first first trial: %s%n",
///     edgeFirst.get(0).id());
/// System.out.printf("Shuffled first trial: %s%n",
///     shuffled.get(0).id());
///
/// // Check if edge-first prioritizes boundaries
/// boolean firstIsEdge = isBoundaryTrial(edgeFirst.get(0));
/// System.out.printf("Edge-first starts with boundary: %s%n",
///     firstIsEdge);
/// ```
///
/// ### Example 5: Hybrid Ordering Strategy
///
/// ```java
/// // Edge-first for first 25%, then dependency-optimized
/// TrialOrdering hybrid = TrialOrdering.custom((t1, t2) -> {
///     boolean t1IsBoundary = isBoundaryTrial(t1);
///     boolean t2IsBoundary = isBoundaryTrial(t2);
///
///     if (t1IsBoundary && !t2IsBoundary) return -1;
///     if (!t1IsBoundary && t2IsBoundary) return 1;
///
///     // Both boundary or both interior: use dependency optimization
///     return compareDependencies(t1, t2);
/// });
/// ```
///
/// ## Contract Requirements
///
/// ### Determinism
/// - SEQUENTIAL ordering MUST produce identical order on repeated calls
/// - SHUFFLED ordering MUST be reproducible with same seed
/// - CUSTOM ordering MUST respect comparator contract
///
/// ### Correctness
/// - All trials MUST appear exactly once in ordered output
/// - No trials may be added or removed during ordering
/// - Trial identity MUST be preserved
///
/// ### Performance
/// - Ordering SHOULD be O(n log n) or better for n trials
/// - EDGE_FIRST SHOULD be O(n log n) in trial count
/// - DEPENDENCY_OPTIMIZED MAY be O(n²) for complex dependencies
///
/// @see ExecutionPlan
/// @see TestPlan
/// @see ExecutionPolicies
/// @see Trial
///
public interface TrialOrdering {

    ///
    /// Sequential ordering: lexicographic traversal in axis order.
    ///
    TrialOrdering SEQUENTIAL = new SequentialOrdering();

    ///
    /// Edge-first ordering: boundaries before interior.
    ///
    TrialOrdering EDGE_FIRST = new EdgeFirstOrdering();

    ///
    /// Dependency-optimized ordering: minimize element deployment churn.
    ///
    TrialOrdering DEPENDENCY_OPTIMIZED = new DependencyOptimizedOrdering();

    ///
    /// Cost-optimized ordering: expensive trials first.
    ///
    TrialOrdering COST_OPTIMIZED = new CostOptimizedOrdering();

    ///
    /// Orders the given trials according to this strategy.
    ///
    /// @param trials Trials to order (unmodified)
    /// @return Ordered trials (new list)
    ///
    List<Trial> order(List<Trial> trials);

    ///
    /// Returns a description of this ordering strategy.
    ///
    /// @return Human-readable description
    ///
    String description();

    ///
    /// Creates shuffled ordering with random seed.
    ///
    /// @return Shuffled ordering with time-based seed
    ///
    static TrialOrdering shuffled() {
        return shuffled(System.currentTimeMillis());
    }

    ///
    /// Creates shuffled ordering with fixed seed for reproducibility.
    ///
    /// @param seed Random seed
    /// @return Shuffled ordering with specified seed
    ///
    static TrialOrdering shuffled(long seed) {
        return new ShuffledOrdering(seed);
    }

    ///
    /// Creates custom ordering using a comparator.
    ///
    /// @param comparator Comparator defining trial order
    /// @return Custom ordering
    ///
    static TrialOrdering custom(Comparator<Trial> comparator) {
        return new CustomOrdering(comparator);
    }

    ///
    /// Creates custom ordering with a description.
    ///
    /// @param comparator Comparator defining trial order
    /// @param description Human-readable description
    /// @return Custom ordering
    ///
    static TrialOrdering custom(Comparator<Trial> comparator, String description) {
        return new CustomOrdering(comparator, description);
    }

    ///
    /// Sequential ordering implementation (placeholder).
    ///
    class SequentialOrdering implements TrialOrdering {
        @Override
        public List<Trial> order(List<Trial> trials) {
            throw new UnsupportedOperationException(
                "SequentialOrdering.order() requires a concrete implementation");
        }

        @Override
        public String description() {
            return "Sequential (lexicographic) ordering";
        }
    }

    ///
    /// Edge-first ordering implementation (placeholder).
    ///
    class EdgeFirstOrdering implements TrialOrdering {
        @Override
        public List<Trial> order(List<Trial> trials) {
            throw new UnsupportedOperationException(
                "EdgeFirstOrdering.order() requires a concrete implementation");
        }

        @Override
        public String description() {
            return "Edge-first (boundaries before interior) ordering";
        }
    }

    ///
    /// Dependency-optimized ordering implementation (placeholder).
    ///
    class DependencyOptimizedOrdering implements TrialOrdering {
        @Override
        public List<Trial> order(List<Trial> trials) {
            throw new UnsupportedOperationException(
                "DependencyOptimizedOrdering.order() requires a concrete implementation");
        }

        @Override
        public String description() {
            return "Dependency-optimized (minimize deployment churn) ordering";
        }
    }

    ///
    /// Cost-optimized ordering implementation (placeholder).
    ///
    class CostOptimizedOrdering implements TrialOrdering {
        @Override
        public List<Trial> order(List<Trial> trials) {
            throw new UnsupportedOperationException(
                "CostOptimizedOrdering.order() requires a concrete implementation");
        }

        @Override
        public String description() {
            return "Cost-optimized (expensive first) ordering";
        }
    }

    ///
    /// Shuffled ordering implementation.
    ///
    class ShuffledOrdering implements TrialOrdering {
        private final long seed;

        public ShuffledOrdering(long seed) {
            this.seed = seed;
        }

        @Override
        public List<Trial> order(List<Trial> trials) {
            List<Trial> shuffled = List.copyOf(trials);
            Random rng = new Random(seed);
            // Shuffle logic would be implemented here
            throw new UnsupportedOperationException(
                "ShuffledOrdering.order() requires a concrete implementation");
        }

        @Override
        public String description() {
            return "Shuffled (randomized, seed=" + seed + ") ordering";
        }

        public long seed() {
            return seed;
        }
    }

    ///
    /// Custom comparator-based ordering implementation.
    ///
    class CustomOrdering implements TrialOrdering {
        private final Comparator<Trial> comparator;
        private final String description;

        public CustomOrdering(Comparator<Trial> comparator) {
            this(comparator, "Custom ordering");
        }

        public CustomOrdering(Comparator<Trial> comparator, String description) {
            this.comparator = comparator;
            this.description = description;
        }

        @Override
        public List<Trial> order(List<Trial> trials) {
            return trials.stream()
                .sorted(comparator)
                .toList();
        }

        @Override
        public String description() {
            return description;
        }

        public Comparator<Trial> comparator() {
            return comparator;
        }
    }
}
