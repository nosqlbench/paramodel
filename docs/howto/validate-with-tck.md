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

# How to Validate with TCK

The Paramodel Technology Compatibility Kit (TCK) validates that your
implementation conforms to the Paramodel contracts. The TCK contains
abstract test classes that verify algebraic properties, immutability,
validation correctness, and contract compliance. You extend these classes,
wire in your `ImplementationProvider`, and run `mvn test`.

> **Prerequisite**: [How to Implement a Contract](./implement-a-contract.md)
> describes how to create the implementation that you will validate here.
> See [API Packages](../reference/api-packages.md) for package organization.

---

## Step 1: Add the TCK dependency

Add `paramodel-tck` as a test-scoped dependency to your module's `pom.xml`.

```xml
<dependency>
    <groupId>io.nosqlbench</groupId>
    <artifactId>paramodel-tck</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <scope>test</scope>
</dependency>
```

You will also need JUnit 5 and AssertJ (which the TCK uses):

```xml
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>5.11.4</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.assertj</groupId>
    <artifactId>assertj-core</artifactId>
    <version>3.27.3</version>
    <scope>test</scope>
</dependency>
```

---

## Step 2: Implement your ImplementationProvider

The TCK discovers your implementation through the `ImplementationProvider`
interface. Each factory method returns an instance of your implementation.

```java
package com.example.paramodel.tck;

import io.nosqlbench.paramodel.tck.ImplementationProvider;
import io.nosqlbench.paramodel.parameters.*;
import io.nosqlbench.paramodel.sequence.*;
import io.nosqlbench.paramodel.plan.*;
// ... remaining imports

public class MyImplementationProvider implements ImplementationProvider {

    @Override
    public <T> Parameter<T> createParameter(String name, Domain<T> domain) {
        return new com.example.paramodel.MyParameter<>(name, domain);
    }

    @Override
    public <T> Value<T> createValue(T value, String parameterName) {
        return new com.example.paramodel.MyValue<>(value, parameterName);
    }

    // ... implement all remaining factory methods
    // Refer to MockImplementationProvider for the full set
}
```

---

## Step 3: Extend TCK base classes

Each TCK class is abstract. You extend it and implement `getProvider()` to
return your `ImplementationProvider`. Here is the minimal pattern:

### ParameterTCK

```java
package com.example.paramodel.tck;

import io.nosqlbench.paramodel.tck.ImplementationProvider;
import io.nosqlbench.paramodel.tck.parameters.ParameterTCK;

public class MyParameterTest extends ParameterTCK {
    @Override
    protected ImplementationProvider getProvider() {
        return new MyImplementationProvider();
    }
}
```

### ConstraintTCK

```java
package com.example.paramodel.tck;

import io.nosqlbench.paramodel.tck.ImplementationProvider;
import io.nosqlbench.paramodel.tck.parameters.ConstraintTCK;

public class MyConstraintTest extends ConstraintTCK {
    @Override
    protected ImplementationProvider getProvider() {
        return new MyImplementationProvider();
    }
}
```

### ValueTCK

```java
package com.example.paramodel.tck;

import io.nosqlbench.paramodel.tck.ImplementationProvider;
import io.nosqlbench.paramodel.tck.parameters.ValueTCK;

public class MyValueTest extends ValueTCK {
    @Override
    protected ImplementationProvider getProvider() {
        return new MyImplementationProvider();
    }
}
```

Repeat this pattern for every TCK class you want to validate against.

---

## Step 4: TCK coverage areas

The TCK provides test classes for every contract area in Paramodel. Here
is the full list of available TCK classes:

### Parameters (`io.nosqlbench.paramodel.tck.parameters`)

| TCK class | What it validates |
|---|---|
| `ParameterTCK` | Parameter creation, domain access, value generation, validation, constraint satisfaction |
| `DomainTCK` | Membership testing, cardinality, sampling, enumeration, boundary values |
| `ValueTCK` | Value storage, parameter name, timestamps, fingerprints, complex types |
| `ValidationResultTCK` | Pass/fail status, messages, violations lists, warnings |
| `ConstraintTCK` | Predicate evaluation, AND/OR/NOT composition, cross-parameter constraints, null handling |

### Sequences (`io.nosqlbench.paramodel.tck.sequence`)

| TCK class | What it validates |
|---|---|
| `TrialTCK` | Trial creation, assignments, ID stability, constraint validation, metadata |
| `SequenceTCK` | Sequence creation, trial iteration, ordering |
| `TrialResultTCK` | Trial result creation, status reporting, failure handling |
| `TrialStatusTCK` | Status enum values and transitions |

### Plans (`io.nosqlbench.paramodel.tck.plan`)

| TCK class | What it validates |
|---|---|
| `TestPlanTCK` | Plan creation, axes, elements, relationships, commit lifecycle |
| `ExecutionPlanTCK` | Execution plan structure, steps, barriers, trial ordering |
| `ExecutionGraphTCK` | Graph structure, dependencies, parallelism analysis |
| `AtomicStepTCK` | Step IDs, types, descriptions, dependencies, metadata immutability |
| `AxisTCK` | Axis values, boundary values, cardinality, descriptions |
| `BarrierTCK` | Barrier creation and properties |
| `ExecutionPoliciesTCK` | Policy creation and configuration |

### Elements (`io.nosqlbench.paramodel.tck.elements`)

| TCK class | What it validates |
|---|---|
| `ElementTCK` | Element names, tags, types, parameters, dependencies, health checks, scopes |
| `RelationshipTypeTCK` | Relationship type semantics (concurrency, instancing, barriers) |

### Compilation (`io.nosqlbench.paramodel.tck.compilation`)

