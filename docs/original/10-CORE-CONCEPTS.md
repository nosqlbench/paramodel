# Core Concepts

**Related**: [11-ALGEBRAIC-LAWS.md](11-ALGEBRAIC-LAWS.md) • [12-TYPE-SYSTEM.md](12-TYPE-SYSTEM.md) • [71-EXAMPLES.md](71-EXAMPLES.md)

This document defines the fundamental concepts of the paramodel framework.

## Parameters

A **Parameter** represents a testable dimension with:

### Essential Properties

1. **Name**: Unique identifier within a scope
2. **Domain**: The set of valid values (`Domain<T>`)
3. **Type**: The value type `T` (discrete, continuous, or composite)
4. **Constraints**: Predicates values must satisfy
5. **Generator**: Function producing valid values

### Contract

All parameter implementations must provide:

```java
// Java
interface Parameter<T> {
    String name();
    Domain<T> domain();
    T generate();
    ValidationResult validate(T value);
    boolean satisfies(Constraint<T> constraint);
}
```

```rust
// Rust
trait Parameter<T> {
    fn name(&self) -> &str;
    fn domain(&self) -> &Domain<T>;
    fn generate(&mut self) -> T;
    fn validate(&self, value: &T) -> ValidationResult;
    fn satisfies(&self, constraint: &Constraint<T>) -> bool;
}
```

### Parameter Categories

| Category | Description | Examples |
|----------|-------------|----------|
| **Discrete** | Finite or countably infinite set of values | Enums, integers in range, string literals |
| **Continuous** | Real-valued with min/max bounds | Floating-point ranges, time durations |
| **Composite** | Structured combination of other parameters | Records, tuples, nested objects |

See [12-TYPE-SYSTEM.md](12-TYPE-SYSTEM.md) for complete type hierarchy.

## Domains

A **Domain** specifies the valid value space for a parameter.

### Domain Types

```java
// Java
sealed interface Domain<T> {
    record Discrete<T>(Set<T> values) implements Domain<T> {}
    record Range<T extends Comparable<T>>(T min, T max) implements Domain<T> {}
    record Composite<T>(Map<String, Domain<?>> fields) implements Domain<T> {}
    record Custom<T>(String description, Predicate<T> membership) implements Domain<T> {}
}
```

```rust
// Rust
enum Domain<T> {
    Discrete { values: HashSet<T> },
    Range { min: T, max: T },
    Composite { fields: HashMap<String, Box<Domain<?>>> },
    Custom { description: String, membership: Box<dyn Fn(&T) -> bool> },
}
```

### Domain Operations

- **Membership testing**: `domain.contains(value) -> bool`
- **Size queries**: `domain.cardinality() -> Option<usize>` (Some for finite, None for infinite)
- **Sampling**: `domain.sample(rng) -> T`
- **Enumeration**: `domain.enumerate() -> Iterator<T>` (for finite domains)

## Constraints

A **Constraint** is a predicate that parameter values must satisfy.

### Basic Constraints

```java
// Java
@FunctionalInterface
interface Constraint<T> {
    boolean test(T value);
    
    // Composition methods
    default Constraint<T> and(Constraint<T> other) {
        return value -> this.test(value) && other.test(value);
    }
    
    default Constraint<T> or(Constraint<T> other) {
        return value -> this.test(value) || other.test(value);
    }
    
    default Constraint<T> negate() {
        return value -> !this.test(value);
    }
}
```

```rust
// Rust
trait Constraint<T> {
    fn test(&self, value: &T) -> bool;
    
    fn and<C: Constraint<T>>(self, other: C) -> And<Self, C> 
    where Self: Sized {
        And { first: self, second: other }
    }
    
    fn or<C: Constraint<T>>(self, other: C) -> Or<Self, C>
    where Self: Sized {
        Or { first: self, second: other }
    }
    
    fn negate(self) -> Not<Self>
    where Self: Sized {
        Not { inner: self }
    }
}
```

### Constraint Categories

