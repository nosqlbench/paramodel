<!--
  ~ Licensed to the Apache Software Foundation (ASF) under one
  ~ or more contributor license agreements.  See the NOTICE file
  ~ distributed with this work for additional information
  ~ regarding copyright ownership.  The ASF licenses this file
  ~ to you under the Apache License, Version 2.0 (the
  ~ "License"); you may not use this file except in compliance
  ~ with the License.  You may obtain a copy of the License at
  ~
  ~   http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing,
  ~ software distributed under the License is distributed on an
  ~ "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  ~ KIND, either express or implied.  See the License for the
  ~ specific language governing permissions and limitations
  ~ under the License.
  -->

# Contract Types

Detailed specification of each major contract interface in Paramodel. Every
contract listed here is defined in `paramodel-api` and implemented by
`paramodel-mock` (for testing) and `paramodel-engine` (for production use).

For a package-level overview, see [API Packages](api-packages.md). For term
definitions, see [Glossary](glossary.md).

---

## Parameter Contracts

### `Parameter<T>`

**Package:** `io.nosqlbench.paramodel.parameters`

**Responsibility:** A testable parameter dimension representing one variable in a
parameter space. Defines the name, domain, constraints, and value generation
capabilities.

```java
public interface Parameter<T> extends Tagged {
    String name();
    Domain<T> domain();
    T generate();
    T generateBoundary();
    T generateRandom();
    ValidationResult validate(T value);
    boolean satisfies(Constraint<T> constraint);
    Map<String, String> tags();
}
```

---

### `DerivedParameter<T>`

**Package:** `io.nosqlbench.paramodel.parameters`

**Responsibility:** A parameter whose value is computed from other bound parameter
values. Evaluated after independent parameters are bound.

```java
public interface DerivedParameter<T> extends Parameter<T> {
    T compute(Map<String, Object> boundValues);
    String expression();
}
```

**Contract Requirements:**
- `compute()` MUST be deterministic and MUST NOT modify the input map.
- Result MUST be within the parameter's domain.

---

### `BindingNode`

**Package:** `io.nosqlbench.paramodel.parameters`

**Responsibility:** A node in the hierarchical element binding tree representing an
element instance and its parameter scope.

```java
public interface BindingNode extends Tagged {
    Optional<Element> element();
    ParameterBinding binding();
    Map<String, Object> cascadedInputs();
    Map<String, Object> localInputs();
    List<BindingNode> parents();
    Map<String, BindingNode> children();
    int depth();
    boolean isRoot();
}
```

**Contract Requirements:**
- Generated values MUST be within the declared domain.
- `validate()` MUST accurately check all constraints.
- Parameter definitions MUST be immutable after creation.
- All methods MUST be thread-safe.

**Typed Specializations** (in `io.nosqlbench.paramodel.parameters.types`):
`IntegerParameter`, `DoubleParameter`, `BooleanParameter`, `SelectionParameter`.

---

### `Domain<T>`

**Package:** `io.nosqlbench.paramodel.parameters`

**Responsibility:** Defines the mathematical set of all valid values for a parameter.
Sealed interface with four permitted subtypes.

```java
public sealed interface Domain<T>
    permits Domain.Discrete, Domain.Range, Domain.Composite, Domain.Custom {

    boolean contains(T value);
    Optional<Long> cardinality();
    T sample(Random rng);
    Iterator<T> enumerate();
    Set<T> boundaryValues();

    non-sealed interface Discrete<T> extends Domain<T> {
        Set<T> values();
    }

    non-sealed interface Range<T extends Comparable<T>> extends Domain<T> {
        T min();
        T max();
    }

    non-sealed interface Composite<T> extends Domain<T> {
        Map<String, Domain<?>> fields();
    }

    non-sealed interface Custom<T> extends Domain<T> {
        Predicate<T> membership();
        String description();
    }
}
```

