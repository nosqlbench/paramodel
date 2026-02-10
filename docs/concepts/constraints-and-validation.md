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

# Constraints and Validation

Constraints express the rules that parameter values must obey. Paramodel
treats constraints as first-class composable predicates that form a Boolean
algebra, and provides a structured validation result type to report how
values fare against those predicates.

## Constraint as a Functional Interface

`Constraint<T>` is a `@FunctionalInterface` in
`io.nosqlbench.paramodel.parameters`. Its single abstract method is:

```java
boolean test(T value);
```

Because it is a SAM type, constraints can be written as lambdas:

```java
Constraint<Integer> positive = n -> n > 0;
Constraint<String> nonEmpty  = s -> s != null && !s.isEmpty();
```

`test` must be a **pure function** -- no side effects, deterministic, and
thread-safe.

## Composition Operators

Constraints compose through three default methods that mirror Boolean logic:

| Operator    | Method              | Semantics                                 |
|-------------|---------------------|-------------------------------------------|
| AND         | `c1.and(c2)`        | True only if both are satisfied           |
| OR          | `c1.or(c2)`         | True if at least one is satisfied         |
| NOT         | `c.negate()`        | True if the original is not satisfied     |

These operators form a complete Boolean algebra with the following laws:

| Law              | Expression                                |
|------------------|-------------------------------------------|
| Associativity    | `(a AND b) AND c  =  a AND (b AND c)`    |
| Commutativity    | `a AND b  =  b AND a`                    |
| Distributivity   | `a AND (b OR c)  =  (a AND b) OR (a AND c)` |
| Identity         | `a AND true  =  a`                        |
| Annihilation     | `a AND false  =  false`                   |
| Idempotence      | `a AND a  =  a`                           |
| De Morgan        | `NOT(a AND b)  =  NOT(a) OR NOT(b)`       |
| Double Negation  | `NOT(NOT(a))  =  a`                       |

Short-circuit evaluation is used: `and` stops on the first `false`, `or`
stops on the first `true`.

Each constraint also exposes a `description()` method that returns a
human-readable label, useful in error messages and diagnostics.

## Constraint Categories

Although all constraints share the same `Constraint<T>` type, they fill
different semantic roles:

| Category           | When It Must Hold | Example                            |
|--------------------|-------------------|------------------------------------|
| **PreCondition**   | Before an operation | `buffer != null`, `age >= 0`     |
| **PostCondition**  | After an operation  | `result.length > 0`             |
| **Invariant**      | Always              | `size == items.count()`          |
| **CrossParameter** | Across multiple parameters | `startDate < endDate`     |

Cross-parameter constraints are special: they operate on the full trial
assignment map (`Map<String, Value<?>>`) rather than on a single value.
This lets them express relationships between two or more parameters within
one trial.

## ValidationResult

Validation outcomes are captured by the sealed interface `ValidationResult`
(in `io.nosqlbench.paramodel.parameters`) with three permitted subtypes:

| Subtype     | Meaning                                          | Key Fields                   |
|-------------|--------------------------------------------------|------------------------------|
| `Passed`    | All constraints satisfied                        | (none)                       |
| `Failed`    | One or more constraints violated                 | `msg`, `violations` list     |
| `Warning`   | Technically valid, but suspicious                | `msg`, `underlying` result   |

The interface provides convenience accessors:

| Method          | Returns            | Semantics                                |
|-----------------|--------------------|------------------------------------------|
| `isPassed()`    | `boolean`          | True for `Passed` or `Warning(Passed)`   |
| `isFailed()`    | `boolean`          | True for `Failed` or `Warning(Failed)`   |
| `message()`     | `Optional<String>` | Summary message (`empty` for `Passed`)   |
| `violations()`  | `List<String>`     | Specific constraint failure descriptions |

All result instances are **immutable** records (or record-like sealed
classes), safe for concurrent access.

## Validation Levels

Validation in Paramodel happens at four levels, each building on the
previous:

1. **Parameter-level** -- `Parameter.validate(T)` checks domain membership
   and per-parameter constraints.
2. **Cross-parameter** -- `Trial.validate()` additionally checks constraints
   that relate multiple parameter assignments within a single trial.
3. **Sequence-level** -- `Sequence.validate()` checks every trial in the
   sequence plus global invariants such as unique trial IDs.
4. **Plan-level** -- `TestPlan.validate()` checks structural rules
   (unique names, acyclic dependencies, schedulability) on top of the
   parameter and trial checks.

## Code Examples

### Simple constraint and composition

```java
import io.nosqlbench.paramodel.parameters.Constraint;

Constraint<Integer> positive    = n -> n > 0;
Constraint<Integer> even        = n -> n % 2 == 0;
Constraint<Integer> lessThan100 = n -> n < 100;

// Compose with AND -- all three must hold
Constraint<Integer> combined = positive.and(even).and(lessThan100);

assert combined.test(42);    // positive, even, < 100
assert !combined.test(-2);   // not positive
assert !combined.test(3);    // not even
assert !combined.test(200);  // not < 100
```

### Cross-parameter constraint

```java
import io.nosqlbench.paramodel.parameters.Constraint;
import io.nosqlbench.paramodel.parameters.Value;
import java.util.Map;

Constraint<Map<String, Value<?>>> startBeforeEnd = assignments -> {
    int start = (Integer) assignments.get("startDay").value();
    int end   = (Integer) assignments.get("endDay").value();
    return start < end;
};
```

### Handling validation results

```java
import io.nosqlbench.paramodel.parameters.ValidationResult;

ValidationResult result = parameter.validate(someValue);

switch (result) {
    case ValidationResult.Passed p   -> System.out.println("Valid");
    case ValidationResult.Failed f   -> {
        System.err.println("Invalid: " + f.msg());
        f.violations().forEach(v -> System.err.println("  - " + v));
    }
    case ValidationResult.Warning w  -> System.out.println("Warning: " + w.msg());
}
```

## Further Reading

- [Parameters and Domains](parameters-and-domains.md) -- the value spaces
  that constraints restrict
- [Trials and Sequences](trials-and-sequences.md) -- where cross-parameter
  constraints are evaluated
- [../howto/compose-constraints.md](../howto/compose-constraints.md) --
  recipes for building constraint expressions
- [../reference/contract-types.md](../reference/contract-types.md) --
  formal contract specifications
