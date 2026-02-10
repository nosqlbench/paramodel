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

# How to Define Parameters

This guide provides self-contained recipes for creating parameters, domains,
and constraints in Paramodel. Parameters represent testable dimensions --
each one defines a named value space with a domain, type, and optional
constraints.

> **Background**: See [Parameters and Domains](../concepts/parameters-and-domains.md)
> for the conceptual model. For a complete walkthrough, see
> [First Test Plan](../tutorials/first-test-plan.md).

---

## Create a discrete string domain

Use `MockDomain.of(...)` with varargs to create a discrete domain from an
explicit set of values.

```java
import io.nosqlbench.paramodel.mock.parameters.MockDomain;

MockDomain<String> ops = MockDomain.of("read", "write", "scan");

// Check membership
assert ops.contains("read");   // true
assert !ops.contains("delete"); // false

// Inspect cardinality
assert ops.cardinality().orElse(0L) == 3L;

// Boundary values (for discrete domains, all values are boundaries)
assert ops.boundaryValues().contains("read");
```

---

## Create a discrete integer domain

The same `MockDomain.of(...)` factory works for any type, including
`Integer`.

```java
import io.nosqlbench.paramodel.mock.parameters.MockDomain;

MockDomain<Integer> threads = MockDomain.of(1, 2, 4, 8, 16);

assert threads.contains(4);   // true
assert !threads.contains(3);  // false
assert threads.cardinality().orElse(0L) == 5L;
```

---

## Create a range domain

For continuous or large integer ranges, use `MockRangeDomain`. It implements
`Domain.Range<T>` and supports membership testing, sampling, enumeration
(for integer types), and boundary value extraction.

```java
import io.nosqlbench.paramodel.mock.parameters.MockRangeDomain;

// Integer range [1, 64]
MockRangeDomain<Integer> threadRange = MockRangeDomain.of(1, 64);

assert threadRange.contains(32);  // true
assert !threadRange.contains(0);  // false
assert threadRange.min() == 1;
assert threadRange.max() == 64;
assert threadRange.cardinality().orElse(0L) == 64L;

// Boundary values are always {min, max}
assert threadRange.boundaryValues().contains(1);
assert threadRange.boundaryValues().contains(64);

// Double range [0.0, 1.0]
MockRangeDomain<Double> temperature = MockRangeDomain.of(0.0, 1.0);

assert temperature.contains(0.5);  // true
assert temperature.cardinality().isEmpty();  // continuous -- not countable
```

---

## Create a parameter from a domain

A `Parameter<T>` binds a name to a `Domain<T>`. Use `MockParameter.of()`
to create one.

```java
import io.nosqlbench.paramodel.mock.parameters.MockDomain;
import io.nosqlbench.paramodel.mock.parameters.MockParameter;
import io.nosqlbench.paramodel.parameters.Parameter;

MockDomain<String> ops = MockDomain.of("read", "write", "scan");
Parameter<String> operation = MockParameter.of("operation", ops);

assert operation.name().equals("operation");
assert operation.domain().contains("read");

// Generate a value from the domain
String value = operation.generate();
assert ops.contains(value);

// Generate a boundary value
String boundary = operation.generateBoundary();
assert ops.contains(boundary);

// Generate a random value
String random = operation.generateRandom();
assert ops.contains(random);
```

---

## Use built-in typed parameters

The `io.nosqlbench.paramodel.parameters.types` package provides concrete
parameter types with built-in domain implementations. These are ready to
use without constructing separate domain objects.

### IntegerParameter

```java
import io.nosqlbench.paramodel.parameters.types.IntegerParameter;

// Range-based: all integers in [1, 64]
IntegerParameter threads = IntegerParameter.range("threads", 1, 64);

assert threads.name().equals("threads");
assert threads.domain().contains(32);
assert !threads.domain().contains(0);

// Discrete: specific integer values
IntegerParameter batchSize = IntegerParameter.of("batch_size",
    java.util.Set.of(32, 64, 128, 256));

assert batchSize.domain().contains(128);
assert !batchSize.domain().contains(100);
```

### DoubleParameter

```java
import io.nosqlbench.paramodel.parameters.types.DoubleParameter;

// Continuous range [0.0, 1.0]
DoubleParameter temp = DoubleParameter.range("temperature", 0.0, 1.0);

assert temp.domain().contains(0.5);
assert !temp.domain().contains(1.5);
```

### BooleanParameter

```java
import io.nosqlbench.paramodel.parameters.types.BooleanParameter;

BooleanParameter cacheEnabled = BooleanParameter.of("enable_cache");

assert cacheEnabled.domain().contains(true);
assert cacheEnabled.domain().contains(false);
```

