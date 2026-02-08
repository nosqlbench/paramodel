# Paramodel Mock

Simple in-memory mock implementations of the Paramodel API contracts for testing and prototyping.

## Overview

This module provides lightweight mock implementations of all Paramodel contracts using basic Java data structures (HashMap, ArrayList, etc.). These implementations are suitable for:

- Unit testing
- Prototyping and experimentation
- Understanding the API behavior
- Integration testing without external dependencies

## Usage

### Basic Parameter and Domain

```java
// Create a mock domain with discrete values
MockDomain<String> domain = MockDomain.of("read", "write", "scan");

// Create a mock parameter
MockParameter<String> param = MockParameter.of("operation", domain);

// Generate values
String value = param.generate();
```

### Building Trials and Sequences

```java
// Create values
MockValue<String> op = MockValue.of("read", "operation");
MockValue<Integer> threads = MockValue.of(16, "threads");

// Build a trial
MockTrial trial = MockTrial.builder()
    .assignment("operation", op)
    .assignment("threads", threads)
    .build();

// Build a sequence
MockSequence sequence = MockSequenceBuilder.create()
    .addTrial(trial)
    .build();
```

### Creating Test Plans

```java
// Create parameters
MockParameter<String> opParam = MockParameter.of("operation",
    MockDomain.of("read", "write"));
MockParameter<Integer> threadParam = MockParameter.of("threads",
    MockDomain.of(1, 2, 4, 8, 16));

// Build test plan
MockTestPlan plan = MockTestPlan.builder()
    .parameter(opParam)
    .parameter(threadParam)
    .axis(MockAxis.of("operations",
        MockElement.exhaustive("operation")))
    .axis(MockAxis.of("concurrency",
        MockElement.boundary("threads")))
    .optimizationStrategy(OptimizationStrategy.PRUNE_REDUNDANT)
    .build();

// Validate
ValidationResult result = plan.validate();

// Commit to execution plan
if (result.isValid()) {
    ExecutionPlan execPlan = plan.commit();
}
```

### Working with Execution Plans

```java
// Create atomic steps
MockAtomicStep step1 = MockAtomicStep.of("step1", trial1);
MockAtomicStep step2 = MockAtomicStep.of("step2", trial2);

// Build execution graph
MockExecutionGraph graph = MockExecutionGraph.builder()
    .addStep(step1)
    .addStep(step2)
    .addDependency(step2.id(), step1.id()) // step2 depends on step1
    .build();

// Create execution plan
MockExecutionPlan plan = MockExecutionPlan.builder(testPlan)
    .step(step1)
    .step(step2)
    .graph(graph)
    .build();

// Get topological order
List<AtomicStep> ordered = graph.topologicalOrder();
```

### Recording Trial Results

```java
// Success case
MockTrialResult success = MockTrialResult.success(
    trialId,
    Map.of("latency", 42.5, "throughput", 1000)
);

// Failure case
MockTrialResult failure = MockTrialResult.failed(
    trialId,
    new RuntimeException("Connection timeout")
);

// Using builder for more control
MockTrialResult result = MockTrialResult.builder(trialId)
    .observation("latency", 42.5)
    .observation("throughput", 1000)
    .startTime(start)
    .endTime(end)
    .status(Trial.TrialResult.Status.SUCCESS)
    .build();
```

## Module Structure

```
paramodel-mock/
├── core/           # Core contract mocks (Parameter, Domain, Value, etc.)
├── sequence/       # Sequence contract mocks (Trial, Sequence, etc.)
└── plan/           # Plan contract mocks (TestPlan, ExecutionPlan, etc.)
```

## Dependencies

- `paramodel-api` - The contract interfaces
- `junit-jupiter` (test scope)
- `assertj-core` (test scope)

## Design Principles

1. **Simplicity**: Uses basic Java collections (HashMap, ArrayList, HashSet)
2. **Immutability**: Returns unmodifiable views where appropriate
3. **Builder Pattern**: Complex objects provide builders for construction
4. **Static Factories**: Convenient factory methods for common cases
5. **No External Dependencies**: Pure Java implementation (except API contracts)

## Not for Production

These mock implementations are **not designed for production use**. They lack:
- Performance optimizations
- Persistence
- Concurrency safety
- Resource management
- Error recovery

For production implementations, see:
- `paramodel-engine` - Full-featured execution engine
- `paramodel-storage` - Persistence implementations
- `paramodel-distributed` - Distributed execution support

## Testing

All mock implementations are validated against the Technology Compatibility Kit (TCK) in the `paramodel-tck` module.