**Contract Requirements:**
- `contains()` MUST return consistent results for the same value.
- `cardinality()` MUST return an accurate count when finite, `Optional.empty()` when infinite.
- `enumerate()` MUST throw `UnsupportedOperationException` for non-enumerable domains.
- `boundaryValues()` MUST return extrema appropriate to the domain type.

---

### `Constraint<T>`

**Package:** `io.nosqlbench.paramodel.parameters`

**Responsibility:** Boolean predicate over values with algebraic composition operators.
Functional interface enabling lambda syntax.

```java
@FunctionalInterface
public interface Constraint<T> {
    boolean test(T value);
    default Constraint<T> and(Constraint<? super T> other);
    default Constraint<T> or(Constraint<? super T> other);
    default Constraint<T> negate();
    default String description();
}
```

**Algebraic Laws:** Associativity, commutativity, distributivity, identity, idempotence,
absorption, De Morgan's laws, and double negation all hold for the composition operators.

**Contract Requirements:**
- `test()` MUST be a pure function with no side effects.
- Same input MUST always produce the same output.
- All methods MUST be thread-safe.

---

### `Value<T>`

**Package:** `io.nosqlbench.paramodel.parameters`

**Responsibility:** Wraps a concrete parameter value with provenance metadata for
traceability.

```java
public interface Value<T> {
    T value();
    String parameterName();
    Instant generatedAt();
    Optional<String> generatorMetadata();
    ValidationResult validate(Constraint<T> constraint);
    String fingerprint();
}
```

**Contract Requirements:**
- Values MUST be immutable.
- `fingerprint()` MUST use SHA-256 or stronger, producing a deterministic hex string.
- `parameterName()` MUST match the originating `Parameter.name()`.

---

### `ValidationResult`

**Package:** `io.nosqlbench.paramodel.parameters`

**Responsibility:** Captures the outcome of validation with three possible states.

```java
public sealed interface ValidationResult
    permits ValidationResult.Passed, ValidationResult.Failed, ValidationResult.Warning {

    boolean isPassed();
    boolean isFailed();
    Optional<String> message();
    List<String> violations();

    record Passed() implements ValidationResult { ... }
    record Failed(String msg, List<String> violations) implements ValidationResult { ... }
    record Warning(String msg, ValidationResult underlying) implements ValidationResult { ... }
}
```

**Contract Requirements:**
- Results MUST be immutable.
- `Failed.msg` MUST NOT be null.
- `Warning` flattens nested warnings (no `Warning(Warning(...))` nesting).

---

### `Tagged`

**Package:** `io.nosqlbench.paramodel.parameters`

**Responsibility:** Base interface for consistently named and tagged entities.

```java
public interface Tagged {
    String name();
    Map<String, String> tags();
}
```

**Contract Requirements:**
- `tags()` MUST contain a `"name"` key equal to `name()`.
- The returned map MUST be unmodifiable.

---

## Sequence Contracts

### `Trial`

**Package:** `io.nosqlbench.paramodel.sequence`

**Responsibility:** A single point in the parameter space -- one complete set of
parameter assignments that can be executed to produce results.

```java
public interface Trial {
    String id();
    Map<String, Value<?>> assignments();
    Optional<Value<?>> assignment(String parameterName);
    List<Constraint<Map<String, Value<?>>>> constraints();
    ValidationResult validate();
    Optional<TrialMetadata> metadata();

    interface TrialMetadata {
        Optional<Integer> sequenceIndex();
        Optional<String> group();
        Optional<String> generationMethod();
        Optional<Integer> priority();
    }
}
```

**Contract Requirements:**
- Every trial MUST have a value for every parameter (complete assignment).
- `id()` MUST be unique within a sequence.
- `validate()` MUST check assignment completeness, individual values, and cross-parameter constraints.

---

### `Sequence`

**Package:** `io.nosqlbench.paramodel.sequence`

**Responsibility:** Ordered, immutable collection of trials representing a systematic
exploration of a parameter space.

```java
public interface Sequence extends Iterable<Trial> {
    List<Trial> trials();
    default int size();
    default boolean isEmpty();
    ValidationResult validate();
    Iterator<Trial> iterator();
}
```

