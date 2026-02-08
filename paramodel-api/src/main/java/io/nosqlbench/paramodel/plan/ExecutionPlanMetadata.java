package io.nosqlbench.paramodel.plan;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

///
/// # ExecutionPlanMetadata
///
/// Comprehensive metadata for {@link ExecutionPlan} instances, capturing compilation
/// details, resource requirements, execution history, and performance characteristics.
/// Metadata enables observability, cost tracking, and execution optimization.
///
/// ## Metadata Dimensions
///
/// Execution plan metadata captures multiple facets:
///
/// ```
/// Metadata Categories:
///
/// Identity & Provenance        Compilation Details
///   │                             │
///   ├─ Plan ID                    ├─ Compiled at
///   ├─ Source TestPlan            ├─ Compiler version
///   └─ Fingerprint                ├─ Step count
///                                 └─ Optimization level
///
/// Resource Requirements        Execution History
///   │                             │
///   ├─ Peak CPU                   ├─ Executions
///   ├─ Peak memory                ├─ Success rate
///   ├─ Estimated cost             ├─ Average duration
///   └─ Critical path              └─ Last execution
///
/// Performance Characteristics   Observability
///   │                             │
///   ├─ Max parallelism            ├─ Checkpoints
///   ├─ Avg parallelism            ├─ Warnings
///   ├─ Graph complexity           └─ Metadata tags
///   └─ Speedup potential
/// ```
///
/// ## Compilation Tracking
///
/// Metadata records compilation process details:
///
/// ```
/// Compilation Process:
///
/// TestPlan
///   │
///   ├─ Validation
///   ├─ Trial enumeration (T trials)
///   ├─ Element instantiation planning (E elements, I instances)
///   ├─ Dependency graph construction (S steps, D dependencies)
///   ├─ Barrier placement (B barriers)
///   ├─ Ordering optimization
///   └─ Resource allocation
///       │
///       └─→ ExecutionPlan + Metadata
///
/// Metadata captures:
///   - Compilation timestamp
///   - Compilation duration
///   - Compiler version
///   - Optimization level applied
///   - Trial space size (T)
///   - Element instance count (I)
///   - Step count (S)
///   - Barrier count (B)
///   - Edge count (D)
/// ```
///
/// ## Resource Profiling
///
/// Metadata provides resource requirement estimates:
///
/// ```
/// Resource Profile:
///
/// Peak Requirements (worst case):
///   CPU: 32.0 cores
///   Memory: 128 GB
///   Storage: 500 GB
///   Network: 10 Gbps
///   Duration: 4 hours 30 minutes
///
/// Average Requirements:
///   CPU: 18.5 cores
///   Memory: 64 GB
///   Storage: 250 GB
///   Network: 5 Gbps
///
/// Cost Estimate:
///   Compute: $24.50
///   Storage: $2.30
///   Network: $0.80
///   Total: $27.60 (estimated)
///
/// Resource Timeline:
///   [Deploy Phase]  → High CPU, low network
///   [Trial Phase]   → Moderate CPU, high network
///   [Teardown Phase] → Low CPU, high storage (logs)
/// ```
///
/// ## Execution History
///
/// Metadata tracks execution attempts and outcomes:
///
/// ```
/// Execution History:
///
/// Execution #1: 2025-01-15T10:00:00Z
///   Status: COMPLETED
///   Duration: 4h 22m 15s
///   Success rate: 98/100 trials (98%)
///   Cost: $26.30 (actual)
///
/// Execution #2: 2025-01-16T14:30:00Z
///   Status: FAILED
///   Duration: 1h 12m 08s (interrupted)
///   Success rate: 23/100 trials (23%)
///   Failure: Element deployment timeout
///   Cost: $7.10 (partial)
///
/// Execution #3: 2025-01-16T16:00:00Z (resumed from #2)
///   Status: COMPLETED
///   Duration: 3h 08m 42s
///   Success rate: 77/77 remaining trials (100%)
///   Cost: $18.90 (actual)
///   Total cost (#2 + #3): $26.00
///
/// Aggregate Statistics:
///   Total executions: 3 (2 successful, 1 failed)
///   Total duration: 8h 43m 05s
///   Total cost: $52.30
///   Average trial duration: 2m 38s
///   Success rate: 98.5%
/// ```
///
/// ## Performance Characteristics
///
/// Metadata captures execution efficiency metrics:
///
/// ```
/// Performance Metrics:
///
/// Parallelism:
///   Maximum: 24 concurrent steps
///   Average: 12.4 concurrent steps
///   Efficiency: 52% (12.4/24)
///
/// Critical Path:
///   Duration: 4h 30m
///   Steps: 87
///   Bottleneck: execute_trial_42 (45m)
///
/// Speedup Analysis:
///   Sequential time: 96h 15m
///   Parallel time: 4h 30m
///   Speedup: 21.4x
///   Ideal speedup (24 cores): 24.0x
///   Efficiency: 89% (21.4/24.0)
///
/// Graph Complexity:
///   Nodes: 312
///   Edges: 847
///   Avg degree: 5.4
///   Max depth: 12
///   Diameter: 18
/// ```
///
/// ## Optimization Metadata
///
/// Records optimization decisions during compilation:
///
/// ```
/// Optimization Applied:
///
/// Level: AGGRESSIVE
///
/// Optimizations:
///   ✓ Trial ordering: DEPENDENCY_OPTIMIZED
///     Savings: 12 deployments eliminated
///
///   ✓ Element instance sharing: ENABLED
///     Savings: 8 instances reused (40%)
///
///   ✓ Barrier coalescing: 18 → 12 barriers
///     Savings: 6 synchronization points removed
///
///   ✓ Step fusion: 45 steps fused to 38
///     Savings: 7 steps eliminated
///
///   ✗ Speculative execution: DISABLED
///     Reason: Cost constraints
///
/// Estimated improvement: 18% faster, 22% cheaper
/// ```
///
/// ## Usage Examples
///
/// ### Example 1: Inspecting Compilation Metadata
///
/// ```java
/// ExecutionPlan plan = testPlan.commit();
/// ExecutionPlanMetadata meta = plan.metadata();
///
/// System.out.printf("Execution Plan: %s%n", meta.id());
/// System.out.printf("Compiled from: %s%n", meta.testPlanFingerprint());
/// System.out.printf("Compiled at: %s%n", meta.compiledAt());
/// System.out.printf("Compilation took: %s%n", meta.compilationDuration());
///
/// System.out.printf("%nPlan Structure:%n");
/// System.out.printf("  Trials: %d%n", meta.trialCount());
/// System.out.printf("  Steps: %d%n", meta.stepCount());
/// System.out.printf("  Barriers: %d%n", meta.barrierCount());
/// System.out.printf("  Elements: %d%n", meta.elementInstanceCount());
/// ```
///
/// ### Example 2: Resource Planning
///
/// ```java
/// ExecutionPlanMetadata meta = plan.metadata();
/// ResourceProfile profile = meta.resourceProfile();
///
/// System.out.printf("Resource Requirements:%n");
/// System.out.printf("  Peak CPU: %.1f cores%n", profile.peakCpu());
/// System.out.printf("  Peak Memory: %.1f GB%n", profile.peakMemoryGb());
/// System.out.printf("  Estimated duration: %s%n",
///     meta.estimatedDuration());
/// System.out.printf("  Estimated cost: $%.2f%n",
///     meta.estimatedCost().orElse(0.0));
///
/// // Check if resources available
/// double availableCpu = getAvailableCpu();
/// if (profile.peakCpu() > availableCpu) {
///     System.err.printf("Insufficient CPU: need %.1f, have %.1f%n",
///         profile.peakCpu(), availableCpu);
/// }
/// ```
///
/// ### Example 3: Execution History Analysis
///
/// ```java
/// ExecutionPlanMetadata meta = plan.metadata();
/// List<ExecutionRecord> history = meta.executionHistory();
///
/// System.out.printf("Execution History: %d runs%n", history.size());
///
/// for (ExecutionRecord record : history) {
///     System.out.printf("%nExecution %s:%n", record.executionId());
///     System.out.printf("  Started: %s%n", record.startedAt());
///     System.out.printf("  Status: %s%n", record.status());
///     System.out.printf("  Duration: %s%n", record.duration());
///     System.out.printf("  Success rate: %.1f%%%n",
///         record.successRate() * 100);
///     System.out.printf("  Cost: $%.2f%n", record.actualCost());
/// }
///
/// // Calculate averages
/// double avgDuration = history.stream()
///     .mapToLong(r -> r.duration().toSeconds())
///     .average()
///     .orElse(0);
/// System.out.printf("%nAverage duration: %.1f minutes%n",
///     avgDuration / 60.0);
/// ```
///
/// ### Example 4: Performance Analysis
///
/// ```java
/// ExecutionPlanMetadata meta = plan.metadata();
/// PerformanceMetrics perf = meta.performanceMetrics();
///
/// System.out.printf("Parallelism Analysis:%n");
/// System.out.printf("  Maximum: %d concurrent steps%n",
///     perf.maximumParallelism());
/// System.out.printf("  Average: %.2f concurrent steps%n",
///     perf.averageParallelism());
/// System.out.printf("  Efficiency: %.1f%%%n",
///     (perf.averageParallelism() / perf.maximumParallelism()) * 100);
///
/// System.out.printf("%nCritical Path:%n");
/// System.out.printf("  Duration: %s%n", perf.criticalPathDuration());
/// System.out.printf("  Sequential time: %s%n", perf.totalDuration());
/// System.out.printf("  Speedup: %.2fx%n", perf.speedup());
/// ```
///
/// ### Example 5: Optimization Review
///
/// ```java
/// ExecutionPlanMetadata meta = plan.metadata();
/// Optional<OptimizationReport> optReport = meta.optimizationReport();
///
/// if (optReport.isPresent()) {
///     OptimizationReport report = optReport.get();
///
///     System.out.printf("Optimizations Applied:%n");
///     System.out.printf("  Level: %s%n", report.optimizationLevel());
///
///     for (Optimization opt : report.optimizations()) {
///         System.out.printf("%n  %s: %s%n",
///             opt.name(),
///             opt.applied() ? "✓ Applied" : "✗ Skipped");
///
///         if (opt.applied()) {
///             System.out.printf("    Savings: %s%n", opt.savings());
///         } else {
///             System.out.printf("    Reason: %s%n", opt.skipReason());
///         }
///     }
///
///     System.out.printf("%nEstimated improvement: %s%n",
///         report.estimatedImprovement());
/// }
/// ```
///
/// ## Contract Requirements
///
/// ### Immutability
/// - All metadata instances MUST be immutable
/// - Execution history MUST be append-only
/// - Performance metrics MUST be recalculated on updates
///
/// ### Accuracy
/// - Resource estimates SHOULD be within 20% of actual usage
/// - Duration estimates SHOULD account for parallelism
/// - Cost estimates SHOULD include all resource types
///
/// ### Observability
/// - Metadata MUST include plan fingerprint for traceability
/// - Execution records MUST capture start/end times
/// - Compilation metadata MUST include version information
///
/// @see ExecutionPlan
/// @see TestPlanMetadata
///
public interface ExecutionPlanMetadata {

