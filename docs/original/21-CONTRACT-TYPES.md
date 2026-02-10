# Contract Types Specification

**Related**: [40-JAVA-IMPLEMENTATION.md](40-JAVA-IMPLEMENTATION.md) • [41-RUST-IMPLEMENTATION.md](41-RUST-IMPLEMENTATION.md) • [20-ARCHITECTURE.md](20-ARCHITECTURE.md)

This document specifies all essential contract types (interfaces/traits) in the paramodel framework and Simplica application. These contracts define the API surface that all conforming implementations must provide.

## Contract-First Principle

Paramodel and Simplica are **contract-first systems**. This means:

1. **Types are specified before implementation** as interfaces (Java) or traits (Rust)
2. **Behavior is defined by contracts**, not implementation details
3. **Multiple implementations** can exist as long as they satisfy contracts
4. **Semantic equivalence** is maintained across languages
5. **Testing validates contracts**, not internal implementation

## Paramodel Core Contracts

These contracts form the foundational layer of the framework.

### Parameter\<T\>

**Responsibility**: Represent a testable parameter dimension with domain, constraints, and value generation.

**Key Operations**:

```java
// Java
interface Parameter<T> {
    // Identity
    String name();
    
    // Domain access
    Domain<T> domain();
    
    // Value generation
    T generate();
    T generateBoundary();
    T generateRandom();
    
    // Validation
    ValidationResult validate(T value);
    boolean satisfies(Constraint<T> constraint);
    
    // Metadata
    ParameterMetadata metadata();
}
```

```rust
// Rust
trait Parameter<T> {
    fn name(&self) -> &str;
    fn domain(&self) -> &Domain<T>;
    fn generate(&mut self) -> T;
    fn generate_boundary(&mut self) -> T;
    fn generate_random(&mut self) -> T;
    fn validate(&self, value: &T) -> ValidationResult;
    fn satisfies(&self, constraint: &dyn Constraint<T>) -> bool;
    fn metadata(&self) -> &ParameterMetadata;
}
```

**Algebraic Laws**: See [11-ALGEBRAIC-LAWS.md](11-ALGEBRAIC-LAWS.md)

---

### Domain\<T\>

**Responsibility**: Specify the valid value space for a parameter.

**Key Operations**:

```java
// Java
sealed interface Domain<T> {
    boolean contains(T value);
    Optional<Long> cardinality();
    T sample(Random rng);
    Iterator<T> enumerate(); // for finite domains
    Set<T> boundaryValues();
}
```

```rust
// Rust
trait Domain<T> {
    fn contains(&self, value: &T) -> bool;
    fn cardinality(&self) -> Option<usize>;
    fn sample(&self, rng: &mut impl Rng) -> T;
    fn enumerate(&self) -> Box<dyn Iterator<Item = T>>; // for finite
    fn boundary_values(&self) -> Vec<T>;
}
```

**Domain Variants**:
- `Discrete<T>`: Finite set of values
- `Range<T>`: Min/max bounds for ordered types
- `Composite<T>`: Structured with named fields
- `Custom<T>`: User-defined membership predicate

---

### Constraint\<T\>

**Responsibility**: Express predicates that parameter values must satisfy.

**Key Operations**:

```java
// Java
@FunctionalInterface
interface Constraint<T> {
    boolean test(T value);
    
    // Algebraic composition
    default Constraint<T> and(Constraint<T> other);
    default Constraint<T> or(Constraint<T> other);
    default Constraint<T> negate();
    
    // Metadata
    default String description();
}
```

```rust
// Rust
trait Constraint<T> {
    fn test(&self, value: &T) -> bool;
    fn and<C: Constraint<T>>(self, other: C) -> And<Self, C> where Self: Sized;
    fn or<C: Constraint<T>>(self, other: C) -> Or<Self, C> where Self: Sized;
    fn negate(self) -> Not<Self> where Self: Sized;
    fn description(&self) -> &str;
}
```

**Algebraic Structure**: Boolean algebra (AND, OR, NOT, identity)

---

### Value\<T\>

**Responsibility**: Wrap a parameter value with provenance metadata.

**Key Operations**:

