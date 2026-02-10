# Paramodel Examples

## Example 1: Simple Integer Parameter

Testing a function with integer inputs.

**Java:**
```java
// Define parameter
DiscreteParameter<Integer> countParam = DiscreteParameter.builder()
    .name("count")
    .domain(Domain.range(0, 100))
    .constraint(n -> n >= 0)
    .build();

// Build sequence
Sequence sequence = Sequence.builder()
    .withParameter(countParam)
    .generateRandom(20)
    .build();

// Execute tests
SequenceExecutor executor = new SequenceExecutor();
executor.execute(sequence, values -> {
    int count = values.get("count");
    assertProcessingSucceeds(count);
    return TestResult.passed();
});
```

**Rust:**
```rust
// Define parameter
let count_param = DiscreteParameter::builder()
    .name("count")
    .domain(Domain::range(0, 100))
    .constraint(|n| *n >= 0)
    .build();

// Build sequence
let sequence = Sequence::builder()
    .with_parameter(count_param)
    .generate_random(20)
    .build()?;

// Execute tests
let executor = SequenceExecutor::new();
executor.execute(&sequence, |values| {
    let count = values.get("count")?;
    assert_processing_succeeds(count);
    Ok(TestResult::passed())
})?;
```

## Example 2: Multiple Parameters with Dependencies

Testing a registration system with dependent parameters.

**Java:**
```java
// Define independent parameters
DiscreteParameter<Integer> ageParam = DiscreteParameter.builder()
    .name("age")
    .domain(Domain.range(0, 150))
    .build();

DiscreteParameter<Boolean> hasParentConsentParam = DiscreteParameter.builder()
    .name("hasParentConsent")
    .domain(Domain.of(true, false))
    .build();

// Define composite with cross-parameter constraint
CompositeParameter<Registration> registrationParam = CompositeParameter.builder()
    .field("age", ageParam)
    .field("hasParentConsent", hasParentConsentParam)
    .constraint(reg -> 
        reg.getAge() >= 18 || reg.getHasParentConsent()
    )
    .build();

// Generate and execute
Sequence sequence = Sequence.builder()
    .withParameter(registrationParam)
    .generatePairwise()
    .validate()
    .build();

executor.execute(sequence, values -> {
    Registration reg = values.get("registration");
    boolean result = registrationService.register(reg);
    assertTrue(result);
    return TestResult.passed();
});
```

**Rust:**
```rust
// Define independent parameters
let age_param = DiscreteParameter::builder()
    .name("age")
    .domain(Domain::range(0, 150))
    .build();

let has_consent_param = DiscreteParameter::builder()
    .name("hasParentConsent")
    .domain(Domain::of(vec![true, false]))
    .build();

// Define composite with cross-parameter constraint
let registration_param = CompositeParameter::builder()
    .field("age", age_param)
    .field("hasParentConsent", has_consent_param)
    .constraint(|reg: &Registration| 
        reg.age >= 18 || reg.has_parent_consent
    )
    .build();

// Generate and execute
let sequence = Sequence::builder()
    .with_parameter(registration_param)
    .generate_pairwise()
    .validate()
    .build()?;

executor.execute(&sequence, |values| {
    let reg = values.get("registration")?;
    let result = registration_service.register(reg)?;
    assert!(result);
    Ok(TestResult::passed())
})?;
```

## Example 3: Boundary Value Testing

Testing with focus on boundary conditions.

**Java:**
```java
DiscreteParameter<Integer> bufferSizeParam = DiscreteParameter.builder()
    .name("bufferSize")
    .domain(Domain.range(0, 1024))
    .boundaryValues(List.of(0, 1, 1023, 1024))
    .build();

Sequence boundarySequence = Sequence.builder()
    .withParameter(bufferSizeParam)
    .generateBoundary()
    .includeNearBoundary(1) // Include values ±1 from boundaries
    .build();

executor.execute(boundarySequence, values -> {
    int size = values.get("bufferSize");
    Buffer buffer = Buffer.allocate(size);
    assertValidBuffer(buffer, size);
    return TestResult.passed();
});
```