**Contract Requirements:**
- `trials()` MUST return the same unmodifiable list on every call.
- `iterator()` MUST produce trials in the same order as `trials()`.
- Multiple independent iterators MUST be supported.

---

### `TrialBuilder`

**Package:** `io.nosqlbench.paramodel.sequence`

**Responsibility:** Fluent builder for constructing `Trial` instances.

### `SequenceBuilder`

**Package:** `io.nosqlbench.paramodel.sequence`

**Responsibility:** Fluent builder for constructing `Sequence` instances with
configurable generation strategies.

### `TrialStatus`

**Package:** `io.nosqlbench.paramodel.sequence`

**Responsibility:** Enum representing trial execution lifecycle states.

### `TrialResult`

**Package:** `io.nosqlbench.paramodel.sequence`

**Responsibility:** Trial outcome capturing metrics, artifacts, timing, provenance
envelope, and error information.

---

## Plan Contracts

### `TestPlan`

**Package:** `io.nosqlbench.paramodel.plan`

**Responsibility:** User-authored declarative specification of a study. Defines WHAT
to test (axes, elements, relationships, policies) before compilation resolves HOW.

```java
public interface TestPlan {
    String name();
    List<Axis<?>> axes();
    default Optional<Axis<?>> axis(String name);
    List<Element> elements();
    default Optional<Element> element(String name);
    Map<ElementPair, RelationshipType> relationships();
    Optional<RelationshipType> relationshipBetween(Element e1, Element e2);
    ExecutionPolicies policies();
    OptimizationStrategy optimizationStrategy();
    long trialSpaceSize();
    boolean isCommitted();
    ValidationResult validate();
    TestPlan reorderAxes(List<String> axisNames);
    ExecutionPlan commit();
    TestPlanMetadata metadata();
}
```

**Contract Requirements:**
- Before `commit()`: mutable. After `commit()`: immutable.
- `reorderAxes()` MUST throw `IllegalStateException` if already committed.
- `commit()` MUST validate, then lock the plan and produce an `ExecutionPlan`.
- `trialSpaceSize()` equals the product of all axis cardinalities.

---

### `ExecutionPlan`

**Package:** `io.nosqlbench.paramodel.plan`

**Responsibility:** Compiled, immutable, deterministic execution plan derived from a
committed `TestPlan`. Contains all steps, barriers, the execution graph, and
checkpoint support.

```java
public interface ExecutionPlan {
    String id();
    String testPlanFingerprint();
    List<AtomicStep> steps();
    List<Barrier> barriers();
    ExecutionGraph executionGraph();
    TrialOrdering trialOrdering();
    Optional<Duration> estimatedDuration();
    int estimatedMaxParallelism();
    ResourceRequirements resourceRequirements();
    Optional<CheckpointStrategy> checkpointStrategy();
    Optional<Checkpoint> latestCheckpoint();
    List<Checkpoint> checkpoints();
    ExecutionResults execute() throws ExecutionException;
    ExecutionResults execute(ExecutionObserver observer) throws ExecutionException;
    ExecutionResults executeWithCheckpoints(Duration interval) throws ExecutionException;
    ExecutionPlan resumeFrom(Checkpoint checkpoint);
    ExecutionPlan withMaxConcurrency(int maxConcurrency);
    ExecutionPlanMetadata metadata();
}
```

**Contract Requirements:**
- Deeply immutable.
- Same `TestPlan` MUST produce equivalent `ExecutionPlan` on repeated `commit()`.
- Execution graph MUST be acyclic.
- All barriers MUST have finite dependency sets.

---

### `ExecutionGraph`

**Package:** `io.nosqlbench.paramodel.plan`

**Responsibility:** DAG of atomic steps exposing critical path analysis, parallelism
metrics, topological ordering, resource-constrained scheduling, and subgraph extraction.