    ///
    /// Returns the unique identifier for this execution plan.
    ///
    /// @return Execution plan ID
    ///
    String id();

    ///
    /// Returns the fingerprint of the source TestPlan.
    ///
    /// @return Source TestPlan fingerprint
    ///
    String testPlanFingerprint();

    ///
    /// Returns the timestamp when this plan was compiled.
    ///
    /// @return Compilation timestamp
    ///
    Instant compiledAt();

    ///
    /// Returns the time taken to compile this execution plan.
    ///
    /// @return Compilation duration
    ///
    Duration compilationDuration();

    ///
    /// Returns the compiler version used to generate this plan.
    ///
    /// @return Compiler version string
    ///
    String compilerVersion();

    ///
    /// Returns the optimization level applied during compilation.
    ///
    /// @return Optimization level
    ///
    OptimizationLevel optimizationLevel();

    ///
    /// Returns the number of trials in this execution plan.
    ///
    /// @return Trial count
    ///
    int trialCount();

    ///
    /// Returns the total number of atomic steps.
    ///
    /// @return Step count
    ///
    int stepCount();

    ///
    /// Returns the number of synchronization barriers.
    ///
    /// @return Barrier count
    ///
    int barrierCount();

    ///
    /// Returns the number of element instances to be deployed.
    ///
    /// @return Element instance count
    ///
    int elementInstanceCount();

