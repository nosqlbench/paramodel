# Glossary

**Quick reference for all paramodel and Simplica terminology.**

## Core Paramodel Terms

### Parameter
A testable dimension with a name, domain, type, constraints, and value generator. Parameters are the fundamental building blocks of parameter spaces.

**Example**: `age: integer in range [0, 120] where age >= 0`

### Domain
The set of valid values for a parameter. Can be discrete (finite set), continuous (range), composite (structured), or custom (membership predicate).

**Example**: `Domain.range(0, 100)` or `Domain.of("red", "green", "blue")`

### Constraint
A predicate that parameter values must satisfy. Constraints compose algebraically using AND, OR, and NOT operations.

**Example**: `age >= 18 && age <= 65`

### Value
A parameter assignment with metadata (name, timestamp, provenance). Values are wrapped instances of parameter types.

**Example**: `Value(42, "age", 2026-02-08T10:30:00Z, "random_generator")`

### Sequence
An ordered collection of trials (parameter assignments) to be executed. Sequences are validated before execution and produce deterministic results.

**Example**: 100 trials exploring combinations of model parameters

### Trial
A single point in the parameter space: one value assigned to each parameter. Execution of a trial produces results and artifacts.

**Example**: `{model: "gpt-4", temperature: 0.7, max_tokens: 500}`

### Validation
The process of checking that parameter configurations and sequences satisfy all constraints and are executable.

**Types**: Parameter validation, cross-parameter validation, sequence validation, plan validation

### Composite Parameter
A structured parameter composed of multiple fields with cross-parameter constraints.

**Example**: A registration form with `age` and `hasParentConsent` fields where `age >= 18 OR hasParentConsent == true`

## Simplica-Specific Terms

### Test Plan
A user-authored declarative specification of a study including axes, elements, dependencies, and execution policies. Test Plans are validated before commitment.

**Immutable after commitment**.

### Execution Plan
A compiled, immutable, atomic plan derived from a validated Test Plan. Contains all scheduling decisions, barriers, retry policies, and operational sequencing.

**Always locked to its source Test Plan**.

### Axis
A named parameter dimension in Simplica with an ordered set of discrete values. The Cartesian product of axes defines the trial space.

**Example**: `platform: [linux, windows, macos]`

### Trial Space
The Cartesian product of all axis value sets. The total number of possible parameter combinations.

**Calculation**: `|Space| = |Axis1| × |Axis2| × ... × |AxisN|`

### Element
Any instantiable/deployable unit required for execution: services, environments, caches, datasets, tools. Elements have lifecycles (start → ready → stop → teardown).

**Examples**: Database instance, Docker container, ML model serving endpoint

### Relationship Type
The user-selected semantic for how elements relate, which fully determines concurrency and serialization rules.

**Types**: Mutually Exclusive, Shared, Instanced-Per

### Barrier
An explicit execution step that blocks progression until a condition is met (resource availability, lifecycle completion). Barriers enforce resource constraints.

**Example**: Wait for database to be ready before starting trials

### Wait Condition
A predicate that must be satisfied before execution can proceed past a barrier.

**Example**: `element.state == READY && element.health == HEALTHY`

### Run
A concrete execution of an Execution Plan, producing run-level status and a set of trial outcomes.

**States**: Running, Paused, Completed, Partial, Failed, Scrapped

### Partial Run
A run where some trials completed successfully and others did not. Successful results are retained and the run is marked PARTIAL. Can be re-run idempotently.

### Grouping
A set of trials that share elements or resources. Groupings are determined by dependency structure and relationship types. Groups may run serially or concurrently depending on relationships.

## Relationship Type Semantics

### Mutually Exclusive
Elements/resources cannot be used concurrently. Execution must serialize where overlap would occur.

**Example**: Two trials cannot use the same database instance simultaneously

### Shared
Element instances/resources may be shared concurrently where safe.

**Example**: Multiple trials reading from a shared cache

### Instanced-Per
A fresh instance is created for each defined scope (per trial, per group, per run).

**Example**: Each trial gets its own container instance

## Execution Concepts

### Atomic Step
An indivisible unit of execution in an Execution Plan. Steps include element lifecycle operations, orchestration, trial actions, barriers, and checkpoints.

### Scheduler
The runtime component that interprets the Execution Plan's schedule semantics without rewriting plan meaning.

### Resource Orchestrator
Manages lifecycles of elements whose scope exceeds a single trial. Supports overlapping dependencies.

### Trial Executor
Executes a single trial including within-trial orchestration and retry logic.

### Health Monitor
Detects liveness, health, and failure for elements. Emits lifecycle events.

## Durability Concepts

### Idempotency
The property that re-running an Execution Plan after partial success produces the same final results without duplicating successful work.

**Guarantees**: No critical unrecoverable state; missing trials can be computed without recomputing successful ones

### Resumability
The ability to continue execution from a persisted checkpoint after interruption.

### Checkpointable
The property that execution state can be persisted at defined intervals to enable resumption.

## Provenance Concepts

### Provenance Envelope
Structured metadata attached to every result and artifact capturing: study ID, Test Plan ID, Execution Plan version, run ID, trial ID, axis values, configuration fingerprint, timestamps.

### Configuration Fingerprint
A cryptographic hash (e.g., SHA-256) of the exact configuration that produced a result. Enables verifying results link to precise configurations.

### Plan Lineage
The version history and derivation relationships between Test Plans and Execution Plans.

### Provenance Service
The component that generates and validates provenance metadata.

## Ordering Strategies

### Edge-First Ordering
Default trial ordering strategy that prioritizes extrema (boundary values) across axes first to scaffold the parameter space, then fills interior using gap-filling heuristics.

