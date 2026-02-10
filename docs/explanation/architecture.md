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

# Architecture

Paramodel is organized as a multi-module Maven project where each module
serves a distinct architectural role. This document explains why the project
is structured the way it is, how the modules relate to one another, and how
data flows through the system from parameter definition to execution results.

## Four Modules

### paramodel-api -- Pure Contract Interfaces

The API module contains approximately 52 contract interfaces with zero
implementation code. It defines the vocabulary of the entire system: what a
parameter is, what a constraint does, how plans are built, how execution
proceeds.

Packages in `paramodel-api`:

| Package | Responsibility |
|---------|---------------|
| `io.nosqlbench.paramodel.parameters` | `Parameter<T>`, `Constraint<T>`, `Domain<T>`, `Value<T>`, `ValidationResult`, `Tagged` |
| `io.nosqlbench.paramodel.parameters.types` | `IntegerParameter`, `DoubleParameter`, `BooleanParameter`, `SelectionParameter` |
| `io.nosqlbench.paramodel.elements` | `Element`, `RelationshipType` |
| `io.nosqlbench.paramodel.sequence` | `Sequence`, `SequenceBuilder`, `Trial`, `TrialBuilder`, `TrialResult`, `TrialStatus` |
| `io.nosqlbench.paramodel.plan` | `TestPlan`, `TestPlanBuilder`, `ExecutionPlan`, `ExecutionGraph`, `AtomicStep`, `Barrier`, `TrialOrdering`, `OptimizationStrategy`, policies |
| `io.nosqlbench.paramodel.plan.policies` | `ExecutionPolicies` |
| `io.nosqlbench.paramodel.compilation` | `Compiler`, `CompilationStage`, `CompilationContext`, `OptimizationPass` |
| `io.nosqlbench.paramodel.execution` | `Executor`, `Scheduler`, `ResourceManager`, `ArtifactCollector`, `Runtime` |
| `io.nosqlbench.paramodel.persistence` | `ExecutionRepository`, `ResultStore`, `ArtifactStore`, `MetadataStore`, `CheckpointStore` |
| `io.nosqlbench.paramodel.security` | `AccessControl`, `AuditLog`, `CredentialManager` |
| `io.nosqlbench.paramodel.util` | `ConfigurationManager`, `SerializationUtil`, `ValidationUtil` |

The API module has no dependencies on any implementation module. It depends
only on the Java standard library. This is deliberate: any downstream code
that programs against the API module can swap implementations freely without
recompilation.

### paramodel-mock -- In-Memory Reference Implementation

The mock module provides simple, in-memory implementations of the API
contracts using standard Java collections. Classes such as `MockParameter`,
`MockDomain`, `MockValue`, `MockTestPlan`, `MockExecutionPlan`,
`MockElement`, `MockSequence`, and `MockTrial` follow builder patterns and
static factory methods to make test setup concise.

The mock module mirrors the API package structure under
`io.nosqlbench.paramodel.mock.*`. It exists for two reasons:

1. **Rapid prototyping** -- users can experiment with parameter modeling
   without standing up a production execution environment.
2. **TCK validation** -- the mock implementation is the first consumer of
   the Technology Compatibility Kit, proving that the contracts are
   implementable and that the TCK tests pass against at least one concrete
   implementation.

### paramodel-tck -- Technology Compatibility Kit

The TCK module contains abstract test classes that any implementation can
extend. Classes like `ParameterTCK`, `DomainTCK`, `ConstraintTCK`,
`ValueTCK`, `ValidationResultTCK`, `TrialTCK`, `TestPlanTCK`,
`ExecutionPlanTCK`, `ExecutionGraphTCK`, and `AtomicStepTCK` define the
behavioral expectations of the API contracts.

An implementor writes a small concrete test class that extends the
appropriate TCK class and provides factory methods for its own types. If the
TCK tests pass, the implementation conforms to the Paramodel contracts.

The TCK exists because contracts specified only in Javadoc can be
misunderstood. Executable specifications remove ambiguity: if the test
passes, the implementation is correct.

### paramodel-engine -- Production Execution Engine

The engine module provides the production-grade components for compiling and
running test plans:

- `DefaultCompiler` -- an 8-stage compilation pipeline (Validation,
  Normalization, Trial Enumeration, Instantiation, Step Generation,
  Dependency Analysis, Optimization, Code Generation).
- `DefaultExecutor` -- thread-pool-based execution of compiled plans.
- `DefaultScheduler` -- priority-based trial scheduling with barrier
  awareness.
- `DefaultResourceManager` -- lifecycle management for element instances.

Each of these classes implements the corresponding API interface, meaning
they can be replaced without affecting user code.

## Dependency Flow

```
                  paramodel-api (contracts)
                         ^
                         |  implements
              +----------+----------+
              |                     |
       paramodel-mock        paramodel-engine
              |
              v  validated by
       paramodel-tck
```