    ///
    /// Returns the resource requirements profile.
    ///
    /// @return Resource profile
    ///
    ResourceProfile resourceProfile();

    ///
    /// Returns the estimated duration to complete this plan.
    ///
    /// Based on critical path analysis with parallelism.
    ///
    /// @return Estimated execution duration
    ///
    Duration estimatedDuration();

    ///
    /// Returns the estimated total cost.
    ///
    /// @return Estimated cost if calculable
    ///
    Optional<Double> estimatedCost();

    ///
    /// Returns performance metrics for this plan.
    ///
    /// @return Performance characteristics
    ///
    PerformanceMetrics performanceMetrics();

    ///
    /// Returns the optimization report if optimizations were applied.
    ///
    /// @return Optimization report if available
    ///
    Optional<OptimizationReport> optimizationReport();

    ///
    /// Returns the execution history for this plan.
    ///
    /// @return Execution records in chronological order (unmodifiable)
    ///
    List<ExecutionRecord> executionHistory();

    ///
    /// Returns the most recent execution record.
    ///
    /// @return Latest execution if any executions occurred
    ///
    Optional<ExecutionRecord> latestExecution();

    ///
    /// Returns the number of times this plan has been executed.
    ///
    /// @return Execution count
    ///
    int executionCount();

    ///
    /// Returns the number of successful executions.
    ///
    /// @return Success count
    ///
    int successfulExecutionCount();