```java
// Java
record Value<T>(
    T value,
    String parameterName,
    Instant generatedAt,
    Optional<String> generatorMetadata
) {
    ValidationResult validate(Constraint<T> constraint);
    String fingerprint(); // cryptographic hash
}
```

```rust
// Rust
struct Value<T> {
    value: T,
    parameter_name: String,
    generated_at: Instant,
    generator_metadata: Option<String>,
}

impl<T> Value<T> {
    fn validate(&self, constraint: &dyn Constraint<T>) -> ValidationResult;
    fn fingerprint(&self) -> String; // cryptographic hash
}
```

---

### Sequence

**Responsibility**: Represent an ordered collection of trials with validation.

**Key Operations**:

```java
// Java
interface Sequence {
    List<Trial> trials();
    ValidationResult validate();
    SequenceMetadata metadata();
    Iterator<Trial> iterator();
    int size();
}
```

```rust
// Rust
trait Sequence {
    fn trials(&self) -> &[Trial];
    fn validate(&self) -> ValidationResult;
    fn metadata(&self) -> &SequenceMetadata;
    fn iter(&self) -> impl Iterator<Item = &Trial>;
    fn size(&self) -> usize;
}
```

---

### SequenceBuilder

**Responsibility**: Fluent API for constructing sequences.

**Key Operations**:

```java
// Java
interface SequenceBuilder {
    SequenceBuilder withParameter(Parameter<?> param);
    SequenceBuilder generateRandom(int count);
    SequenceBuilder generateExhaustive();
    SequenceBuilder generatePairwise();
    SequenceBuilder generateBoundary();
    SequenceBuilder constraint(Constraint<?> constraint);
    Sequence build();
}
```

```rust
// Rust
trait SequenceBuilder {
    fn with_parameter(self, param: Box<dyn Parameter<?>>) -> Self;
    fn generate_random(self, count: usize) -> Self;
    fn generate_exhaustive(self) -> Self;
    fn generate_pairwise(self) -> Self;
    fn generate_boundary(self) -> Self;
    fn constraint(self, constraint: Box<dyn Constraint<?>>) -> Self;
    fn build(self) -> Result<Box<dyn Sequence>, Error>;
}
```

---

### ValidationResult

**Responsibility**: Represent validation outcome with details.

**Key Operations**:

```java
// Java
sealed interface ValidationResult {
    record Passed() implements ValidationResult {}
    record Failed(String message, List<String> violations) implements ValidationResult {}
    record Warning(String message, ValidationResult underlying) implements ValidationResult {}
    
    boolean isPassed();
    boolean isFailed();
    Optional<String> message();
    List<String> violations();
}
```

```rust
// Rust
enum ValidationResult {
    Passed,
    Failed { message: String, violations: Vec<String> },
    Warning { message: String, underlying: Box<ValidationResult> },
}

impl ValidationResult {
    fn is_passed(&self) -> bool;
    fn is_failed(&self) -> bool;
    fn message(&self) -> Option<&str>;
    fn violations(&self) -> &[String];
}
```

## Simplica Application Contracts

These contracts build on paramodel to provide complete study execution.

### TestPlan

**Responsibility**: User-authored declarative study specification.

**Key Operations**:

```java
// Java
interface TestPlan {
    String name();
    List<Axis<?>> axes();
    List<Element> elements();
    Map<String, RelationshipType> relationships();
    ExecutionPolicies policies();
    
    ValidationResult validate();
    TestPlan reorderAxes(List<String> newOrder);
    ExecutionPlan commit(); // locks and compiles
    
    TestPlanMetadata metadata();
}
```

```rust
// Rust
trait TestPlan {
    fn name(&self) -> &str;
    fn axes(&self) -> &[Box<dyn Axis<?>>];
    fn elements(&self) -> &[Element];
    fn relationships(&self) -> &HashMap<String, RelationshipType>;
    fn policies(&self) -> &ExecutionPolicies;
    
    fn validate(&self) -> ValidationResult;
    fn reorder_axes(&self, new_order: &[String]) -> Box<dyn TestPlan>;
    fn commit(self) -> Result<Box<dyn ExecutionPlan>, Error>;
    
    fn metadata(&self) -> &TestPlanMetadata;
}
```

**Immutability**: After `commit()`, TestPlan is immutable and locked to ExecutionPlan.

