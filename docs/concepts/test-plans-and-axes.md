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

# Test Plans and Axes

A **TestPlan** is the user-authored, declarative specification of a study.
It answers the question *"what do I want to test?"* by declaring parameter
dimensions (axes), required resources (elements), how those resources
relate, and what policies govern execution.

An **Axis** is a parameter elevated to study context -- a named dimension
with an explicit, ordered list of discrete values.

Both types live in `io.nosqlbench.paramodel.plan`.

## TestPlan

### Interface overview

| Method                  | Returns                                 | Purpose                                        |
|-------------------------|-----------------------------------------|------------------------------------------------|
| `name()`                | `String`                                | Study identifier                               |
| `axes()`                | `List<Axis<?>>`                         | Ordered parameter dimensions                   |
| `elements()`            | `List<Element>`                         | Resources required for execution               |
| `policies()`            | `ExecutionPolicies`                     | Retry, timeout, ordering policies              |
| `optimizationStrategy()`| `OptimizationStrategy`                  | Compilation aggressiveness                     |
| `trialSpaceSize()`      | `long`                                  | Total trials (product of axis cardinalities)   |
| `isCommitted()`         | `boolean`                               | Whether the plan is locked                     |
| `validate()`            | `ValidationResult`                      | Full structural and semantic check             |
| `reorderAxes(List)`     | `TestPlan`                              | Return a copy with axes in a different order   |
| `commit()`              | `ExecutionPlan`                         | Lock the plan and compile an execution plan    |
| `metadata()`            | `TestPlanMetadata`                      | Creation time, author, description, version    |

### Mutability and commitment

A `TestPlan` starts **mutable**. You can reorder axes, inspect trial space
size, and validate. Calling `commit()` performs two things:

1. Compiles the plan into an immutable `ExecutionPlan`.
2. Locks the `TestPlan` itself -- `isCommitted()` returns `true` and
   further modifications throw `IllegalStateException`.

To make any change after commitment, create a new `TestPlan` and commit
again. This guarantees a one-to-one algebraic relationship between a
test plan version and its execution plan.

### Execution policies

The `plan.policies` sub-package provides `ExecutionPolicies`, which
bundles:

