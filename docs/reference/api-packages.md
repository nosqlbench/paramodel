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
| `DerivedParameter<T>` | A parameter whose value is computed from other bound parameter values. |
| `Domain<T>` | Valid value space for a parameter (sealed: `Discrete`, `Range`, `Composite`, `Custom`). |
| `Constraint<T>` | Boolean predicate over values with algebraic composition (`and`, `or`, `negate`). Functional interface. |
| `Value<T>` | Parameter value with provenance metadata (parameter name, timestamp, generator info, fingerprint). |
| `BindingNode` | A node in the hierarchical element binding tree representing an element instance. |
| `ParameterBinder` | Orchestrates the binding of inputs to parameters for an element or tree. |
| `ParameterBinding` | The result of binding, containing assignments, derived values, and errors. |
| `BindingPolicy` | Strategy for handling missing values or conflicts during binding. |
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
Map<String, String> tags();
```

**`DerivedParameter<T> extends Parameter<T>`**

```java
T compute(Map<String, Object> boundValues);
String expression();
```

**`BindingNode extends Tagged`**

```java
Optional<Element> element();
ParameterBinding binding();
Map<String, Object> cascadedInputs();
Map<String, Object> localInputs();
List<BindingNode> parents();
Map<String, BindingNode> children();
int depth();
boolean isRoot();
```

**`ParameterBinding`**

```java
Map<String, Object> assignments();
Map<String, Object> derivedValues();
ValidationResult validationResult();
boolean isSuccessful();
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

---

## 2. `io.nosqlbench.paramodel.parameters.types`

**Purpose:** Typed parameter contracts providing concrete parameter specializations
for common value types.

| Interface | Responsibility |
|-----------|---------------|
| `IntegerParameter` | Integer parameter (ranges or discrete sets). |
| `DoubleParameter` | Continuous double parameter (ranges). |
| `BooleanParameter` | Boolean parameter (`{true, false}`). |
| `StringParameter` | String parameter (regex or set). |
| `SelectionParameter` | Single or multi-select from string options. |
| `SelectionResolver` | Resolves selection values from external sources. |

---

## 3. `io.nosqlbench.paramodel.elements`

**Purpose:** Study element contracts representing deployable resources with lifecycles.

| Type | Responsibility |
|------|---------------|
| `Element` | A deployable resource with lifecycle (deploy, ready, teardown). Extends `Tagged`. |
| `RelationshipType` | Enum defining how a dependent element relates to its dependency: `SHARED`, `EXCLUSIVE`, `DEDICATED`, `LIFELINE`. |
| `Element.Dependency` | Record representing a directed dependency edge with target element and relationship type. |

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
| `TrialStatus` | Trial execution lifecycle states: `PENDING`, `IN_PROGRESS`, `COMPLETED`, `FAILED`, `SKIPPED`, `CANCELLED`. |
| `TrialResult` | Trial outcome with metrics, artifacts, timing, provenance, and error info. |

---

## 5. `io.nosqlbench.paramodel.plan`

**Purpose:** Test plan and execution plan contracts -- the central types
that bridge user intent to compiled execution strategy.

| Type | Responsibility |
|------|---------------|
| `TestPlan` | Declarative study specification (axes, elements, relationships, policies). |
| `ExecutionPlan` | Compiled, immutable execution plan with steps, barriers, graph, and metadata. |
| `ExecutionGraph` | DAG of atomic steps with critical path, parallelism, and scheduling analysis. |
| `AtomicStep` | Sealed interface: indivisible execution unit (Deploy, Execute, Teardown, BarrierSync, Checkpoint). |
| `Barrier` | Synchronization primitive with types, states, timeouts, and dependency tracking. |
| `Axis<T>` | Named parameter dimension with ordered values. |
| `TrialOrdering` | Trial ordering strategies: `SEQUENTIAL`, `EDGE_FIRST`, `SHUFFLED`, etc. |

### Key Method Signatures

**`ExecutionGraph`**

```java
List<AtomicStep> steps();
List<Edge> edges();
List<AtomicStep> criticalPath();
Duration criticalPathDuration();
Map<Integer, List<AtomicStep>> parallelWaves();
int maximumParallelism();
double averageParallelism();
boolean canExecuteConcurrently(AtomicStep s1, AtomicStep s2);
ExecutionGraph subgraphForElement(String elementId);
```

**`Barrier`**

```java
String id();
BarrierType type();
BarrierState state();
List<String> dependencies();
List<String> dependentSteps();
Optional<Duration> timeout();
boolean isSatisfied();
void await() throws InterruptedException, BarrierException;
```

**`AtomicStep` (sealed)**

```java
String id();
StepType type();
List<String> dependencies();
StepResult execute(ExecutionContext context) throws StepExecutionException;
```

Permitted subtypes:
- `DeployElement(String elementId, int instanceNumber, ...)`
- `ExecuteTrial(String trialId, Map<String, String> elementBindings, ...)`
- `TeardownElement(String elementId, int instanceNumber, ...)`
- `BarrierSync(String barrierId, List<String> dependencies, ...)`
- `CheckpointState(String checkpointId, ...)`

---

## 6. `io.nosqlbench.paramodel.plan.policies`

**Purpose:** Execution policy contracts controlling retry, timeout, and checkpointing
behavior.

| Interface | Responsibility |
|-----------|---------------|
| `ExecutionPolicies` | Container for retry, timeout, checkpointing, and intervention policies. |

---

## 7. `io.nosqlbench.paramodel.compilation`

**Purpose:** Compilation pipeline contracts for transforming `TestPlan` into
`ExecutionPlan`.

| Interface | Responsibility |
|-----------|---------------|
| `Compiler` | Main compiler: validates, compiles, and incrementally recompiles test plans. |
| `CompilationStage` | Individual stage in the compilation pipeline. |
| `CompilationContext` | Shared state passed between stages during compilation. |

---

## 8. `io.nosqlbench.paramodel.execution`

**Purpose:** Execution runtime contracts for orchestrating resource provisioning,
trial execution, scheduling, and artifact collection.

| Interface | Responsibility |
|-----------|---------------|
| `Executor` | Execution orchestrator: synchronous/async execution, resume from checkpoints. |
| `Runtime` | Runtime service provider: deploy elements, execute trials, manage resources. |
| `Scheduler` | Step scheduling: dependency resolution, resource-aware admission. |

---

## 9. `io.nosqlbench.paramodel.persistence`

**Purpose:** Persistence contracts for storing results, execution state, artifacts,
and metadata.

---

## 10. `io.nosqlbench.paramodel.security`

**Purpose:** Security contracts for credential management, access control, and audit
logging.

---

## 11. `io.nosqlbench.paramodel.util`

**Purpose:** Utility contracts for configuration, serialization, and validation
helpers.
