# Getting Started with Paramodel

## Installation

### Java

Add Paramodel to your project dependencies:

```xml
<dependency>
    <groupId>com.paramodel</groupId>
    <artifactId>paramodel-core</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Rust

Add Paramodel to your `Cargo.toml`:

```toml
[dependencies]
paramodel = "1.0.0"
```

## Quick Start

### Defining a Simple Parameter

**Java:**
```java
DiscreteParameter<Integer> ageParameter = DiscreteParameter.builder()
    .name("age")
    .domain(Domain.range(0, 120))
    .constraint(age -> age >= 0 && age <= 120)
    .build();
```

**Rust:**
```rust
let age_parameter = DiscreteParameter::builder()
    .name("age")
    .domain(Domain::range(0, 120))
    .constraint(|age| *age >= 0 && *age <= 120)
    .build();
```

### Creating a Test Sequence

**Java:**
```java
Sequence sequence = Sequence.builder()
    .withParameter(ageParameter)
    .generateValues(10)
    .validate()
    .build();
```

**Rust:**
```rust
let sequence = Sequence::builder()
    .with_parameter(age_parameter)
    .generate_values(10)
    .validate()
    .build()?;
```

### Executing a Sequence

**Java:**
```java
SequenceExecutor executor = new SequenceExecutor();
Result result = executor.execute(sequence, testCase -> {
    // Your test logic here
    return testCase.run();
});
```

**Rust:**
```rust
let executor = SequenceExecutor::new();
let result = executor.execute(&sequence, |test_case| {
    // Your test logic here
    test_case.run()
})?;
```

## Core Concepts Example

### Composite Parameters

**Java:**
```java
CompositeParameter<User> userParameter = CompositeParameter.builder()
    .field("name", StringParameter.anyString())
    .field("age", ageParameter)
    .field("email", StringParameter.email())
    .constraint(user -> user.getAge() >= 18 || user.getEmail() == null)
    .build();
```

**Rust:**
```rust
let user_parameter = CompositeParameter::builder()
    .field("name", StringParameter::any_string())
    .field("age", age_parameter)
    .field("email", StringParameter::email())
    .constraint(|user| user.age >= 18 || user.email.is_none())
    .build();
```

### Constraint Composition

**Java:**
```java
Constraint<Integer> positive = n -> n > 0;
Constraint<Integer> even = n -> n % 2 == 0;
Constraint<Integer> positiveEven = positive.and(even);
```

**Rust:**
```rust
let positive = |n: &i32| *n > 0;
let even = |n: &i32| *n % 2 == 0;
let positive_even = Constraint::and(positive, even);
```

## Advanced Usage

### Custom Parameter Types

**Java:**
```java
public class CustomParameter implements Parameter<CustomType> {
    @Override
    public CustomType generate() {
        // Generation logic
    }
    
    @Override
    public ValidationResult validate(CustomType value) {
        // Validation logic
    }
    
    @Override
    public Domain<CustomType> domain() {
        // Domain definition
    }
}
```

**Rust:**
```rust
impl Parameter<CustomType> for CustomParameter {
    fn generate(&self) -> CustomType {
        // Generation logic
    }
    
    fn validate(&self, value: &CustomType) -> ValidationResult {
        // Validation logic
    }
    
    fn domain(&self) -> Domain<CustomType> {
        // Domain definition
    }
}
```

### Sequence Strategies

Generate sequences using different strategies:

- **Exhaustive**: Cover all possible combinations
- **Random**: Generate random valid combinations
- **Pairwise**: Cover all pairs of parameter values
- **Boundary**: Focus on boundary values
- **Targeted**: Use custom generation logic

## Best Practices

1. **Define constraints explicitly**: Make parameter constraints clear and testable
2. **Use composite parameters**: Group related parameters together
3. **Validate early**: Validate sequences before execution
4. **Handle errors gracefully**: Use Result types and proper error handling
5. **Test your parameters**: Write unit tests for custom parameter types
6. **Document domains**: Clearly specify the valid range of parameter values
7. **Maintain algebraic properties**: Ensure operations compose correctly

## Next Steps

- Read the [Specification](SPECIFICATION.md) for formal definitions
- Explore the [Architecture](ARCHITECTURE.md) to understand system design
- Check [Examples](EXAMPLES.md) for common use cases
- Review [API Documentation](API_REFERENCE.md) for detailed API information