See [31-TEST-PLANS.md](31-TEST-PLANS.md)

---

### ExecutionPlan

**Responsibility**: Immutable compiled plan with atomic steps and barriers.

**Key Operations**:

```java
// Java
interface ExecutionPlan {
    String name();
    String version();
    TestPlan sourcePlan();
    
    List<AtomicStep> steps();
    Graph<AtomicStep> dependencyGraph();
    List<Barrier> barriers();
    
    TrialOrdering trialOrdering();
    RetryPolicies retryPolicies();
    
    ExecutionPlanMetadata metadata();
    String fingerprint(); // cryptographic hash
}
```

```rust
// Rust
trait ExecutionPlan {
    fn name(&self) -> &str;
    fn version(&self) -> &str;
    fn source_plan(&self) -> &dyn TestPlan;
    
    fn steps(&self) -> &[AtomicStep];
    fn dependency_graph(&self) -> &Graph<AtomicStep>;
    fn barriers(&self) -> &[Barrier];
    
    fn trial_ordering(&self) -> &TrialOrdering;
    fn retry_policies(&self) -> &RetryPolicies;
    
    fn metadata(&self) -> &ExecutionPlanMetadata;
    fn fingerprint(&self) -> String;
}
```

**Immutability**: ExecutionPlan is fully immutable after creation. Any change requires new version.

See [32-EXECUTION-PLANS.md](32-EXECUTION-PLANS.md)

---

### PlanValidator

**Responsibility**: Validate Test Plans and Execution Plans for executability.

**Key Operations**:

```java
// Java
interface PlanValidator {
    ValidationResult validateTestPlan(TestPlan plan);
    ValidationResult validateExecutionPlan(ExecutionPlan plan);
    List<ValidationIssue> checkSchedulability(TestPlan plan);
    List<ValidationIssue> checkResourceConstraints(TestPlan plan);
    List<ValidationIssue> checkAmbiguities(TestPlan plan);
}
```

```rust
// Rust
trait PlanValidator {
    fn validate_test_plan(&self, plan: &dyn TestPlan) -> ValidationResult;
    fn validate_execution_plan(&self, plan: &dyn ExecutionPlan) -> ValidationResult;
    fn check_schedulability(&self, plan: &dyn TestPlan) -> Vec<ValidationIssue>;
    fn check_resource_constraints(&self, plan: &dyn TestPlan) -> Vec<ValidationIssue>;
    fn check_ambiguities(&self, plan: &dyn TestPlan) -> Vec<ValidationIssue>;
}
```

---

### PlanCompiler (ExecutionPlanner)

**Responsibility**: Compile Test Plan into Execution Plan with scheduling resolution.

**Key Operations**:

```java
// Java
interface PlanCompiler {
    ExecutionPlan compile(TestPlan testPlan);
    CompilationReport report();
    List<SchedulingDecision> schedulingDecisions();
}
```

```rust
// Rust
trait PlanCompiler {
    fn compile(&mut self, test_plan: Box<dyn TestPlan>) -> Result<Box<dyn ExecutionPlan>, Error>;
    fn report(&self) -> &CompilationReport;
    fn scheduling_decisions(&self) -> &[SchedulingDecision];
}
```

**Compilation Process**:
1. Validate Test Plan
2. Resolve scheduling based on relationship types
3. Insert barriers for resource constraints
4. Generate atomic steps
5. Assign version and fingerprint
6. Lock Test Plan ↔ Execution Plan relationship

---

### Scheduler

**Responsibility**: Interpret Execution Plan schedule semantics at runtime.

**Key Operations**:

```java
// Java
interface Scheduler {
    List<AtomicStep> nextReadySteps();
    void markCompleted(AtomicStep step);
    void applyBarriers(List<Barrier> barriers);
    boolean allCompleted();
    ScheduleState state();
}
```

```rust
// Rust
trait Scheduler {
    fn next_ready_steps(&mut self) -> Vec<&AtomicStep>;
    fn mark_completed(&mut self, step: &AtomicStep);
    fn apply_barriers(&mut self, barriers: &[Barrier]);
    fn all_completed(&self) -> bool;
    fn state(&self) -> &ScheduleState;
}
```

**Constraint**: Scheduler must NOT rewrite or reinterpret plan meaning. It only interprets.

