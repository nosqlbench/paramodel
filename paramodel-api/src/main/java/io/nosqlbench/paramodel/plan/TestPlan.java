package io.nosqlbench.paramodel.plan;

import io.nosqlbench.paramodel.elements.Element;
import io.nosqlbench.paramodel.elements.RelationshipType;
import io.nosqlbench.paramodel.parameters.ValidationResult;
import io.nosqlbench.paramodel.plan.policies.ExecutionPolicies;

import java.util.List;
import java.util.Map;
import java.util.Optional;

///
/// A user-authored declarative specification of a study defining parameter space and resources.
///
/// ## Concept
///
/// {@code TestPlan} is the "WHAT to test" specification in Simplica. It declares:
/// - **Axes**: Parameter dimensions to explore
/// - **Elements**: Resources needed for execution
/// - **Relationships**: How elements interact
/// - **Policies**: Retry strategies and error handling
///
/// ## TestPlan Lifecycle
///
/// ```
/// Authoring Phase (Mutable):
///   TestPlan.builder()
///     .withAxis(...)
///     .withElement(...)
///     .relationship(...)
///     .policies(...)
///     .build()
///          ↓
///   TestPlan (uncommitted, mutable)
///
/// Validation Phase:
///   PlanValidator.validate(testPlan)
///          ↓
///   ValidationResult
///
/// Commitment Phase (Immutable):
///   testPlan.commit()
///          ↓
///   ExecutionPlan (immutable)
///          ↑
///   TestPlan (now immutable, locked to ExecutionPlan)
/// ```
///
/// ## TestPlan vs ExecutionPlan
///
/// ```
/// TestPlan (User Intent):
///   "I want to test these parameters with these resources"
///   - High-level, declarative
///   - Expresses intent
///   - May have ambiguities (resolved at compile time)
///
/// ExecutionPlan (System Directive):
///   "Execute these exact steps in this exact order"
///   - Low-level, imperative
///   - Fully resolved
///   - No ambiguities
/// ```
///
/// ## Immutability Principle
///
/// ```
/// Before commit():
///   TestPlan is mutable
///   Can modify axes, elements, relationships
///
/// After commit():
///   TestPlan becomes immutable
///   ExecutionPlan generated
///   Algebraically locked relationship:
///     TestPlan ⟷ ExecutionPlan
///
/// To change anything:
///   Create new TestPlan
///   Validate
///   Commit → new ExecutionPlan
///   New version number
/// ```
///
/// ## Structure
///
/// ```
/// TestPlan
/// ├── name: String
/// │   └── Study identifier
/// │
/// ├── axes: List<Axis<?>>
/// │   └── Parameter dimensions (ordered)
/// │
/// ├── elements: List<Element>
/// │   └── Required resources
/// │
/// ├── relationships: Map<(Element, Element), RelationshipType>
/// │   └── How elements relate
/// │
/// ├── policies: ExecutionPolicies
/// │   └── Retry, timeout, error handling
/// │
/// ├── trialSpace: int
/// │   └── Total trials = ∏ axis.cardinality()
/// │
/// └── committed: boolean
///     └── Is this plan locked?
/// ```
///
/// ## Usage Example: Simple Study
///
/// ```java
/// // Define axes
/// Axis<String> modelAxis = Axis.discrete("model",
///     List.of("gpt-4", "claude-3", "gemini-pro")
/// );
/// Axis<Double> tempAxis = Axis.range("temperature", 0.0, 1.0, 0.1);
///
/// // Define elements (element types are system-specific)
/// Element apiService = ...; // "llm-api" with endpoint parameter
///
/// // Create test plan
/// TestPlan plan = TestPlan.builder()
///     .name("llm-temperature-sweep")
///     .withAxis(modelAxis)
///     .withAxis(tempAxis)
///     .withElement(apiService)
///     .policies(ExecutionPolicies.defaults())
///     .build();
///
/// // Trial space: 3 models × 11 temps = 33 trials
/// assert plan.trialSpaceSize() == 33;
///
/// // Validate
/// ValidationResult result = planValidator.validate(plan);
/// assert result.isPassed();
///
/// // Commit (becomes immutable)
/// ExecutionPlan execPlan = plan.commit();
/// ```
///
/// ## Usage Example: Complex Dependencies
///
/// ```java
/// // Elements with dependencies (types are system-specific)
/// Element storage = ...;    // "storage-volume"
/// Element database = ...;   // "postgres", depends on storage
/// Element cache = ...;      // "redis"
/// Element appServer = ...;  // "app-server", depends on database and cache
///
/// TestPlan plan = TestPlan.builder()
///     .name("performance-study")
///     .withAxis(...)
///     .withElement(storage)
///     .withElement(database)
///     .withElement(cache)
///     .withElement(appServer)
///     // Database cannot be used concurrently
///     .relationship(database, appServer, RelationshipType.MUTUALLY_EXCLUSIVE)
///     // Cache can be shared
///     .relationship(cache, appServer, RelationshipType.SHARED)
///     .policies(ExecutionPolicies.defaults())
///     .build();
///
/// // Planner will serialize database access, allow cache sharing
/// ExecutionPlan execPlan = plan.commit();
/// ```
///
/// ## Usage Example: Axis Reordering
///
/// ```java
/// TestPlan plan = TestPlan.builder()
///     .name("study")
///     .withAxis(modelAxis)      // Major axis
///     .withAxis(tempAxis)       // Minor axis
///     .withAxis(maxTokensAxis)  // Tertiary axis
///     .build();
///
/// ExecutionPlan plan1 = plan.commit();
/// // Edge-first varies major axis (model) first
///
/// // Reorder to prioritize different axis
/// TestPlan reordered = plan.reorderAxes(
///     List.of("temperature", "model", "max_tokens")
/// );
///
/// ExecutionPlan plan2 = reordered.commit();
/// // Edge-first now varies temperature first
/// // Different trial ordering!
///
/// // Compare trial orderings
/// assert !plan1.trials().equals(plan2.trials());
/// ```
///
/// ## Trial Space Calculation
///
/// ```
/// Trial Space Size = ∏ axis.cardinality() for all axes
///
/// Example:
///   Axis 1: 5 values
///   Axis 2: 10 values
///   Axis 3: 3 values
///
///   Trial space = 5 × 10 × 3 = 150 trials
/// ```
///
/// ## Validation Requirements
///
/// Before commitment, TestPlan must be validated:
///
/// ```
/// Validation Checks:
///   1. At least one axis defined
///   2. All axis names unique
///   3. All element names unique
///   4. All relationships well-formed
///   5. No dependency cycles
///   6. Resources are schedulable
///   7. Policies are sensible
///   8. No ambiguities
/// ```
///
/// ## Compilation Process
///
/// ```
/// commit() algorithm:
///   1. Validate TestPlan
///      - Check all validation rules
///      - Throw if invalid
///
///   2. Generate Trial Space
///      - Compute Cartesian product of axes
///      - Apply ordering strategy (edge-first, etc.)
///
///   3. Resolve Dependencies
///      - Build element dependency graph
///      - Topological sort for start order
///
///   4. Insert Barriers
///      - For MUTUALLY_EXCLUSIVE relationships
///      - Ensure no concurrent access
///
///   5. Generate Atomic Steps
///      - Element start/stop steps
///      - Trial execution steps
///      - Barrier wait steps
///
///   6. Create ExecutionPlan
///      - Immutable plan with all steps
///      - Assign version number
///      - Link to TestPlan
///
///   7. Lock TestPlan
///      - Mark as committed
///      - Prevent further modification
/// ```
///
/// ## Versioning
///
/// Each committed TestPlan has a version:
///
/// ```java
/// TestPlan v1 = TestPlan.builder()
///     .name("study")
///     .withAxis(axis1)
///     .build();
///
/// ExecutionPlan exec1 = v1.commit();  // Version 1
///
/// // Modify and create new version
/// TestPlan v2 = TestPlan.builder()
///     .name("study")
///     .withAxis(axis1)
///     .withAxis(axis2)  // Added axis
///     .build();
///
/// ExecutionPlan exec2 = v2.commit();  // Version 2
///
/// // Both versions persist
/// assert !exec1.version().equals(exec2.version());
/// ```
///
/// ## Metadata
///
/// TestPlan carries metadata:
///
/// ```java
/// TestPlanMetadata meta = plan.metadata();
/// System.out.println("Created: " + meta.createdAt());
/// System.out.println("Author: " + meta.createdBy().orElse("unknown"));
/// System.out.println("Description: " + meta.description().orElse("none"));
/// ```
///
/// @see ExecutionPlan
/// @see Axis
/// @see Element
/// @see RelationshipType
/// @see ExecutionPolicies
/// @see io.nosqlbench.paramodel.compilation.Compiler
/// @since 0.1.0
///
public interface TestPlan {