- **TrialOrdering** -- the strategy for ordering trials
  (see [Trial Ordering Strategies](#trial-ordering-strategies) below).
- **Retry strategies** -- how many times and with what backoff to retry
  failed steps.
- **Timeout policies** -- maximum durations for steps and barriers.
- **Intervention rules** -- what to do when errors exceed thresholds.

## Axis

An `Axis<T>` adapts a `Parameter<T>` to study context by pinning down an
**explicit ordered list of values** rather than relying on on-demand
generation from a domain.

| Method                    | Returns                     | Purpose                                    |
|---------------------------|-----------------------------|--------------------------------------------|
| `name()`                  | `String`                    | Unique identifier in the study             |
| `values()`                | `List<T>`                   | Ordered discrete values to test            |
| `cardinality()`           | `int`                       | `values().size()`                          |
| `boundaryValues()`        | `List<T>`                   | First and last values                      |
| `description()`           | `Optional<String>`          | What this axis controls                    |
| `underlyingParameter()`   | `Optional<Parameter<T>>`   | Link back to the paramodel parameter       |
| `contains(T)`             | `boolean`                   | Is a value present in this axis?           |
| `indexOf(T)`              | `int`                       | 0-based position of a value                |

### Axis vs Parameter

| Concern              | Parameter                          | Axis                                 |
|----------------------|------------------------------------|--------------------------------------|
| Values               | Domain-based, generated on demand  | Explicit ordered list, pre-determined|
| Cardinality          | May be infinite (continuous range) | Always finite                        |
| Scope                | Reusable across studies            | Specific to one test plan            |

An axis can be created directly from explicit values, or derived from a
parameter by sampling or enumerating its domain.

## Axis-to-Parameter Binding

The most critical relationship in a study is the link between an **Axis**
and the **Parameters** of the **Elements** it configures. When a study is
compiled, the engine automatically binds axis values to element parameters
using three prioritized matching strategies:

1.  **Identity Matching**: If an axis is created using `Axis.fromParameter(p)`,
    the engine uses object identity to bind the axis to that specific
    parameter wherever it appears in an element.
2.  **Qualified Name Matching**: If an axis name follows the pattern
    `elementName.parameterName`, it is bound exclusively to that parameter
    of that specific element.
3.  **Simple Name Matching**: If an axis name matches a parameter name
    (e.g., both are named `"threads"`), the axis drives that parameter for
    *all* elements that possess it.

This binding is the "glue" that allows the Cartesian product of axes to
drive concrete resource configurations during trial execution.

## Trial Space

The trial space is the Cartesian product of all axes:

```
Trial Space = A1 x A2 x ... x An
|Trial Space| = |A1.values| x |A2.values| x ... x |An.values|
```

For example:

| Axis          | Values                       | Count |
|---------------|------------------------------|-------|
| `model`       | `gpt-4`, `claude-3`          | 2     |
| `temperature` | `0.0`, `0.5`, `1.0`         | 3     |
| `max_tokens`  | `100`, `500`, `1000`, `2000` | 4     |

Total trials: 2 x 3 x 4 = **24**.

## Trial Ordering Strategies

The `TrialOrdering` interface (in `io.nosqlbench.paramodel.plan`) defines
how trials are sequenced within the execution plan. Five built-in
strategies are available:

| Strategy               | Constant / Factory                | Behaviour                                       |
|------------------------|-----------------------------------|-------------------------------------------------|
| Sequential             | `TrialOrdering.SEQUENTIAL`        | Lexicographic traversal in axis definition order |
| Shuffled               | `TrialOrdering.shuffled(seed)`    | Pseudo-random permutation (reproducible if seeded) |
| Edge-First             | `TrialOrdering.EDGE_FIRST`        | Boundary values first, then interior fill        |
| Dependency-Optimized   | `TrialOrdering.DEPENDENCY_OPTIMIZED` | Group trials to minimize element deploy/teardown churn |
| Cost-Optimized         | `TrialOrdering.COST_OPTIMIZED`    | Expensive trials first for fail-fast behaviour   |
| Custom                 | `TrialOrdering.custom(comparator)`| User-supplied comparator                         |

### Edge-First in detail

Edge-First is Paramodel's signature ordering strategy. It proceeds in three
phases:

1. **Corners** -- all combinations of boundary values across every axis.
   For *n* axes this produces up to 2^n trials.
2. **Edges** -- one axis varies through interior values while all others
   sit at boundary values.
3. **Interior** -- remaining combinations are filled in using a
   gap-maximizing heuristic.

The result is a sequence that outlines the boundary of the parameter space
first, then progressively refines interior detail. This makes partial
results informative even if the study is stopped early.

### Axis prioritization

The order in which axes appear in `TestPlan.axes()` determines which axis
is treated as "major" for ordering purposes. Higher-priority (earlier) axes
have their detail filled first in Edge-First mode. You can experiment with
axis order using `TestPlan.reorderAxes()` to produce a new plan with
different prioritization without modifying the original.

## Barriers

A `Barrier` (in `io.nosqlbench.paramodel.plan`) is a synchronization
primitive within the compiled execution plan. Barriers block downstream
steps until all upstream dependencies are satisfied.

Common barrier types:

| Type                  | Purpose                                          |
|-----------------------|--------------------------------------------------|
| `ELEMENT_READY`       | Element has been deployed and passed health checks |
| `ELEMENT_SCOPE_END`   | All trials using an element have finished         |
| `TRIAL_BATCH`         | Groups trials for checkpointing boundaries        |
| `CHECKPOINT_BOUNDARY` | Forces state persistence before continuing        |
| `CUSTOM`              | Application-specific synchronization              |

Barriers are not authored directly by users; the compiler inserts them
during `commit()` based on element relationships and policies. For
conceptual detail on how barriers integrate into the execution graph, see
[Execution Plans](execution-plans.md).

## Further Reading

- [Execution Plans](execution-plans.md) -- how a committed test plan becomes
  an immutable execution plan
- [Elements and Relationships](elements-and-relationships.md) -- the
  resources that test plans orchestrate
- [Trials and Sequences](trials-and-sequences.md) -- the trials that axes
  produce
- [../howto/build-test-plan.md](../howto/build-test-plan.md) -- step-by-step
  recipe for authoring a plan
- [../tutorials/first-test-plan.md](../tutorials/first-test-plan.md) --
  end-to-end tutorial
