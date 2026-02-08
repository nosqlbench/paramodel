package io.nosqlbench.paramodel.compilation;

import io.nosqlbench.paramodel.plan.ExecutionPlan;
import io.nosqlbench.paramodel.plan.TestPlan;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

///
/// # Compiler
///
/// Transforms a declarative {@link TestPlan} into an executable {@link ExecutionPlan}
/// through validation, optimization, and code generation. The compiler bridges the gap
/// between user intent (WHAT to test) and execution strategy (HOW to execute).
///
/// ## Compilation Pipeline
///
/// The compiler executes a multi-stage pipeline:
///
/// ```
/// Compilation Stages:
///
/// TestPlan
///   │
///   ├─→ [1] Validation
///   │     ├─ Syntax validation
///   │     ├─ Semantic validation
///   │     ├─ Resource validation
///   │     └─ Policy validation
///   │
///   ├─→ [2] Normalization
///   │     ├─ Axis ordering
///   │     ├─ Element resolution
///   │     ├─ Relationship normalization
///   │     └─ Policy defaults
///   │
///   ├─→ [3] Trial Enumeration
///   │     ├─ Cartesian product generation
///   │     ├─ Constraint filtering
///   │     ├─ Trial ordering
///   │     └─ Trial space pruning
///   │
///   ├─→ [4] Element Instantiation Planning
///   │     ├─ Analyze relationships
///   │     ├─ Compute instance scopes
///   │     ├─ Determine instance counts
///   │     └─ Plan instance lifecycle
///   │
///   ├─→ [5] Step Generation
///   │     ├─ Generate DEPLOY steps
///   │     ├─ Generate EXECUTE steps
///   │     ├─ Generate TEARDOWN steps
///   │     ├─ Insert BARRIER steps
///   │     └─ Insert CHECKPOINT steps
///   │
///   ├─→ [6] Dependency Analysis
///   │     ├─ Build dependency graph
///   │     ├─ Check for cycles
///   │     ├─ Compute transitive closure
///   │     └─ Identify critical path
///   │
///   ├─→ [7] Optimization
///   │     ├─ Barrier coalescing
///   │     ├─ Step fusion
///   │     ├─ Resource packing
///   │     └─ Redundancy elimination
///   │
///   └─→ [8] Code Generation
///         ├─ Finalize execution graph
///         ├─ Compute metadata
///         ├─ Generate fingerprint
///         └─ Create ExecutionPlan
///             │
///             └─→ ExecutionPlan
/// ```
///
/// ## Validation Phase
///
/// Comprehensive validation before compilation proceeds:
///
/// ```
/// Validation Checks:
///
/// Syntax Validation:
///   ✓ All axes have non-empty value lists
///   ✓ All elements have valid types
///   ✓ All relationships reference existing elements
///   ✓ Policies are well-formed
///
/// Semantic Validation:
///   ✓ Relationship graph is acyclic
///   ✓ Element dependencies are satisfiable
///   ✓ Trial space size is reasonable (< 10^9 by default)
///   ✓ Resource requirements are within bounds
///
/// Policy Validation:
///   ✓ Retry counts are positive
///   ✓ Timeouts are positive
///   ✓ Checkpoint intervals are reasonable
///   ✓ Intervention modes are compatible
///
/// Example Validation Failure:
///
///   TestPlan:
///     axis cache_size: [128, 256, 512]
///     element db: postgres
///     element cache: redis
///     relationship(cache, db, MUTUALLY_EXCLUSIVE)
///     relationship(db, cache, SHARED)
///
///   Error: Conflicting relationships
///     - cache MUTUALLY_EXCLUSIVE with db
///     - db SHARED with cache
///     → Relationships must be symmetric
/// ```
///
/// ## Element Instantiation Strategy
///
/// The compiler determines how many instances of each element to create:
///
/// ```
/// Instantiation Analysis:
///
/// Given:
///   - Axes: [cache_size={128,256}, concurrency={10,50}]
///   - Elements: db, cache, app
///   - Relationships:
///       db ←SHARED→ cache
///       cache ←INSTANCED_PER→ app
///
/// Analysis:
///   - db: SHARED by all → 1 instance total
///   - cache: INSTANCED_PER concurrency → 2 instances (one per concurrency value)
///   - app: INSTANCED_PER trial → 4 instances (one per trial)
///
/// Instance Plan:
///   db_instance_1:
///     scope: all trials
///     lifecycle: deploy at start, teardown at end
///
///   cache_instance_10:
///     scope: trials with concurrency=10
///     lifecycle: deploy after db, teardown when concurrency=10 trials done
///
///   cache_instance_50:
///     scope: trials with concurrency=50
///     lifecycle: deploy after db, teardown when concurrency=50 trials done
///
///   app_instance_t1, app_instance_t2, app_instance_t3, app_instance_t4:
///     scope: single trial each
///     lifecycle: deploy before trial, teardown after trial
///
/// Total instances: 1 + 2 + 4 = 7 instances
/// ```
///
/// ## Optimization Techniques
///
/// The compiler applies various optimizations:
///
/// ```
/// Optimization Catalog:
///
/// 1. Barrier Coalescing:
///    Before: BARRIER(a), BARRIER(b), BARRIER(c) [serial]
///    After:  BARRIER(a,b,c) [single synchronization]
///
/// 2. Step Fusion:
///    Before: DEPLOY(cache), HEALTH_CHECK(cache) [two steps]
///    After:  DEPLOY_WITH_HEALTH_CHECK(cache) [one step]
///
/// 3. Instance Sharing:
///    Before: 100 trials × 1 db instance each = 100 instances
///    After:  100 trials sharing 1 db instance = 1 instance
///
/// 4. Redundancy Elimination:
///    Before: DEPLOY(db), ..., DEPLOY(db) [duplicate]
///    After:  DEPLOY(db) [once]
///
/// 5. Critical Path Prioritization:
///    Before: Steps scheduled by ID order
///    After:  Critical path steps scheduled first
///
/// 6. Resource Packing:
///    Before: Small trials scattered across timeline
///    After:  Small trials packed together for better utilization
/// ```
///
/// ## Compilation Strategies
///
/// Different strategies trade off compilation time vs. execution efficiency:
///
/// ```
/// Strategy Comparison:
///
/// FAST_COMPILE (default):
///   Compilation time: O(n log n)
///   Execution quality: Good
///   Optimizations: Basic (coalescing, deduplication)
///   Use case: Development, iteration
///
/// BALANCED:
///   Compilation time: O(n²)
///   Execution quality: Better
///   Optimizations: Standard (+ fusion, packing)
///   Use case: Production, moderate trial counts
///
/// OPTIMIZE_EXECUTION:
///   Compilation time: O(n² log n)
///   Execution quality: Best
///   Optimizations: Aggressive (+ critical path, speculation)
///   Use case: Large studies, cost-sensitive
///
/// Example Impact:
///   Trial space: 10,000 trials
///
///   FAST_COMPILE:
///     Compile: 2.3 seconds
///     Execute: 4h 30m
///     Cost: $450
///
///   BALANCED:
///     Compile: 8.7 seconds
///     Execute: 3h 45m
///     Cost: $380
///
///   OPTIMIZE_EXECUTION:
///     Compile: 28.4 seconds
///     Execute: 3h 15m
///     Cost: $340
/// ```
///
/// ## Incremental Compilation
///
/// Recompile only changed portions for faster iteration:
///
/// ```
/// Incremental Compilation:
///
/// Initial: TestPlan v1.0
///   → Full compilation → ExecutionPlan ep1
///
/// Change: Add one axis value (cache_size: add 2048)
///   → Incremental compilation:
///     1. Detect change: axis cache_size modified
///     2. Recompute trials: only new combinations
///     3. Reuse existing steps: deployments unchanged
///     4. Add new steps: execute new trials
///     5. Update graph: merge new subgraph
///   → ExecutionPlan ep2 (compiled in 15% of full time)
///
/// Change Types:
///   - Add axis value: Incremental (add trials only)
///   - Remove axis value: Incremental (remove trials only)
///   - Add element: Incremental (add deployment steps)
///   - Change relationship: Full recompile (affects all trials)
///   - Change policies: Metadata-only (no structural change)
/// ```
///
/// ## Usage Examples
///
/// ### Example 1: Basic Compilation
///
/// ```java
/// TestPlan testPlan = TestPlanBuilder.create()
///     .name("cache-perf-study")
///     .withAxis(Axis.of("cache_size", List.of(128, 256, 512)))
///     .withAxis(Axis.of("concurrency", List.of(10, 50, 100)))
///     .withElement(Element.redis("cache"))
///     .build();
///
/// Compiler compiler = Compiler.create();
/// CompilationResult result = compiler.compile(testPlan);
///
/// if (result.isSuccess()) {
///     ExecutionPlan execPlan = result.executionPlan();
///     System.out.printf("Compiled successfully: %d steps%n",
///         execPlan.steps().size());
/// } else {
///     System.err.println("Compilation failed:");
///     result.errors().forEach(err ->
///         System.err.printf("  - %s%n", err.message()));
/// }
/// ```
///
/// ### Example 2: Compilation with Options
///
/// ```java
/// CompilerOptions options = CompilerOptions.builder()
///     .strategy(CompilationStrategy.OPTIMIZE_EXECUTION)
///     .optimizationLevel(OptimizationLevel.AGGRESSIVE)
///     .maxTrialSpaceSize(1_000_000)
///     .parallelCompilation(true)
///     .build();
///
/// Compiler compiler = Compiler.create(options);
/// CompilationResult result = compiler.compile(testPlan);
///
/// System.out.printf("Compilation took %s%n",
///     result.compilationDuration());
/// System.out.printf("Optimizations applied: %d%n",
///     result.optimizationReport().optimizations().size());
/// ```
///
/// ### Example 3: Validation Only
///
/// ```java
/// Compiler compiler = Compiler.create();
/// ValidationResult validation = compiler.validate(testPlan);
///
/// if (validation.hasErrors()) {
///     System.err.println("Validation errors:");
///     validation.errors().forEach(err ->
///         System.err.printf("  [%s] %s%n", err.severity(), err.message()));
/// }
///
/// if (validation.hasWarnings()) {
///     System.out.println("Warnings:");
///     validation.warnings().forEach(warn ->
///         System.out.printf("  [WARN] %s%n", warn.message()));
/// }
/// ```
///
/// ### Example 4: Incremental Compilation
///
/// ```java
/// // Initial compilation
/// CompilationResult initial = compiler.compile(testPlan);
/// ExecutionPlan plan1 = initial.executionPlan();
///
/// // Modify test plan
/// TestPlan modified = testPlan.withAxis(
///     Axis.of("cache_size", List.of(128, 256, 512, 1024))); // Added 1024
///
/// // Incremental recompilation
/// CompilationResult incremental = compiler.compileIncremental(
///     modified, plan1);
///
/// ExecutionPlan plan2 = incremental.executionPlan();
///
/// System.out.printf("Incremental compilation saved %s%n",
///     initial.compilationDuration().minus(incremental.compilationDuration()));
/// ```
///
/// ### Example 5: Dry Run Analysis
///
/// ```java
/// // Compile without executing to analyze plan
/// CompilerOptions dryRunOptions = CompilerOptions.builder()
///     .dryRun(true)
///     .build();
///
/// Compiler compiler = Compiler.create(dryRunOptions);
/// CompilationResult result = compiler.compile(testPlan);
///
/// ExecutionPlan plan = result.executionPlan();
///
/// System.out.println("Dry Run Analysis:");
/// System.out.printf("  Trial space: %d trials%n",
///     plan.metadata().trialCount());
/// System.out.printf("  Estimated duration: %s%n",
///     plan.estimatedDuration());
/// System.out.printf("  Estimated cost: $%.2f%n",
///     plan.metadata().estimatedCost().orElse(0.0));
/// System.out.printf("  Max parallelism: %d%n",
///     plan.estimatedMaxParallelism());
/// ```
///
/// ## Contract Requirements
///
/// ### Correctness
/// - Compiler MUST produce valid ExecutionPlan instances
/// - Compiled plans MUST respect all TestPlan constraints
/// - Dependency graphs MUST be acyclic
/// - Resource allocations MUST be feasible
///
/// ### Determinism
/// - Same TestPlan MUST produce equivalent ExecutionPlan (modulo optimization choices)
/// - Compilation with fixed seed MUST be reproducible
///
/// ### Performance
/// - Validation SHOULD be O(n) in trial count for basic checks
/// - Compilation SHOULD be O(n log n) for FAST_COMPILE strategy
/// - Memory usage SHOULD be linear in trial space size
///
/// ### Error Handling
/// - Validation errors MUST prevent compilation
/// - Warnings SHOULD be reported but allow compilation
/// - Errors MUST include actionable messages
///
/// @see TestPlan
/// @see ExecutionPlan
/// @see CompilationResult
/// @see CompilerOptions
///
public interface Compiler {

