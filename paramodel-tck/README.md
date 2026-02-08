# Paramodel TCK (Technology Compatibility Kit)

Validation suite for ensuring Paramodel API implementations comply with contract specifications.

## Overview

The Paramodel TCK provides comprehensive test suites that validate implementations of the Paramodel API contracts. Any implementation claiming conformance to the Paramodel specification must pass all TCK tests.

## Structure

```
paramodel-tck/
├── core/           # Tests for core contracts (Parameter, Domain, Value, etc.)
├── sequence/       # Tests for sequence contracts (Trial, Sequence, etc.)
└── plan/           # Tests for plan contracts (TestPlan, ExecutionPlan, etc.)
```

## Usage

### 1. Implement the ImplementationProvider

Create a provider that supplies instances of your implementation:

```java
public class MyImplementationProvider implements ImplementationProvider {
    @Override
    public <T> Parameter<T> createParameter(String name, Domain<T> domain) {
        return new MyParameter<>(name, domain);
    }

    @Override
    public <T> Domain<T> createDiscreteDomain(Iterable<T> values) {
        return new MyDomain<>(values);
    }

    // ... implement all provider methods
}
```

### 2. Extend TCK Test Classes

For each contract, extend the corresponding TCK test class:

```java
public class MyParameterTest extends ParameterTCK {
    @Override
    protected ImplementationProvider getProvider() {
        return new MyImplementationProvider();
    }
}

public class MyDomainTest extends DomainTCK {
    @Override
    protected ImplementationProvider getProvider() {
        return new MyImplementationProvider();
    }
}

// ... extend remaining TCK classes
```

### 3. Run Tests

Execute tests using Maven:

```bash
mvn test
```

All TCK tests must pass for your implementation to be considered conformant.

## TCK Test Coverage

### Core Contracts

**ParameterTCK** - Validates:
- Name and domain accessors
- Value generation (with and without Random)
- Value validation
- Metadata access
- Consistent generation with same seed

**DomainTCK** - Validates:
- Value containment checks
- Sampling from value space
- Cardinality computation
- Finite/infinite status
- Consistent sampling with same seed
- Edge cases (empty, single-element domains)

**ValueTCK** - Validates:
- Value storage and retrieval
- Parameter name tracking
- Generation timestamps
- Fingerprint generation
- Null value handling
- Generator metadata
- Complex type support

**ConstraintTCK** - Validates:
- Predicate evaluation
- Logical operators (AND, OR, NOT)
- Constraint composition
- Null handling

**ValidationResultTCK** - Validates:
- Valid/invalid status
- Error messages
- Violation lists
- Warning support

### Sequence Contracts

**TrialTCK** - Validates:
- Unique identifiers
- Assignment storage and retrieval
- Fingerprint computation and stability
- Constraint validation
- Metadata tracking
- Empty trials
- Multiple constraints

**SequenceTCK** - Validates:
- Trial storage
- Order preservation
- Size computation
- Empty sequences
- Metadata tracking
- Builder pattern
- Immutability
- Duplicate trial handling

### Plan Contracts

**TestPlanTCK** - Validates:
- Parameter storage
- Axis organization
- Constraint application
- Optimization strategy
- Validation
- Commit to execution plan
- Immutability after commit
- Metadata tracking

**ExecutionPlanTCK** - Validates:
- Test plan reference
- Atomic step storage
- Execution graph structure
- Trial count estimation
- Metadata tracking
- Creation from committed test plan

**ExecutionGraphTCK** - Validates:
- DAG structure (nodes, edges)
- Dependency tracking
- Topological ordering
- Barrier support
- Empty graph handling

**AtomicStepTCK** - Validates:
- Unique identifiers
- Trial reference
- Execution context
- Immutability

## Test Execution Requirements

All TCK tests are written using JUnit 5 and AssertJ. Implementations must:

1. **Pass all tests** - No test failures allowed
2. **No test modifications** - TCK tests must not be altered
3. **Proper isolation** - Tests must be independent and repeatable
4. **Deterministic behavior** - Same inputs produce same outputs

## Validation Criteria

### Correctness
- Implementations must satisfy all contract specifications
- Behavior must match expected semantics
- Edge cases must be handled appropriately

### API Compliance
- Method signatures must match exactly
- Return types must be compatible
- Exceptions must be thrown as specified

### Immutability
- Collections returned must be unmodifiable
- Defensive copies must be used where appropriate
- State must not change unexpectedly

### Consistency
- Operations with same inputs produce same results
- Fingerprints and identifiers are stable
- Random generation is reproducible with seeds

## Example: Running TCK Against Mock Implementation

```java
// Create provider for mock implementation
public class MockImplementationProvider implements ImplementationProvider {
    @Override
    public <T> Parameter<T> createParameter(String name, Domain<T> domain) {
        return MockParameter.of(name, domain);
    }

    @Override
    public <T> Domain<T> createDiscreteDomain(Iterable<T> values) {
        return MockDomain.of((T[]) values);
    }

    // ... additional implementations
}

// Extend TCK tests
public class MockParameterTest extends ParameterTCK {
    @Override
    protected ImplementationProvider getProvider() {
        return new MockImplementationProvider();
    }
}

// Run: mvn test
```

## Contributing Additional Tests

When adding new contracts to Paramodel API:

1. Create corresponding TCK test class
2. Implement comprehensive test coverage
3. Document validation criteria
4. Update this README

## Dependencies

- `paramodel-api` - Contract interfaces
- `junit-jupiter` - Test framework
- `assertj-core` - Fluent assertions

## Compliance Statement

An implementation is **Paramodel TCK Compliant** if and only if:

1. All TCK tests pass without modification
2. Tests execute in reasonable time (< 10s per test class)
3. Implementation uses only public API contracts
4. No test execution is skipped or disabled

Implementations should document their TCK compliance status and version tested against.