**Rust:**
```rust
let buffer_size_param = DiscreteParameter::builder()
    .name("bufferSize")
    .domain(Domain::range(0, 1024))
    .boundary_values(vec![0, 1, 1023, 1024])
    .build();

let boundary_sequence = Sequence::builder()
    .with_parameter(buffer_size_param)
    .generate_boundary()
    .include_near_boundary(1) // Include values ±1 from boundaries
    .build()?;

executor.execute(&boundary_sequence, |values| {
    let size = values.get("bufferSize")?;
    let buffer = Buffer::allocate(size)?;
    assert_valid_buffer(&buffer, size);
    Ok(TestResult::passed())
})?;
```

## Example 4: String Parameter with Patterns

Testing string validation with pattern constraints.

**Java:**
```java
DiscreteParameter<String> emailParam = DiscreteParameter.builder()
    .name("email")
    .domain(Domain.strings())
    .constraint(StringConstraints.matchesPattern(
        "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    ))
    .exampleValues(List.of(
        "user@example.com",
        "test.user+tag@domain.co.uk",
        "name@subdomain.example.org"
    ))
    .build();

Sequence emailSequence = Sequence.builder()
    .withParameter(emailParam)
    .generateFromExamples()
    .generateRandom(10)
    .build();
```

**Rust:**
```rust
let email_param = DiscreteParameter::builder()
    .name("email")
    .domain(Domain::strings())
    .constraint(StringConstraints::matches_pattern(
        r"^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$"
    ))
    .example_values(vec![
        "user@example.com",
        "test.user+tag@domain.co.uk",
        "name@subdomain.example.org"
    ])
    .build();

let email_sequence = Sequence::builder()
    .with_parameter(email_param)
    .generate_from_examples()
    .generate_random(10)
    .build()?;
```

## Example 5: Algebraic Operations

Demonstrating algebraic properties of parameters.

**Java:**
```java
// Create base parameters
Parameter<Integer> p1 = DiscreteParameter.range("x", 0, 10);
Parameter<Integer> p2 = DiscreteParameter.range("y", 0, 10);

// Combine parameters (maintains algebraic properties)
Parameter<Pair<Integer, Integer>> combined = Parameter.combine(p1, p2);

// Verify associativity: (p1 + p2) + p3 = p1 + (p2 + p3)
Parameter<Integer> p3 = DiscreteParameter.range("z", 0, 10);
Parameter<Triple<Integer, Integer, Integer>> left = 
    Parameter.combine(Parameter.combine(p1, p2), p3);
Parameter<Triple<Integer, Integer, Integer>> right = 
    Parameter.combine(p1, Parameter.combine(p2, p3));

// Both produce equivalent sequences
assertEquals(
    Sequence.from(left).generate(100),
    Sequence.from(right).generate(100)
);
```

**Rust:**
```rust
// Create base parameters
let p1 = DiscreteParameter::range("x", 0, 10);
let p2 = DiscreteParameter::range("y", 0, 10);

// Combine parameters (maintains algebraic properties)
let combined = Parameter::combine(p1, p2);

// Verify associativity: (p1 + p2) + p3 = p1 + (p2 + p3)
let p3 = DiscreteParameter::range("z", 0, 10);
let left = Parameter::combine(Parameter::combine(p1.clone(), p2.clone()), p3.clone());
let right = Parameter::combine(p1, Parameter::combine(p2, p3));

// Both produce equivalent sequences
assert_eq!(
    Sequence::from(&left).generate(100)?,
    Sequence::from(&right).generate(100)?
);
```

## Example 6: Custom Parameter Type

Implementing a custom parameter for complex types.