    ///
    /// Creates a compiler with default options.
    ///
    /// @return Compiler instance
    ///
    static Compiler create() {
        throw new UnsupportedOperationException(
            "Compiler.create() requires a concrete implementation");
    }

    ///
    /// Creates a compiler with specified options.
    ///
    /// @param options Compiler options
    /// @return Compiler instance
    ///
    static Compiler create(CompilerOptions options) {
        throw new UnsupportedOperationException(
            "Compiler.create(options) requires a concrete implementation");
    }

    ///
    /// Validates a test plan without compiling.
    ///
    /// @param testPlan Test plan to validate
    /// @return Validation result
    ///
    ValidationResult validate(TestPlan testPlan);

    ///
    /// Compiles a test plan into an execution plan.
    ///
    /// @param testPlan Test plan to compile
    /// @return Compilation result containing execution plan or errors
    ///
    CompilationResult compile(TestPlan testPlan);

    ///
    /// Incrementally recompiles a modified test plan.
    ///
    /// Reuses portions of the previous execution plan where possible.
    ///
    /// @param modified Modified test plan
    /// @param previous Previous execution plan
    /// @return Compilation result
    ///
    CompilationResult compileIncremental(TestPlan modified, ExecutionPlan previous);

    ///
    /// Returns the compiler options.
    ///
    /// @return Compiler options
    ///
    CompilerOptions options();

