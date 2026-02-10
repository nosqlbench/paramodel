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

# How to Compose Constraints

This guide provides self-contained recipes for creating, composing, and
applying constraints in Paramodel. Constraints are predicates that values
must satisfy. The `Constraint<T>` interface is a `@FunctionalInterface`,
so you can express constraints as lambdas. Constraints compose using
boolean algebra: AND, OR, and NOT.

> **Background**: See [Constraints and Validation](../concepts/constraints-and-validation.md)
> for the algebraic properties and theory. See
> [Contract Types](../reference/contract-types.md) for the full API.

---

## Create a simple constraint

`Constraint<T>` is a functional interface with a single `test(T)` method.
Define constraints as lambdas.

```java
import io.nosqlbench.paramodel.parameters.Constraint;

Constraint<Integer> positive = n -> n > 0;

assert positive.test(5);   // true
assert !positive.test(-1); // false
assert !positive.test(0);  // false
```

---

## Compose with AND

Use `.and()` to create a constraint that requires **both** predicates to
hold. The result short-circuits: if the first constraint fails, the second
is not evaluated.

```java
import io.nosqlbench.paramodel.parameters.Constraint;

Constraint<Integer> positive = n -> n > 0;
Constraint<Integer> even = n -> n % 2 == 0;

Constraint<Integer> positiveEven = positive.and(even);

assert positiveEven.test(4);   // true  (positive AND even)
assert !positiveEven.test(3);  // false (positive but NOT even)
assert !positiveEven.test(-2); // false (even but NOT positive)
assert !positiveEven.test(-3); // false (neither)
```

---

## Compose with OR

Use `.or()` to create a constraint satisfied when **at least one** predicate
holds. Short-circuits when the first constraint passes.

```java
import io.nosqlbench.paramodel.parameters.Constraint;

Constraint<Integer> isZero = n -> n == 0;
Constraint<Integer> isMax = n -> n == Integer.MAX_VALUE;

Constraint<Integer> edgeCase = isZero.or(isMax);

assert edgeCase.test(0);                    // true
assert edgeCase.test(Integer.MAX_VALUE);    // true
assert !edgeCase.test(42);                  // false
```

---

## Negate a constraint

Use `.negate()` to invert a constraint. Double negation restores the
original: `c.negate().negate()` is equivalent to `c`.

```java
import io.nosqlbench.paramodel.parameters.Constraint;

Constraint<Integer> even = n -> n % 2 == 0;
Constraint<Integer> odd = even.negate();

assert odd.test(3);  // true
assert odd.test(5);  // true
assert !odd.test(4); // false
```

---

## Build a complex composition

Chain `.and()`, `.or()`, and `.negate()` to express compound rules. Use
parentheses to control precedence.

```java
import io.nosqlbench.paramodel.parameters.Constraint;

Constraint<Integer> positive = n -> n > 0;
Constraint<Integer> lessThan100 = n -> n < 100;
Constraint<Integer> even = n -> n % 2 == 0;
Constraint<Integer> divisibleBy3 = n -> n % 3 == 0;

// Positive, less than 100, and (even or divisible by 3)
Constraint<Integer> complex = positive
    .and(lessThan100)
    .and(even.or(divisibleBy3));

assert complex.test(6);   // true  (positive, <100, even, div-by-3)
assert complex.test(9);   // true  (positive, <100, div-by-3)
assert complex.test(50);  // true  (positive, <100, even)
assert !complex.test(7);  // false (not even, not div-by-3)
assert !complex.test(150); // false (not <100)
assert !complex.test(-6);  // false (not positive)
```

---

## Create a cross-parameter constraint

Cross-parameter constraints check relationships between multiple parameter
values. They operate on a `Map<String, Value<?>>` representing the trial
assignment.

```java
import io.nosqlbench.paramodel.parameters.Constraint;
import io.nosqlbench.paramodel.parameters.Value;
import io.nosqlbench.paramodel.mock.parameters.MockValue;

// Constraint: batchSize must be <= threads * 100
Constraint<java.util.Map<String, Value<?>>> batchThreadRatio = assignment -> {
    Value<?> threadsVal = assignment.get("threads");
    Value<?> batchVal = assignment.get("batch_size");
    if (threadsVal == null || batchVal == null) return true;

    int threads = (Integer) threadsVal.value();
    int batchSize = (Integer) batchVal.value();
    return batchSize <= threads * 100;
};

// Valid assignment: batchSize (128) <= threads (4) * 100
var valid = java.util.Map.<String, Value<?>>of(
    "threads", MockValue.of(4, "threads"),
    "batch_size", MockValue.of(128, "batch_size")
);
assert batchThreadRatio.test(valid);

// Invalid assignment: batchSize (1000) > threads (2) * 100
var invalid = java.util.Map.<String, Value<?>>of(
    "threads", MockValue.of(2, "threads"),
    "batch_size", MockValue.of(1000, "batch_size")
);
assert !batchThreadRatio.test(invalid);
```

