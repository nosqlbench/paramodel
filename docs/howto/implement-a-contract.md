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

# How to Implement a Contract

This guide shows how to create your own Paramodel implementation. Paramodel
is contract-first: all functionality is defined as interfaces in the
`paramodel-api` module. You implement those interfaces, wire them up through
an `ImplementationProvider`, and validate with the TCK.

> **See also**: [How to Validate with TCK](./validate-with-tck.md) for
> running compliance tests, and [Contract Types](../reference/contract-types.md)
> for the full API reference.

---

## Overview: the implementation pattern

Every Paramodel implementation follows the same pattern:

1. Depend on `paramodel-api` for the contract interfaces
2. Implement the interfaces (`Parameter`, `Domain`, `Value`, `Constraint`, etc.)
3. Create an `ImplementationProvider` that wires up your implementations
4. Depend on `paramodel-tck` and validate with the TCK test suite

The `paramodel-mock` module is the reference implementation -- study it as
a working example.

---

## Step 1: Add the paramodel-api dependency

Add `paramodel-api` to your `pom.xml`. This gives you access to all the
contract interfaces.

```xml
<dependency>
    <groupId>io.nosqlbench</groupId>
    <artifactId>paramodel-api</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

---

## Step 2: Implement Parameter

`Parameter<T>` is the central interface. It requires six methods: `name()`,
`domain()`, `generate()`, `generateBoundary()`, `generateRandom()`,
`validate()`, and `satisfies()`. It also extends `Tagged`, which requires
`tags()`.

```java
package com.example.paramodel;

import io.nosqlbench.paramodel.parameters.*;

import java.util.Map;
import java.util.Objects;
import java.util.Random;

public class MyParameter<T> implements Parameter<T> {
    private final String name;
    private final Domain<T> domain;
    private final Random random = new Random();

    public MyParameter(String name, Domain<T> domain) {
        this.name = Objects.requireNonNull(name);
        this.domain = Objects.requireNonNull(domain);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Map<String, String> tags() {
        return Map.of("name", name);
    }

    @Override
    public Domain<T> domain() {
        return domain;
    }

    @Override
    public T generate() {
        return domain.sample(random);
    }

    @Override
    public T generateBoundary() {
        var boundaries = domain.boundaryValues();
        if (boundaries.isEmpty()) {
            return generate();
        }
        return boundaries.iterator().next();
    }

    @Override
    public T generateRandom() {
        return domain.sample(random);
    }

    @Override
    public ValidationResult validate(T value) {
        if (domain.contains(value)) {
            return new ValidationResult.Passed();
        }
        return new ValidationResult.Failed(
            "Value not in domain",
            java.util.List.of("value " + value + " is outside the parameter domain"));
    }

    @Override
    public boolean satisfies(Constraint<T> constraint) {
        // Sample boundary values and random values to check satisfiability
        for (T boundary : domain.boundaryValues()) {
            if (constraint.test(boundary)) {
                return true;
            }
        }
        for (int i = 0; i < 10; i++) {
            if (constraint.test(domain.sample(random))) {
                return true;
            }
        }
        return false;
    }
}
```

### Contract requirements for Parameter

The TCK verifies these properties:

- **Domain consistency**: `generate()` must return values within `domain()`
- **Constraint satisfaction**: `validate()` must accurately check constraints
- **Immutability**: parameter definitions must be immutable after creation
- **Thread safety**: all methods must be safe for concurrent calls
- **Non-null name**: `name()` must return a non-null, non-empty string

---

## Step 3: Implement Domain

`Domain<T>` is a sealed interface with four permitted subtypes: `Discrete`,
`Range`, `Composite`, and `Custom`. Implement the subtype that matches your
use case.

### Discrete domain example

```java
package com.example.paramodel;

import io.nosqlbench.paramodel.parameters.Domain;

import java.util.*;

public class MyDiscreteDomain<T> implements Domain.Discrete<T> {
    private final Set<T> values;

