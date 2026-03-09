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

# Design Principles

This document explains the foundational design decisions behind Paramodel
and the reasoning that led to each one. Where the
[Architecture](architecture.md) page describes the structure of the system,
this page explains *why* it is structured that way.

## Contract-First Architecture

Every type in Paramodel is defined as a Java interface before any
implementation exists. `Parameter<T>`, `Constraint<T>`, `Domain<T>`,
`Value<T>`, `TestPlan`, `ExecutionPlan`, `Compiler`, `Executor`,
`Scheduler` -- all are interfaces in the `paramodel-api` module.

The choice of interfaces over abstract classes is intentional:

- **No implementation leakage.** An abstract class tempts authors into
  adding protected helper methods and mutable fields. Interfaces prevent
  this entirely; the contract surface is exactly the public method
  signatures.
- **Multiple inheritance of type.** A class can implement any number of
  interfaces. `Parameter<T>` extends `Tagged`; `Element` extends `Labeled`,
  `Traits`, and `Tagged`. This would require awkward class hierarchies if
  either were an abstract class.
- **Default methods for derived behavior.** Java interfaces support default
  methods (`Constraint.and()`, `Constraint.or()`, `Constraint.negate()`,
  `TestPlan.axis(String)`, `TestPlan.element(String)`), which provide
  convenience without forcing implementation inheritance.

The payoff of contract-first design is visible today: the `paramodel-mock`
module and the `paramodel-engine` module both implement the same interfaces
and are validated by the same TCK, yet they share no implementation code.
A future distributed engine or a Rust-native binding via JNI would implement
the same interfaces and pass the same tests.

## Algebraic Foundations

Parameters and constraints in Paramodel are not ad-hoc validation functions.
They compose using well-defined algebraic laws, which means their behavior
is predictable and refactorable.

### Constraint Algebra

`Constraint<T>` (in `io.nosqlbench.paramodel.parameters`) forms a Boolean
algebra through its composition operators:

| Law | Expression | Significance |
|-----|-----------|--------------|
| Associativity | `(a AND b) AND c = a AND (b AND c)` | Grouping does not affect result |
| Commutativity | `a AND b = b AND a` | Order does not affect result |
| Distributivity | `a AND (b OR c) = (a AND b) OR (a AND c)` | Standard algebraic distribution |
| Identity | `a AND true = a` | A trivially-true constraint is a no-op |
| Annihilation | `a AND false = false` | A trivially-false constraint dominates |
| Idempotence | `a AND a = a` | Duplicating a constraint has no effect |
| De Morgan | `NOT(a AND b) = NOT(a) OR NOT(b)` | Negation distributes over operators |
| Double Negation | `NOT(NOT(a)) = a` | Negation is its own inverse |

Why does this matter? Because when users build complex constraint
expressions -- and in real studies they do -- the system behaves
predictably. If you extract a sub-expression into a variable, the behavior
does not change. If you reorder independent constraints, the behavior does
not change. If you accidentally duplicate a constraint in a composition
chain, the behavior does not change. These guarantees are not features that
had to be implemented; they are consequences of the algebraic structure.

### Parameter Space Algebra

Parameters compose to form higher-dimensional spaces:

```
Parameter<A> x Parameter<B> = two-dimensional space
|Space| = |Domain<A>| x |Domain<B>|
```

When parameters become axes in a `TestPlan`, the trial space is the
Cartesian product of all axis values. This is an algebraic structure (a
product of sets) with well-defined cardinality, enumeration order, and
boundary behavior.

## Type-Driven Design

Paramodel uses the Java type system to catch errors at compile time rather
than at runtime wherever possible.

- **Sealed interfaces.** `ValidationResult` is a sealed interface with
  exactly three permitted subtypes: `Passed`, `Failed`, and `Warning`.
  Pattern matching over these subtypes is exhaustive -- the compiler
  ensures every case is handled.
- **Records.** `Value<T>`, `Element.Dependency`, and numerous result
  types are records, giving them automatic immutability, `equals`,
  `hashCode`, and `toString`. Records make the "value object" pattern
  zero-cost to express.
- **Generics.** `Parameter<T>`, `Constraint<T>`, `Domain<T>`, and
  `Value<T>` are all generic, preventing type-confusion bugs where an
  `Integer` constraint is accidentally applied to a `String` parameter.
- **Enums.** `RelationshipType` (`SHARED`, `EXCLUSIVE`, `DEDICATED`,
  `LINEAR`, `LIFELINE`), `TrialOrdering`, `TrialStatus`, and compilation
 strategy
  types are enums, giving a closed set of legal values that the compiler
  can check exhaustively.