| TCK class | What it validates |
|---|---|
| `CompilerTCK` | Compilation pipeline execution |
| `CompilationContextTCK` | Context creation and state management |
| `CompilationStageTCK` | Stage creation and execution |
| `OptimizationPassTCK` | Optimization pass creation and execution |

### Execution (`io.nosqlbench.paramodel.tck.execution`)

| TCK class | What it validates |
|---|---|
| `RuntimeTCK` | Runtime lifecycle management |
| `ExecutorTCK` | Execution orchestration |
| `SchedulerTCK` | Trial scheduling |
| `ResourceManagerTCK` | Resource allocation and release |
| `ArtifactCollectorTCK` | Artifact collection from trials |

### Persistence (`io.nosqlbench.paramodel.tck.persistence`)

| TCK class | What it validates |
|---|---|
| `ArtifactStoreTCK` | Artifact storage and retrieval |
| `ResultStoreTCK` | Result storage, querying, filtering |
| `MetadataStoreTCK` | Metadata persistence |
| `CheckpointStoreTCK` | Checkpoint save and load |
| `ExecutionRepositoryTCK` | Full execution lifecycle persistence |

---

## Step 5: Run the tests

Use Maven to run your TCK tests:

```bash
mvn test
```

To run a specific TCK test class:

```bash
mvn test -Dtest=com.example.paramodel.tck.MyParameterTest
```

To run all TCK tests in a specific package:

```bash
mvn test -Dtest="com.example.paramodel.tck.*"
```

---

## What the TCK tests verify

Each TCK test class verifies specific contract properties. Here are
representative examples of what the tests check:

### Parameter contract

- Generated values are within the declared domain
- `name()` returns a non-null, non-empty string
- `domain()` returns a non-null domain
- `validate()` correctly identifies valid and invalid values
- `satisfies()` tests constraint compatibility

### Constraint contract (algebraic properties)

- Simple predicate evaluation (`test()`)
- AND composition: `c1.and(c2)` is true only when both hold
- OR composition: `c1.or(c2)` is true when at least one holds
- NOT (negate): `c.negate()` inverts the result
- Complex composition chains
- Cross-parameter constraints on `Map<String, Value<?>>`
- Null handling

### Value contract

- Value storage and retrieval
- Parameter name tracking
- Generation timestamp is non-null
- Fingerprint is non-null and non-empty
- Support for complex and null values
- Equality of values with same parameter name

### Immutability

- `AtomicStep.metadata()` returns an unmodifiable map
- Modifying returned collections throws `UnsupportedOperationException`

---

## Troubleshooting common TCK failures

### "Value not in domain"

Your `Parameter.generate()` returned a value outside of `domain()`. Make
sure your generation logic respects domain boundaries.

**Fix**: Ensure `domain.sample()` always returns values for which
`domain.contains()` is true.

### "metadata must be unmodifiable"

The TCK checks that collections returned by your implementation are
unmodifiable. Calling `.put()`, `.add()`, or other mutating operations
on the returned collection must throw `UnsupportedOperationException`.

**Fix**: Use `Map.of()`, `List.of()`, `Collections.unmodifiableMap()`,
or `Map.copyOf()` for all returned collections.

### "fingerprint must not be null or empty"

`Value.fingerprint()` must always return a non-null, non-empty string.

**Fix**: Implement fingerprinting using a hash function (SHA-256
recommended). Handle null wrapped values gracefully.

### "Constraint AND composition failed"

The TCK verifies that `c1.and(c2).test(v)` is true only when both
`c1.test(v)` and `c2.test(v)` are true. If you override the default
`and()` method, make sure it follows short-circuit AND semantics.

**Fix**: Use the default implementation from the `Constraint` interface
unless you have a specific reason to override it.

### "NullPointerException in createValue"

The TCK tests creating a `Value` with a null wrapped value:
`createValue(null, "param1")`. Your implementation must handle this.

**Fix**: Allow null in your `Value` implementation. Do not call
`.toString()` on the value without a null check.

### "Trial validation failed unexpectedly"

Cross-parameter constraints operate on `Map<String, Value<?>>`. Make
sure your `Trial` implementation passes the assignment map to all
registered constraints during `validate()`.

**Fix**: Check that your trial builder stores constraints and that
`trial.validate()` evaluates all of them against the assignments map.

---

## Example: complete TCK test suite

Here is a complete example of a test suite that validates all parameter
contracts:

```java
package com.example.paramodel.tck;

import io.nosqlbench.paramodel.tck.ImplementationProvider;
import io.nosqlbench.paramodel.tck.parameters.*;

// Parameter tests
public class MyParameterTest extends ParameterTCK {
    @Override
    protected ImplementationProvider getProvider() {
        return new MyImplementationProvider();
    }
}

// In separate files:

public class MyDomainTest extends DomainTCK {
    @Override
    protected ImplementationProvider getProvider() {
        return new MyImplementationProvider();
    }
}

public class MyValueTest extends ValueTCK {
    @Override
    protected ImplementationProvider getProvider() {
        return new MyImplementationProvider();
    }
}

public class MyConstraintTest extends ConstraintTCK {
    @Override
    protected ImplementationProvider getProvider() {
        return new MyImplementationProvider();
    }
}

public class MyValidationResultTest extends ValidationResultTCK {
    @Override
    protected ImplementationProvider getProvider() {
        return new MyImplementationProvider();
    }
}
```

Each class goes in its own file. When you run `mvn test`, all inherited
test methods from the TCK base classes are executed against your
implementation.

---

## Next steps

- [How to Implement a Contract](./implement-a-contract.md) -- creating the
  implementation that the TCK validates
- [Contract Types](../reference/contract-types.md) -- full API reference
- [API Packages](../reference/api-packages.md) -- package organization and
  module structure
