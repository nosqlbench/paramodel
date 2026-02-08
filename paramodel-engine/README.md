# Paramodel Engine

Production-ready execution engine implementing the full Paramodel specification.

## Overview

The Paramodel Engine is a complete implementation of the Paramodel API providing:

- **8-Stage Compilation Pipeline** - TestPlan → ExecutionPlan transformation
- **Concurrent Execution Runtime** - Resource-aware parallel execution
- **Scheduling** - Priority-based, work-stealing scheduler
- **Resource Management** - Admission control for CPU, memory, I/O

## Architecture

```
TestPlan (Declarative)
    ↓
┌───────────────────────────────────────────┐
│   Compilation Pipeline (8 Stages)        │
├───────────────────────────────────────────┤
│ 1. Validation                             │
│ 2. Normalization                          │
│ 3. Trial Enumeration                      │
│ 4. Instantiation                          │
│ 5. Step Generation                        │
│ 6. Dependency Analysis                    │
│ 7. Optimization                           │
│ 8. Code Generation                        │
└───────────────────────────────────────────┘
    ↓
ExecutionPlan (Immutable)
    ↓
┌───────────────────────────────────────────┐
│   Execution Runtime                       │
├───────────────────────────────────────────┤
│ • Executor (Thread pool, futures)         │
│ • Scheduler (Work stealing, priorities)   │
│ • Resource Manager (Admission control)    │
└───────────────────────────────────────────┘
    ↓
Results
```

## Components

### Compilation Pipeline

**DefaultCompiler** - Orchestrates 8-stage compilation:
- Validates TestPlan correctness
- Enumerates trial space
- Instantiates concrete values
- Generates atomic steps
- Analyzes dependencies
- Optimizes execution
- Produces ExecutionPlan

**Compilation Stages:**
1. **Validation** - Verify parameters, axes, constraints
2. **Normalization** - Canonicalize representation
3. **Trial Enumeration** - Expand parameter space (Cartesian products)
4. **Instantiation** - Generate concrete values
5. **Step Generation** - Create AtomicSteps
6. **Dependency Analysis** - Build execution DAG
7. **Optimization** - Prune redundant, merge equivalent
8. **Code Generation** - Materialize ExecutionPlan

### Execution Runtime

**DefaultExecutor** - Concurrent execution engine:
- Thread pool-based parallelism
- Configurable concurrency limits
- Progress tracking
- Graceful shutdown

**DefaultScheduler** - Step scheduling:
- Priority-based policies
- Work stealing for load balance
- Admission control integration
- Block/unblock support

**DefaultResourceManager** - Resource tracking:
- CPU, memory, I/O limits
- Semaphore-based capacity
- Atomic acquire/release
- Custom resource types

## Dependencies

- `paramodel-api` - Contract interfaces
- `slf4j-api` - Logging
