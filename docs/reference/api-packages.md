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

# API Packages

Complete package-by-package listing of all contract interfaces defined in `paramodel-api`.
Each package groups related contracts by responsibility. For conceptual background on
how these types fit together, see the [concepts documentation](../concepts/).

---

## 1. `io.nosqlbench.paramodel.parameters`

**Purpose:** Core parameter contracts defining the fundamental building blocks of
parameter spaces -- parameters, domains, constraints, values, and validation.

| Interface | Responsibility |
|-----------|---------------|
| `Parameter<T>` | Testable parameter dimension with domain, constraints, and value generation. |
| `Domain<T>` | Valid value space for a parameter (sealed: `Discrete`, `Range`, `Composite`, `Custom`). |
| `Constraint<T>` | Boolean predicate over values with algebraic composition (`and`, `or`, `negate`). Functional interface. |
| `Value<T>` | Parameter value with provenance metadata (parameter name, timestamp, generator info, fingerprint). |
| `ValidationResult` | Validation outcome (sealed: `Passed`, `Failed`, `Warning`). |
| `Tagged` | Named, tagged entity base interface. |

### Key Method Signatures

**`Parameter<T> extends Tagged`**

```java
String name();
Domain<T> domain();
T generate();
T generateBoundary();
T generateRandom();
ValidationResult validate(T value);
boolean satisfies(Constraint<T> constraint);
```

**`Domain<T>` (sealed)**

```java
boolean contains(T value);
Optional<Long> cardinality();
T sample(Random rng);
Iterator<T> enumerate();
Set<T> boundaryValues();
```

Permitted subtypes:
- `Domain.Discrete<T>` -- finite set of values. Method: `Set<T> values()`.
- `Domain.Range<T extends Comparable<T>>` -- bounded range. Methods: `T min()`, `T max()`.
- `Domain.Composite<T>` -- structured multi-field domain. Method: `Map<String, Domain<?>> fields()`.
- `Domain.Custom<T>` -- predicate-defined domain. Methods: `Predicate<T> membership()`, `String description()`.

**`Constraint<T>` (@FunctionalInterface)**

```java
boolean test(T value);
default Constraint<T> and(Constraint<? super T> other);
default Constraint<T> or(Constraint<? super T> other);
default Constraint<T> negate();
default String description();
```

**`Value<T>`**

```java
T value();
String parameterName();
Instant generatedAt();
Optional<String> generatorMetadata();
ValidationResult validate(Constraint<T> constraint);
String fingerprint();
```

**`ValidationResult` (sealed)**

```java
boolean isPassed();
boolean isFailed();
Optional<String> message();
List<String> violations();
```

Records: `Passed()`, `Failed(String msg, List<String> violations)`, `Warning(String msg, ValidationResult underlying)`.

**`Tagged`**

```java
String name();
Map<String, String> tags();
```

---

## 2. `io.nosqlbench.paramodel.parameters.types`

**Purpose:** Typed parameter contracts providing concrete parameter specializations
for common value types.

| Interface | Responsibility |
|-----------|---------------|
| `IntegerParameter` | Integer parameter (ranges or discrete sets). |
| `DoubleParameter` | Continuous double parameter (ranges). |
| `BooleanParameter` | Boolean parameter (`{true, false}`). |
| `SelectionParameter` | Single or multi-select from string options. |
| `SelectionResolver` | Resolves selection values from external sources. |

These extend `Parameter<T>` with type-specific factory methods:

```java
IntegerParameter.range("threads", 1, 64);
IntegerParameter.of("batch", Set.of(32, 64, 128));
DoubleParameter.range("temperature", 0.0, 1.0);
BooleanParameter.of("enable_cache");
SelectionParameter.of("region", Set.of("us-east-1", "eu-west-1"));
SelectionParameter.external("model", resolver);
```

---

## 3. `io.nosqlbench.paramodel.elements`

**Purpose:** Study element contracts representing deployable resources with lifecycles.

| Type | Responsibility |
|------|---------------|
| `Element` | A deployable resource with lifecycle (deploy, ready, teardown). Extends `Tagged`. |
| `RelationshipType` | Enum defining how elements relate: `MUTUALLY_EXCLUSIVE`, `SHARED`, `INSTANCED_PER`. |

`Element` represents infrastructure components like databases, caches, or application
servers that trials depend on. `RelationshipType` drives the compiler's barrier
placement and concurrency decisions.

---