---

### TrialExecutor

**Responsibility**: Execute a single trial including orchestration and retry logic.

**Key Operations**:

```java
// Java
interface TrialExecutor {
    TrialResult runTrial(Trial trial);
    TrialResult retryTrialAction(Trial trial, int attemptNumber);
    void setupTrial(Trial trial);
    void teardownTrial(Trial trial);
}
```

```rust
// Rust
trait TrialExecutor {
    fn run_trial(&mut self, trial: &Trial) -> TrialResult;
    fn retry_trial_action(&mut self, trial: &Trial, attempt_number: usize) -> TrialResult;
    fn setup_trial(&mut self, trial: &Trial) -> Result<(), Error>;
    fn teardown_trial(&mut self, trial: &Trial) -> Result<(), Error>;
}
```

---

### ResourceOrchestrator

**Responsibility**: Manage element lifecycles across trial boundaries.

**Key Operations**:

```java
// Java
interface ResourceOrchestrator {
    void startElement(Element element);
    void stopElement(Element element);
    HealthStatus healthStatus(Element element);
    List<Element> activeElements();
    void enforceResourceConstraints(List<Constraint<?>> constraints);
}
```

```rust
// Rust
trait ResourceOrchestrator {
    fn start_element(&mut self, element: &Element) -> Result<(), Error>;
    fn stop_element(&mut self, element: &Element) -> Result<(), Error>;
    fn health_status(&self, element: &Element) -> HealthStatus;
    fn active_elements(&self) -> Vec<&Element>;
    fn enforce_resource_constraints(&mut self, constraints: &[Box<dyn Constraint<?>>]);
}
```

---

### ResultStore

**Responsibility**: Persist structured trial results with provenance.

**Key Operations**:

```java
// Java
interface ResultStore {
    void writeTrialResult(TrialResult result);
    Optional<TrialResult> readTrialResult(String trialId);
    List<TrialResult> queryResults(ResultQuery query);
    void export(ExportFormat format, Path destination);
}
```

```rust
// Rust
trait ResultStore {
    fn write_trial_result(&mut self, result: &TrialResult) -> Result<(), Error>;
    fn read_trial_result(&self, trial_id: &str) -> Option<TrialResult>;
    fn query_results(&self, query: &ResultQuery) -> Vec<TrialResult>;
    fn export(&self, format: ExportFormat, destination: &Path) -> Result<(), Error>;
}
```

**Formats**: JSON, JSONL, YAML (minimum)

See [50-RESULT-PERSISTENCE.md](50-RESULT-PERSISTENCE.md)

---

### ExecutionController

**Responsibility**: Run control with user intervention support.

**Key Operations**:

```java
// Java
interface ExecutionController {
    RunHandle startRun(ExecutionPlan plan);
    void pause(PauseMode mode); // NOW or AFTER_ACTIVE
    void resume();
    void stop(StopMode mode); // SCRAP or RETAIN_PARTIAL
    RunState state();
}
```

```rust
// Rust
trait ExecutionController {
    fn start_run(&mut self, plan: Box<dyn ExecutionPlan>) -> RunHandle;
    fn pause(&mut self, mode: PauseMode);
    fn resume(&mut self);
    fn stop(&mut self, mode: StopMode);
    fn state(&self) -> &RunState;
}
```

---

### RunStateService

**Responsibility**: Provide real-time execution snapshots.

**Key Operations**:

```java
// Java
interface RunStateService {
    RunSnapshot snapshot();
    List<Element> activeElements();
    StepProgress stepProgress();
    Map<String, TrialStatus> trialStatuses();
}
```

```rust
// Rust
trait RunStateService {
    fn snapshot(&self) -> RunSnapshot;
    fn active_elements(&self) -> Vec<&Element>;
    fn step_progress(&self) -> &StepProgress;
    fn trial_statuses(&self) -> &HashMap<String, TrialStatus>;
}
```

See [51-OBSERVABILITY.md](51-OBSERVABILITY.md)

---

### ProvenanceService

**Responsibility**: Generate and validate provenance metadata.

**Key Operations**:

```java
// Java
interface ProvenanceService {
    ProvenanceEnvelope envelope(TrialResult result);
    String fingerprint(Configuration config);
    boolean validateProvenance(ProvenanceEnvelope envelope);
}
```

