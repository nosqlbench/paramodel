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

# Your First Test Plan

In this tutorial you will build a simple test plan that explores a
two-dimensional parameter space: three **operations** crossed with five
**thread counts**. By the end you will have a validated `ExecutionPlan` that
represents 15 distinct trials.

**Prerequisites:** complete the [Getting Started](getting-started.md) tutorial
so that all modules are built and available.

## Goal

We want to model a benchmark study that tests every combination of:

| Parameter | Values |
|-----------|--------|
| `operation` | `read`, `write`, `scan` |
| `threads` | `1`, `2`, `4`, `8`, `16` |

The total trial space is 3 x 5 = **15 trials**.

## Step 1: Define the Domains

A **domain** describes the set of legal values a parameter can take. We will
create two discrete domains -- one for the operation names and one for the
thread counts.

```java
import io.nosqlbench.paramodel.mock.parameters.*;

MockDomain<String> operationDomain = MockDomain.of("read", "write", "scan");
MockDomain<Integer> threadDomain = MockDomain.of(1, 2, 4, 8, 16);
```

`MockDomain.of(...)` is a convenience factory that creates a discrete domain
from varargs. Under the hood it stores the values in a `Set` and implements the
`Domain.Discrete<T>` interface from `io.nosqlbench.paramodel.parameters`.

**What happened:** you declared *what values are possible* without yet saying
how those values will be used.

## Step 2: Create the Parameters

A **parameter** pairs a name with a domain. The name is how other parts of the
system refer to the parameter.

```java
MockParameter<String> operation = MockParameter.of("operation", operationDomain);
MockParameter<Integer> threads = MockParameter.of("threads", threadDomain);
```

`MockParameter<T>` implements `Parameter<T>` and adds generation methods
(`generate()`, `generateBoundary()`, `generateRandom()`) that sample from the
domain.

**What happened:** you gave each domain a human-readable name so it can be
referenced by axes and constraints later.

## Step 3: Build the TestPlan

A **TestPlan** is a declarative specification of what to test. You add
parameters and then define **axes** that describe how to explore those
parameters. Each axis references one or more parameters and a traversal
strategy (exhaustive, boundary, random, and so on).

```java
import io.nosqlbench.paramodel.mock.plan.*;
import io.nosqlbench.paramodel.plan.*;

MockTestPlan plan = MockTestPlan.builder()
    .parameter(operation)
    .parameter(threads)
    .axis(MockAxis.of("operations",
        MockElement.exhaustive("operation")))
    .axis(MockAxis.of("concurrency",
        MockElement.exhaustive("threads")))
    .build();
```

Breaking this down:

- `.parameter(operation)` -- registers the `operation` parameter with the plan.
- `.parameter(threads)` -- registers the `threads` parameter.
- `.axis(MockAxis.of("operations", MockElement.exhaustive("operation")))` --
  creates an axis named `"operations"` that exhaustively enumerates every value
  in the `"operation"` parameter's domain.
- `.axis(MockAxis.of("concurrency", MockElement.exhaustive("threads")))` --
  creates an axis named `"concurrency"` that exhaustively enumerates every value
  in the `"threads"` parameter's domain.

Axis ordering matters: the first axis listed is the *major* axis, meaning it
varies most slowly during trial generation.

**What happened:** you told Paramodel "explore all operation values crossed
with all thread values."

## Step 4: Validate the Plan

Before committing a plan you should validate it. Validation checks structural
requirements, name uniqueness, dependency cycles, and policy consistency.

```java
import io.nosqlbench.paramodel.parameters.ValidationResult;

ValidationResult validation = plan.validate();

if (validation.isValid()) {
    System.out.println("Plan validation passed.");
} else {
    System.out.println("Plan validation failed:");
    validation.violations().forEach(v ->
        System.out.println("  - " + v));
}
```

If the plan is structurally sound, `validation.isValid()` returns `true`.

**What happened:** the system checked that the plan is internally consistent
and ready for compilation.

## Step 5: Commit to an ExecutionPlan

Committing a plan freezes it (makes it immutable) and produces an
`ExecutionPlan` -- the fully resolved, executable form.

```java
ExecutionPlan execPlan = plan.commit();
```

After this call:

- `plan.isCommitted()` returns `true`.
- The plan can no longer be modified or reordered.
- `execPlan` contains the steps, barriers, and execution graph needed to run
  the trials.