    ///
    /// Returns arbitrary custom metadata.
    ///
    /// @return Custom metadata map (unmodifiable)
    ///
    Map<String, Object> customMetadata();

    ///
    /// Optimization level for compilation.
    ///
    enum OptimizationLevel {
        NONE,
        BASIC,
        STANDARD,
        AGGRESSIVE
    }

    ///
    /// Resource requirements profile.
    ///
    record ResourceProfile(
        double peakCpu,
        double averageCpu,
        double peakMemoryGb,
        double averageMemoryGb,
        double peakStorageGb,
        double peakNetworkGbps
    ) {}

    ///
    /// Performance metrics.
    ///
    record PerformanceMetrics(
        int maximumParallelism,
        double averageParallelism,
        Duration criticalPathDuration,
        Duration totalDuration,
        double speedup,
        double efficiency,
        GraphComplexity graphComplexity
    ) {}

    ///
    /// Graph complexity metrics.
    ///
    record GraphComplexity(
        int nodeCount,
        int edgeCount,
        double averageDegree,
        int maxDepth,
        int diameter
    ) {}

    ///
    /// Optimization report.
    ///
    interface OptimizationReport {
        OptimizationLevel optimizationLevel();
        List<Optimization> optimizations();
        String estimatedImprovement();
    }

    ///
    /// Individual optimization applied.
    ///
    record Optimization(
        String name,
        boolean applied,
        String savings,
        Optional<String> skipReason
    ) {}

    ///
    /// Execution record.
    ///
    interface ExecutionRecord {
        String executionId();
        Instant startedAt();
        Optional<Instant> completedAt();
        Duration duration();
        ExecutionStatus status();
        int trialsAttempted();
        int trialsSucceeded();
        double successRate();
        double actualCost();
        Optional<String> failureReason();
        Map<String, Object> metrics();
    }

    ///
    /// Execution status.
    ///
    enum ExecutionStatus {
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELLED
    }
}