---

## Apply a constraint to a trial builder

Cross-parameter constraints are added to trials via the `TrialBuilder`. The
constraint is evaluated during `trial.validate()`.

```java
import io.nosqlbench.paramodel.parameters.Constraint;
import io.nosqlbench.paramodel.parameters.Value;
import io.nosqlbench.paramodel.mock.parameters.MockValue;
import io.nosqlbench.paramodel.mock.sequence.MockTrial;

// Define constraint: threads must be a power of 2
Constraint<java.util.Map<String, Value<?>>> powerOf2Threads = assignment -> {
    Value<?> threadsVal = assignment.get("threads");
    if (threadsVal == null) return true;
    int threads = (Integer) threadsVal.value();
    return threads > 0 && (threads & (threads - 1)) == 0;
};

var trial = MockTrial.builder()
    .id("trial-1")
    .assignment("threads", MockValue.of(8, "threads"))
    .constraint(powerOf2Threads)
    .build();

// Validation passes because 8 is a power of 2
assert trial.validate().isPassed();
```

---

## Add multiple constraints to a trial

You can chain `.constraint()` calls to add several constraints. All
constraints must pass for validation to succeed.

```java
import io.nosqlbench.paramodel.parameters.Constraint;
import io.nosqlbench.paramodel.parameters.Value;
import io.nosqlbench.paramodel.mock.parameters.MockValue;
import io.nosqlbench.paramodel.mock.sequence.MockTrial;

var trial = MockTrial.builder()
    .id("trial-1")
    .assignment("threads", MockValue.of(8, "threads"))
    .constraint(a -> ((Integer) a.get("threads").value()) > 0)
    .constraint(a -> ((Integer) a.get("threads").value()) <= 64)
    .build();

assert trial.validate().isPassed();
```

---

## Validate a value against a constraint manually

Call `constraint.test(value)` directly to check a value without going
through a parameter or trial.

```java
import io.nosqlbench.paramodel.parameters.Constraint;

Constraint<Integer> inRange = n -> n >= 1 && n <= 100;

assert inRange.test(50);   // true
assert !inRange.test(0);   // false
assert !inRange.test(101); // false
```

---

## Validate a Value object against a constraint

`Value<T>` has a `validate(Constraint<T>)` method that wraps the result in
a `ValidationResult`.

```java
import io.nosqlbench.paramodel.mock.parameters.MockValue;
import io.nosqlbench.paramodel.parameters.Constraint;
import io.nosqlbench.paramodel.parameters.ValidationResult;

var ageValue = MockValue.of(25, "age");
Constraint<Integer> adultAge = n -> n >= 18 && n <= 120;

ValidationResult result = ageValue.validate(adultAge);
assert result.isPassed();

var childAge = MockValue.of(10, "age");
ValidationResult childResult = childAge.validate(adultAge);
assert childResult.isFailed();
```

---

## Provide a description for a constraint

Override the `description()` default method to give your constraint a
human-readable label. This improves error messages and debugging output.

```java
import io.nosqlbench.paramodel.parameters.Constraint;

Constraint<Integer> validPort = new Constraint<>() {
    @Override
    public boolean test(Integer port) {
        return port >= 1024 && port <= 65535;
    }

    @Override
    public String description() {
        return "port in range [1024, 65535]";
    }
};

assert validPort.test(8080);
assert !validPort.test(80);
assert validPort.description().equals("port in range [1024, 65535]");
```

---

## Next steps

- [How to Define Parameters](./define-parameters.md) -- creating parameters
  and domains
- [How to Build a Test Plan](./build-test-plan.md) -- using parameters and
  constraints in a test plan
- [Constraints and Validation](../concepts/constraints-and-validation.md) --
  algebraic properties and theory
- [Contract Types](../reference/contract-types.md) -- full API reference