### SelectionParameter

```java
import io.nosqlbench.paramodel.parameters.types.SelectionParameter;

// Single-select from a set of strings
SelectionParameter region = SelectionParameter.of("region",
    java.util.Set.of("us-east-1", "us-west-2", "eu-west-1"));

// Multi-select: allow up to 2 selections
SelectionParameter tags = SelectionParameter.of("tags",
    java.util.Set.of("fast", "accurate", "cheap", "reliable"))
    .maxSelections(2);
```

---

## Understand boundary values

Every domain exposes boundary values -- the extrema that are critical for
edge-case testing.

| Domain type | Boundary values |
|---|---|
| Discrete `{A, B, C}` | All values (for `MockDomain`) or `{first, last}` (for sorted typed domains) |
| Range `[min, max]` | `{min, max}` |
| Composite | Cartesian product of field boundaries |
| Custom | Best-effort sampling |

```java
import io.nosqlbench.paramodel.mock.parameters.MockRangeDomain;
import io.nosqlbench.paramodel.parameters.types.IntegerParameter;

// Range domain boundaries
MockRangeDomain<Integer> range = MockRangeDomain.of(0, 100);
assert range.boundaryValues().equals(java.util.Set.of(0, 100));

// IntegerParameter discrete boundaries (sorted: min and max)
IntegerParameter batch = IntegerParameter.of("batch",
    java.util.Set.of(32, 64, 128, 256));
assert batch.domain().boundaryValues().contains(32);
assert batch.domain().boundaryValues().contains(256);
```

---

## Add a constraint to a typed parameter

Typed parameters like `IntegerParameter` support a `withConstraint()` method
for adding validation rules. Constraints are checked during `validate()`.

```java
import io.nosqlbench.paramodel.parameters.Constraint;
import io.nosqlbench.paramodel.parameters.ValidationResult;
import io.nosqlbench.paramodel.parameters.types.IntegerParameter;

Constraint<Integer> powerOfTwo = n -> n > 0 && (n & (n - 1)) == 0;

IntegerParameter threads = IntegerParameter.range("threads", 1, 64)
    .withConstraint(powerOfTwo);

// Validation succeeds for valid values
ValidationResult ok = threads.validate(8);
assert ok.isPassed();

// Validation fails for constraint violations
ValidationResult bad = threads.validate(5);
assert bad.isFailed();
assert bad.violations().size() > 0;
```

---

## Validate a parameter value

Use `Parameter.validate(value)` to check domain membership and constraint
satisfaction. The result is a sealed `ValidationResult` with three subtypes:
`Passed`, `Failed`, and `Warning`.

```java
import io.nosqlbench.paramodel.mock.parameters.MockDomain;
import io.nosqlbench.paramodel.mock.parameters.MockParameter;
import io.nosqlbench.paramodel.parameters.ValidationResult;

MockDomain<String> ops = MockDomain.of("read", "write");
var operation = MockParameter.of("operation", ops);

ValidationResult result = operation.validate("read");
assert result.isPassed();

ValidationResult invalid = operation.validate("delete");
assert invalid.isFailed();
assert invalid.message().isPresent();
```

---

## Create multiple parameters for a test plan

Parameters are independent -- create several and then add them to a test plan
as axes. See [How to Build a Test Plan](./build-test-plan.md) for details on
combining parameters into a plan.

```java
import io.nosqlbench.paramodel.mock.parameters.MockDomain;
import io.nosqlbench.paramodel.mock.parameters.MockParameter;
import io.nosqlbench.paramodel.mock.parameters.MockRangeDomain;
import io.nosqlbench.paramodel.parameters.Parameter;

// Define three independent parameters
Parameter<String> operation = MockParameter.of("operation",
    MockDomain.of("read", "write", "scan"));

Parameter<Integer> threads = MockParameter.of("threads",
    MockRangeDomain.of(1, 64));

Parameter<Integer> batchSize = MockParameter.of("batch_size",
    MockDomain.of(32, 64, 128, 256));

// Each parameter is self-contained and can be used independently
assert operation.name().equals("operation");
assert threads.name().equals("threads");
assert batchSize.name().equals("batch_size");
```

---

## Next steps

- [How to Compose Constraints](./compose-constraints.md) -- combine
  constraints with AND, OR, and NOT
- [How to Build a Test Plan](./build-test-plan.md) -- assemble parameters
  into axes and build a test plan
- [Parameters and Domains](../concepts/parameters-and-domains.md) -- the
  conceptual background
