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

# Trials and Sequences

A **Trial** is a single point in parameter space -- one concrete value for
every parameter. A **Sequence** is an ordered collection of trials that
defines the complete set of points to explore and the order in which to
visit them.

Both types live in `io.nosqlbench.paramodel.sequence`.

## Trial

### Structure

A `Trial` bundles together:

| Method            | Returns                             | Purpose                              |
|-------------------|-------------------------------------|--------------------------------------|
| `id()`            | `String`                            | Unique identifier (UUID or sequential) |
| `assignments()`   | `Map<String, Value<?>>`             | One `Value` per parameter            |
| `assignment(name)`| `Optional<Value<?>>`                | Convenience accessor by name         |
| `constraints()`   | `List<Constraint<Map<String, Value<?>>>>` | Cross-parameter constraints   |
| `validate()`      | `ValidationResult`                  | Check all constraints                |
| `metadata()`      | `Optional<TrialMetadata>`           | Index, group, generation method      |

A trial is **complete** if and only if it assigns a value to every parameter
in the parameter set. Partial assignments are not permitted.

### Validation

`Trial.validate()` runs three checks in order:

1. **Assignment completeness** -- every required parameter has a value.
2. **Per-value validation** -- each value is within its parameter's domain.
3. **Cross-parameter constraints** -- every constraint in `constraints()`
   receives the full assignment map and must return `true`.

The aggregated result is a single `ValidationResult` (see
[Constraints and Validation](constraints-and-validation.md)).

### Linkage to Elements

In a study context, a trial's assignments are used to drive the configuration
of **Elements**. During the **Instantiation** compilation stage, the engine
matches trial values to element parameters.

If a trial assignment binds to an element parameter, that element's
configuration is considered **dynamic**. This forces the element into a
`PER_TRIAL` lifecycle scope to ensure isolation between different trial
configurations.

### TrialMetadata

Optional metadata carried by a trial:

| Field              | Type                | Purpose                          |
|--------------------|---------------------|----------------------------------|
| `sequenceIndex()`  | `Optional<Integer>` | 0-based position in the sequence |
| `group()`          | `Optional<String>`  | Logical grouping for batching    |
| `generationMethod()`| `Optional<String>` | How this trial was created       |
| `priority()`       | `Optional<Integer>` | Execution priority hint          |

## TrialResult

Executing a trial produces a `TrialResult` that captures:

| Field              | Type                           | Purpose                                  |
|--------------------|--------------------------------|------------------------------------------|
| `trial()`          | `Trial`                        | The trial that was executed              |
| `status()`         | `TrialStatus`                  | Outcome of execution                     |
| `metrics()`        | `Map<String, Object>`          | Structured dependent variables           |
| `artifacts()`      | `List<ArtifactReference>`      | Unstructured outputs (logs, models)      |
| `timing()`         | `ExecutionTiming`              | Start, end, and duration                 |
| `provenance()`     | `ProvenanceInfo`               | Fingerprint linking to configuration     |
| `error()`          | `Optional<ErrorInfo>`          | Failure details (if status is FAILED)    |
| `attemptNumber()`  | `int`                          | Retry count (1-based)                    |

### TrialStatus

`TrialStatus` is an enum tracking the trial's lifecycle:

```
PENDING  -->  IN_PROGRESS  -->  COMPLETED
                           -->  FAILED
                           -->  CANCELLED
         -->  SKIPPED
```

Terminal states (`COMPLETED`, `FAILED`, `SKIPPED`, `CANCELLED`) indicate
execution is finished. The convenience methods `isTerminal()`,
`isSuccess()`, and `isFailure()` simplify branching logic.

## Sequence

### Core interface

`Sequence` extends `Iterable<Trial>` and provides:

| Method        | Returns              | Purpose                                 |
|---------------|----------------------|-----------------------------------------|
| `trials()`    | `List<Trial>`        | Complete ordered trial list (immutable)  |
| `size()`      | `int`                | Number of trials                        |
| `isEmpty()`   | `boolean`            | True if the sequence has no trials       |
| `validate()`  | `ValidationResult`   | Validate every trial plus global rules   |
| `iterator()`  | `Iterator<Trial>`    | Stream-friendly access in order          |

A built sequence is **immutable**: `trials()` always returns the same list
in the same order.

### SequenceBuilder

`SequenceBuilder` offers a fluent DSL for constructing sequences:

```java
Sequence seq = Sequence.builder()
    .withParameter(param1)
    .withParameter(param2)
    .constraint(crossParamConstraint)
    .generatePairwise()
    .build();
```

### Sequence Generation Strategies

The builder exposes six generation strategies:

| Strategy               | Method                   | Coverage       | Trial Count                    |
|------------------------|--------------------------|----------------|--------------------------------|
| **Exhaustive**         | `generateExhaustive()`   | 100 %          | Product of all domain sizes    |
| **Random**             | `generateRandom(n)`      | Probabilistic  | Exactly `n`                    |
| **Seeded random**      | `generateFromSeed(seed)` | Reproducible   | Uses deterministic RNG         |
| **Edge-First**         | `generateEdgeFirst()`    | Progressive    | All, boundaries first          |
| **Pairwise**           | `generatePairwise()`     | All pairs      | Approximately O(max |Pi|^2)    |
| **Boundary**           | `generateBoundary()`     | Extrema only   | 2^n for n parameters           |

Constraints added via `constraint()` filter out invalid combinations
regardless of the strategy chosen.

## Parameter Space as Cartesian Product

Given parameters P1, P2, ..., Pn:

```
|Space| = |P1| x |P2| x ... x |Pn|
```

For example, three parameters with 5, 10, and 2 values produce a space of
5 x 10 x 2 = 100 possible trials. Constraints reduce this effective space:
only trials where all cross-parameter constraints return `true` survive.

## Properties of Sequences

| Property         | Guarantee                                                    |
|------------------|--------------------------------------------------------------|
| **Ordered**      | The list returned by `trials()` defines execution order      |
| **Validated**    | `validate()` checks every trial before execution             |
| **Deterministic**| Same builder inputs produce the same sequence                |
| **Traceable**    | Each trial carries an `id` and optional metadata for linking |

## Progressive Execution

Because sequences are ordered and iterable, they support early stopping:

```java
for (Trial trial : sequence) {
    TrialResult result = executor.execute(trial);
    if (meetsGoal(result)) {
        break; // boundary trials may already satisfy the study goal
    }
}
```

This is especially useful with the Edge-First strategy, where the most
informative boundary trials run first.

## Further Reading

- [Parameters and Domains](parameters-and-domains.md) -- the building blocks
  that populate trial assignments
- [Constraints and Validation](constraints-and-validation.md) -- how
  constraints reduce the space
- [Test Plans and Axes](test-plans-and-axes.md) -- how sequences integrate
  into a full study specification
- [../reference/contract-types.md](../reference/contract-types.md) --
  formal interface contracts