    ///
    /// Returns the unique name of this test plan.
    ///
    /// ## Naming Guidelines
    ///
    /// Good names describe:
    /// - What is being studied
    /// - Key variables being tested
    ///
    /// Examples:
    /// ```
    /// "llm-temperature-sweep"
    /// "database-scaling-study"
    /// "cache-eviction-comparison"
    /// "ml-hyperparameter-optimization"
    /// ```
    ///
    /// @return plan name, never null or empty
    ///
    String name();

    ///
    /// Returns the ordered list of axes defining the parameter space.
    ///
    /// ## Axis Order Significance
    ///
    /// Order determines:
    /// - Default iteration priority (major → minor)
    /// - Edge-first scaffolding order
    /// - Grouping for resource sharing
    ///
    /// ## Example
    ///
    /// ```java
    /// List<Axis<?>> axes = plan.axes();
    /// Axis<?> major = axes.get(0);  // Varied first in edge-first
    /// Axis<?> minor = axes.get(1);  // Varied second
    /// ```
    ///
    /// @return immutable, ordered list of axes, never null
    ///
    List<Axis<?>> axes();

    ///
    /// Returns an axis by name.
    ///
    /// @param name axis name to look up
    /// @return axis if present
    ///
    default Optional<Axis<?>> axis(String name) {
        return axes().stream()
            .filter(a -> a.name().equals(name))
            .findFirst();
    }

