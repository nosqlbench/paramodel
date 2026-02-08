# Simplica: Study Execution System

**Related**: [31-TEST-PLANS.md](31-TEST-PLANS.md) • [32-EXECUTION-PLANS.md](32-EXECUTION-PLANS.md) • [33-DEPENDENCIES.md](33-DEPENDENCIES.md)

**Source**: Based on Simplica Design Specification v0.1 (February 2026)

## What is Simplica?

Simplica is a **contract-first study execution system** built on the paramodel framework. It provides a complete solution for building, validating, and running parameterized scientific and engineering test plans.

### Relationship to Paramodel

```
┌───────────────────────────────────────────────┐
│              Simplica Layer                   │
│  • Test Plan authoring and validation         │
│  • Execution Plan compilation                 │
│  • Resource scheduling and orchestration      │
│  • Result persistence with provenance         │
│  • Operational durability (idempotency)       │
└───────────────────────────────────────────────┘
                      ↓ uses
┌───────────────────────────────────────────────┐
│             Paramodel Layer                   │
│  • Parameter types and domains                │
│  • Constraint algebra                         │
│  • Sequence generation                        │
│  • Validation primitives                      │
│  • Cross-language contracts                   │
└───────────────────────────────────────────────┘
```

**Analogy**: Paramodel provides the algebraic "atoms" (parameters, constraints, domains). Simplica assembles them into complete "molecules" (execution plans with scheduling, barriers, and resource lifecycle management).

## Key Capabilities

### 1. Rigorous Test Plans

User-authored declarative specifications of studies including:
- **Parameter axes** defining high-dimensional spaces
- **Element dependencies** with explicit relationship semantics
- **Trial actions** describing what to execute
- **Execution policies** (retry strategies, intervention rules)

See [31-TEST-PLANS.md](31-TEST-PLANS.md)

### 2. Immutable Execution Plans

Once validated and committed, Test Plans compile to immutable Execution Plans that:
- Contain atomic steps with deterministic scheduling
- Include barriers enforcing resource constraints
- Lock retry policies and error handling
- Cannot be modified (Ship of Theseus principle)

See [32-EXECUTION-PLANS.md](32-EXECUTION-PLANS.md)

### 3. Smart Scheduling

Deterministic resource-aware scheduling derived at planning time:
- **Relationship types** determine concurrency rules (mutually exclusive, shared, instanced-per)
- **Barriers and wait conditions** prevent resource conflicts
- **Groupings** enable shared persistent resources
- **No ambiguity** left for runtime resolution

See [33-DEPENDENCIES.md](33-DEPENDENCIES.md)

### 4. Operational Durability

Built-in guarantees for long-running studies:
- **Idempotent re-runs** after partial success
- **Resumable execution** from checkpoints
- **Partial result retention** on failure
- **No critical unrecoverable state**

See [34-DURABILITY.md](34-DURABILITY.md)

### 5. Complete Provenance

Every result traced to exact configuration:
- **Cryptographic fingerprints** of configurations
- **Immutable plan versions** linked to results
- **Structured metadata** for all artifacts
- **Exportable to user tools** (Jupyter, etc.)

See [50-RESULT-PERSISTENCE.md](50-RESULT-PERSISTENCE.md)

### 6. Real-Time Observability

Comprehensive execution visibility:
- **Real-time progress tracking** of all trials and elements
- **Dependency visualization** showing what blocks what
- **Structured logging** of all atomic steps
- **User intervention controls** (pause, stop, resume)

See [51-OBSERVABILITY.md](51-OBSERVABILITY.md)

## Workflow

### Typical Usage Flow

```
1. Author Test Plan
   • Define parameter axes and values
   • Specify element dependencies
   • Configure execution policies
   ↓
2. Validate Test Plan
   • Check constraint coherence
   • Verify schedulability
   • Resolve ambiguities
   ↓
3. Commit Test Plan
   • Lock the specification
   • Generate immutable Execution Plan
   • Assign version number
   ↓
4. Execute Plan
   • Run trials according to schedule
   • Collect results with provenance
   • Handle errors per policy
   ↓
5. Observe & Intervene (optional)
   • Monitor real-time progress
   • Pause/resume as needed
   • Export partial results
   ↓
6. Complete or Resume
   • Mark run as complete/partial
   • Re-run missing trials idempotently
   • Export final results
```

