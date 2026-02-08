package io.nosqlbench.paramodel.plan;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

///
/// # TestPlanMetadata
///
/// Comprehensive metadata for {@link TestPlan} instances, capturing provenance,
/// authorship, versioning, cost estimates, and construction history. Metadata
/// supports auditability, reproducibility, and plan management across the lifecycle.
///
/// ## Metadata Philosophy
///
/// Test plans are first-class versioned artifacts with full provenance tracking:
///
/// ```
/// Metadata Dimensions:
///
/// Identity         Provenance        Economics
///   │                │                 │
///   ├─ Name          ├─ Created       ├─ Estimated cost
///   ├─ Version       ├─ Modified      ├─ Resource hours
///   ├─ Fingerprint   ├─ Author        └─ Trial budget
///   └─ Tags          └─ Source plan
///                         │
/// Lifecycle                Classification
///   │                         │
///   ├─ Status                 ├─ Purpose
///   ├─ Committed              ├─ Domain
///   └─ Archived               └─ Sensitivity
/// ```
///
/// ## Fingerprinting and Identity
///
/// Test plan fingerprints provide cryptographic identity for exact reproducibility:
///
/// ```
/// Fingerprint Calculation:
///
/// fingerprint = SHA-256(
///     name +
///     version +
///     canonical(axes) +
///     canonical(elements) +
///     canonical(relationships) +
///     canonical(policies)
/// )
///
/// Where canonical() provides deterministic serialization:
///   - Sorted keys
///   - Normalized whitespace
///   - Stable floating-point representation
///   - UTC timestamps
///
/// Fingerprint Properties:
///   - Stable: Same plan → same fingerprint
///   - Unique: Different plans → different fingerprints (with high probability)
///   - Tamper-evident: Modifications change fingerprint
/// ```
///
/// ## Versioning Strategy
///
/// Test plans follow semantic versioning with execution-aware semantics:
///
/// ```
/// Version Format: MAJOR.MINOR.PATCH
///
/// MAJOR: Incompatible changes to trial space
///   - Different axes
///   - Different axis cardinalities
///   - Different element types
///   Example: v1.0.0 → v2.0.0 (added new axis)
///
/// MINOR: Backward-compatible additions
///   - Additional metadata
///   - New relationships
///   - Policy refinements
///   Example: v1.0.0 → v1.1.0 (added retry policy)
///
/// PATCH: Cosmetic changes with no semantic impact
///   - Documentation updates
///   - Description changes
///   - Tag modifications
///   Example: v1.0.0 → v1.0.1 (fixed typo)
///
/// Version Compatibility:
///   - Results from v1.2.3 can be compared with v1.2.4 (same trial space)
///   - Results from v1.2.3 cannot be compared with v1.3.0 (different semantics)
///   - Results from v1.2.3 incompatible with v2.0.0 (different trial space)
/// ```
///
/// ## Provenance Chain
///
/// Metadata captures full construction and modification history:
///
/// ```
/// Provenance Graph:
///
/// TestPlan v1.0.0
///   ├─ created: 2025-01-15T10:00:00Z
///   ├─ author: alice@example.com
///   ├─ source: null (original)
///   └─ fingerprint: a1b2c3...
///        │
///        ├─→ TestPlan v1.1.0
///        │     ├─ created: 2025-01-16T14:30:00Z
///        │     ├─ author: bob@example.com
///        │     ├─ source: a1b2c3... (v1.0.0)
///        │     ├─ changes: ["added retry policy"]
///        │     └─ fingerprint: d4e5f6...
///        │          │
///        │          └─→ TestPlan v2.0.0
///        │                ├─ created: 2025-01-20T09:00:00Z
///        │                ├─ author: alice@example.com
///        │                ├─ source: d4e5f6... (v1.1.0)
///        │                ├─ changes: ["added concurrency axis"]
///        │                └─ fingerprint: g7h8i9...
///        │
///        └─→ TestPlan v1.0.1
///              ├─ created: 2025-01-15T16:00:00Z
///              ├─ author: alice@example.com
///              ├─ source: a1b2c3... (v1.0.0)
///              ├─ changes: ["fixed description"]
///              └─ fingerprint: j1k2l3...
/// ```
///
/// ## Cost Estimation
///
/// Metadata includes cost estimates for resource planning:
///
/// ```
/// Cost Model:
///
/// Total Cost = ∑ (trial_cost × trial_count × retry_factor)
///
/// trial_cost = ∑ (element_i_cost × element_i_duration × element_i_instances)
///
/// Example Calculation:
///   Axes: cache_size={128,256,512}  concurrency={10,50,100}
///   Trial count: 3 × 3 = 9 trials
///
///   Elements:
///     - redis: $0.10/hour × 1 instance × 0.5 hour = $0.05/trial
///     - loadgen: $0.20/hour × 1 instance × 0.5 hour = $0.10/trial
///
///   Trial cost: $0.15/trial
///   Total cost: $0.15 × 9 = $1.35 (baseline)
///   With retries (1.2x): $1.62 (estimated)
/// ```
///
/// ## Lifecycle States
///
/// ```
/// Lifecycle State Machine:
///
///   DRAFT ───────→ VALIDATED ───────→ COMMITTED ───────→ EXECUTING
///     │              │                   │                  │
///     │              │                   │                  ├─→ COMPLETED
///     │              │                   │                  ├─→ FAILED
///     │              │                   │                  └─→ CANCELLED
///     │              │                   │
///     └──────────────┴───────────────────┴─────────────────→ ARCHIVED
///
/// Transitions:
///   - DRAFT → VALIDATED: TestPlanBuilder.build()
///   - VALIDATED → COMMITTED: TestPlan.commit()
///   - COMMITTED → EXECUTING: ExecutionPlan.start()
///   - EXECUTING → {COMPLETED, FAILED, CANCELLED}: Execution completion
///   - Any → ARCHIVED: Administrative archival
/// ```
///
/// ## Usage Examples
///
/// ### Example 1: Basic Metadata Inspection
///
/// ```java
/// TestPlan plan = /* ... */;
/// TestPlanMetadata meta = plan.metadata();
///
/// System.out.printf("Plan: %s v%s%n", meta.name(), meta.version());
/// System.out.printf("Created: %s by %s%n",
///     meta.createdAt(), meta.author());
/// System.out.printf("Trial space: %d trials%n", meta.trialSpaceSize());
/// System.out.printf("Estimated cost: $%.2f%n",
///     meta.estimatedCost().orElse(0.0));
/// System.out.printf("Fingerprint: %s%n", meta.fingerprint());
/// ```
///
/// ### Example 2: Provenance Chain Navigation
///
/// ```java
/// TestPlan currentPlan = /* ... */;
/// TestPlanMetadata meta = currentPlan.metadata();
///
/// // Walk back through provenance chain
/// Optional<String> sourceFingerprint = meta.sourceFingerprint();
/// while (sourceFingerprint.isPresent()) {
///     TestPlan sourcePlan = repository.findByFingerprint(sourceFingerprint.get());
///     TestPlanMetadata sourceMeta = sourcePlan.metadata();
///
///     System.out.printf("Based on: %s v%s (created %s)%n",
///         sourceMeta.name(),
///         sourceMeta.version(),
///         sourceMeta.createdAt());
///
///     sourceFingerprint = sourceMeta.sourceFingerprint();
/// }
/// ```
///
/// ### Example 3: Version Compatibility Check
///
/// ```java
/// boolean canCompareResults(TestPlanMetadata meta1, TestPlanMetadata meta2) {
///     // Same major and minor version → compatible trial spaces
///     SemanticVersion v1 = meta1.semanticVersion();
///     SemanticVersion v2 = meta2.semanticVersion();
///
///     return v1.major() == v2.major() && v1.minor() == v2.minor();
/// }
///
/// // Example usage
/// TestPlanMetadata baseline = baselinePlan.metadata();
/// TestPlanMetadata candidate = candidatePlan.metadata();
///
/// if (canCompareResults(baseline, candidate)) {
///     // Safe to compare results from both plans
///     compareExecutionResults(baselineResults, candidateResults);
/// } else {
///     System.err.println("Incompatible plan versions - cannot compare results");
/// }
/// ```
///
/// ### Example 4: Cost-Based Filtering
///
/// ```java
/// List<TestPlan> plans = repository.findAll();
/// double budget = 100.0;
///
/// List<TestPlan> affordablePlans = plans.stream()
///     .filter(plan -> plan.metadata().estimatedCost().orElse(Double.MAX_VALUE) <= budget)
///     .sorted((p1, p2) -> Double.compare(
///         p1.metadata().estimatedCost().orElse(0.0),
///         p2.metadata().estimatedCost().orElse(0.0)))
///     .toList();
///
/// affordablePlans.forEach(plan -> {
///     TestPlanMetadata meta = plan.metadata();
///     System.out.printf("%s: %d trials, $%.2f estimated%n",
///         meta.name(),
///         meta.trialSpaceSize(),
///         meta.estimatedCost().orElse(0.0));
/// });
/// ```
///
/// ### Example 5: Custom Metadata for Classification
///
/// ```java
/// TestPlan plan = TestPlanBuilder.create()
///     .name("security-regression")
///     .withAxis(/* ... */)
///     .metadata("domain", "security")
///     .metadata("sensitivity", "confidential")
///     .metadata("owner_team", "security-eng")
///     .metadata("compliance_tags", List.of("SOC2", "HIPAA"))
///     .metadata("notification_channel", "#security-alerts")
///     .build();
///
/// // Later, filter by custom metadata
/// List<TestPlan> securityPlans = repository.findAll().stream()
///     .filter(p -> "security".equals(
///         p.metadata().customMetadata().get("domain")))
///     .toList();
/// ```
///
/// ## Contract Requirements
///
/// ### Immutability
/// - All metadata instances MUST be immutable
/// - All collections MUST be unmodifiable
///
/// ### Fingerprint Stability
/// - Fingerprints MUST be deterministic: same plan → same fingerprint
/// - Fingerprints MUST use cryptographic hash (SHA-256 minimum)
/// - Fingerprints MUST be represented as hexadecimal strings
///
/// ### Versioning
/// - Versions MUST follow semantic versioning (MAJOR.MINOR.PATCH)
/// - Version changes MUST align with semantic versioning rules
/// - Version comparison MUST support ordering
///
/// ### Provenance
/// - Source fingerprints MUST reference valid parent plans when present
/// - Creation timestamps MUST be in UTC
/// - Author information SHOULD be present but MAY be anonymized
///
/// ### Cost Estimation
/// - Cost estimates SHOULD be in USD or configurable currency
/// - Cost calculations SHOULD include retry overhead
/// - Missing cost estimates MUST be represented as Optional.empty()
///
/// @see TestPlan
/// @see TestPlanBuilder
///
public interface TestPlanMetadata {

