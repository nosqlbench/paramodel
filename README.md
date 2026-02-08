# Paramodel

Contract-first framework for pseudo-formal parameter modeling and systematic test sequence execution.

## Overview

Paramodel provides a rigorous foundation for defining, compiling, and executing parametric studies. Built on algebraic principles with an emphasis on correctness, composability, and reproducibility.

**Key Features:**
- **Pure contract interfaces** - Zero implementation coupling
- **8-stage compilation pipeline** - TestPlan → ExecutionPlan transformation
- **Concurrent execution engine** - Resource-aware parallel execution
- **Technology Compatibility Kit (TCK)** - Validates implementation conformance
- **Mock implementations** - For testing and prototyping
- **Java 25** - Sealed interfaces, records, pattern matching

## Modules

### paramodel-api
Pure contract interfaces defining the Paramodel specification.

**Packages:**
- `core` - Parameter, Domain, Value, Constraint, ValidationResult
- `sequence` - Trial, Sequence, TrialResult
- `plan` - TestPlan, ExecutionPlan, Axis, Element, ExecutionGraph
- `compilation` - Compiler, CompilationStage, OptimizationPass
- `execution` - Executor, Runtime, Scheduler, ResourceManager
- `observability` - Observer, MetricsExporter, Logger, Profiler
- `persistence` - ResultStore, ExecutionRepository, CheckpointStore
- `cost` - CostEstimator, CostModel, BudgetTracker
- `security` - CredentialManager, AccessControl, AuditLog
- `versioning` - VersionManager, MigrationStrategy, CompatibilityChecker
- `utilities` - ConfigurationManager, SerializationUtil, ValidationUtil

**Artifacts:**
```xml
<dependency>
    <groupId>io.nosqlbench</groupId>
    <artifactId>paramodel-api</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### paramodel-mock
Simple in-memory mock implementations for testing and prototyping.

**Features:**
- Lightweight implementations using Java collections
- Builder patterns for convenient construction
- Static factory methods
- TCK-validated correctness

**Artifacts:**
```xml
<dependency>
    <groupId>io.nosqlbench</groupId>
    <artifactId>paramodel-mock</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <scope>test</scope>
</dependency>
```

### paramodel-tck
Technology Compatibility Kit validating implementation conformance.

**Test Coverage:**
- Core contracts (Parameter, Domain, Value, Constraint, ValidationResult)
- Sequence contracts (Trial, Sequence)
- Plan contracts (TestPlan, ExecutionPlan, ExecutionGraph, AtomicStep)

**Usage:**
```java
public class MyParameterTest extends ParameterTCK {
    @Override
    protected ImplementationProvider getProvider() {
        return new MyImplementationProvider();
    }
}
```

**Artifacts:**
```xml
<dependency>
    <groupId>io.nosqlbench</groupId>
    <artifactId>paramodel-tck</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <scope>test</scope>
</dependency>
```

### paramodel-engine
Production-ready execution engine with full 8-stage compilation pipeline.

**Components:**
- **DefaultCompiler** - 8-stage pipeline (Validation → Code Generation)
- **DefaultExecutor** - Thread pool-based concurrent execution
- **DefaultScheduler** - Priority-based, work-stealing scheduler
- **DefaultResourceManager** - Admission control for CPU/memory/I/O

**Artifacts:**
```xml
<dependency>
    <groupId>io.nosqlbench</groupId>
    <artifactId>paramodel-engine</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## Quick Start

### 1. Define Parameters and Domains

```java
import io.nosqlbench.paramodel.mock.core.*;

// Create discrete domain
MockDomain<String> opDomain = MockDomain.of("read", "write", "scan");
MockDomain<Integer> threadDomain = MockDomain.of(1, 2, 4, 8, 16);

// Create parameters
MockParameter<String> operation = MockParameter.of("operation", opDomain);
MockParameter<Integer> threads = MockParameter.of("threads", threadDomain);
```

### 2. Build TestPlan

```java
import io.nosqlbench.paramodel.mock.plan.*;

TestPlan plan = MockTestPlan.builder()
    .parameter(operation)
    .parameter(threads)
    .axis(MockAxis.of("operations",
        MockElement.exhaustive("operation")))
    .axis(MockAxis.of("concurrency",
        MockElement.boundary("threads")))
    .optimizationStrategy(OptimizationStrategy.PRUNE_REDUNDANT)
    .build();
```

### 3. Compile to ExecutionPlan

```java
import io.nosqlbench.paramodel.engine.compiler.*;

Compiler compiler = DefaultCompiler.builder()
    .standardPipeline()
    .build();

ExecutionPlan executionPlan = compiler.compile(plan);
System.out.println("Trials: " + executionPlan.estimatedTrialCount());
```

### 4. Execute