```java
public interface ExecutionGraph {
    List<AtomicStep> steps();
    List<Edge> edges();
    Optional<AtomicStep> findStep(String stepId);
    Set<AtomicStep> dependencies(AtomicStep step);
    Set<AtomicStep> transitiveDependencies(AtomicStep step);
    Set<AtomicStep> dependents(AtomicStep step);
    Set<AtomicStep> transitiveDependents(AtomicStep step);
    List<AtomicStep> criticalPath();
    Duration criticalPathDuration();
    Duration totalDuration();
    List<AtomicStep> topologicalSort();
    Map<Integer, List<AtomicStep>> parallelWaves();
    int maximumParallelism();
    double averageParallelism();
    boolean canExecuteConcurrently(AtomicStep step1, AtomicStep step2);
    Schedule computeSchedule(ResourceLimits limits);
    ExecutionGraph subgraph(Set<String> stepIds);
    ExecutionGraph subgraphForElement(String elementId);
    ExecutionGraph subgraphForTrials(List<String> trialIds);
    boolean isAcyclic();
    GraphStatistics statistics();
}
```

---

### `AtomicStep`

**Package:** `io.nosqlbench.paramodel.plan`

**Responsibility:** Sealed interface representing an indivisible unit of work. Five
permitted record types cover the complete execution lifecycle.

```java
public sealed interface AtomicStep
    permits AtomicStep.DeployElement, AtomicStep.ExecuteTrial,
            AtomicStep.TeardownElement, AtomicStep.BarrierSync,
            AtomicStep.CheckpointState {

    String id();
    StepType type();
    String description();
    List<String> dependencies();
    Optional<Duration> estimatedDuration();
    ResourceRequirements resourceRequirements();
    Optional<RetryPolicy> retryPolicy();
    Map<String, Object> metadata();
    StepResult execute(ExecutionContext context) throws StepExecutionException;
}
```

**Step Types:**
- `DeployElement` -- provisions infrastructure and runs health checks.
- `ExecuteTrial` -- executes a trial with bound element instances.
- `TeardownElement` -- cleans up resources and collects artifacts.
- `BarrierSync` -- synchronization point waiting for dependencies.
- `CheckpointState` -- persists execution state for resumability.

---

### `Barrier`

**Package:** `io.nosqlbench.paramodel.plan`

**Responsibility:** Synchronization primitive coordinating concurrent execution by
enforcing dependency relationships.

```java
public interface Barrier {
    String id();
    BarrierType type();
    String description();
    List<String> dependencies();
    List<String> dependentSteps();
    Optional<Duration> timeout();
    TimeoutAction timeoutAction();
    BarrierState state();
    Set<String> satisfiedDependencies();
    Set<String> pendingDependencies();
    Instant createdAt();
    Optional<Instant> satisfiedAt();
    Optional<Duration> waitDuration();
    Map<String, Object> metadata();
    boolean isSatisfied();
    boolean isFailed();
    boolean isTimedOut();
    void await() throws InterruptedException, BarrierException;
    boolean await(Duration timeout) throws InterruptedException, BarrierException;
    void release();
    void fail(String reason);
}
```

**Barrier Types:** `ELEMENT_READY`, `ELEMENT_SCOPE_END`, `TRIAL_BATCH`,
`CHECKPOINT_BOUNDARY`, `CUSTOM`.

---

### `Axis<T>`

**Package:** `io.nosqlbench.paramodel.plan`

**Responsibility:** Named parameter dimension in a study with an ordered list of
discrete values.

```java
public interface Axis<T> extends Tagged {
    String name();
    Map<String, String> tags();
    List<T> values();
    default int cardinality();
    List<T> boundaryValues();
    Optional<String> description();
    Optional<Parameter<T>> underlyingParameter();
    default boolean contains(T value);
    default int indexOf(T value);
}
```

---

### `TrialOrdering`

**Package:** `io.nosqlbench.paramodel.plan`

**Responsibility:** Defines strategies for ordering trials within an execution plan.