    ///
    /// Returns all elements required for this study.
    ///
    /// Elements are unordered at this level.
    /// Execution order is determined by dependencies and compilation.
    ///
    /// @return immutable list of elements, never null
    ///
    List<Element> elements();

    ///
    /// Returns an element by name.
    ///
    /// @param name element name to look up
    /// @return element if present
    ///
    default Optional<Element> element(String name) {
        return elements().stream()
            .filter(e -> e.name().equals(name))
            .findFirst();
    }

    ///
    /// Returns all relationships between elements.
    ///
    /// ## Relationship Map Structure
    ///
    /// ```
    /// Map<ElementPair, RelationshipType>
    ///
    /// ElementPair: (elementA, elementB)
    /// RelationshipType: MUTUALLY_EXCLUSIVE | SHARED | INSTANCED_PER
    /// ```
    ///
    /// ## Example
    ///
    /// ```java
    /// Map<ElementPair, RelationshipType> rels = plan.relationships();
    /// for (var entry : rels.entrySet()) {
    ///     ElementPair pair = entry.getKey();
    ///     RelationshipType type = entry.getValue();
    ///     System.out.printf("%s ←%s→ %s%n",
    ///         pair.first().name(), type, pair.second().name());
    /// }
    /// ```
    ///
    /// @return immutable relationship map, never null
    ///
    Map<ElementPair, RelationshipType> relationships();

    ///
    /// Returns the relationship type between two elements.
    ///
    /// @param element1 first element
    /// @param element2 second element
    /// @return relationship type if defined
    ///
    Optional<RelationshipType> relationshipBetween(Element element1, Element element2);