```rust
// Rust
trait ProvenanceService {
    fn envelope(&self, result: &TrialResult) -> ProvenanceEnvelope;
    fn fingerprint(&self, config: &Configuration) -> String;
    fn validate_provenance(&self, envelope: &ProvenanceEnvelope) -> bool;
}
```

**Fingerprint Algorithm**: SHA-256 (minimum)

---

### VersionRegistry

**Responsibility**: Persist and resolve plan versions.

**Key Operations**:

```java
// Java
interface VersionRegistry {
    void register(ExecutionPlan plan);
    Optional<ExecutionPlan> resolve(String name, String version);
    List<PlanVersion> lineage(String name);
    String latestVersion(String name);
}
```

```rust
// Rust
trait VersionRegistry {
    fn register(&mut self, plan: Box<dyn ExecutionPlan>) -> Result<(), Error>;
    fn resolve(&self, name: &str, version: &str) -> Option<Box<dyn ExecutionPlan>>;
    fn lineage(&self, name: &str) -> Vec<PlanVersion>;
    fn latest_version(&self, name: &str) -> Option<String>;
}
```

See [61-VERSIONING.md](61-VERSIONING.md)

---

### CostEstimator

**Responsibility**: Infer cost estimates from historical telemetry.

**Key Operations**:

```java
// Java
interface CostEstimator {
    CostEstimate estimate(ExecutionPlan plan);
    Confidence confidence(ExecutionPlan plan);
    Optional<Duration> estimatedDuration(ExecutionPlan plan);
    Optional<ResourceUsage> estimatedResources(ExecutionPlan plan);
}
```

```rust
// Rust
trait CostEstimator {
    fn estimate(&self, plan: &dyn ExecutionPlan) -> CostEstimate;
    fn confidence(&self, plan: &dyn ExecutionPlan) -> Confidence;
    fn estimated_duration(&self, plan: &dyn ExecutionPlan) -> Option<Duration>;
    fn estimated_resources(&self, plan: &dyn ExecutionPlan) -> Option<ResourceUsage>;
}
```

See [52-COST-ESTIMATION.md](52-COST-ESTIMATION.md)

---

### TelemetryRecorder

**Responsibility**: Record operational telemetry for cost estimation.

**Key Operations**:

```java
// Java
interface TelemetryRecorder {
    void recordDuration(String operation, Duration duration);
    void recordResourceUse(String resource, ResourceUsage usage);
    void recordRetry(String operation, int attemptNumber);
    void recordFailure(String operation, FailureReason reason);
}
```

```rust
// Rust
trait TelemetryRecorder {
    fn record_duration(&mut self, operation: &str, duration: Duration);
    fn record_resource_use(&mut self, resource: &str, usage: &ResourceUsage);
    fn record_retry(&mut self, operation: &str, attempt_number: usize);
    fn record_failure(&mut self, operation: &str, reason: &FailureReason);
}
```

## Contract Compliance

### Faithful Implementation Requirements

All implementations must:

1. **Implement all specified operations** with correct signatures
2. **Maintain algebraic laws** (associativity, commutativity, identity)
3. **Preserve immutability** where specified
4. **Support cross-language interoperability** via canonical serialization
5. **Provide equivalent semantics** across Java and Rust

### Testing Contracts

Contract tests should verify:
- Algebraic properties hold
- Immutability is enforced
- Validation catches errors
- Serialization round-trips correctly
- Cross-language equivalence

## Method Name Conventions

Method names in this specification are illustrative. Actual implementations may use language idioms:

**Java**: `camelCase`, builder pattern, Optional<T>  
**Rust**: `snake_case`, Result<T, E>, Option<T>

Semantic behavior must match even if syntax differs.

## Next Steps

- [40-JAVA-IMPLEMENTATION.md](40-JAVA-IMPLEMENTATION.md) - Java interface implementations
- [41-RUST-IMPLEMENTATION.md](41-RUST-IMPLEMENTATION.md) - Rust trait implementations
- [42-INTEROP.md](42-INTEROP.md) - Cross-language serialization protocols
- [20-ARCHITECTURE.md](20-ARCHITECTURE.md) - How contracts compose into system architecture