The principle is: if a constraint can be expressed in the type system, it
should be. A `Constraint<Integer>` cannot accidentally validate a `String`.
A `ValidationResult.Passed` cannot carry violation messages. An
`ExecutionPlan` cannot be modified after construction.

## Why Formal Parameter Modeling Matters

Many testing and benchmarking systems treat parameters as unstructured
key-value pairs. A configuration file lists `threads=10` and
`cache_size=256`, and the system blindly consumes them. This approach has
three problems:

1. **No validation until runtime.** An invalid parameter value is
   discovered only when the test crashes, potentially after expensive setup.
2. **No composition.** If two parameters interact (e.g., `batch_size` must
   be less than `buffer_capacity`), the constraint lives in procedural code
   scattered across the system, not in a declarative specification.
3. **No reproducibility.** Without explicit parameter spaces and ordering
   strategies, re-running a study may not produce the same sequence of
   trials.

Paramodel replaces this ad-hoc approach with explicit, composable
constraints; produces reproducible trial sequences from declarative
specifications; and provides mathematical rigor for parameter spaces. The
formal model means you can reason about a study's coverage, enumerate its
boundary cases, and verify its constraint satisfaction before any
infrastructure is provisioned.

## Scope Decisions

The scope of Paramodel was chosen deliberately. Understanding what is out of
scope is as important as understanding what is in scope.

**In scope:**

- Parameter contracts (`Parameter<T>`, `Constraint<T>`, `Domain<T>`,
  `Value<T>`)
- Algebraic laws and their TCK verification
- Sequence generation (`Sequence`, `Trial`, `TrialBuilder`)
- Execution framework (`TestPlan`, `ExecutionPlan`, `Compiler`, `Executor`,
  `Scheduler`, `ResourceManager`)
- Provenance (fingerprinting, metadata, result linkage)
- Technology Compatibility Kit

**Out of scope:**

- **Distributed infrastructure.** Paramodel defines contracts for
  scheduling and resource management but does not include a distributed
  scheduler or cluster manager. These are left to platform-specific
  implementations.
- **Analytics UI.** Visualization of results, dashboards, and reporting
  tools are not part of the framework.
- **Post-execution analysis.** Statistical analysis, hypothesis testing,
  and charting belong to downstream tools that consume `ExecutionResults`.
- **Non-Java bindings.** While a Rust implementation could conform to the
  contracts, Paramodel does not currently provide bindings or code
  generation for other languages.

The boundary is drawn at the point where domain-specific decisions begin.
Paramodel provides the substrate -- the parameter algebra, the plan
lifecycle, the execution contracts -- and leaves platform decisions to
implementors.

## Separation of Concerns

Paramodel enforces a strict separation between three concerns:

| Concern | Type | Role |
|---------|------|------|
| **WHAT** to test | `TestPlan` | Declarative specification of axes, elements, relationships, policies |
| **HOW** to execute | `ExecutionPlan` | Compiled, immutable schedule of atomic steps with barriers and ordering |
| **Running** the plan | `Executor` + `Scheduler` | Thread management, resource allocation, progress tracking |

`TestPlan` is the user's intent expressed declaratively. It says "I want to
test these parameters across these elements with these relationships." It
does not say anything about thread counts, barrier placement, or step
ordering.

`ExecutionPlan` is the compiled artifact. The 8-stage compilation pipeline
in `DefaultCompiler` transforms the declarative intent into a deterministic
sequence of `AtomicStep` instances connected by `Barrier` synchronization
points and organized into an `ExecutionGraph`. The compilation process
resolves all ambiguity: element instantiation counts, trial ordering,
concurrency constraints, checkpoint placement.

`Executor` and `Scheduler` run the compiled plan. They manage thread pools,
enforce barriers, track progress, handle failures, and collect results. They
operate on the immutable `ExecutionPlan` and produce `ExecutionResults`.

This separation means:

- Changing the execution strategy (e.g., from single-machine to
  distributed) does not require changing any `TestPlan` definitions.
- Changing the compilation strategy (e.g., from `FAST_COMPILE` to
  `OPTIMIZE_EXECUTION`) does not require changing the executor.
- The same `TestPlan` can be compiled and executed multiple times with
  different strategies, producing comparable results.

## Further Reading

- [Architecture](architecture.md) -- the structural realization of these
  principles
- [Immutability and Reproducibility](immutability-and-reproducibility.md) --
  why immutability is central to the design
- [Constraints and Validation](../concepts/constraints-and-validation.md) --
  the Boolean algebra of constraints in detail
- [Parameters and Domains](../concepts/parameters-and-domains.md) -- the
  core parameter types and their algebraic properties