    ///
    /// Returns execution policies for this plan.
    ///
    /// Policies control:
    /// - Retry strategies
    /// - Timeouts
    /// - Error handling
    /// - Intervention behavior
    ///
    /// @return execution policies, never null
    ///
    ExecutionPolicies policies();

    ///
    /// Returns the optimization strategy for this plan.
    ///
    /// The optimization strategy controls how aggressive the compiler
    /// should be when optimizing the execution plan.
    ///
    /// @return optimization strategy, never null
    ///
    OptimizationStrategy optimizationStrategy();

    ///
    /// Returns the total size of the trial space (number of trials).
    ///
    /// ## Calculation
    ///
    /// ```
    /// trialSpaceSize() = ∏ axis.cardinality() for all axes
    /// ```
    ///
    /// ## Example
    ///
    /// ```java
    /// TestPlan plan = ...;  // 3 axes: [5 values, 10 values, 2 values]
    /// assert plan.trialSpaceSize() == 5 * 10 * 2;  // 100 trials
    /// ```
    ///
    /// @return total number of trials in space
    ///
    long trialSpaceSize();

    ///
    /// Checks if this test plan has been committed.
    ///
    /// ## Commitment State
    ///
    /// ```
    /// Before commit():  isCommitted() = false (mutable)
    /// After commit():   isCommitted() = true (immutable)
    /// ```
    ///
    /// ## Example
    ///
    /// ```java
    /// TestPlan plan = TestPlan.builder()...build();
    /// assert !plan.isCommitted();
    ///
    /// ExecutionPlan exec = plan.commit();
    /// assert plan.isCommitted();
    ///
    /// // Cannot modify committed plan
    /// try {
    ///     plan.reorderAxes(...);  // Throws IllegalStateException
    /// } catch (IllegalStateException e) {
    ///     // Expected
    /// }
    /// ```
    ///
    /// @return true if committed, false if mutable
    ///
    boolean isCommitted();

    ///
    /// Validates this test plan for executability and consistency.
    ///
    /// ## Validation Process
    ///
    /// ```
    /// validate():
    ///   1. Check structural requirements
    ///      - At least one axis
    ///      - All names unique
    ///
    ///   2. Check dependencies
    ///      - No cycles
    ///      - All dependencies satisfiable
    ///
    ///   3. Check relationships
    ///      - All elements in relationships exist
    ///      - Relationships are consistent
    ///
    ///   4. Check schedulability
    ///      - Resources can be allocated
    ///      - Barriers can enforce constraints
    ///
    ///   5. Check policies
    ///      - Timeouts reasonable
    ///      - Retry counts sensible
    /// ```
    ///
    /// ## Example
    ///
    /// ```java
    /// TestPlan plan = TestPlan.builder()...build();
    /// ValidationResult result = plan.validate();
    ///
    /// if (result.isFailed()) {
    ///     System.err.println("Plan validation failed:");
    ///     result.violations().forEach(v ->
    ///         System.err.println("  - " + v)
    ///     );
    ///     throw new InvalidPlanException(result);
    /// }
    ///
    /// // Validation passed, can commit
    /// ExecutionPlan exec = plan.commit();
    /// ```
    ///
    /// @return validation result with any violations
    ///
    ValidationResult validate();

    ///
    /// Reorders axes and returns a new test plan with the new ordering.
    ///
    /// ## Axis Ordering Effects
    ///
    /// Reordering affects:
    /// - Trial generation order
    /// - Edge-first prioritization
    /// - Grouping boundaries
    ///
    /// ## Example
    ///
    /// ```java
    /// TestPlan original = TestPlan.builder()
    ///     .withAxis(modelAxis)  // Major
    ///     .withAxis(tempAxis)   // Minor
    ///     .build();
    ///
    /// // See effect of different ordering
    /// TestPlan reordered = original.reorderAxes(
    ///     List.of("temperature", "model")  // Now temp is major
    /// );
    ///
    /// // Compare execution plans
    /// ExecutionPlan exec1 = original.commit();
    /// ExecutionPlan exec2 = reordered.commit();
    ///
    /// // Different trial orderings!
    /// List<String> order1 = exec1.trials().stream()
    ///     .map(Trial::id).toList();
    /// List<String> order2 = exec2.trials().stream()
    ///     .map(Trial::id).toList();
    /// assert !order1.equals(order2);
    /// ```
    ///
    /// ## Contract
    ///
    /// - MUST NOT modify this plan (returns new plan)
    /// - MUST fail if plan is committed (immutable)
    /// - MUST fail if axis names don't match existing axes
    ///
    /// @param axisNames new axis order (by name)
    /// @return new test plan with reordered axes
    /// @throws IllegalStateException if plan is committed
    /// @throws IllegalArgumentException if axis names invalid
    ///
    TestPlan reorderAxes(List<String> axisNames);