**Benefits**: Early boundary feedback, progressive refinement, informative partial results

### Exhaustive Ordering
Execute all combinations in the Cartesian product systematically.

### Random Ordering
Random sampling from the parameter space.

### Pairwise Ordering
Ensure all pairs of parameter values are covered at least once (combinatorial interaction testing).

### Boundary Ordering
Focus on minimum and maximum values for each parameter.

## Algebraic Concepts

### Associativity
Property where grouping doesn't matter: `(a ∘ b) ∘ c = a ∘ (b ∘ c)`

**Example**: `(c1 AND c2) AND c3 = c1 AND (c2 AND c3)`

### Commutativity
Property where order doesn't matter: `a ∘ b = b ∘ a`

**Example**: `c1 AND c2 = c2 AND c1`

### Identity Element
An element that leaves others unchanged: `a ∘ identity = a`

**Example**: `constraint AND always_true = constraint`

### Composition
Combining multiple elements to form a new element using algebraic operations.

**Example**: Combining parameters into composite parameters, combining constraints with AND/OR

## Validation Terms

### ValidationResult
The outcome of validation: Passed, Failed (with violations), or Warning (with underlying result).

### PreCondition
A constraint that must hold before an operation executes.

### PostCondition
A constraint that must hold after an operation completes.

### Invariant
A constraint that must always hold throughout execution.

### Cross-Parameter Constraint
A constraint relating multiple parameters.

**Example**: `startDate < endDate`

## Persistence Concepts

### Result Store
Persists structured trial metrics (dependent variables) with provenance metadata.

**Formats**: JSON, JSONL, YAML

### Artifact Store
Stores unstructured files associated with trials: logs, model outputs, dataset snapshots.

### Telemetry Recorder
Records operational telemetry for cost estimation: durations, resource usage, retry counts, failure rates.

## Control Concepts

### Execution Controller
Provides run control operations: start, pause, stop. Implements user intervention policies.

### Run State Service
Provides real-time snapshots of execution state and progress: active elements, step progress, trial statuses.

### Event Bus
Publishes execution events and supports callbacks/notifications for milestones.

### User Intervention
Operational actions users can take during execution without modifying the plan: pause, stop, resume.

## Cost Estimation

### Cost Estimator
Infers time and resource cost estimates based on historical telemetry and parameter set similarity.

**Confidence**: Indicates whether sufficient historical data exists for reliable estimates

### Congruent Parameter Set
A historical parameter configuration sufficiently similar to the current one to enable cost inference.

### Simulation Engine
Executes dry-run simulations with assumed failure rates to analyze error handling behavior.

## Version Management

### Version Registry
Persists and resolves plan versions and lineage. Enables looking up plans by (name, version).

### Plan Identity
The unique identifier for a plan: `(name, version)` tuple.

### Ship of Theseus Principle
Any refinement to a plan creates a fundamentally new unique plan. Versioning aids user semantics but the system treats refined plans as distinct.

## Access Control

### Access Control Service
Enforces multi-user permissions and sharing semantics.

### Ownership
The user who created a plan or result. Separate from visibility.

### Visibility
Who can see a plan or result. Does not imply modification rights.

### Grant
Explicit permission for mutating operations on shared plans/results.

## Implementation Terms

### Contract Type
An interface (Java) or trait (Rust) defining the behavioral contract for a component. Implementations must faithfully implement contract semantics.

### Standard API
The canonical API surface exposed by all conforming implementations. Supports rich clients and multi-language interoperability.

### Platform-Agnostic
Independent of specific distributed infrastructure, compute backends, or vendor-specific technologies.

### Faithful Implementation
An implementation that correctly realizes all specified contract types and algebraic laws.

## Metadata Terms

### Study ID
Unique identifier for a collection of related Test Plans and runs exploring a research question.

### Test Plan ID
Unique identifier for a specific Test Plan specification.

### Execution Plan Version
The version number of an Execution Plan, always linked to its source Test Plan.

### Run ID
Unique identifier for a single execution of an Execution Plan.

### Trial ID
Unique identifier for a single trial within a run.

### Axis Values
The specific parameter value assignments for a trial: `{axis1: value1, axis2: value2, ...}`

## Abbreviations

**API**: Application Programming Interface  
**RBAC**: Role-Based Access Control  
**ABAC**: Attribute-Based Access Control  
**JSON**: JavaScript Object Notation  
**JSONL**: JSON Lines (newline-delimited JSON)  
**YAML**: YAML Ain't Markup Language  
**SHA**: Secure Hash Algorithm  
**URI**: Uniform Resource Identifier  
**VM**: Virtual Machine  
**ML**: Machine Learning  

## Cross-References

- **Parameter System**: [10-CORE-CONCEPTS.md](10-CORE-CONCEPTS.md)
- **Algebraic Laws**: [11-ALGEBRAIC-LAWS.md](11-ALGEBRAIC-LAWS.md)
- **Type System**: [12-TYPE-SYSTEM.md](12-TYPE-SYSTEM.md)
- **Test Plans**: [31-TEST-PLANS.md](31-TEST-PLANS.md)
- **Execution Plans**: [32-EXECUTION-PLANS.md](32-EXECUTION-PLANS.md)
- **Dependencies**: [33-DEPENDENCIES.md](33-DEPENDENCIES.md)
- **Durability**: [34-DURABILITY.md](34-DURABILITY.md)
- **Provenance**: [50-RESULT-PERSISTENCE.md](50-RESULT-PERSISTENCE.md)
- **Observability**: [51-OBSERVABILITY.md](51-OBSERVABILITY.md)
- **Trial Ordering**: [53-TRIAL-ORDERING.md](53-TRIAL-ORDERING.md)
