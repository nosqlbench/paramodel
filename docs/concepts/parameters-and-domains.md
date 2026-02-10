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

# Parameters and Domains

A **Parameter** is the foundational abstraction in Paramodel. It represents a
single testable dimension -- a named variable whose valid values are drawn from
a well-defined domain, governed by constraints, and produced by pluggable
generators.

## What a Parameter Represents

Every parameter captures five facets:

| Facet          | Purpose                                                    |
|----------------|------------------------------------------------------------|
| **Name**       | Unique identifier within its scope                         |
| **Domain**     | The set of valid values (membership, cardinality, bounds)  |
| **Type**       | The Java type `T` of values produced                       |
| **Constraints**| Predicates that values must satisfy                        |
| **Generation** | Methods for producing values from the domain               |

The `Parameter<T>` interface lives in the package
`io.nosqlbench.paramodel.parameters` and exposes the following core methods:

| Method                      | Returns              | Purpose                                      |
|-----------------------------|----------------------|----------------------------------------------|
| `name()`                    | `String`             | The parameter's unique name                  |
| `domain()`                  | `Domain<T>`          | The value space definition                   |
| `generate()`                | `T`                  | Produce a value (strategy is impl-defined)   |
| `generateBoundary()`        | `T`                  | Produce a boundary (extremum) value          |
| `generateRandom()`          | `T`                  | Produce a uniformly random value             |
| `validate(T)`               | `ValidationResult`   | Check a value against all constraints        |
| `satisfies(Constraint<T>)`  | `boolean`            | Test whether a constraint is satisfiable     |

Parameters are **immutable after creation**, **thread-safe**, and guarantee that
every generated value is a member of the declared domain.

## Parameter Categories

Built-in parameter types are provided in the
`io.nosqlbench.paramodel.parameters.types` sub-package:

| Type                  | Domain Shape         | Example Factory                                         |
|-----------------------|----------------------|---------------------------------------------------------|
| `IntegerParameter`    | Integer range or set | `IntegerParameter.range("threads", 1, 64)`              |
| `DoubleParameter`     | Continuous range     | `DoubleParameter.range("temperature", 0.0, 1.0)`       |
| `BooleanParameter`    | `{true, false}`      | `BooleanParameter.of("enable_cache")`                   |
| `SelectionParameter`  | String set or resolver | `SelectionParameter.of("region", Set.of("us-east-1"))` |

These categories map to common parameter patterns:

- **Discrete** parameters have a finite, enumerable set of values.
- **Continuous** parameters span a numeric range (uncountable).
- **Composite** parameters combine multiple named fields into a structured type.

## Domains

`Domain<T>` is a sealed interface that specifies the valid value space for a
parameter. Every domain supports four operations:

| Operation          | Method              | Purpose                                    |
|--------------------|---------------------|--------------------------------------------|
| Membership testing | `contains(T)`       | Is this value in the domain?               |
| Cardinality        | `cardinality()`     | How many values exist? (`Optional<Long>`)  |
| Sampling           | `sample(Random)`    | Pick a random value                        |
| Enumeration        | `enumerate()`       | Iterate all values (finite domains only)   |
| Boundary values    | `boundaryValues()`  | Return extrema of the domain               |

### Domain Variants

The sealed interface permits exactly four subtypes:

**Discrete** -- a finite set of explicit values.

```
{v1, v2, ..., vn}        cardinality = n        always enumerable
```

**Range** -- a min/max bounded interval.

```
[min, max]                cardinality = max-min+1 (integers) or infinite (doubles)
```

**Composite** -- named fields, each with its own domain.

```
{field1: Domain<A>, field2: Domain<B>}     cardinality = |A| x |B|
```

**Custom** -- defined by an arbitrary membership predicate.

```
{v : T | predicate(v)}   cardinality may be unknown   usually not enumerable
```

## Value and Provenance

`Value<T>` (in `io.nosqlbench.paramodel.parameters`) wraps a concrete parameter
assignment with metadata for traceability:

| Field                 | Type                | Purpose                              |
|-----------------------|---------------------|--------------------------------------|
| `value()`             | `T`                 | The actual value                     |
| `parameterName()`     | `String`            | Which parameter produced it          |
| `generatedAt()`       | `Instant`           | When the value was generated         |
| `generatorMetadata()` | `Optional<String>`  | How it was generated (strategy info) |
| `fingerprint()`       | `String`            | SHA-256 hash for deduplication       |

Values are immutable. They form a provenance chain that links results back
through trials to the exact parameter configuration that produced them.

## The Tagged Interface

`Parameter<T>` extends `Tagged`, a small contract in
`io.nosqlbench.paramodel.parameters` that provides a `name()` and an
unmodifiable `tags()` map. The `tags()` map always contains at least a
`"name"` entry equal to `name()` and may carry additional classification
metadata such as `"type"`.

`Tagged` is also implemented by `Element` and `Axis`, giving the entire type
system a uniform naming and tagging mechanism.

## Algebraic Properties

Parameters compose to form higher-dimensional spaces:

```
Parameter<A> x Parameter<B>  =  two-dimensional space

|Space| = |Domain<A>| x |Domain<B>|
```

When parameters are combined into a test plan as **Axes**, the resulting trial
space is the Cartesian product of all axis values. See
[Test Plans and Axes](test-plans-and-axes.md) for how parameters become axes.

## Code Example

```java
import io.nosqlbench.paramodel.mock.parameters.MockDomain;
import io.nosqlbench.paramodel.mock.parameters.MockParameter;
import io.nosqlbench.paramodel.parameters.Domain;
import io.nosqlbench.paramodel.parameters.Parameter;
import io.nosqlbench.paramodel.parameters.ValidationResult;

import java.util.Set;

// Create a discrete domain
Domain<String> platformDomain = MockDomain.of(Set.of("linux", "windows", "macos"));

// Create a parameter backed by that domain
Parameter<String> platform = MockParameter.of("platform", platformDomain);

// Query the domain
assert platformDomain.contains("linux");
assert platformDomain.cardinality().orElse(0L) == 3L;

// Generate and validate values
String value = platform.generate();
ValidationResult result = platform.validate(value);
assert result.isPassed();
```

## Further Reading

- [Constraints and Validation](constraints-and-validation.md) -- how constraints
  restrict the value space
- [Trials and Sequences](trials-and-sequences.md) -- how parameter values become
  trial assignments
- [Test Plans and Axes](test-plans-and-axes.md) -- how parameters become axes in
  a study
- [../reference/api-packages.md](../reference/api-packages.md) -- full API
  package reference
- [../tutorials/first-test-plan.md](../tutorials/first-test-plan.md) -- hands-on
  walkthrough
- [../howto/define-parameters.md](../howto/define-parameters.md) -- recipe-style
  parameter definitions
