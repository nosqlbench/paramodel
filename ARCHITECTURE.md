# Paramodel Architecture

Detailed architectural documentation for the Paramodel framework.

---

## Table of Contents

1. [Overview](#overview)
2. [Design Principles](#design-principles)
3. [Module Architecture](#module-architecture)
4. [Compilation Pipeline](#compilation-pipeline)
5. [Execution Model](#execution-model)
6. [Data Flow](#data-flow)
7. [Extension Points](#extension-points)

---

## Overview

Paramodel is a **contract-first framework** for systematic parameter space exploration. It transforms declarative test plans into efficient execution plans through an 8-stage compilation pipeline.

### Core Concepts

```
Parameter Space → TestPlan → [Compilation] → ExecutionPlan → Results
```

**Key Abstractions:**
- **Domain** - Value space (discrete, continuous, conditional)
- **Parameter** - Named generator over a domain
- **Trial** - Assignment of values to parameters
- **Sequence** - Ordered collection of trials
- **TestPlan** - Declarative specification (mutable)
- **ExecutionPlan** - Immutable execution blueprint
- **Executor** - Concurrent trial execution engine

---

## Design Principles

### 1. Contract-First Architecture

All functionality defined as pure interfaces with **zero implementation coupling**.

```
┌─────────────────────┐
│   paramodel-api     │  ← Pure contracts (57 interfaces)
│   (Pure Interfaces) │     No implementations allowed
└──────────┬──────────┘
           │ implements
           ▼
┌──────────────────────────────────────┐
│  Implementations (Multiple Modules)  │
│  - paramodel-mock                    │
│  - paramodel-engine                  │
│  - Your custom implementation        │
└──────────────────────────────────────┘
```

**Benefits:**
- Implementations can be swapped
- Testing with mocks is trivial
- Multiple implementations can coexist
- Clear separation of concerns

### 2. Algebraic Foundations

Built on formal mathematical foundations:

```
Domain D = Set of values
Parameter P = (name, D, generator: () → D)
Constraint C = Predicate on assignments
Trial T = Map[Parameter → Value]
Sequence S = List[Trial]
```

**Operations:**
- **Cartesian Product**: D₁ × D₂ × ... × Dₙ
- **Constraint Algebra**: C₁ ∧ C₂, C₁ ∨ C₂, ¬C
- **Trial Filtering**: S' = {t ∈ S | C(t)}

### 3. Immutability

State transitions are explicit and one-way:

```
TestPlan (mutable) → commit() → ExecutionPlan (immutable)
                                      ↓
                                  [Execution]
                                      ↓
                                  Results (immutable)
```

**Guarantees:**
- No accidental mutations
- Thread-safe by default
- Reproducible execution
- Easy reasoning about state

### 4. Type Safety

Leverages Java 25 features:

```java
// Sealed interfaces - closed type hierarchies
public sealed interface Domain<T>
    permits Domain.Discrete, Domain.Continuous, Domain.Conditional {}

// Records - immutable data carriers
public record MockValue<T>(T value, String parameterName, ...)
    implements Value<T> {}

// Pattern matching - exhaustive case analysis
ValidationResult result = switch (validationResult) {
    case Passed p -> handleSuccess();
    case Failed f -> handleFailure(f.violations());
    case Warning w -> handleWarning(w.message());
};
```

---

## Module Architecture

### Layer Diagram

```
┌───────────────────────────────────────────────────┐
│                 Application Layer                 │
│            (Your code using Paramodel)            │
└───────────────────┬───────────────────────────────┘
                    │ depends on
┌───────────────────▼───────────────────────────────┐
│              paramodel-api (Contracts)            │
│  • Core contracts (Parameter, Domain, Value)      │
│  • Sequence contracts (Trial, Sequence)           │
│  • Plan contracts (TestPlan, ExecutionPlan)       │
│  • Compilation contracts (Compiler, Pipeline)     │
│  • Execution contracts (Executor, Scheduler)      │
│  • Observability, Persistence, Cost, Security...  │
└───────────────────┬───────────────────────────────┘
                    │ implemented by
        ┌───────────┴──────────┬───────────────────┐
        ▼                      ▼                   ▼
┌──────────────┐    ┌──────────────────┐   ┌──────────────┐
│ paramodel-   │    │  paramodel-      │   │   Your       │
│ mock         │    │  engine          │   │   Custom     │
│              │    │                  │   │   Impl       │
│ • Simple     │    │ • DefaultCompiler│   │              │
│   in-memory  │    │ • DefaultExecutor│   │              │
│ • For        │    │ • Scheduler      │   │              │
│   testing    │    │ • ResourceMgr    │   │              │
└──────────────┘    └──────────────────┘   └──────────────┘
        │                      │
        └──────────┬───────────┘
                   │ validated by
        ┌──────────▼───────────┐
        │   paramodel-tck      │
        │   (Test Suite)       │
        │                      │
        │ • ParameterTCK       │
        │ • TrialTCK           │
        │ • TestPlanTCK        │
        │ • etc.               │
        └──────────────────────┘
```

### Module Responsibilities

| Module | Purpose | Dependencies |
|--------|---------|--------------|
| **paramodel-api** | Contract definitions | None |
| **paramodel-mock** | Simple implementations | paramodel-api |
| **paramodel-tck** | Contract validation | paramodel-api, JUnit |
| **paramodel-engine** | Production engine | paramodel-api, SLF4J |

---

## Compilation Pipeline

The 8-stage pipeline transforms TestPlan → ExecutionPlan:

```
┌─────────────────────────────────────────────────────────┐
│                  COMPILATION PIPELINE                   │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  TestPlan (Declarative, Mutable)                       │
│      ↓                                                  │
│  ┌───────────────────────────────────────────┐        │
│  │ Stage 1: Validation                       │        │
│  │ • Verify parameter definitions            │        │
│  │ • Check axis references                   │        │
│  │ • Validate constraints                    │        │
│  └───────────────┬───────────────────────────┘        │
│                  ↓                                      │
│  ┌───────────────────────────────────────────┐        │
│  │ Stage 2: Normalization                    │        │
│  │ • Sort parameters alphabetically          │        │
│  │ • Merge duplicate constraints             │        │
│  │ • Canonicalize representation             │        │
│  └───────────────┬───────────────────────────┘        │
│                  ↓                                      │
│  ┌───────────────────────────────────────────┐        │
│  │ Stage 3: Trial Enumeration                │        │
│  │ • Compute Cartesian products              │        │
│  │ • Apply sampling strategies               │        │
│  │ • Generate trial specifications           │        │
│  └───────────────┬───────────────────────────┘        │
│                  ↓                                      │
│  ┌───────────────────────────────────────────┐        │
│  │ Stage 4: Instantiation                    │        │
│  │ • Generate values from domains            │        │
│  │ • Apply fixed values                      │        │
│  │ • Create Trial instances                  │        │
│  └───────────────┬───────────────────────────┘        │
│                  ↓                                      │
│  ┌───────────────────────────────────────────┐        │
│  │ Stage 5: Step Generation                  │        │
│  │ • Create AtomicSteps                      │        │
│  │ • Add execution context                   │        │
│  │ • Compute step identifiers                │        │
│  └───────────────┬───────────────────────────┘        │
│                  ↓                                      │
│  ┌───────────────────────────────────────────┐        │
│  │ Stage 6: Dependency Analysis              │        │
│  │ • Analyze data dependencies               │        │
│  │ • Build directed acyclic graph (DAG)      │        │
│  │ • Insert barriers                         │        │
│  └───────────────┬───────────────────────────┘        │
│                  ↓                                      │
│  ┌───────────────────────────────────────────┐        │
│  │ Stage 7: Optimization                     │        │
│  │ • Prune redundant trials                  │        │
│  │ • Merge equivalent steps                  │        │
│  │ • Reorder for cache locality              │        │
│  └───────────────┬───────────────────────────┘        │
│                  ↓                                      │
│  ┌───────────────────────────────────────────┐        │
│  │ Stage 8: Code Generation                  │        │
│  │ • Materialize AtomicSteps                 │        │
│  │ • Build ExecutionGraph                    │        │
│  │ • Attach metadata                         │        │
│  └───────────────┬───────────────────────────┘        │
│                  ↓                                      │
│  ExecutionPlan (Immutable)                             │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

### Stage Details

#### Stage 1: Validation
**Input**: TestPlan
**Output**: CompilationContext with validation results
**Operations**:
- Check parameter definitions are valid
- Verify axes reference existing parameters
- Validate constraint well-formedness
- Detect circular dependencies

#### Stage 3: Trial Enumeration
**Input**: Normalized TestPlan
**Output**: Trial specifications
**Complexity**: O(|D₁| × |D₂| × ... × |Dₙ|)

**Algorithm**:
```
for each axis combination:
    for each element in axis:
        generate trial_spec
    end
end
```

#### Stage 6: Dependency Analysis
**Input**: AtomicSteps
**Output**: DAG of dependencies
**Algorithm**: Topological sort with cycle detection

```
Build adjacency list
For each step:
    Analyze data dependencies
    Add edges to graph
Validate acyclicity
```

---

## Execution Model

### Concurrent Execution Architecture

```
┌─────────────────────────────────────────────────┐
│              Executor (Orchestrator)            │
│  • Manages thread pool                         │
│  • Coordinates Scheduler and ResourceManager   │
└─────────┬───────────────────────────────────────┘
          │
          ├──────────────┬──────────────┬─────────
          ▼              ▼              ▼
┌──────────────┐ ┌──────────────┐ ┌──────────────┐
│  Scheduler   │ │  Resource    │ │  Observer    │
│              │ │  Manager     │ │              │
│ • Priority   │ │ • CPU slots  │ │ • Metrics    │
│ • Work steal │ │ • Memory     │ │ • Logs       │
│ • Fair queue │ │ • I/O        │ │ • Events     │
└──────────────┘ └──────────────┘ └──────────────┘
```

### Execution Flow

```
1. Schedule(ExecutionPlan)
   └─→ Scheduler enqueues all steps

2. Loop:
   a. step = Scheduler.next()
   b. if ResourceManager.acquire(step.resources):
      └─→ Execute(step)
      └─→ ResourceManager.release(step.resources)

3. Collect results
```

### Scheduling Policies

**FIFO** (First-In-First-Out):
```
Queue: [s1, s2, s3, s4, ...]
Next: s1
```

**PRIORITY** (Higher priority first):
```
PriorityQueue: [(p=10, s3), (p=5, s1), (p=3, s2), ...]
Next: s3
```

**FAIR** (Round-robin across plans):
```
Queues: {plan1: [s1, s2], plan2: [s3, s4]}
Next: Alternate between plans
```

---

## Data Flow

### From Definition to Execution

```
┌─────────────┐
│   Define    │ User defines parameters, domains
│  Parameters │ operation ∈ {read, write, scan}
└──────┬──────┘ threads ∈ {1, 2, 4, 8, 16}
       │
       ▼
┌─────────────┐
│   Create    │ TestPlan.builder()
│  TestPlan   │   .parameter(operation)
└──────┬──────┘   .parameter(threads)
       │          .axis(...)
       ▼          .build()
┌─────────────┐
│  Validate   │ plan.validate()
└──────┬──────┘ → ValidationResult
       │
       ▼
┌─────────────┐
│   Commit    │ plan.commit()
└──────┬──────┘ → ExecutionPlan (immutable)
       │
       ▼
┌─────────────┐
│   Compile   │ compiler.compile(plan)
│  (8 stages) │ → ExecutionPlan with steps
└──────┬──────┘
       │
       ▼
┌─────────────┐
│   Execute   │ executor.execute(execPlan, trialFn)
│  (Parallel) │ → List<TrialResult>
└──────┬──────┘
       │
       ▼
┌─────────────┐
│   Analyze   │ results.stream()
│   Results   │   .filter(...)
└─────────────┘   .collect(...)
```

---

## Extension Points

### 1. Custom Implementations

Implement contracts for your domain:

```java
public class MyParameter<T> implements Parameter<T> {
    @Override
    public String name() { ... }

    @Override
    public Domain<T> domain() { ... }

    @Override
    public T generate() { ... }
}
```

### 2. Custom Compilation Stages

Add new stages to the pipeline:

```java
public class MyOptimizationStage implements CompilationStage {
    @Override
    public String name() { return "MyOptimization"; }

    @Override
    public CompilationContext execute(CompilationContext ctx) {
        // Your optimization logic
        return ctx;
    }
}

Compiler compiler = DefaultCompiler.builder()
    .stage(new ValidationStage())
    .stage(new MyOptimizationStage())  // Custom stage
    .stage(new CodeGenerationStage())
    .build();
```

### 3. Custom Schedulers

Implement scheduling policies:

```java
public class MyScheduler implements Scheduler {
    @Override
    public void schedule(ExecutionPlan plan) { ... }

    @Override
    public Optional<AtomicStep> next() { ... }
}
```

### 4. Custom Resource Managers

Track custom resources:

```java
ResourceManager rm = new DefaultResourceManager();
rm.registerResource("gpu", 4);
rm.registerResource("network_mbps", 1000);
```

---

## Summary

Paramodel's architecture emphasizes:

✓ **Contract-first design** - Pure interfaces, multiple implementations
✓ **Algebraic foundations** - Formal semantics, composable operations
✓ **Immutability** - Thread-safe, reproducible
✓ **Type safety** - Sealed interfaces, records, pattern matching
✓ **Extensibility** - Custom implementations, stages, policies
✓ **Validation** - TCK ensures correctness

This architecture enables systematic parameter space exploration with strong correctness guarantees and flexible execution strategies.