    ///
    /// Returns the compiler version.
    ///
    /// @return Version string
    ///
    String version();

    ///
    /// Compilation result.
    ///
    interface CompilationResult {
        boolean isSuccess();
        Optional<ExecutionPlan> executionPlan();
        List<CompilationError> errors();
        List<CompilationWarning> warnings();
        Duration compilationDuration();
        Optional<OptimizationReport> optimizationReport();
        CompilationStatistics statistics();
    }

    ///
    /// Compilation error.
    ///
    interface CompilationError {
        ErrorSeverity severity();
        String message();
        Optional<String> location();
        Optional<String> suggestion();
    }

    ///
    /// Error severity levels.
    ///
    enum ErrorSeverity {
        ERROR,
        WARNING,
        INFO
    }

    ///
    /// Compilation warning.
    ///
    interface CompilationWarning {
        String message();
        Optional<String> location();
        Optional<String> suggestion();
    }

    ///
    /// Validation result.
    ///
    interface ValidationResult {
        boolean isValid();
        boolean hasErrors();
        boolean hasWarnings();
        List<CompilationError> errors();
        List<CompilationWarning> warnings();
    }

    ///
    /// Optimization report.
    ///
    interface OptimizationReport {
        List<Optimization> optimizations();
        String estimatedImprovement();
    }

