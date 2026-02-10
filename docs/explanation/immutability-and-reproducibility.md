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

# Immutability and Reproducibility

Immutability is not an incidental property of Paramodel's types. It is a
deliberate design decision with consequences that ripple through every
layer of the system -- from individual parameter values to complete
execution plans. This document explains the immutability model, the
reasoning behind it, and why it is essential for reproducible studies.

## TestPlan: Mutable Until Commit

A `TestPlan` (in `io.nosqlbench.paramodel.plan`) begins its life as a
mutable draft. During the authoring phase, the user can:

- Add, remove, or reorder axes
- Add or remove elements
- Define or change relationships between elements
- Set or modify execution policies

This mutability is intentional. Study design is an iterative process.
Users need the freedom to experiment with different axis orderings, try
different element configurations, and refine their constraints before
committing to a plan.

The mutability is bounded. The moment `testPlan.commit()` is called, two
things happen simultaneously:

1. The `TestPlan` becomes immutable. Subsequent attempts to modify it
   (e.g., `reorderAxes()`) throw `IllegalStateException`.
2. An `ExecutionPlan` is derived through the compilation pipeline.

This two-phase model -- mutable draft, then immutable commitment -- gives
users flexibility during design and rigidity during execution.

```
Before commit():                     After commit():
  TestPlan is mutable                  TestPlan is immutable
  Can modify axes, elements,           Cannot modify anything
  relationships, policies              ExecutionPlan generated
                                       Algebraic lock established:
                                         TestPlan <-> ExecutionPlan
```

## ExecutionPlan: Immutable After Compilation

The 8-stage compilation pipeline in `DefaultCompiler` (from
`io.nosqlbench.paramodel.engine.compiler`) produces an `ExecutionPlan` that
is deeply immutable. Every component of the execution plan -- the list of
`AtomicStep` instances, the `ExecutionGraph`, the `Barrier` list, the
`TrialOrdering`, the `ResourceRequirements`, the `CheckpointStrategy` --
is frozen at compilation time.

The compilation stages, in order, are:

1. **Validation** -- verify TestPlan correctness
2. **Normalization** -- canonicalize representation
3. **Trial Enumeration** -- expand parameter space into concrete trials
4. **Instantiation** -- determine element instance counts and scopes
5. **Step Generation** -- build DEPLOY, EXECUTE, TEARDOWN, BARRIER, and
   CHECKPOINT steps
6. **Dependency Analysis** -- compute execution graph, check for cycles,
   identify critical path
7. **Optimization** -- barrier coalescing, step fusion, resource packing,
   redundancy elimination
8. **Code Generation** -- finalize execution graph, compute metadata,
   generate fingerprint, create ExecutionPlan

After stage 8 completes, no aspect of the resulting `ExecutionPlan` is
changeable. Any desired change requires creating a new `TestPlan`,
modifying it, and compiling a new `ExecutionPlan`.

The `withMaxConcurrency(int)` method on `ExecutionPlan` appears to violate
this rule, but it does not: it returns a *new* `ExecutionPlan` instance
with the adjusted concurrency limit. The original remains unchanged.

## The Ship of Theseus Principle

Paramodel takes a firm position on plan identity: any refinement to a plan
creates a fundamentally new, unique plan.

Consider a study that has been committed and partially executed. The user
discovers that an additional axis value would be useful. In some systems,
the user might "amend" the existing plan. Paramodel does not support this.
Instead, the user:

1. Creates a new `TestPlan` (possibly copying the structure of the
   original).
2. Adds the new axis value.
3. Validates and commits the new plan, producing a new `ExecutionPlan`
   with a new fingerprint.

The two execution plans are distinct entities with distinct fingerprints,
distinct trial orderings, and distinct provenance chains. Versioning
metadata may link them semantically (both are "v1" and "v2" of the same
study), but the system treats them as unrelated plans.

This principle prevents a class of subtle bugs where a "modified" plan is
conflated with the "original" plan, leading to results that cannot be
traced to a single, unambiguous specification.

## Why Immutability Matters

### Correctness

Mutable execution plans would introduce ambiguous runtime decisions. If a
plan could be modified while trials are executing -- say, adding a new
element relationship -- the scheduler would need to reconcile the change
with in-flight trials. This reconciliation is complex, error-prone, and
fundamentally at odds with deterministic execution.

With immutable plans, the scheduler operates on a fixed specification.
There are no race conditions between plan modification and plan execution
because plan modification is impossible.

### Repeatability

Given the same `TestPlan` inputs, the compiler produces the same
`ExecutionPlan`. This means:

- Running a study twice produces the same trial ordering, the same barrier
  placement, the same element instantiation strategy.
- A reviewer can inspect the `ExecutionPlan` before execution and know
  exactly what will happen.