## 4. `io.nosqlbench.paramodel.sequence`

**Purpose:** Trial and sequence contracts for representing points in parameter space
and ordered collections of those points.

| Interface | Responsibility |
|-----------|---------------|
| `Trial` | Single point in parameter space with assignments, constraints, and validation. |
| `Trial.TrialMetadata` | Metadata about a trial's context (index, group, generation method, priority). |
| `TrialBuilder` | Fluent builder for constructing `Trial` instances. |
| `Sequence` | Ordered, immutable collection of trials. Implements `Iterable<Trial>`. |
| `SequenceBuilder` | Fluent builder for constructing `Sequence` instances. |
| `TrialStatus` | Trial execution lifecycle states. |
| `TrialResult` | Trial outcome with metrics, artifacts, timing, provenance, and error info. |

### Key Method Signatures

**`Trial`**

```java
String id();
Map<String, Value<?>> assignments();
Optional<Value<?>> assignment(String parameterName);
List<Constraint<Map<String, Value<?>>>> constraints();
ValidationResult validate();
Optional<TrialMetadata> metadata();
```

**`Sequence extends Iterable<Trial>`**

```java
List<Trial> trials();
default int size();
default boolean isEmpty();
ValidationResult validate();
Iterator<Trial> iterator();
```

---

## 5. `io.nosqlbench.paramodel.plan`

**Purpose:** Test plan and execution plan contracts -- the central Simplica types
that bridge user intent to compiled execution strategy.

| Type | Responsibility |
|------|---------------|
| `TestPlan` | Declarative study specification (axes, elements, relationships, policies). |
| `TestPlan.ElementPair` | Record: pair of elements for relationship mapping. |
| `TestPlan.TestPlanMetadata` | Nested interface: creation info, author, tags, version. |
| `TestPlanBuilder` | Fluent builder for `TestPlan`. |
| `ExecutionPlan` | Compiled, immutable execution plan with steps, barriers, graph, and checkpoints. |
| `ExecutionPlan.ResourceRequirements` | Record: peak resource needs (CPU, memory, storage, network). |
| `ExecutionPlan.CheckpointStrategy` | Record: checkpoint configuration. |
| `ExecutionPlan.Checkpoint` | Interface: checkpoint state for recovery. |
| `ExecutionPlan.ExecutionObserver` | Interface: callbacks for execution events. |
| `ExecutionPlan.ExecutionResults` | Interface: results from completed execution. |
| `ExecutionPlan.ExecutionException` | Exception class for execution failures. |
| `ExecutionGraph` | DAG of atomic steps with critical path, parallelism, and scheduling analysis. |
| `ExecutionGraph.Edge` | Record: dependency edge (source, target, weight). |
| `ExecutionGraph.ResourceLimits` | Record: resource constraints for scheduling. |
| `ExecutionGraph.Schedule` | Interface: resource-constrained schedule. |
| `ExecutionGraph.GraphStatistics` | Record: graph statistical summary. |
| `AtomicStep` | Sealed interface: indivisible execution unit. |
| `AtomicStep.DeployElement` | Record: step to provision an element. |
| `AtomicStep.ExecuteTrial` | Record: step to run a trial. |
| `AtomicStep.TeardownElement` | Record: step to clean up an element. |
| `AtomicStep.BarrierSync` | Record: synchronization barrier step. |
| `AtomicStep.CheckpointState` | Record: step to persist execution state. |
| `AtomicStep.StepType` | Enum: `DEPLOY_ELEMENT`, `EXECUTE_TRIAL`, `TEARDOWN_ELEMENT`, `BARRIER_SYNC`, `CHECKPOINT_STATE`. |
| `Barrier` | Synchronization primitive with types, states, timeouts, and dependency tracking. |
| `Barrier.BarrierType` | Enum: `ELEMENT_READY`, `ELEMENT_SCOPE_END`, `TRIAL_BATCH`, `CHECKPOINT_BOUNDARY`, `CUSTOM`. |
| `Barrier.BarrierState` | Enum: `PENDING`, `SATISFIED`, `FAILED`, `TIMEOUT`. |
| `Barrier.TimeoutAction` | Enum: `FAIL_FAST`, `SKIP_DEPENDENT`, `WAIT_FOREVER`, `RETRY`. |
| `Axis<T>` | Named parameter dimension with ordered values and boundary identification. |
| `TrialOrdering` | Trial ordering strategies with built-in constants and factory methods. |
| `OptimizationStrategy` | Enum: `NONE`, `BASIC`, `PRUNE_REDUNDANT`, `AGGRESSIVE`. |
| `ExecutionPlanMetadata` | Compilation version, compiled timestamp, fingerprint, optimization metrics. |
| `TestPlanMetadata` | Creation info, author, tags. |