| Category | Description | Examples |
|----------|-------------|----------|
| **PreCondition** | Must hold before operation | `age >= 0`, `buffer != null` |
| **PostCondition** | Must hold after operation | `result.length > 0`, `balance >= 0` |
| **Invariant** | Must always hold | `size == items.count()`, `sorted(list)` |
| **CrossParameter** | Relates multiple parameters | `startDate < endDate`, `width * height <= maxArea` |

### Algebraic Properties

Constraints form a Boolean algebra:

- **Conjunction**: `c1 ∧ c2` (AND)
- **Disjunction**: `c1 ∨ c2` (OR)
- **Negation**: `¬c` (NOT)
- **Identity**: `true` (always satisfied), `false` (never satisfied)

See [11-ALGEBRAIC-LAWS.md](11-ALGEBRAIC-LAWS.md) for formal properties.

## Values

A **Value** is a parameter assignment with metadata.

### Value Wrapper

```java
// Java
record Value<T>(
    T value,
    String parameterName,
    Instant generatedAt,
    Optional<String> generatorMetadata
) {
    public ValidationResult validate(Constraint<T> constraint) {
        return constraint.test(value) 
            ? ValidationResult.passed()
            : ValidationResult.failed("Constraint violation");
    }
}
```

```rust
// Rust
struct Value<T> {
    value: T,
    parameter_name: String,
    generated_at: Instant,
    generator_metadata: Option<String>,
}

impl<T> Value<T> {
    fn validate(&self, constraint: &impl Constraint<T>) -> ValidationResult {
        if constraint.test(&self.value) {
            ValidationResult::Passed
        } else {
            ValidationResult::Failed("Constraint violation".to_string())
        }
    }
}
```

### Value Provenance

Values carry provenance metadata:
- **Source**: How the value was generated (random, boundary, explicit)
- **Timestamp**: When the value was generated
- **Context**: Associated metadata (seed, iteration, etc.)

This enables tracing results back to generation decisions.

## Sequences

A **Sequence** is an ordered collection of parameter assignments (trials).

### Sequence Structure

```java
// Java
interface Sequence {
    List<Trial> trials();
    ValidationResult validate();
    ExecutionPlan compile();
    SequenceMetadata metadata();
}

record Trial(
    String id,
    Map<String, Value<?>> assignments,
    List<Constraint<?>> constraints
) {}
```

```rust
// Rust
trait Sequence {
    fn trials(&self) -> &[Trial];
    fn validate(&self) -> ValidationResult;
    fn compile(&self) -> ExecutionPlan;
    fn metadata(&self) -> &SequenceMetadata;
}

struct Trial {
    id: String,
    assignments: HashMap<String, Value<?>>,
    constraints: Vec<Box<dyn Constraint<?>>>,
}
```

### Sequence Properties

1. **Ordered**: Trials have a defined execution order
2. **Validated**: All constraints satisfied before execution
3. **Deterministic**: Same specification produces same sequence
4. **Traceable**: Each trial linked to parameter configuration

### Sequence Generation Strategies

| Strategy | Description | Use Case |
|----------|-------------|----------|
| **Exhaustive** | All combinations in domain | Small parameter spaces |
| **Random** | Random sampling from domain | Quick coverage estimates |
| **Pairwise** | All pairs of values covered | Interaction testing |
| **Boundary** | Focus on extrema and edges | Boundary condition bugs |
| **Edge-First** | Extrema first, then fill interior | Progressive refinement (Simplica) |
| **Custom** | User-defined generation logic | Domain-specific strategies |

See [53-TRIAL-ORDERING.md](53-TRIAL-ORDERING.md) for Simplica's edge-first strategy.

## Composite Parameters

**Composite Parameters** group related parameters with cross-parameter constraints.

### Structure

```java
// Java
interface CompositeParameter<T> extends Parameter<T> {
    Map<String, Parameter<?>> fields();
    List<Constraint<T>> crossConstraints();
    T compose(Map<String, Value<?>> fieldValues);
}
```

```rust
// Rust
trait CompositeParameter<T>: Parameter<T> {
    fn fields(&self) -> &HashMap<String, Box<dyn Parameter<?>>>;
    fn cross_constraints(&self) -> &[Box<dyn Constraint<T>>];
    fn compose(&self, field_values: &HashMap<String, Value<?>>) -> T;
}
```