**What happened:** the plan was compiled into a concrete execution strategy.

## Step 6: Inspect the Trial Count

```java
System.out.println("Estimated trials: " + execPlan.estimatedTrialCount());
// Expected output: Estimated trials: 15
```

The trial count equals the Cartesian product of the two axes: 3 operations x 5
thread counts = 15.

## Step 7: Create Sample Trials Manually

To see what a trial looks like at the data level, you can construct one by
hand. Each trial is a mapping from parameter names to concrete values.

```java
import io.nosqlbench.paramodel.mock.sequence.MockTrial;
import java.util.List;

for (String op : List.of("read", "write", "scan")) {
    for (Integer t : List.of(1, 4, 16)) {
        MockValue<String> opVal = MockValue.of(op, "operation");
        MockValue<Integer> threadVal = MockValue.of(t, "threads");

        MockTrial trial = MockTrial.builder()
            .assignment("operation", opVal)
            .assignment("threads", threadVal)
            .build();

        System.out.println("Trial " + trial.id() + ": " +
            "operation=" + opVal.value() + ", threads=" + threadVal.value());
    }
}
```

Each `MockTrial` receives a unique UUID as its `id()`. The `assignments()` map
holds the parameter-name-to-value bindings for that trial.

**What happened:** you manually constructed the same kind of objects the
compilation pipeline would generate, giving you a concrete view of what a
"trial" is.

## Complete Program

Putting it all together:

```java
import io.nosqlbench.paramodel.parameters.*;
import io.nosqlbench.paramodel.mock.parameters.*;
import io.nosqlbench.paramodel.mock.plan.*;
import io.nosqlbench.paramodel.mock.sequence.*;
import io.nosqlbench.paramodel.plan.*;

import java.util.List;

public class FirstTestPlan {
    public static void main(String[] args) {
        // 1. Domains
        MockDomain<String> operationDomain = MockDomain.of("read", "write", "scan");
        MockDomain<Integer> threadDomain = MockDomain.of(1, 2, 4, 8, 16);

        // 2. Parameters
        MockParameter<String> operation = MockParameter.of("operation", operationDomain);
        MockParameter<Integer> threads = MockParameter.of("threads", threadDomain);

        // 3. TestPlan
        MockTestPlan plan = MockTestPlan.builder()
            .parameter(operation)
            .parameter(threads)
            .axis(MockAxis.of("operations",
                MockElement.exhaustive("operation")))
            .axis(MockAxis.of("concurrency",
                MockElement.exhaustive("threads")))
            .build();

        // 4. Validate
        ValidationResult validation = plan.validate();
        System.out.println("Validation: " +
            (validation.isValid() ? "PASSED" : "FAILED"));

        // 5. Commit
        ExecutionPlan execPlan = plan.commit();
        System.out.println("Estimated trials: " + execPlan.estimatedTrialCount());

        // 6. Sample trials
        System.out.println("\nSample trials:");
        for (String op : List.of("read", "write", "scan")) {
            for (Integer t : List.of(1, 4, 16)) {
                MockValue<String> opVal = MockValue.of(op, "operation");
                MockValue<Integer> threadVal = MockValue.of(t, "threads");

                MockTrial trial = MockTrial.builder()
                    .assignment("operation", opVal)
                    .assignment("threads", threadVal)
                    .build();

                System.out.println("  Trial " + trial.id() + ": " +
                    "operation=" + opVal.value() +
                    ", threads=" + threadVal.value());
            }
        }
    }
}
```

## What You Learned

- **Domains** define the set of legal values a parameter can take.
- **Parameters** pair a name with a domain.
- **Axes** describe how to traverse a parameter's domain (exhaustive, boundary,
  etc.).
- **TestPlan** is a declarative, mutable specification of what to test.
- **Validation** checks the plan for structural correctness before compilation.
- **Committing** freezes the plan and produces an immutable `ExecutionPlan`.
- **Trials** are concrete assignments of values to parameters, identified by
  a unique ID.

## Next Steps

- **[Parameters and Domains](../concepts/parameters-and-domains.md)** -- deeper
  exploration of domain types, constraints, and boundary values.
- **[Test Plans and Axes](../concepts/test-plans-and-axes.md)** -- learn about
  axis ordering, traversal strategies, and element relationships.
- **[The Compilation Pipeline](compilation-pipeline.md)** -- see what happens
  inside `commit()` by using the 8-stage compiler explicitly.