Key observations:

- **paramodel-api** has no outward dependencies. It is the stable center of
  the system.
- **paramodel-mock** and **paramodel-engine** both depend on
  **paramodel-api** and on nothing else in the project.
- **paramodel-tck** depends on **paramodel-api** for the contract types it
  verifies, and uses **paramodel-mock** in its own test scope to prove the
  TCK is self-consistent.
- There is no dependency from **paramodel-api** to any implementation
  module. This inversion is the single most important structural property of
  the project.

## Layered Architecture

Paramodel follows a four-layer architecture. Each layer depends only on the
layers below it, never above.

```
+-------------------------------------------------------+
|  Application Layer                                     |
|    User code, test suites, CLI tools                   |
+-------------------------------------------------------+
|  Framework Layer                                       |
|    Sequence execution, plan compilation, scheduling    |
|    (paramodel-engine)                                  |
+-------------------------------------------------------+
|  Contract Layer                                        |
|    Interfaces, algebraic laws, TCK specifications      |
|    (paramodel-api, paramodel-tck)                      |
+-------------------------------------------------------+
|  Core Types Layer                                      |
|    Parameters, Constraints, Domains, Values            |
|    (io.nosqlbench.paramodel.parameters)                |
+-------------------------------------------------------+
```

The Core Types Layer contains the algebraic primitives: `Parameter<T>`,
`Constraint<T>`, `Domain<T>`, `Value<T>`. These types compose into
higher-level structures (Trials, Sequences, Axes) at the Contract Layer.
The Framework Layer adds execution semantics. The Application Layer is
where users write their studies.

## Why Contract-First

The decision to define all types as interfaces before writing any
implementation was motivated by four concerns:

1. **Multiple conforming implementations.** The mock module and the engine
   module coexist today. Future implementations (distributed, GPU-backed,
   Rust-native via JNI) can be added without modifying any existing code.

2. **Clear API boundaries.** Because interfaces carry no implementation
   baggage, the API surface is minimal and stable. Adding a method to an
   interface is a conscious, versioned decision.

3. **Testable specifications.** The TCK turns the contract prose into
   executable assertions. An interface is not just a suggestion; it is a
   specification with teeth.

4. **No implementation coupling.** User code depends on
   `io.nosqlbench.paramodel.parameters.Parameter`, never on
   `io.nosqlbench.paramodel.mock.parameters.MockParameter`. This means
   switching from mock to engine (or to a future implementation) requires
   changing only the wiring code, not the business logic.

## Design Patterns

Paramodel uses a small, consistent set of design patterns:

| Pattern | Where Used | Why |
|---------|-----------|-----|
| **Builder** | `TestPlanBuilder`, `SequenceBuilder`, `TrialBuilder`, `Compiler.Builder` | Complex objects with many optional parts benefit from step-by-step construction rather than telescoping constructors. |
| **Strategy** | `Constraint<T>` (pluggable validation), `Domain<T>` (pluggable generation), `OptimizationStrategy` | Algorithms vary independently of the objects that use them. |
| **Composite** | Hierarchical parameters, composed constraints (`and`/`or`/`negate`), execution graphs | Tree structures where leaves and branches share the same interface. |
| **Iterator** | `Sequence`, `Domain.enumerate()`, parameter space traversal | Uniform traversal of potentially large or infinite collections. |
| **Factory Method** | `MockParameter.of(...)`, `MockDomain.of(...)`, `Compiler.create()` | Decouple creation from usage; let the implementation choose the concrete class. |

## Data Flow

The end-to-end flow from parameter definition to result collection follows
this sequence:

```
Define Parameters
    |
    v
Specify Constraints
    |
    v
Build TestPlan (axes, elements, relationships, policies)
    |
    v
Validate (structural, semantic, policy checks)
    |
    v
Compile (8-stage pipeline in DefaultCompiler)
    |
    v
Execute (DefaultExecutor + DefaultScheduler)
    |
    v
Collect Results (TrialResult per trial, aggregate metrics)
```

At each stage, the data representation changes:

- **Parameters and Constraints** are user-defined algebraic primitives.
- **TestPlan** is a mutable, declarative specification of user intent.
- **ExecutionPlan** is an immutable, deterministic schedule of atomic steps.
- **ExecutionResults** are the collected outcomes linked back to the plan
  by fingerprint.

This progression from abstract intent to concrete execution is the backbone
of Paramodel's architecture.

## Further Reading

- [Parameters and Domains](../concepts/parameters-and-domains.md) -- the
  core types at the foundation of the architecture
- [Constraints and Validation](../concepts/constraints-and-validation.md) --
  the algebraic constraint system
- [Design Principles](design-principles.md) -- the reasoning behind the
  architectural choices
- [Simplica](simplica.md) -- how the execution layer assembles the
  algebraic primitives into complete study plans