**Java:**
```java
public class DateRangeParameter implements Parameter<DateRange> {
    private final LocalDate minDate;
    private final LocalDate maxDate;
    private final Random random;
    
    @Override
    public DateRange generate() {
        long minDay = minDate.toEpochDay();
        long maxDay = maxDate.toEpochDay();
        
        long startDay = minDay + random.nextLong(maxDay - minDay);
        long endDay = startDay + random.nextLong(maxDay - startDay);
        
        return new DateRange(
            LocalDate.ofEpochDay(startDay),
            LocalDate.ofEpochDay(endDay)
        );
    }
    
    @Override
    public ValidationResult validate(DateRange value) {
        if (value.getStart().isBefore(minDate)) {
            return ValidationResult.failed("Start date before minimum");
        }
        if (value.getEnd().isAfter(maxDate)) {
            return ValidationResult.failed("End date after maximum");
        }
        if (value.getStart().isAfter(value.getEnd())) {
            return ValidationResult.failed("Start date after end date");
        }
        return ValidationResult.passed();
    }
    
    @Override
    public Domain<DateRange> domain() {
        return Domain.custom(DateRange.class, 
            "DateRange[" + minDate + ", " + maxDate + "]");
    }
}
```

**Rust:**
```rust
pub struct DateRangeParameter {
    min_date: NaiveDate,
    max_date: NaiveDate,
    rng: ThreadRng,
}

impl Parameter<DateRange> for DateRangeParameter {
    fn generate(&mut self) -> DateRange {
        let min_day = self.min_date.num_days_from_ce();
        let max_day = self.max_date.num_days_from_ce();
        
        let start_day = self.rng.gen_range(min_day..=max_day);
        let end_day = self.rng.gen_range(start_day..=max_day);
        
        DateRange {
            start: NaiveDate::from_num_days_from_ce_opt(start_day).unwrap(),
            end: NaiveDate::from_num_days_from_ce_opt(end_day).unwrap(),
        }
    }
    
    fn validate(&self, value: &DateRange) -> ValidationResult {
        if value.start < self.min_date {
            return ValidationResult::failed("Start date before minimum");
        }
        if value.end > self.max_date {
            return ValidationResult::failed("End date after maximum");
        }
        if value.start > value.end {
            return ValidationResult::failed("Start date after end date");
        }
        ValidationResult::passed()
    }
    
    fn domain(&self) -> Domain<DateRange> {
        Domain::custom(
            format!("DateRange[{}, {}]", self.min_date, self.max_date)
        )
    }
}
```

## Example 7: Stateful Sequence Testing

Testing sequences where order matters.

**Java:**
```java
// Define state transition sequence
StatefulSequence<AccountState> accountSequence = StatefulSequence.builder()
    .initialState(new AccountState(0))
    .transition("deposit", 
        amount -> DiscreteParameter.range("amount", 1, 1000),
        (state, amount) -> state.deposit(amount))
    .transition("withdraw",
        amount -> DiscreteParameter.range("amount", 1, 100)
            .constraint(a -> a <= state.getBalance()),
        (state, amount) -> state.withdraw(amount))
    .invariant(state -> state.getBalance() >= 0)
    .generateSequence(50)
    .build();

executor.executeStateful(accountSequence, (state, action, value) -> {
    Account account = createAccount(state);
    account.apply(action, value);
    assertEquals(state.getBalance(), account.getBalance());
    return TestResult.passed();
});
```

**Rust:**
```rust
// Define state transition sequence
let account_sequence = StatefulSequence::builder()
    .initial_state(AccountState::new(0))
    .transition("deposit",
        |_| DiscreteParameter::range("amount", 1, 1000),
        |state, amount| state.deposit(amount))
    .transition("withdraw",
        |state| DiscreteParameter::range("amount", 1, 100)
            .constraint(move |a| *a <= state.balance),
        |state, amount| state.withdraw(amount))
    .invariant(|state| state.balance >= 0)
    .generate_sequence(50)
    .build()?;

executor.execute_stateful(&account_sequence, |state, action, value| {
    let mut account = create_account(state);
    account.apply(action, value)?;
    assert_eq!(state.balance, account.balance);
    Ok(TestResult::passed())
})?;
```