## Core Concepts

### Axes and Trial Space

An **Axis** is a named parameter dimension with an ordered set of discrete values.

The **Trial Space** is the Cartesian product of all axes.

**Example**:
```
Axes:
  model: [gpt-4, claude-3]           → 2 values
  temperature: [0.0, 0.5, 1.0]       → 3 values
  max_tokens: [100, 500, 1000]       → 3 values

Trial Space: 2 × 3 × 3 = 18 trials
```

### Elements

An **Element** is any instantiable/deployable unit required by execution:
- Services (databases, APIs, models)
- Environments (containers, VMs)
- Caches (persistent data shared across trials)
- Datasets (training/test data)
- Tools (profilers, monitors)

Elements have **lifecycles**: start → ready → running → stop → teardown

### Relationship Types

When elements depend on each other, users must explicitly select the **relationship type**, which fully determines execution semantics:

| Type | Concurrency | Use Case |
|------|-------------|----------|
| **Mutually Exclusive** | Serialize all overlap | Same database instance |
| **Shared** | Allow concurrent use | Read-only cache |
| **Instanced-Per** | Fresh instance per scope | Per-trial containers |

See [33-DEPENDENCIES.md](33-DEPENDENCIES.md) for complete semantics.

### Barriers and Wait Conditions

A **Barrier** is an explicit execution step that blocks progression until conditions are met:
- Resource availability (e.g., "database ready")
- Lifecycle completion (e.g., "teardown finished")
- Mutual exclusion enforcement (e.g., "no other trials using this element")

Barriers are compiled into the Execution Plan at planning time, not decided ad-hoc at runtime.

### Runs and Partial Runs

A **Run** is a concrete execution of an Execution Plan.

A **Partial Run** occurs when some trials succeed and others fail:
- Successful results are retained
- Run marked as `PARTIAL`
- Plan can be re-run to attempt only missing trials (idempotency)

## Goals and Non-Goals

### Goals

✅ Rigorous, unambiguous representation of studies  
✅ Immutable, logically locked Test Plan ↔ Execution Plan relationship  
✅ Deterministic, resource-aware scheduling (not ad-hoc at runtime)  
✅ Robust execution with comprehensive logging and real-time diagnostics  
✅ Structured result persistence with provenance and artifact support  
✅ Operational durability: idempotent re-runs and resumable execution  
✅ Standard API for rich clients (web UI, CLI)  
✅ Contract type libraries in Java and Rust  

### Non-Goals (Current Scope)

❌ Designing user-facing data warehouse or analytics UI  
❌ Defining post-execution analysis hooks  
❌ Specifying particular distributed infrastructure or vendor orchestration  
❌ Perfectly accurate pre-run cost models (only telemetry-based estimation)  

## Immutability Principle

**Once a Test Plan is validated and committed**:

1. The derived Execution Plan is materialized
2. Test Plan and Execution Plan are locked together algebraically
3. **No material aspect** of the Execution Plan is changeable
4. Any change requires updating Test Plan, re-validating, generating new plan

This is a **foundational principle** for:
- Correctness (no ambiguous runtime decisions)
- Repeatability (same plan → same behavior)
- Provenance (results link to exact plan version)

See [61-VERSIONING.md](61-VERSIONING.md) for version management.

## Trial Ordering

Simplica supports multiple trial ordering strategies. The default is **Edge-First**.

### Edge-First Strategy

1. **Execute extrema first**: Trials at the "edges" of the parameter space (combinations of min/max values across axes)
2. **Scaffold the boundary**: This outlines the result "shell" early
3. **Fill interior**: Use gap-filling heuristic to maximize coverage of interior points

**Benefits**:
- Early feedback on boundary conditions
- Progressive refinement of result space
- Informative partial results if execution interrupted

See [53-TRIAL-ORDERING.md](53-TRIAL-ORDERING.md) for complete specification.

## Error Handling

Error handling is **explicitly part of the Test Plan**, not left to runtime defaults.

### Configurable Retry Policies

- **Element deployment**: Retry up to N times on failure
- **Trial actions**: Retry up to N times on failure
- **Trial validity**: Retry if result deemed invalid by user condition

### Partial Failure Handling

Default behavior:
- Retain successful trial results
- Mark run as `PARTIAL`
- Support re-running only failed trials

