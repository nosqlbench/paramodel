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

Every parameter captures six facets:

| Facet          | Purpose                                                    |
|----------------|------------------------------------------------------------|
| **Name**       | Unique identifier within its scope                         |
| **Domain**     | The set of valid values (membership, cardinality, bounds)  |
| **Type**       | The Java type `T` of values produced                       |
| **Constraints**| Predicates that values must satisfy                        |
| **Generation** | Methods for producing values from the domain               |
| **Tags**       | Metadata for classification (e.g., `"type"`, `"unit"`)     |

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
| `tags()`                    | `Map<String, String>`| Metadata tags for this parameter             |

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
| `StringParameter`     | Regex or Set         | `StringParameter.of("region", "us-.*")`                 |
| `SelectionParameter`  | String set or resolver | `SelectionParameter.of("region", Set.of("us-east-1"))` |

## Derived Parameters

A **DerivedParameter** is a parameter whose value is computed from other bound
parameter values. They are evaluated **after** independent parameters are bound
and before validation.

| Method                      | Returns              | Purpose                                      |
|-----------------------------|----------------------|----------------------------------------------|
| `compute(Map<String, Object>)` | `T`               | Compute value from bound independent params  |
| `expression()`              | `String`             | Human-readable description of derivation     |

Derived parameters allow modeling dependencies between configuration values,
such as `batchSize = threads * 2`. They should NOT be used as axes in test
plans, as they are deterministic functions of other parameters.

## Domains

`Domain<T>` is a sealed interface that specifies the valid value space for a
parameter. Every domain supports five operations:

| Operation          | Method              | Purpose                                    |
|--------------------|---------------------|--------------------------------------------|
| Membership testing | `contains(T)`       | Is this value in the domain?               |
| Cardinality        | `cardinality()`     | How many values exist? (`Optional<Long>`)  |
| Sampling           | `sample(Random)`    | Pick a random value                        |
| Enumeration        | `enumerate()`       | Iterate all values (finite domains only)   |
| Boundary values    | `boundaryValues()`  | Return extrema of the domain               |

### Domain Variants

The sealed interface permits exactly four subtypes:

- **Discrete**: A finite set of explicit values.
- **Range**: A min/max bounded interval (inclusive/exclusive).
- **Composite**: Named fields, each with its own domain (e.g., a struct).
- **Custom**: Defined by an arbitrary membership predicate.

## Parameter Binding and the Binding Tree

In a study context, parameters must be bound to values for each trial. The
`ParameterBinder` orchestrates this process, producing a `ParameterBinding`
which contains both the final assignments and any validation errors.

### The Binding Node

Elements and their dependencies form a hierarchical **Binding Tree**. Each
`BindingNode` in this tree corresponds to an element instance and manages
its own parameter scope.

- **Global Inputs**: Provided by the virtual root of the tree.
- **Cascaded Inputs**: Merged from parent nodes (dependencies) down to children.
- **Local Inputs**: Overrides provided specifically for one node.

### Binding Policies

The `BindingPolicy` determines how the binder behaves when required values
are missing or when multiple inputs conflict:

- **STRICT**: Fail if any required parameter lacks a value.
- **DEFAULT_IF_MISSING**: Use the parameter's default value if available.
- **IGNORE_UNKNOWN**: Silently skip inputs that don't match any parameter.

## Value and Provenance

`Value<T>` wraps a concrete parameter assignment with metadata for traceability:

| Field                 | Type                | Purpose                              |
|-----------------------|---------------------|--------------------------------------|
| `value()`             | `T`                 | The actual value                     |
| `parameterName()`     | `String`            | Which parameter produced it          |
| `generatedAt()`       | `Instant`           | When the value was generated         |
| `generatorMetadata()` | `Optional<String>`  | How it was generated (strategy info) |
| `fingerprint()`       | `String`            | SHA-256 hash for deduplication       |

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