### Key Method Signatures

**`TestPlan`**

```java
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
```

**`ExecutionPlan`**

```java
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
```

**`Axis<T> extends Tagged`**

```java
String name();
Map<String, String> tags();
List<T> values();
default int cardinality();
List<T> boundaryValues();
Optional<String> description();
Optional<Parameter<T>> underlyingParameter();
default boolean contains(T value);
default int indexOf(T value);
```

**`TrialOrdering`**

```java
List<Trial> order(List<Trial> trials);
String description();
static TrialOrdering shuffled();
static TrialOrdering shuffled(long seed);
static TrialOrdering custom(Comparator<Trial> comparator);
```

Constants: `SEQUENTIAL`, `EDGE_FIRST`, `DEPENDENCY_OPTIMIZED`, `COST_OPTIMIZED`.

---

## 6. `io.nosqlbench.paramodel.plan.policies`

**Purpose:** Execution policy contracts controlling retry, timeout, and checkpointing
behavior.

| Interface | Responsibility |
|-----------|---------------|
| `ExecutionPolicies` | Container for retry, timeout, checkpointing, and intervention policies. |

`ExecutionPolicies` is referenced by `TestPlan` and governs runtime behavior including
retry strategies, timeout durations, error handling, and checkpoint intervals.

---

## 7. `io.nosqlbench.paramodel.compilation`

**Purpose:** Compilation pipeline contracts for transforming `TestPlan` into
`ExecutionPlan` through staged validation, normalization, enumeration, and optimization.

| Interface | Responsibility |
|-----------|---------------|
| `Compiler` | Main compiler: validates, compiles, and incrementally recompiles test plans. |
| `Compiler.CompilationResult` | Result of compilation (success/failure, plan, errors, warnings, statistics). |
| `Compiler.CompilerOptions` | Configurable compilation strategy, optimization level, max trial space. |
| `CompilationStage` | Individual stage in the compilation pipeline. |
| `CompilationContext` | Shared state passed between stages during compilation. |
| `OptimizationPass` | Individual optimization pass applied during the optimization stage. |

### Key Method Signatures

**`Compiler`**

```java
static Compiler create();
static Compiler create(CompilerOptions options);
ValidationResult validate(TestPlan testPlan);
CompilationResult compile(TestPlan testPlan);
CompilationResult compileIncremental(TestPlan modified, ExecutionPlan previous);
CompilerOptions options();
String version();
```

**`CompilationStage`**

```java
String name();
default String description();
void execute(CompilationContext context);
default List<String> dependencies();
default boolean canSkip(CompilationContext context);
default Optional<Duration> estimatedDuration(CompilationContext context);
```

For details on the 8-stage pipeline, see [Compilation Stages](compilation-stages.md).

---

## 8. `io.nosqlbench.paramodel.execution`

**Purpose:** Execution runtime contracts for orchestrating resource provisioning,
trial execution, scheduling, and artifact collection.