    ///
    /// Returns the test plan name.
    ///
    /// @return Test plan name (non-null, non-empty)
    ///
    String name();

    ///
    /// Returns the semantic version of this test plan.
    ///
    /// @return Semantic version string (e.g., "1.2.3")
    ///
    String version();

    ///
    /// Returns parsed semantic version for programmatic comparison.
    ///
    /// @return Semantic version components
    ///
    SemanticVersion semanticVersion();

    ///
    /// Returns the cryptographic fingerprint of this test plan.
    ///
    /// The fingerprint uniquely identifies the plan's structure and configuration.
    /// Any change to axes, elements, relationships, or policies produces a different
    /// fingerprint.
    ///
    /// @return SHA-256 fingerprint as hexadecimal string (64 characters)
    ///
    String fingerprint();

    ///
    /// Returns the fingerprint of the source plan this was derived from.
    ///
    /// Empty if this is an original plan not derived from another.
    ///
    /// @return Source plan fingerprint if derived
    ///
    Optional<String> sourceFingerprint();

    ///
    /// Returns the timestamp when this test plan was created.
    ///
    /// @return Creation timestamp in UTC
    ///
    Instant createdAt();

    ///
    /// Returns the timestamp when this test plan was last modified.
    ///
    /// For immutable plans, this equals createdAt(). For mutable plans
    /// in draft state, this reflects the last modification.
    ///
    /// @return Last modification timestamp in UTC
    ///
    Instant modifiedAt();