    public MyDiscreteDomain(Set<T> values) {
        this.values = Set.copyOf(values);
    }

    @Override
    public Set<T> values() {
        return values;
    }

    @Override
    public boolean contains(T value) {
        return values.contains(value);
    }

    @Override
    public Optional<Long> cardinality() {
        return Optional.of((long) values.size());
    }

    @Override
    public T sample(Random rng) {
        var list = new ArrayList<>(values);
        return list.get(rng.nextInt(list.size()));
    }

    @Override
    public Iterator<T> enumerate() {
        return values.iterator();
    }

    @Override
    public Set<T> boundaryValues() {
        return Set.copyOf(values);
    }
}
```

### Range domain example

```java
package com.example.paramodel;

import io.nosqlbench.paramodel.parameters.Domain;

import java.util.*;

public class MyRangeDomain implements Domain.Range<Integer> {
    private final int min;
    private final int max;

    public MyRangeDomain(int min, int max) {
        if (min > max) throw new IllegalArgumentException("min must be <= max");
        this.min = min;
        this.max = max;
    }

    @Override
    public Integer min() { return min; }

    @Override
    public Integer max() { return max; }

    @Override
    public boolean contains(Integer value) {
        return value != null && value >= min && value <= max;
    }

    @Override
    public Optional<Long> cardinality() {
        return Optional.of((long) max - min + 1);
    }

    @Override
    public Integer sample(Random rng) {
        return min + rng.nextInt(max - min + 1);
    }

    @Override
    public Iterator<Integer> enumerate() {
        return new Iterator<>() {
            int current = min;
            @Override public boolean hasNext() { return current <= max; }
            @Override public Integer next() {
                if (!hasNext()) throw new NoSuchElementException();
                return current++;
            }
        };
    }

    @Override
    public Set<Integer> boundaryValues() {
        return min == max ? Set.of(min) : Set.of(min, max);
    }
}
```

---

## Step 4: Implement Value

`Value<T>` wraps a concrete value with provenance metadata. A Java record
is a natural fit.

```java
package com.example.paramodel;

import io.nosqlbench.paramodel.parameters.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

public record MyValue<T>(
    T value,
    String parameterName,
    Instant generatedAt,
    Optional<String> generatorMetadata
) implements Value<T> {

    public MyValue(T value, String parameterName) {
        this(value, parameterName, Instant.now(), Optional.empty());
    }

    @Override
    public ValidationResult validate(Constraint<T> constraint) {
        if (constraint.test(value)) {
            return new ValidationResult.Passed();
        }
        return new ValidationResult.Failed(
            "Constraint validation failed",
            java.util.List.of("value " + value + " failed constraint"));
    }

    @Override
    public String fingerprint() {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String input = parameterName + ":" +
                (value != null ? value.getClass().getName() : "null") + ":" +
                (value != null ? value.toString() : "null");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute fingerprint", e);
        }
    }
}
```

---

## Step 5: Wire up the ImplementationProvider

The `ImplementationProvider` interface from `paramodel-tck` is how the TCK
discovers your implementations. You must implement all factory methods.

```java
package com.example.paramodel;

import io.nosqlbench.paramodel.parameters.*;
import io.nosqlbench.paramodel.tck.ImplementationProvider;
// ... other imports

public class MyImplementationProvider implements ImplementationProvider {

    @Override
    public <T> Parameter<T> createParameter(String name, Domain<T> domain) {
        return new MyParameter<>(name, domain);
    }

    @Override
    public <T> Domain<T> createDiscreteDomain(Iterable<T> values) {
        var set = new java.util.HashSet<T>();
        values.forEach(set::add);
        return new MyDiscreteDomain<>(set);
    }

    @Override
    public <T extends Comparable<T>> Domain<T> createRangeDomain(T min, T max) {
        // Implement or delegate based on type
        // ...
    }

