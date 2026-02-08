///
/// Simplica test plan and execution plan contracts for study execution.
///
/// ## Overview
///
/// This package provides the Simplica layer built on top of paramodel foundations.
/// It transforms abstract parameter spaces into concrete, executable study plans.
///
/// ## Paramodel → Simplica Transition
///
/// ```
/// Paramodel Foundation:
///   Parameter<T>     - Abstract parameter with domain
///   Sequence         - Ordered trials
///   Trial            - Parameter assignments
///
///   ↓ becomes
///
/// Simplica Application:
///   Axis<T>          - Named parameter dimension in study
///   TestPlan         - Declarative study specification
///   ExecutionPlan    - Compiled, immutable execution graph
/// ```
///
/// ## Core Concepts
///
/// ```
/// Test Plan (User-Authored):
///   "WHAT to test"
///   ├── Axes (parameter dimensions)
///   ├── Elements (resources needed)
///   ├── Dependencies (relationships)
///   └── Policies (retry, error handling)
///
///   ↓ compiles into
///
/// Execution Plan (System-Generated):
///   "HOW to test"
///   ├── Atomic steps (indivisible units)
///   ├── Barriers (wait conditions)
///   ├── Scheduling (order and concurrency)
///   └── Resource allocation (lifecycle)
/// ```
///
/// ## Key Components
///
/// ### Test Plan Components
///
/// ```
/// TestPlan
/// ├── Axis<T>
/// │   └── Named parameter dimension
/// │       (paramodel Parameter + study context)
/// │
/// ├── Element
/// │   └── Instantiable/deployable resource
/// │       (database, service, cache, dataset)
/// │
/// ├── RelationshipType
/// │   └── How elements relate
/// │       (MUTUALLY_EXCLUSIVE, SHARED, INSTANCED_PER)
/// │
/// └── ExecutionPolicies
///     └── Retry strategies, error handling
/// ```
///
/// ### Execution Plan Components
///
/// ```
/// ExecutionPlan
/// ├── AtomicStep
/// │   └── Indivisible execution unit
/// │
/// ├── Barrier
/// │   └── Wait condition blocking progress
/// │
/// ├── TrialOrdering
/// │   └── Ordering strategy (edge-first, etc.)
/// │
/// └── ExecutionGraph
///     └── Dependency graph of steps
/// ```
///
/// ## Relationship Semantics
///
/// Dependencies between elements determine concurrency:
///
/// ```
/// RelationshipType          Concurrency          Use Case
/// ─────────────────────────────────────────────────────────────────
/// MUTUALLY_EXCLUSIVE        Serialize all        Same database instance
/// SHARED                    Allow concurrent     Read-only cache
/// INSTANCED_PER             Fresh per scope      Per-trial containers
/// ```
///
/// ## Plan Lifecycle
///
/// ```
/// 1. Authoring:
///    User creates TestPlan
///    ├── Define axes
///    ├── Specify elements
///    ├── Set dependencies
///    └── Configure policies
///
/// 2. Validation:
///    PlanValidator.validate(testPlan)
///    ├── Check schedulability
///    ├── Verify constraints
///    └── Detect ambiguities
///
/// 3. Commitment:
///    testPlan.commit()
///    ├── TestPlan becomes immutable
///    ├── ExecutionPlan generated
///    └── Locked relationship established
///
/// 4. Execution:
///    ExecutionController.execute(executionPlan)
///    ├── Follow atomic steps
///    ├── Respect barriers
///    ├── Track progress
///    └── Produce results
/// ```
///
/// ## Immutability Principle
///
/// Once committed, plans are immutable:
///
/// ```
/// TestPlan (mutable during authoring)
///      ↓ commit()
/// TestPlan (immutable) ⟷ ExecutionPlan (immutable)
///      ↑
///      Algebraically locked relationship
///
/// Any change requires:
///   - New TestPlan
///   - Re-validation
///   - New ExecutionPlan
///   - New version number
/// ```
///
/// ## Example: Simple Study
///
/// ```java
/// // 1. Define axes (parameter dimensions)
/// Axis<String> modelAxis = Axis.discrete("model", "gpt-4", "claude-3");
/// Axis<Double> tempAxis = Axis.range("temperature", 0.0, 1.0, 0.1);
///
/// // 2. Define elements (resources)
/// Element apiService = Element.service("llm-api")
///     .withEndpoint("https://api.example.com")
///     .build();
///
/// Element cache = Element.cache("response-cache")
///     .withCapacity("10GB")
///     .build();
///
/// // 3. Create test plan
/// TestPlan plan = TestPlan.builder()
///     .name("llm-temperature-study")
///     .withAxis(modelAxis)
///     .withAxis(tempAxis)
///     .withElement(apiService)
///     .withElement(cache)
///     .relationship(apiService, cache, RelationshipType.SHARED)
///     .policies(ExecutionPolicies.defaults())
///     .build();
///
/// // 4. Validate
/// ValidationResult validation = planValidator.validate(plan);
/// if (validation.isFailed()) {
///     throw new InvalidPlanException(validation);
/// }
///
/// // 5. Commit (becomes immutable)
/// ExecutionPlan execPlan = plan.commit();
///
/// // 6. Execute
/// Run run = executionController.startRun(execPlan);
/// ```
///
/// ## Example: Complex Dependencies
///
/// ```java
/// // Elements with complex relationships
/// Element database = Element.database("postgres");
/// Element appServer = Element.service("app-server");
/// Element loadBalancer = Element.service("load-balancer");
///
/// TestPlan plan = TestPlan.builder()
///     .name("scaling-study")
///     // ... axes ...
///     .withElement(database)
///     .withElement(appServer)
///     .withElement(loadBalancer)
///     // Database cannot be shared by concurrent trials
///     .relationship(database, appServer, RelationshipType.MUTUALLY_EXCLUSIVE)
///     // Load balancer can be shared
///     .relationship(loadBalancer, appServer, RelationshipType.SHARED)
///     // App server instanced per trial
///     .instancingScope(appServer, InstancingScope.PER_TRIAL)
///     .build();
///
/// // Compiler inserts barriers to enforce mutual exclusion
/// ExecutionPlan execPlan = plan.commit();
/// List<Barrier> barriers = execPlan.barriers();
/// // Barriers ensure database not accessed concurrently
/// ```
///
/// @see TestPlan
/// @see ExecutionPlan
/// @see Axis
/// @see Element
/// @see RelationshipType
/// @see com.paramodel.api.compilation.PlanCompiler
/// @since 0.1.0
///
package io.nosqlbench.paramodel.plan;