    ///
    /// Returns the author or creator of this test plan.
    ///
    /// This may be a username, email, or service account identifier.
    ///
    /// @return Author identifier if known
    ///
    Optional<String> author();

    ///
    /// Returns the description of this test plan.
    ///
    /// @return Human-readable description if provided
    ///
    Optional<String> description();

    ///
    /// Returns the list of changes from the source plan.
    ///
    /// Empty if this is an original plan or changes are not tracked.
    ///
    /// @return List of change descriptions
    ///
    List<String> changesSinceSource();

    ///
    /// Returns the total number of trials in the trial space.
    ///
    /// This is the Cartesian product cardinality: ∏(|axis_i|) for all axes.
    ///
    /// @return Total trial count
    ///
    long trialSpaceSize();

    ///
    /// Returns the estimated cost to execute this test plan.
    ///
    /// Cost includes all element resources, trial duration, and retry overhead.
    /// Cost is typically in USD but may be in other currencies based on configuration.
    ///
    /// @return Estimated total cost if calculable
    ///
    Optional<Double> estimatedCost();

    ///
    /// Returns the estimated total resource-hours for execution.
    ///
    /// This is the sum of all element-hours across all trials, including retries.
    ///
    /// @return Estimated resource-hours if calculable
    ///
    Optional<Double> estimatedResourceHours();