```java
public interface TrialOrdering {
    TrialOrdering SEQUENTIAL = ...;
    TrialOrdering EDGE_FIRST = ...;
    TrialOrdering DEPENDENCY_OPTIMIZED = ...;
    TrialOrdering COST_OPTIMIZED = ...;

    List<Trial> order(List<Trial> trials);
    String description();

    static TrialOrdering shuffled();
    static TrialOrdering shuffled(long seed);
    static TrialOrdering custom(Comparator<Trial> comparator);
    static TrialOrdering custom(Comparator<Trial> comparator, String description);
}
```

---

### `OptimizationStrategy`

**Package:** `io.nosqlbench.paramodel.plan`

**Responsibility:** Enum controlling compiler optimization aggressiveness.

Values: `NONE`, `BASIC`, `PRUNE_REDUNDANT`, `AGGRESSIVE`.

---

### `ExecutionPolicies`

**Package:** `io.nosqlbench.paramodel.plan.policies`

**Responsibility:** Container for retry, timeout, checkpointing, and intervention
policies that govern runtime behavior.

---

## Compilation Contracts

### `Compiler`

**Package:** `io.nosqlbench.paramodel.compilation`

**Responsibility:** Transforms a `TestPlan` into an `ExecutionPlan` through an
8-stage pipeline of validation, normalization, enumeration, and optimization.

```java
public interface Compiler {
    static Compiler create();
    static Compiler create(CompilerOptions options);
    ValidationResult validate(TestPlan testPlan);
    CompilationResult compile(TestPlan testPlan);
    CompilationResult compileIncremental(TestPlan modified, ExecutionPlan previous);
    CompilerOptions options();
    String version();
}
```

See [Compilation Stages](compilation-stages.md) for details on the 8-stage pipeline.

---

### `CompilationStage`

**Package:** `io.nosqlbench.paramodel.compilation`

**Responsibility:** Individual stage in the compiler pipeline.

```java
public interface CompilationStage {
    String name();
    default String description();
    void execute(CompilationContext context);
    default List<String> dependencies();
    default boolean canSkip(CompilationContext context);
    default Optional<Duration> estimatedDuration(CompilationContext context);
}
```

---

### `CompilationContext`

**Package:** `io.nosqlbench.paramodel.compilation`

**Responsibility:** Shared mutable state passed between compilation stages. Holds
the test plan, intermediate results, errors, warnings, and timing metrics.

---

### `OptimizationPass`

**Package:** `io.nosqlbench.paramodel.compilation`

**Responsibility:** Individual optimization applied during the optimization stage
of compilation (barrier coalescing, step fusion, redundancy elimination, etc.).

---

## Execution Contracts

### `Executor`

**Package:** `io.nosqlbench.paramodel.execution`

**Responsibility:** Orchestrates execution of an `ExecutionPlan` including resource
provisioning, concurrency management, failure handling, and checkpointing.

```java
public interface Executor {
    static Executor create();
    static Executor create(ExecutorConfig config);
    ExecutionResult execute(ExecutionPlan plan) throws ExecutionFailedException;
    ExecutionHandle executeAsync(ExecutionPlan plan);
    ExecutionResult resume(ExecutionPlan plan, Checkpoint checkpoint) throws ExecutionFailedException;
    Optional<Checkpoint> latestCheckpoint(ExecutionPlan plan);
    List<Checkpoint> checkpoints(ExecutionPlan plan);
    ExecutorConfig config();
}
```

---

### `Runtime`

**Package:** `io.nosqlbench.paramodel.execution`

**Responsibility:** Provides runtime services for deploying elements, executing
trials, managing resources, and collecting metrics.

```java
public interface Runtime {
    static Runtime create();
    ElementInstance deploy(DeploymentRequest request) throws DeploymentException;
    void awaitReady(ElementInstance instance, Duration timeout) throws TimeoutException;
    HealthStatus checkHealth(ElementInstance instance);
    void restart(ElementInstance instance) throws DeploymentException;
    void teardown(ElementInstance instance, boolean collectArtifacts);
    TrialResult executeTrial(TrialExecutionRequest request) throws TrialExecutionException;
    ResourceAvailability availableResources();
    ResourceAllocation allocateResources(Resources resources) throws InsufficientResourcesException;
    void releaseResources(ResourceAllocation allocation);
    MetricsCollector metricsCollector();
    RuntimeConfig config();
}
```