- Debugging a failed trial is straightforward because the execution
  context is fully determined by the plan.

### Provenance

Results link to exact plan versions through fingerprints. When a
`TrialResult` is collected, it carries the `executionPlanId` and the plan's
fingerprint. If someone asks "under what conditions was this result
produced?", the answer is a deterministic lookup: load the execution plan
with that fingerprint, and you have the complete specification.

If execution plans were mutable, this chain would break. A result might
reference a plan that has since been modified, and the original
specification would be lost.

### Thread Safety

Immutable objects are inherently thread-safe. The `DefaultExecutor` runs
trials on a thread pool, with multiple threads reading the execution plan
concurrently. If the plan were mutable, every access would require
synchronization. With immutability, no synchronization is needed -- all
threads see the same, unchanging data.

This is not a minor convenience. Thread-safety bugs are among the hardest
to diagnose. Immutability eliminates an entire category of them by
construction.

## Immutable Types Throughout

The immutability principle extends beyond plans to the entire type system:

- **Value<T>** -- once created, a `Value` instance is immutable. Its
  wrapped value, parameter name, generation timestamp, generator metadata,
  and fingerprint are all fixed at construction time.
- **Trial** -- a trial is an immutable assignment of parameter values. Its
  `assignments()` map is unmodifiable.
- **Sequence** -- a `Sequence` is an immutable, ordered list of trials.
  New sequences are created rather than modifying existing ones.
- **Constraint<T>** -- constraints are pure functions. The `and()`, `or()`,
  and `negate()` methods return new `Constraint` instances; they do not
  modify the originals.
- **ValidationResult** -- the sealed subtypes `Passed`, `Failed`, and
  `Warning` are immutable records (or record-like types).

This consistency means users can safely pass Paramodel objects between
threads, store them in collections, and cache them without defensive
copying.

## Deterministic Compilation

The `Compiler` contract requires that the same `TestPlan` inputs produce
equivalent `ExecutionPlan` outputs. This determinism enables:

- **Reproducible results.** Running a study on Monday and again on Friday
  produces the same trial ordering, the same element deployment sequence,
  and the same barrier placement.
- **Verifiable compilation.** A CI pipeline can compile a `TestPlan` and
  compare the resulting `ExecutionPlan` fingerprint against a known-good
  value. If the fingerprints differ, something changed.
- **Caching and memoization.** If a `TestPlan` with a known fingerprint
  has already been compiled, the compilation result can be reused. The
  `compileIncremental()` method on `Compiler` exploits this: when only a
  portion of the plan changes, unchanged subgraphs are reused.

Determinism does not mean identical object references. Two compilation
runs may produce `ExecutionPlan` instances at different memory addresses.
Determinism means the plans are *equivalent*: same steps, same ordering,
same barriers, same fingerprint.

## Fingerprinting

Every `ExecutionPlan` carries a cryptographic fingerprint -- a hash
computed from the plan's configuration during the Code Generation stage of
compilation. This fingerprint enables:

- **Change detection.** If two execution plans have different fingerprints,
  they differ in at least one structural aspect. If they have the same
  fingerprint, they are structurally identical.
- **Result linkage.** `ExecutionResults.executionPlanId()` and the plan's
  `testPlanFingerprint()` create a two-hop provenance chain: result ->
  execution plan -> test plan.
- **Version verification.** When resuming from a checkpoint, the system
  verifies that the checkpoint's fingerprint matches the current plan's
  fingerprint. A mismatch indicates that the plan was recompiled between
  runs, and the checkpoint is incompatible.

The fingerprint is computed from the plan's structural content, not from
timestamps or object identity. Two plans compiled from identical
`TestPlan` inputs at different times will have the same fingerprint.

## Seeded Generation

When trial ordering uses a randomized strategy (such as `SHUFFLED`), the
randomization is seeded. This means:

- A "random" trial ordering is reproducible given the same seed.
- The seed is part of the `ExecutionPlan` metadata.
- Two executions of the same plan produce the same "random" ordering.

Similarly, when `Domain.sample(Random)` is used to generate parameter
values, the `Random` instance can be seeded for reproducibility. The
`Value<T>` metadata records how the value was generated, completing the
provenance chain.

This approach gives users the statistical benefits of randomized ordering
(avoiding systematic bias) while preserving the reproducibility that
scientific studies require.

## Further Reading

- [Design Principles](design-principles.md) -- the broader design
  philosophy that motivates immutability
- [Architecture](architecture.md) -- the module and layer structure
- [Simplica](simplica.md) -- the execution system that relies on
  immutability for operational durability
- [Parameters and Domains](../concepts/parameters-and-domains.md) --
  immutable parameter types at the foundation of the system
- [Constraints and Validation](../concepts/constraints-and-validation.md)
  -- pure, composable constraint functions