See [62-ERROR-HANDLING.md](62-ERROR-HANDLING.md)

## User Intervention

Users can intervene operationally **without changing the plan**:

| Action | Behavior |
|--------|----------|
| **Stop and scrap** | Stop execution, discard all results |
| **Pause now** | Stop immediately, discard active trials, keep completed results |
| **Pause after active** | Let current trials finish, then pause |

Operational controls affect **run control only**, not committed plan semantics.

## Multi-User Collaboration

Simplica supports multiple users with sharing:
- Users can share plans and results
- Sharing supports visibility without modification (default)
- Explicit grants required for mutation
- Security posture: separate ownership from visibility

See [60-ACCESS-CONTROL.md](60-ACCESS-CONTROL.md)

## Cost Estimation

Simplica provides cost estimates (time, resources) before execution:

**Rules**:
- If no prior data exists for similar parameter sets → **indicate estimates unavailable**
- If historical data exists → **infer from telemetry**

Simplica records operational telemetry during execution:
- Trial execution durations
- Element startup/teardown times
- Resource usage
- Retry counts and failure rates

See [52-COST-ESTIMATION.md](52-COST-ESTIMATION.md)

## Simulation and Dry Runs

Simplica supports **dry-run simulation** for analysis:
- Assume hypothetical failure rates for elements
- Observe how retry strategies and barriers behave
- Does not modify committed plan (analysis mode only)

## Contract Types

Simplica defines essential contract types (interfaces/traits):

| Contract | Responsibility |
|----------|----------------|
| `TestPlan` | Declarative study specification |
| `ExecutionPlan` | Immutable compiled plan with atomic steps |
| `PlanValidator` | Validates Test Plan and compiled plan |
| `PlanCompiler` | Compiles Test Plan → Execution Plan |
| `Scheduler` | Interprets schedule semantics at runtime |
| `TrialExecutor` | Executes single trials |
| `ResourceOrchestrator` | Manages element lifecycles |
| `HealthMonitor` | Detects element health/failure |
| `ResultStore` | Persists structured results |
| `ArtifactStore` | Stores unstructured artifacts |
| `ExecutionController` | Run control (start/pause/stop) |
| `RunStateService` | Real-time execution snapshots |
| `EventBus` | Publishes execution events |
| `TelemetryRecorder` | Records operational telemetry |
| `CostEstimator` | Infers cost estimates |
| `SimulationEngine` | Dry-run simulation |
| `AccessControlService` | Multi-user permissions |
| `VersionRegistry` | Plan version persistence |
| `ProvenanceService` | Generates provenance metadata |

See [21-CONTRACT-TYPES.md](21-CONTRACT-TYPES.md) for complete specifications.

## Platform Agnosticism

Simplica is intentionally **platform-agnostic**. The specification defines:
- Contract types and behaviors
- Algebraic laws and invariants
- API surface

Implementing stacks (Java, Rust) provide faithful implementations. Specific infrastructure choices (cluster schedulers, storage systems) are left to implementations.

## Summary

Simplica transforms paramodel's algebraic foundations into a production-ready study execution system with:

1. **Rigorous specifications** (Test Plans)
2. **Immutable execution artifacts** (Execution Plans)
3. **Deterministic scheduling** (relationship-driven)
4. **Operational durability** (idempotency, resumability)
5. **Complete provenance** (cryptographic traceability)
6. **Real-time observability** (structured logging, visualization)

## Next Steps

### Understanding Simplica
- [31-TEST-PLANS.md](31-TEST-PLANS.md) - Authoring declarative studies
- [32-EXECUTION-PLANS.md](32-EXECUTION-PLANS.md) - Compiled execution graphs
- [33-DEPENDENCIES.md](33-DEPENDENCIES.md) - Relationship semantics
- [34-DURABILITY.md](34-DURABILITY.md) - Idempotency and resumability

### Advanced Topics
- [50-RESULT-PERSISTENCE.md](50-RESULT-PERSISTENCE.md) - Provenance and artifacts
- [51-OBSERVABILITY.md](51-OBSERVABILITY.md) - Logging and monitoring
- [52-COST-ESTIMATION.md](52-COST-ESTIMATION.md) - Telemetry-based prediction
- [53-TRIAL-ORDERING.md](53-TRIAL-ORDERING.md) - Edge-first strategy