| Interface | Responsibility |
|-----------|---------------|
| `Executor` | Execution orchestrator: synchronous/async execution, resume from checkpoints. |
| `Executor.ExecutionResult` | Final execution result with trial results, metrics, and status. |
| `Executor.ExecutionHandle` | Handle for async execution: pause, resume, cancel, progress tracking. |
| `Executor.ExecutionStatus` | Real-time execution status with progress percentage and phase. |
| `Executor.ExecutionPhase` | Enum: `INITIALIZING`, `DEPLOYING`, `EXECUTING`, `TEARING_DOWN`, `COMPLETED`, `FAILED`, `CANCELLED`. |
| `Executor.ExecutionMetrics` | Peak and average resource usage metrics. |
| `Executor.Checkpoint` | Checkpoint for resumable execution. |
| `Executor.ExecutorConfig` | Configuration: max concurrency, resource limits, checkpoint settings. |
| `Runtime` | Runtime service provider: deploy elements, execute trials, manage resources. |
| `Runtime.ElementInstance` | Deployed element instance with endpoint, state, and configuration. |
| `Runtime.InstanceState` | Enum: `PROVISIONING`, `STARTING`, `HEALTH_CHECK`, `READY`, `UNHEALTHY`, `STOPPING`, `TERMINATED`. |
| `Runtime.DeploymentRequest` | Request to deploy an element. |
| `Runtime.TrialExecutionRequest` | Request to execute a trial with element bindings. |
| `Runtime.Resources` | Record: CPU, memory, storage specification. |
| `Runtime.RuntimeConfig` | Default timeouts and custom configuration. |
| `Scheduler` | Step scheduling: dependency resolution, resource-aware admission, work stealing. |
| `Scheduler.SchedulingPolicy` | Enum: `FIFO`, `PRIORITY`, `FAIR`, `RESOURCE_AWARE`. |
| `Scheduler.Priority` | Enum: `LOW`, `NORMAL`, `HIGH`, `CRITICAL`. |
| `Scheduler.SchedulerState` | Snapshot of pending, running, completed counts and utilization. |
| `Scheduler.SchedulerConfig` | Policy, resource limits, concurrency, work stealing settings. |
| `ResourceManager` | Resource allocation and lifecycle management. |
| `ArtifactCollector` | Artifact capture: logs, metrics, files from trial execution. |

### Key Method Signatures

**`Executor`**

```java
static Executor create();
static Executor create(ExecutorConfig config);
ExecutionResult execute(ExecutionPlan plan) throws ExecutionFailedException;
ExecutionHandle executeAsync(ExecutionPlan plan);
ExecutionResult resume(ExecutionPlan plan, Checkpoint checkpoint) throws ExecutionFailedException;
ExecutorConfig config();
```

**`Runtime`**

```java
static Runtime create();
ElementInstance deploy(DeploymentRequest request) throws DeploymentException;
void awaitReady(ElementInstance instance, Duration timeout) throws TimeoutException;
HealthStatus checkHealth(ElementInstance instance);
TrialResult executeTrial(TrialExecutionRequest request) throws TrialExecutionException;
ResourceAvailability availableResources();
ResourceAllocation allocateResources(Resources resources) throws InsufficientResourcesException;
void releaseResources(ResourceAllocation allocation);
```

**`Scheduler`**

```java
static Scheduler create();
static Scheduler create(SchedulingPolicy policy);
void initialize(ExecutionGraph graph);
List<AtomicStep> nextSteps();
List<AtomicStep> nextSteps(Runtime.ResourceAvailability available);
void markStarted(AtomicStep step, Instant startTime);
void markCompleted(AtomicStep step);
void markFailed(AtomicStep step, Throwable error);
boolean isComplete();
SchedulerState state();
```

---

## 9. `io.nosqlbench.paramodel.persistence`

**Purpose:** Persistence contracts for storing results, execution state, artifacts,
and metadata.

| Interface | Responsibility |
|-----------|---------------|
| `ResultStore` | Persists structured trial metrics with provenance metadata. |
| `ExecutionRepository` | Stores and retrieves execution plans, runs, and their relationships. |
| `CheckpointStore` | Persists and retrieves execution checkpoints for resumability. |
| `ArtifactStore` | Stores unstructured files (logs, model outputs, dataset snapshots). |
| `MetadataStore` | Stores and queries metadata about plans, runs, and trials. |

These contracts are intended for implementation by backends such as file systems,
databases, or cloud storage services.

---

## 10. `io.nosqlbench.paramodel.security`

**Purpose:** Security contracts for credential management, access control, and audit
logging.

| Interface | Responsibility |
|-----------|---------------|
| `CredentialManager` | Manages credentials for accessing external resources (databases, APIs, etc.). |
| `AccessControl` | Enforces multi-user permissions and sharing semantics (RBAC/ABAC). |
| `AuditLog` | Records security-relevant events for compliance and debugging. |

---

## 11. `io.nosqlbench.paramodel.util`

**Purpose:** Utility contracts for configuration, serialization, and validation
helpers.

| Interface | Responsibility |
|-----------|---------------|
| `ConfigurationManager` | Manages configuration loading, merging, and validation. |
| `SerializationUtil` | Serialization and deserialization utilities for plans, results, and metadata. |
| `ValidationUtil` | Reusable validation helpers for common constraint patterns. |

---

## See Also

- [Contract Types](contract-types.md) -- detailed signatures for each contract
- [Compilation Stages](compilation-stages.md) -- the 8-stage compilation pipeline
- [Glossary](glossary.md) -- term definitions