    ///
    /// Commits this test plan, making it immutable and generating an execution plan.
    ///
    /// ## Commitment Process
    ///
    /// ```
    /// commit():
    ///   1. Validate plan
    ///      → Throw if invalid
    ///
    ///   2. Compile to ExecutionPlan
    ///      - Generate trials
    ///      - Resolve dependencies
    ///      - Insert barriers
    ///      - Create atomic steps
    ///
    ///   3. Lock TestPlan
    ///      → Mark as committed (immutable)
    ///
    ///   4. Establish algebraic lock
    ///      → TestPlan ⟷ ExecutionPlan
    ///
    ///   5. Persist both plans
    ///      → Assign version numbers
    ///
    ///   6. Return ExecutionPlan
    /// ```
    ///
    /// ## Example
    ///
    /// ```java
    /// TestPlan plan = TestPlan.builder()
    ///     .name("study")
    ///     .withAxis(...)
    ///     .withElement(...)
    ///     .build();
    ///
    /// // Validate before committing (optional but recommended)
    /// ValidationResult validation = plan.validate();
    /// if (validation.isFailed()) {
    ///     throw new InvalidPlanException(validation);
    /// }
    ///
    /// // Commit (becomes immutable)
    /// ExecutionPlan execPlan = plan.commit();
    ///
    /// // Now locked
    /// assert plan.isCommitted();
    /// assert execPlan.sourcePlan() == plan;
    /// ```
    ///
    /// ## Contract
    ///
    /// - MUST validate plan first
    /// - MUST throw if validation fails
    /// - MUST make this plan immutable
    /// - MUST generate valid ExecutionPlan
    /// - MUST be idempotent (calling twice returns same plan)
    ///
    /// @return immutable execution plan
    /// @throws IllegalStateException if validation fails or already committed
    ///
    ExecutionPlan commit();

    ///
    /// Returns metadata about this test plan.
    ///
    /// @return plan metadata, never null
    ///
    TestPlanMetadata metadata();

    ///
    /// Pair of elements for relationship mapping.
    ///
    /// ## Symmetry
    ///
    /// ElementPair treats relationships as symmetric:
    /// ```
    /// (A, B) == (B, A)
    /// ```
    ///
    /// @param first first element
    /// @param second second element
    ///
    record ElementPair(Element first, Element second) {
        public ElementPair {
            if (first == null || second == null) {
                throw new IllegalArgumentException("Elements cannot be null");
            }
        }

        ///
        /// Checks if this pair contains the given element.
        ///
        public boolean contains(Element element) {
            return first.equals(element) || second.equals(element);
        }

        ///
        /// Returns the other element in the pair.
        ///
        public Element other(Element element) {
            if (first.equals(element)) return second;
            if (second.equals(element)) return first;
            throw new IllegalArgumentException("Element not in pair");
        }
    }

    ///
    /// Metadata about a test plan.
    ///
    interface TestPlanMetadata {
        /// When plan was created
        java.time.Instant createdAt();

        /// Who created the plan
        Optional<String> createdBy();

        /// Human-readable description
        Optional<String> description();

        /// Arbitrary tags for categorization
        Map<String, String> tags();

        /// Version if plan has been committed
        Optional<String> version();
    }
}