    ///
    /// Returns the estimated wall-clock time to complete execution.
    ///
    /// This accounts for parallelism based on element relationships and
    /// concurrency constraints.
    ///
    /// @return Estimated duration if calculable
    ///
    Optional<java.time.Duration> estimatedDuration();

    ///
    /// Returns the lifecycle state of this test plan.
    ///
    /// @return Current lifecycle state
    ///
    LifecycleState lifecycleState();

    ///
    /// Returns whether this test plan has been committed to execution.
    ///
    /// Committed plans are immutable and have associated ExecutionPlans.
    ///
    /// @return True if committed, false if still in draft/validated state
    ///
    boolean isCommitted();

    ///
    /// Returns arbitrary custom metadata attached to this plan.
    ///
    /// Custom metadata can include tags, classifications, owner information,
    /// or any other application-specific attributes.
    ///
    /// @return Custom metadata map (unmodifiable)
    ///
    Map<String, Object> customMetadata();

    ///
    /// Returns classification tags attached to this plan.
    ///
    /// Tags support categorization, filtering, and organization.
    /// Examples: "regression", "performance", "security", "smoke-test"
    ///
    /// @return Classification tags (unmodifiable)
    ///
    List<String> tags();

    ///
    /// Semantic version components for programmatic comparison.
    ///
    record SemanticVersion(int major, int minor, int patch) implements Comparable<SemanticVersion> {

        ///
        /// Parses a semantic version string.
        ///
        /// @param version Version string (e.g., "1.2.3")
        /// @return Parsed semantic version
        /// @throws IllegalArgumentException if version string is invalid
        ///
        public static SemanticVersion parse(String version) {
            String[] parts = version.split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException(
                    "Invalid semantic version: " + version + " (expected MAJOR.MINOR.PATCH)");
            }
            try {
                return new SemanticVersion(
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2])
                );
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                    "Invalid semantic version: " + version + " (non-numeric components)", e);
            }
        }

        ///
        /// Checks if this version is compatible with another for result comparison.
        ///
        /// Versions are compatible if major and minor versions match.
        ///
        /// @param other Version to compare with
        /// @return True if compatible
        ///
        public boolean isCompatibleWith(SemanticVersion other) {
            return this.major == other.major && this.minor == other.minor;
        }

        @Override
        public int compareTo(SemanticVersion other) {
            int majorCmp = Integer.compare(this.major, other.major);
            if (majorCmp != 0) return majorCmp;

            int minorCmp = Integer.compare(this.minor, other.minor);
            if (minorCmp != 0) return minorCmp;

            return Integer.compare(this.patch, other.patch);
        }

        @Override
        public String toString() {
            return major + "." + minor + "." + patch;
        }
    }

    ///
    /// Lifecycle states for test plans.
    ///
    enum LifecycleState {
        ///
        /// Plan is being constructed, not yet validated.
        ///
        DRAFT,

        ///
        /// Plan has been validated but not committed.
        ///
        VALIDATED,

        ///
        /// Plan has been committed and has an associated ExecutionPlan.
        ///
        COMMITTED,

        ///
        /// Associated ExecutionPlan is currently running.
        ///
        EXECUTING,

        ///
        /// Execution completed successfully.
        ///
        COMPLETED,

        ///
        /// Execution failed.
        ///
        FAILED,

        ///
        /// Execution was cancelled.
        ///
        CANCELLED,

        ///
        /// Plan has been archived (no longer active).
        ///
        ARCHIVED;

        ///
        /// Checks if this state is terminal (no further transitions expected).
        ///
        /// @return True if terminal state
        ///
        public boolean isTerminal() {
            return this == COMPLETED || this == FAILED || this == CANCELLED || this == ARCHIVED;
        }

        ///
        /// Checks if this state represents successful completion.
        ///
        /// @return True if completed successfully
        ///
        public boolean isSuccess() {
            return this == COMPLETED;
        }

        ///
        /// Checks if this state represents a failure.
        ///
        /// @return True if failed or cancelled
        ///
        public boolean isFailure() {
            return this == FAILED || this == CANCELLED;
        }
    }
}