    @Override
    public <T> Value<T> createValue(T value, String parameterName) {
        return new MyValue<>(value, parameterName);
    }

    @Override
    public ValidationResult createValidationResult(boolean valid, String message) {
        if (valid) {
            return new ValidationResult.Passed();
        }
        return new ValidationResult.Failed(message, java.util.List.of(message));
    }

    // ... implement remaining methods
    // Refer to MockImplementationProvider in paramodel-tck for the full list
}
```

The `ImplementationProvider` interface has methods for every contract area:

| Area | Methods |
|---|---|
| Parameters | `createParameter`, `createDiscreteDomain`, `createRangeDomain`, `createValue`, `createValidationResult` |
| Sequences | `createTrial`, `createTrialBuilder`, `createSequence`, `createSequenceBuilder` |
| Plans | `createTestPlan`, `createTestPlanBuilder`, `createAxis`, `createElement`, `createExecutionPlan`, `createAtomicStep`, `createExecutionGraph` |
| Elements | `createTypedElement`, `createElementWithDependencies`, `createElementWithHealthCheck`, `createElementWithScope` |
| Compilation | `createCompiler`, `createCompilationContext`, `createCompilationStage`, `createOptimizationPass` |
| Execution | `createRuntime`, `createExecutor`, `createScheduler`, `createResourceManager`, `createArtifactCollector` |
| Persistence | `createArtifactStore`, `createResultStore`, `createMetadataStore`, `createCheckpointStore`, `createExecutionRepository` |

---

## Step 6: Use the mock module as a reference

The `paramodel-mock` module is a complete reference implementation. Study
these files:

| Contract | Mock implementation |
|---|---|
| `Parameter<T>` | `io.nosqlbench.paramodel.mock.parameters.MockParameter` |
| `Domain.Discrete<T>` | `io.nosqlbench.paramodel.mock.parameters.MockDomain` |
| `Domain.Range<T>` | `io.nosqlbench.paramodel.mock.parameters.MockRangeDomain` |
| `Value<T>` | `io.nosqlbench.paramodel.mock.parameters.MockValue` |
| `ValidationResult` | `io.nosqlbench.paramodel.mock.parameters.MockValidationResult` |
| `TestPlan` | `io.nosqlbench.paramodel.mock.plan.MockTestPlan` |
| `Axis<T>` | `io.nosqlbench.paramodel.mock.plan.MockAxis` |
| `Element` | `io.nosqlbench.paramodel.mock.plan.MockElement` |
| `ExecutionPlan` | `io.nosqlbench.paramodel.mock.plan.MockExecutionPlan` |
| `Trial` | `io.nosqlbench.paramodel.mock.sequence.MockTrial` |
| `Sequence` | `io.nosqlbench.paramodel.mock.sequence.MockSequence` |
| `ImplementationProvider` | `io.nosqlbench.paramodel.tck.mock.MockImplementationProvider` |

---

## Common pitfalls

**Forgetting immutability**: Parameter, Value, and Domain instances must be
immutable after creation. Use `Set.copyOf()`, `List.copyOf()`, and
`Collections.unmodifiable*()` for collections.

**Null handling**: The TCK tests null values. Make sure `Value<T>` handles
`null` wrapped values and `Domain.contains(null)` returns `false` (unless
your domain explicitly allows null).

**Thread safety**: All contract methods must be safe for concurrent use.
Avoid mutable shared state in parameter and domain implementations.

**Domain sealed type**: `Domain<T>` is a sealed interface. You must implement
one of its four permitted subtypes: `Discrete`, `Range`, `Composite`, or
`Custom`. You cannot create an arbitrary `Domain<T>` implementation.

---

## Next steps

- [How to Validate with TCK](./validate-with-tck.md) -- run the TCK to
  verify your implementation
- [Contract Types](../reference/contract-types.md) -- full API reference
- [API Packages](../reference/api-packages.md) -- package organization