---

### `Scheduler`

**Package:** `io.nosqlbench.paramodel.execution`

**Responsibility:** Determines execution order and timing of atomic steps, managing
dependency resolution, resource-aware admission control, and concurrency.

```java
public interface Scheduler {
    static Scheduler create();
    static Scheduler create(SchedulingPolicy policy);
    static Scheduler create(SchedulerConfig config);
    void initialize(ExecutionGraph graph);
    List<AtomicStep> nextSteps();
    List<AtomicStep> nextSteps(Runtime.ResourceAvailability available);
    Optional<AtomicStep> nextStep(int workerId);
    void markStarted(AtomicStep step, Instant startTime);
    void markCompleted(AtomicStep step);
    void markFailed(AtomicStep step, Throwable error);
    void setPriority(AtomicStep step, Priority priority);
    Priority getPriority(AtomicStep step);
    boolean isComplete();
    SchedulerState state();
    SchedulerStatistics statistics();
}
```

---

### `ResourceManager`

**Package:** `io.nosqlbench.paramodel.execution`

**Responsibility:** Resource allocation, tracking, and release. Enforces resource
limits and performs admission control.

---

### `ArtifactCollector`

**Package:** `io.nosqlbench.paramodel.execution`

**Responsibility:** Captures artifacts (logs, metrics, files) from element instances
and trial executions for later analysis and provenance.

---

## Persistence Contracts

### `ResultStore`

**Package:** `io.nosqlbench.paramodel.persistence`

**Responsibility:** Persists structured trial metrics with provenance metadata.
Supports query by trial ID, run ID, and parameter values.

### `ExecutionRepository`

**Package:** `io.nosqlbench.paramodel.persistence`

**Responsibility:** Stores and retrieves execution plans, runs, and their
relationships. Tracks plan lineage and version history.

### `CheckpointStore`

**Package:** `io.nosqlbench.paramodel.persistence`

**Responsibility:** Persists and retrieves execution checkpoints to enable
resumability after interruption.

### `ArtifactStore`

**Package:** `io.nosqlbench.paramodel.persistence`

**Responsibility:** Stores unstructured files associated with trials: logs, model
outputs, dataset snapshots, configuration dumps.

### `MetadataStore`

**Package:** `io.nosqlbench.paramodel.persistence`

**Responsibility:** Stores and queries metadata about plans, runs, and trials.
Supports tagging, search, and filtering.

---

## Security Contracts

### `AccessControl`

**Package:** `io.nosqlbench.paramodel.security`

**Responsibility:** Enforces multi-user permissions and sharing semantics using
role-based or attribute-based access control.

### `AuditLog`

**Package:** `io.nosqlbench.paramodel.security`

**Responsibility:** Records security-relevant events (authentication, authorization
decisions, data access) for compliance and debugging.

### `CredentialManager`

**Package:** `io.nosqlbench.paramodel.security`

**Responsibility:** Manages credentials for accessing external resources such as
databases, APIs, and cloud services. Supports rotation and secure storage.

---

## Utility Contracts

### `ConfigurationManager`

**Package:** `io.nosqlbench.paramodel.util`

**Responsibility:** Manages configuration loading from files, environment variables,
and programmatic sources. Supports merging and validation.

### `SerializationUtil`

**Package:** `io.nosqlbench.paramodel.util`

**Responsibility:** Serialization and deserialization utilities for plans, results,
and metadata across formats (JSON, YAML).

### `ValidationUtil`

**Package:** `io.nosqlbench.paramodel.util`

**Responsibility:** Reusable validation helpers for common patterns such as range
checks, non-null assertions, and collection invariants.

---

## See Also

- [API Packages](api-packages.md) -- package-level overview
- [Compilation Stages](compilation-stages.md) -- the 8-stage pipeline
- [Glossary](glossary.md) -- term definitions