    ///
    /// Optimization applied during compilation.
    ///
    record Optimization(
        String name,
        String description,
        boolean applied,
        Optional<String> savings
    ) {}

    ///
    /// Compilation statistics.
    ///
    record CompilationStatistics(
        int trialsGenerated,
        int stepsGenerated,
        int barriersGenerated,
        int optimizationsApplied,
        Duration validationTime,
        Duration enumerationTime,
        Duration optimizationTime,
        Duration codeGenTime
    ) {}

    ///
    /// Compilation strategy.
    ///
    enum CompilationStrategy {
        FAST_COMPILE,
        BALANCED,
        OPTIMIZE_EXECUTION
    }

    ///
    /// Optimization level.
    ///
    enum OptimizationLevel {
        NONE,
        BASIC,
        STANDARD,
        AGGRESSIVE
    }

    ///
    /// Compiler options.
    ///
    interface CompilerOptions {
        CompilationStrategy strategy();
        OptimizationLevel optimizationLevel();
        long maxTrialSpaceSize();
        boolean parallelCompilation();
        boolean dryRun();
        Map<String, Object> customOptions();

        static Builder builder() {
            throw new UnsupportedOperationException(
                "CompilerOptions.builder() requires a concrete implementation");
        }

        interface Builder {
            Builder strategy(CompilationStrategy strategy);
            Builder optimizationLevel(OptimizationLevel level);
            Builder maxTrialSpaceSize(long maxSize);
            Builder parallelCompilation(boolean parallel);
            Builder dryRun(boolean dryRun);
            Builder customOption(String key, Object value);
            CompilerOptions build();
        }
    }
}
