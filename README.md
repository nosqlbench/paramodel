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
- `parameters` - Parameter, Domain, Value, Constraint, ValidationResult, Tagged
- `parameters.types` - IntegerParameter, DoubleParameter, BooleanParameter, SelectionParameter
- `elements` - Element, RelationshipType
- `sequence` - Trial, Sequence, TrialResult, TrialStatus, builders
- `plan` - TestPlan, ExecutionPlan, Axis, Barrier, ExecutionGraph, AtomicStep, TrialOrdering
- `plan.policies` - ExecutionPolicies
- `compilation` - Compiler, CompilationStage, CompilationContext, OptimizationPass
- `execution` - Executor, Runtime, Scheduler, ResourceManager, ArtifactCollector
- `persistence` - ResultStore, ExecutionRepository, CheckpointStore, ArtifactStore, MetadataStore
- `security` - CredentialManager, AccessControl, AuditLog
- `util` - ConfigurationManager, SerializationUtil, ValidationUtil

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
- Parameter contracts (Parameter, Domain, Value, Constraint, ValidationResult)
- Element contracts (Element, RelationshipType)
- Sequence contracts (Trial, Sequence, TrialResult, TrialStatus)
- Plan contracts (TestPlan, ExecutionPlan, ExecutionGraph, AtomicStep, Axis, Barrier, ExecutionPolicies)
- Compilation contracts (Compiler, CompilationContext, CompilationStage)
- Execution contracts (Executor, Runtime, Scheduler, ResourceManager)
- Persistence contracts (ResultStore, ExecutionRepository, CheckpointStore, ArtifactStore, MetadataStore)
- Security contracts (AccessControl, AuditLog, CredentialManager)
- Utility contracts (ConfigurationManager, SerializationUtil, ValidationUtil)

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
- **DefaultResourceManager** - Admission control for resource constraints

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
import io.nosqlbench.paramodel.mock.parameters.*;

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
┌──────────────────────────────────────────────────────────┐
│                     paramodel-api                        │
│  Pure contract interfaces (~52 contracts)                │
│  parameters, elements, sequence, plan, compilation,     │
│  execution, persistence, security, util                  │
└──────────────────────────────────────────────────────────┘
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
- **Verify**: `mvn clean verify` (runs tests + coverage checks)

## Build

```bash
# Build all modules
mvn clean install

# Run tests
mvn test

# Full verification (tests + coverage enforcement + javadoc)
mvn clean verify

# Generate javadocs
mvn javadoc:javadoc

# Skip tests
mvn clean install -DskipTests
```

## Build Quality Gates

The build enforces two quality gates that run during `mvn verify`:

### JaCoCo Coverage Regression Prevention

Each module declares minimum coverage thresholds. The build fails if coverage drops below the configured floor. Thresholds are defined as Maven properties (`jacoco.instruction.minimum`, `jacoco.line.minimum`, `jacoco.method.minimum`) in each module's POM.

| Module | Instruction | Line | Method |
|--------|------------|------|--------|
| paramodel-api | 0.00 (no direct tests) | 0.00 | 0.00 |
| paramodel-mock | 0.00 (default) | 0.00 | 0.00 |
| paramodel-tck | 0.95 | 0.95 | 0.90 |
| paramodel-engine | 0.30 | 0.30 | 0.20 |

Override at the command line to test enforcement:
```bash
mvn clean verify -Djacoco.instruction.minimum=0.99  # will fail
```

### Javadoc Warning Failure

The build fails if `javadoc:jar` produces any warnings (`failOnWarnings` is enabled). This ensures documentation stays consistent with code changes across all modules.

## Project Structure

```
paramodel/
├── paramodel-api/          # Contract interfaces (~52 contracts)
│   └── src/main/java/io/nosqlbench/paramodel/
│       ├── parameters/     # Parameter, Domain, Value, Constraint, ValidationResult
│       │   └── types/      # IntegerParameter, DoubleParameter, BooleanParameter, SelectionParameter
│       ├── elements/       # Element, RelationshipType
│       ├── sequence/       # Trial, Sequence, TrialResult, TrialStatus, builders
│       ├── plan/           # TestPlan, ExecutionPlan, Axis, Barrier, ExecutionGraph
│       │   └── policies/   # ExecutionPolicies
│       ├── compilation/    # Compiler, CompilationStage, CompilationContext, OptimizationPass
│       ├── execution/      # Executor, Runtime, Scheduler, ResourceManager, ArtifactCollector
│       ├── persistence/    # ResultStore, ExecutionRepository, CheckpointStore, ArtifactStore
│       ├── security/       # CredentialManager, AccessControl, AuditLog
│       └── util/           # ConfigurationManager, SerializationUtil, ValidationUtil
│
├── paramodel-mock/         # Mock implementations (mirrors API packages)
│   └── src/main/java/io/nosqlbench/paramodel/mock/
│       ├── parameters/     # MockParameter, MockDomain, MockValue, etc.
│       ├── elements/       # MockElement
│       ├── sequence/       # MockSequence, MockTrial, etc.
│       ├── plan/           # MockTestPlan, MockAxis, MockExecutionGraph, etc.
│       ├── compilation/    # MockCompiler, MockCompilationContext
│       ├── execution/      # MockExecutor, MockScheduler, MockResourceManager
│       ├── persistence/    # MockResultStore, MockCheckpointStore, etc.
│       ├── security/       # MockAccessControl, MockAuditLog, MockCredentialManager
│       └── util/           # MockConfigurationManager, MockSerializationUtil
│
├── paramodel-tck/          # Technology Compatibility Kit
│   ├── src/main/java/io/nosqlbench/paramodel/tck/
│   │   ├── parameters/     # Parameter, Domain, Value, Constraint TCK tests
│   │   ├── elements/       # Element TCK tests
│   │   ├── sequence/       # Sequence, Trial, TrialResult TCK tests
│   │   ├── plan/           # TestPlan, ExecutionPlan, Axis, Barrier TCK tests
│   │   ├── compilation/    # Compiler, CompilationStage TCK tests
│   │   ├── execution/      # Executor, Runtime TCK tests
│   │   ├── persistence/    # Persistence TCK tests
│   │   ├── security/       # Security TCK tests
│   │   └── util/           # Utility TCK tests
│   └── src/test/java/      # TCK validation of mock impl
│
└── paramodel-engine/       # Production engine
    └── src/main/java/io/nosqlbench/paramodel/engine/
        ├── compiler/       # 8-stage compilation pipeline
        └── execution/      # Executor, Scheduler, ResourceManager
```

## Documentation

- **API Contracts**: See triple-slash Javadocs in `paramodel-api` (generate with `mvn javadoc:javadoc`)
- **Quick Start**: See `QUICKSTART.md`
- **Examples**: See `examples/README.md`
- **Coverage Reports**: Generated at `{module}/target/site/jacoco/` and aggregate at `paramodel-tck/target/site/jacoco-aggregate/`

## License

Apache License 2.0

## Contributing

1. All public interfaces must have triple-slash Javadocs
2. New implementations must pass TCK validation
3. Use Java 25 features (sealed, records, pattern matching)
4. Follow existing code style and conventions
5. Add tests for new functionality
6. `mvn clean verify` must pass — this enforces coverage thresholds and javadoc correctness
7. Do not lower per-module coverage thresholds without justification