### Example: User Registration

```java
// Java
var registration = CompositeParameter.builder()
    .field("age", DiscreteParameter.range(0, 150))
    .field("hasParentConsent", DiscreteParameter.of(true, false))
    .crossConstraint(r -> r.age >= 18 || r.hasParentConsent)
    .build();
```

```rust
// Rust
let registration = CompositeParameter::builder()
    .field("age", DiscreteParameter::range(0, 150))
    .field("hasParentConsent", DiscreteParameter::of(vec![true, false]))
    .cross_constraint(|r: &Registration| r.age >= 18 || r.has_parent_consent)
    .build();
```

## Validation

**Validation** ensures parameter configurations and sequences are executable.

### Validation Levels

1. **Parameter Validation**: Single parameter satisfies its own constraints
2. **Cross-Parameter Validation**: Composite constraints satisfied
3. **Sequence Validation**: All trials satisfy global constraints
4. **Plan Validation**: Execution plan is unambiguous and schedulable (Simplica)

### ValidationResult

```java
// Java
sealed interface ValidationResult {
    record Passed() implements ValidationResult {}
    record Failed(String message, List<String> violations) implements ValidationResult {}
    record Warning(String message, ValidationResult underlying) implements ValidationResult {}
}
```

```rust
// Rust
enum ValidationResult {
    Passed,
    Failed { message: String, violations: Vec<String> },
    Warning { message: String, underlying: Box<ValidationResult> },
}
```

## Parameter Space

The **Parameter Space** is the Cartesian product of all parameter domains.

### Cardinality

For parameters `P1, P2, ..., Pn` with cardinalities `|P1|, |P2|, ..., |Pn|`:

```
|Space| = |P1| × |P2| × ... × |Pn|
```

Example:
- `age: range(0, 120)` → cardinality 121
- `platform: {linux, windows, macos}` → cardinality 3
- `enabled: {true, false}` → cardinality 2
- **Total space**: `121 × 3 × 2 = 726 trials`

### Constrained Space

Constraints reduce the effective space:

```
|ConstrainedSpace| ≤ |Space|
```

The planner must compute the actual executable space considering all constraints.

## Cross-Parameter Dependencies

Dependencies between parameters create scheduling constraints.

### Dependency Types (Simplica)

See [33-DEPENDENCIES.md](33-DEPENDENCIES.md) for complete dependency semantics.

| Type | Semantics | Example |
|------|-----------|---------|
| **Independent** | No shared resources, fully parallel | Two unrelated services |
| **Mutually Exclusive** | Cannot run concurrently | Same database instance |
| **Shared** | Can share resources concurrently | Read-only cache |
| **Instanced-Per** | Fresh instance per scope | Per-trial containers |

Dependencies determine execution order and parallelism in compiled execution plans.

## Metadata and Provenance

All core types carry metadata for traceability.

### Provenance Fields

- **Fingerprint**: Cryptographic hash of configuration
- **Timestamp**: Generation/validation time
- **Version**: Specification version used
- **Generator**: Tool/method that created the element
- **Lineage**: Parent/derived relationships

See [50-RESULT-PERSISTENCE.md](50-RESULT-PERSISTENCE.md) for result provenance.

## Summary

The core concepts form a layered abstraction:

```
Sequences (ordered trials)
    ↓
Trials (parameter assignments)
    ↓
Values (typed assignments with metadata)
    ↓
Parameters (domains + constraints + generation)
    ↓
Domains (valid value spaces)
```

Each layer builds on the one below, maintaining type safety and algebraic properties throughout.

## Next Steps

- [11-ALGEBRAIC-LAWS.md](11-ALGEBRAIC-LAWS.md) - Formal composition rules
- [12-TYPE-SYSTEM.md](12-TYPE-SYSTEM.md) - Complete type hierarchy
- [21-CONTRACT-TYPES.md](21-CONTRACT-TYPES.md) - Interface specifications
- [71-EXAMPLES.md](71-EXAMPLES.md) - Practical examples
