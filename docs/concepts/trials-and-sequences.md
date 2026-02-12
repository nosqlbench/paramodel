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

## Trial lifecycle and Result

Executing a trial produces a `TrialResult` that captures its entire lifecycle.

### TrialStatus

The `TrialStatus` enum tracks the trial's execution state:

| Status        | Category  | Meaning                                              |
|---------------|-----------|------------------------------------------------------|
| `PENDING`     | Initial   | Trial is created but not yet queued for execution.  |
| `QUEUED`      | Active    | Trial is in the execution queue waiting for resources. |
| `IN_PROGRESS` | Active    | Trial logic is currently running.                    |
| `COMPLETED`   | Terminal  | Trial finished successfully.                         |
| `FAILED`      | Terminal  | Trial encountered an error during execution.         |
| `SKIPPED`     | Terminal  | Trial was bypassed (e.g., due to a prior failure).   |
| `CANCELLED`   | Terminal  | Trial was stopped before completion by the user.     |

### TrialResult

A `TrialResult` is the immutable record of a trial's execution:

| Field              | Type                           | Purpose                                  |
|--------------------|--------------------------------|------------------------------------------|
| `trial()`          | `Trial`                        | The trial that was executed              |
| `status()`         | `TrialStatus`                  | Outcome of execution                     |
| `metrics()`        | `Map<String, Object>`          | Structured dependent variables (latency, etc.) |
| `artifacts()`      | `List<ArtifactReference>`      | Unstructured outputs (logs, snapshots)   |
| `timing()`         | `ExecutionTiming`              | Start, end, and duration                 |
| `provenance()`     | `ProvenanceInfo`               | Fingerprint linking back to config       |
| `error()`          | `Optional<ErrorInfo>`          | Failure details if status is `FAILED`    |

## Sequence

### Core interface

`Sequence` extends `Iterable<Trial>` and provides an ordered, immutable list
of trials.

| Method        | Returns              | Purpose                                 |
|---------------|----------------------|-----------------------------------------|
| `trials()`    | `List<Trial>`        | Complete ordered trial list              |
| `size()`      | `int`                | Number of trials                        |
| `validate()`  | `ValidationResult`   | Validate every trial in the sequence     |

### Generation Strategies

The `SequenceBuilder` offers several strategies for populating a sequence:

| Strategy               | Coverage       | Best For                                     |
|------------------------|----------------|----------------------------------------------|
| **Exhaustive**         | 100 %          | Small parameter spaces, full verification    |
| **Random**             | Probabilistic  | Large spaces where full coverage is impossible|
| **Edge-First**         | Progressive    | Identifying boundary failures early          |
| **Pairwise**           | All pairs      | High-interaction coverage with fewer trials   |
| **Boundary**           | Extrema only   | Quick "smoke test" of limits                 |

## Parameter Space as Cartesian Product

The trial space is the Cartesian product of all parameters. If you have
three parameters with domain sizes 5, 10, and 2, the total space is 100 trials.
Constraints filter this space, ensuring only valid combinations are executed.

## Further Reading

- [Parameters and Domains](parameters-and-domains.md) -- building blocks
- [Constraints and Validation](constraints-and-validation.md) -- space reduction
- [Test Plans and Axes](test-plans-and-axes.md) -- study integration