```java
import io.nosqlbench.paramodel.engine.execution.*;

Executor executor = DefaultExecutor.builder()
    .maxConcurrency(8)
    .build();

List<TrialResult> results = executor.execute(executionPlan, trial -> {
    // Your trial execution logic
    Map<String, Object> observations = runTrial(trial);
    return new TrialResult(trial.id(), observations);
});

executor.shutdown();
```

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    paramodel-api                        │
│  Pure contract interfaces (57 contracts)                │
│  - Core, Sequence, Plan, Compilation, Execution, etc.  │
└─────────────────────────────────────────────────────────┘
                            ▲
                            │ implements
                ┌───────────┴───────────┐
                │                       │
    ┌───────────▼──────────┐ ┌─────────▼──────────┐
    │   paramodel-mock     │ │  paramodel-engine  │
    │  Simple in-memory    │ │  Production engine │
    │  implementations     │ │  - Compiler        │
    │                      │ │  - Executor        │
    │  Validated by:       │ │  - Scheduler       │
    │       │              │ │  - ResourceMgr     │
    │       ▼              │ └────────────────────┘
    │  paramodel-tck       │
    │  (TCK validation)    │
    └──────────────────────┘
```

## Compilation Pipeline

The 8-stage pipeline transforms declarative TestPlans into executable ExecutionPlans:

1. **Validation** - Verify TestPlan correctness
2. **Normalization** - Canonicalize representation
3. **Trial Enumeration** - Expand parameter space (Cartesian products)
4. **Instantiation** - Generate concrete values from domains
5. **Step Generation** - Create AtomicSteps for each trial
6. **Dependency Analysis** - Build execution DAG
7. **Optimization** - Prune redundant, merge equivalent
8. **Code Generation** - Materialize ExecutionPlan

```
TestPlan (mutable) → [8 stages] → ExecutionPlan (immutable)
```

## Execution Model

**Phases:**
1. **Scheduling** - Priority-based work queue
2. **Resource Allocation** - Admission control
3. **Parallel Execution** - Thread pool with futures
4. **Result Collection** - Aggregation of trial results

**Guarantees:**
- Topological ordering respects dependencies
- Resource limits enforced atomically
- Graceful cancellation and shutdown
- Progress tracking and observability

## Design Principles

### Contract-First Architecture
All functionality defined as pure interfaces. Zero implementation coupling. Implementations depend only on contracts.

### Algebraic Foundations
- **Domains** - Value spaces (discrete, continuous, conditional)
- **Parameters** - Named generators over domains
- **Constraints** - Boolean predicates (support AND, OR, NOT)
- **Trials** - Assignments mapping parameters → values
- **Sequences** - Ordered collections of trials

### Immutability
- TestPlan mutable until `commit()`
- ExecutionPlan immutable after compilation
- Values, Trials, Sequences immutable
- Thread-safe by default

### Reproducibility
- Deterministic compilation with same inputs
- Seeded random generation
- Fingerprints for change detection
- Versioning and migration support

## Requirements

- **Java**: 25+ (for sealed interfaces, records, pattern matching)
- **Maven**: 3.9.0+
- **Build**: `mvn clean install`
- **Test**: `mvn test`

## Build

```bash
# Build all modules
mvn clean install

# Run tests
mvn test

# Generate javadocs
mvn javadoc:javadoc

# Skip tests
mvn clean install -DskipTests
```

## Project Structure

```
paramodel/
├── paramodel-api/          # Contract interfaces (57 contracts)
│   └── src/main/java/io/nosqlbench/paramodel/
│       ├── core/           # 7 core contracts
│       ├── sequence/       # 5 sequence contracts
│       ├── plan/           # 7 plan contracts
│       ├── compilation/    # 4 compilation contracts
│       ├── execution/      # 5 execution contracts
│       ├── observability/  # 6 observability contracts
│       ├── persistence/    # 5 persistence contracts
│       ├── cost/           # 4 cost contracts
│       ├── security/       # 3 security contracts
│       ├── versioning/     # 4 versioning contracts
│       └── utilities/      # 3 utility contracts
│
├── paramodel-mock/         # Mock implementations
│   └── src/main/java/io/nosqlbench/paramodel/mock/
│       ├── core/           # Core mocks
│       ├── sequence/       # Sequence mocks
│       └── plan/           # Plan mocks
│
├── paramodel-tck/          # Technology Compatibility Kit
│   ├── src/main/java/io/nosqlbench/paramodel/tck/
│   │   ├── core/           # Core TCK tests
│   │   ├── sequence/       # Sequence TCK tests
│   │   └── plan/           # Plan TCK tests
│   └── src/test/java/      # TCK validation of mock impl
│
└── paramodel-engine/       # Production engine
    └── src/main/java/io/nosqlbench/paramodel/engine/
        ├── compiler/       # 8-stage compilation pipeline
        ├── execution/      # Executor, Scheduler, ResourceManager
        └── observability/  # Metrics, logging, profiling
```

## Documentation

- **API Contracts**: See triple-slash Javadocs in `paramodel-api`
- **Mock Usage**: See `paramodel-mock/README.md`
- **TCK Usage**: See `paramodel-tck/README.md`
- **Engine Usage**: See `paramodel-engine/README.md`

## License

Apache License 2.0

## Contributing

1. All public interfaces must have triple-slash Javadocs
2. New implementations must pass TCK validation
3. Use Java 25 features (sealed, records, pattern matching)
4. Follow existing code style and conventions
5. Add tests for new functionality
