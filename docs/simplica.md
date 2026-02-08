# Simplica

Design Specification (Draft)

Working Title • Version 0.1 • February 08, 2026

## 1. Overview

Simplica is a contract-first study execution system for building, validating, and running parameterized scientific/engineering test plans. A user-authored Test Plan expresses a high-dimensional parameter space (axes and values) and explicit relationships/dependencies between elements. Simplica compiles a validated Test Plan into an immutable, atomic Execution Plan that can be executed reliably, observed in real time, resumed from checkpoints, and re-run idempotently after partial success. Results are persisted with strong provenance and can be exported into user tools (e.g., Jupyter notebooks).

This specification captures the concepts, behaviors, and design decisions agreed so far. It is intentionally platform-agnostic: implementing stacks (e.g., Java or Rust) provide faithful implementations of the defined contract types and standard API surface.

## 2. Goals and Non-goals

### 2.1 Goals

* Provide a rigorous, unambiguous representation of studies as Test Plans and their compiled Execution Plans.
* Ensure that once a Test Plan is validated and committed, its derived Execution Plan is immutable and logically locked to it.
* Enable deterministic, resource-aware scheduling derived during planning (not ad-hoc at runtime).
* Support robust execution with comprehensive logging, real-time diagnostics, and user intervention controls (pause/stop).
* Persist structured trial results and associated artifacts with consistent schema and clear provenance; support JSON, JSONL, and YAML outputs.
* Guarantee operational durability: idempotent re-runs after partial success and resumable/checkpointable execution.
* Expose all core concepts via a standard API suitable for rich clients (e.g., web UI) and multiple implementation stacks.
* Provide contract type stub libraries in both Java and Rust.

### 2.2 Non-goals (current scope)

* Designing the user-facing data warehouse or analytics UI for results (only persistence and provenance requirements are specified).
* Defining post-execution analysis hooks (beyond ensuring results land in system storage).
* Specifying a particular distributed infrastructure, compute backend, or vendor-specific orchestration technology.
* Perfectly accurate pre-run cost models; cost estimation is based on historical operational telemetry when available.

## 3. Key Concepts and Definitions

Test Plan: A user-authored declarative specification of a study, including parameter axes, trial space, elements, and explicit dependencies/relationships.

Execution Plan: A compiled, atomic, step-wise plan derived from a validated Test Plan. It includes scheduling decisions, barriers, retry strategies, and all operational sequencing needed for execution.

Axis: A named parameter dimension with an ordered set of discrete values. The Cartesian product of axes defines the trial space.

Trial: A single point in the parameter space (an assignment of one value per axis) executed according to the Execution Plan; produces results and artifacts.

Element: Any instantiable/deployable unit required by execution (e.g., services, environments, caches, datasets, tools).

Relationship Type: The user-selected semantic for how elements relate (e.g., mutually exclusive, shared, instanced-per). Relationship type fully determines concurrency/serialization rules.

Barrier / Wait Condition: An explicit execution step that blocks progression until a condition is met (e.g., resource availability, lifecycle completion).

Run: A concrete execution of an Execution Plan, producing run-level status and a set of trial outcomes.

Partial Run: A run where some trials completed successfully and others did not. Partial results are retained and the run is marked partial.

## 4. Test Plan Model

A Test Plan describes WHAT should be tested and the logical structure of the study. It is authored by a user (potentially via a rich client) and includes:

* Axes and values defining the parameter space (potentially high-dimensional).
* Elements required to run trials and the relationships/dependencies between them.
* Trial actions (what constitutes a trial, including orchestration actions within a trial).
* Execution policy selections that must be baked into the compiled Execution Plan (e.g., retry strategies, intervention policies).
* Axis ordering (definition order is the default priority order; users may reorder axes to see its effect on the compiled Execution Plan).

### 4.1 Plan Commitment and Immutability

Once a Test Plan is validated and the user commits it:

* The derived Execution Plan is materialized.
* The Test Plan and Execution Plan are locked together as an algebraic relationship.
* No material aspect of the Execution Plan is changeable after commitment.
* Any change to desired execution behavior requires updating the Test Plan, re-validating, and generating a new Execution Plan (a new plan lineage/version).

This immutability is a foundational principle for correctness, repeatability, and unambiguous provenance.

## 5. Dependency and Relationship Semantics

Dependencies between elements create logical groupings and determine whether resources can be shared serially or concurrently.

### 5.1 Supported Relationship Types

Users explicitly select the relationship type when defining dependencies. The relationship type fully determines execution semantics, including concurrency, serialization, and instance scoping.

At minimum, the system must support:

* Mutually exclusive: Element A and B cannot be used concurrently; execution must serialize where overlap would occur.
* Shared: Element instances/resources may be shared concurrently by dependent components/trials where safe.
* Instanced-per: A fresh instance is created per defined scope (e.g., per trial, per group, per run), as specified by the plan.

### 5.2 Groupings and Shared Persistent Resources

User-defined dependencies can form groupings where cached or persistent resources may be shared across multiple trials.

The planner must:

* Identify group boundaries implied by dependency structure.
* Determine whether groups can run concurrently or must be serialized based on relationship types.
* Insert explicit barriers/waits to prevent illegal overlaps.
* Ensure group lifecycle overlaps are consistent and unambiguous.

## 6. Execution Plan Model

The Execution Plan describes HOW the study will be executed. It is derived from the Test Plan via compilation/planning and is a fully executable, unambiguous, atomic sequence/graph of steps.

### 6.1 Atomic Steps

The Execution Plan is composed of atomic steps, including:

* Element lifecycle steps (start, readiness, stop, teardown).
* Orchestration steps required for trial setup.
* Trial action steps.
* Barriers and waiting conditions.
* Checkpoint steps.
* Logging and event emission steps (conceptually; implementations may integrate these but must preserve semantics).

## 7. Planning and Validation

The planner (compiler) is responsible for producing a fully executable plan with no ambiguities.

Validation must ensure:

* All dependency relationships are coherent and deterministically schedulable.
* Resource constraints (maximum concurrency/instances) are honored by the plan structure.
* Any conflicts are resolved at plan time (not left to runtime).
* The compiled plan is executable without requiring user intervention for ambiguous choices.

### 7.1 External Parameters

External parameters that influence execution are treated as fixed inputs to the Test Plan and therefore compiled into the Execution Plan.

* After the user commits a validated plan, external parameters are not changeable during execution.
* Any change requires creating and committing a new Test Plan (and thus a new Execution Plan).

## 8. Scheduling and Resource Constraints

All scheduling decisions must be “baked into” the Execution Plan at planning time.

Resource constraints are considered during compilation, including:

* Maximum number of a particular kind of element (instances) allowed concurrently.
* Whether elements are shared, mutually exclusive, or instanced-per scope.
* Any ordering/sequence constraints required to meet resource limits.

The Execution Plan uses explicit barriers or blocking/wait conditions to ensure resource constraints are honored.

### 8.1 Barriers and Waiting Conditions

Barriers are explicit steps that:

* Prevent the start of an action until required resources are available or conditions are met.
* Enforce mutual exclusivity by preventing overlap.
* Allow safe concurrency for shared resources while preventing illegal concurrency for exclusive ones.

## 9. Trial Ordering Strategies

The Execution Plan defines a logical sequence of trials.

A plan may support multiple trial ordering strategies; ordering strategies are deterministic functions of:

* The axes and their values.
* The axis ordering/prioritization.
* The chosen ordering strategy.

### 9.1 Default Strategy: Edge-First

Default ordering is “Edge-First” (also described as “edge-first scaffolding”), which prioritizes executing the extrema of the Cartesian space first to outline the boundaries of results.

Informally:

* Execute trials that represent the “outside” values (extrema) across axes first, including combinations of extrema.
* This scaffolds the “outer shell” of the high-dimensional space early.
* Then fill interior coordinates according to a gap-filling heuristic: prefer points that maximize distance from already executed points (or otherwise maximize coverage), so the most informative missing interior regions are evaluated earlier.

Example: For 3 axes with 3 values each (3×3×3 = 27 trials), Edge-First prioritizes endpoints of the “gamut” first, then fills in the interior.

### 9.2 Axis Prioritization

Edge-First supports axis prioritization such that:

* When filling interior points, the system prefers completing detail along higher-priority (major) axes before exploring connections across lower-priority axes.
* By default, axis priority follows the order of axis definition in the Test Plan.
* Users may reorder axes in the Test Plan and see how the reordering affects the derived Execution Plan’s trial ordering.

## 10. Execution Runtime Behavior

Execution interprets the immutable Execution Plan. Runtime must not rewrite or reinterpret plan meaning.

### 10.1 Real-time State and Progress

At minimum, the execution runtime (executor) must provide, at any point in time:

* A snapshot of all active elements (including their state).
* A view of where execution is within the plan (e.g., current step(s), completed steps, queued/blocked steps).
* Clear differentiation between trials in progress, completed, failed, skipped, and pending.

### 10.2 Dependency Visualization

The system must be able to convey the dependency chain/tree/graph to users.

Because the Execution Plan lays out atomic steps, a natural visualization is:

* A plan-affine view: show the step structure and how steps have progressed.
* Dependency edges between steps/elements should be visible (at least as a graph representation).
* Users should be able to understand what is blocked on what, and why.

## 11. Error Handling and Retry Policies

Error handling behavior is configurable and must be explicitly part of the Test Plan such that it is compiled into the Execution Plan.

Executors may have defaults, but user selections override defaults and become part of the committed plan semantics.

### 11.1 Retryable Operations

At minimum, the system must support configuring retries for:

* Deployment of any deployable element: if deployment fails, retry deployment up to N times.
* Trial actions: if an action fails, retry the action up to N times.
* Trial validity detection: the user may configure conditions under which a completed trial is considered invalid or incomplete based on results; such invalidity conditions may trigger trial retries.

Retry behavior must be represented in the Execution Plan, including:

* Max retry count per operation type.
* Whether retries are immediate, delayed/backoff (if supported), or require pause/stop (policy-driven).
* Whether a retry is allowed for certain error classes.

### 11.2 Partial Failures

Default behavior:

* If some trials succeed and others fail, successful results are retained.
* The overall run state is marked clearly as PARTIAL.
* The system supports re-running the Execution Plan in a way that attempts only the trials that did not complete successfully (subject to idempotency and dependency requirements).

## 12. User Intervention Controls

Users must be able to intervene operationally during execution, without changing the plan definition.

Supported user actions:

* Stop and scrap: stop the whole plan and discard all results (scrap the run).
* Pause now, discard active trials: pause execution immediately, disregarding any current trials in progress, but keeping completed trial results.
* Pause after active trials complete: allow currently executing trials to finish, then pause before starting new work.

Operational controls must not modify the committed plan semantics; they only affect run control.

## 13. Observability and Logging

### 13.1 Execution Log Requirements

Every single step in the Execution Plan must be logged clearly, including:

* All orchestration steps needed within a trial setup.
* All element lifecycle events (start, readiness, health changes, stop, teardown).
* Barriers/waits and the conditions they wait on.
* Retry attempts and outcomes.
* State transitions for trials and elements.

Logs should be structured enough to support:

* Forensic debugging.
* Cost estimation telemetry (see Section 18).
* Provenance traces between results and plan steps.

## 14. Results, Artifacts, and Provenance

### 14.1 Persistence and Identification

The system must persist results for each trial in a way that:

* Clearly identifies provenance: which trial, which study, which Test Plan, which Execution Plan (name+version), and which run produced them.
* Supports many trials per run and many runs per study/plan.
* Supports structured metrics (dependent variables) as the primary results.
* Supports associated unstructured artifacts as a set of files linked to the trial (e.g., logs, model outputs, dataset snapshots).

Users must be able to retrieve results into their own tools (e.g., Jupyter) and analyze them outside Simplica.

### 14.2 Supported Formats

At minimum, persisted/exported results must support:

* JSON
* JSONL
* YAML

### 14.3 Provenance Standard

Simplica must define a standard format (a provenance envelope) that can be attached to every result record and artifact reference, capturing:

* Study identifier
* Test Plan identifier (including version/commit identity)
* Execution Plan name + version
* Run identifier
* Trial identifier and axis values
* Configuration fingerprint (e.g., hash) linking the results to the exact configuration
* Timestamps and attempt numbers (for retries)

## 15. Durability: Idempotency, Resumability, Checkpointing

Long-running and failure-prone environments require operational durability rules that are foundational and planned, not incidental.

### 15.1 Rule 1: Idempotent Re-runs from Partial Success

The Execution Plan must be runnable idempotently after partial success:

* There must not exist critical execution state that cannot be recomputed or restored from persisted state such that results cannot be reproduced.
* Given a partial run and persisted results, the same Execution Plan can be re-run to compute missing results without needing a special recomputation of the plan.
* The plan itself must encode what pieces are required and how to safely execute only missing trials, respecting dependencies and resource lifecycles.

### 15.2 Rule 2: Resumable and Checkpointable Plans

From the foundational level up, plans must be:

* Resumable: execution can continue from a persisted checkpoint after interruption.
* Checkpointable: at defined intervals, the executor persists sufficient state to resume.

The idempotent nature of the plan should be encodable and representable to enable efficient execution of only the pieces needed to fill in missing results after interruption or partial failure.

## 16. Multi-user Collaboration and Access Control

The system must support multiple users and sharing.

Requirements:

* Users can share elements of the system with each other.
* Sharing must support visibility/access without allowing unintentional modification of another user’s system or plans.
* By default, sharing should prevent modification unless explicitly permitted via access controls.

Exact role models are flexible, but the security posture is:

* Separate ownership from visibility.
* Require explicit grants for mutating operations.

## 17. Notifications and Hooks

The system should support callbacks/hooks that allow notifications to occur.

Notes:

* The specific event set is intentionally undefined at this point.
* The system must allow notifications on meaningful milestones (example: completion of the Edge-First scaffold phase, i.e., all extrema trials computed).
* Notification hooks should integrate with the execution event stream (steps, lifecycle events, trial outcomes).

## 18. Cost Estimation and Operational Telemetry

Simplica should provide rough cost estimates (time and resource usage) before execution, but must be honest about confidence.

Rules:

* If the system has no prior operational data for a particular parameter set (or sufficiently similar set), it must indicate that estimates are unavailable.
* If historical data exists for congruent/similar parameter sets, the planner should infer estimates from this data (time, node/resource counts, etc.).

Therefore, during execution, Simplica must record operational telemetry needed to support future inference, including (at minimum):

* Trial execution durations.
* Element startup/teardown durations.
* Resource usage measures available in the implementing stack.
* Retry counts and failure rates.

## 19. Versioning and Lineage

All execution plans used must be persisted indefinitely under specific version numbers.

Naming and identity:

* An Execution Plan’s “name” only matters in conjunction with its version.
* A “plan identity” is effectively (nominal name, version).

Plan refinement behavior:

* Any refinement to a plan is a fundamentally new unique plan (Ship of Theseus principle).
* Versioning exists to aid user semantics and convenience, but the system must make clear when a user has created a new distinct plan versus re-running an existing immutable plan version.

## 20. Simulation and Dry Runs

Simplica should support dry-run simulation as an optional analysis tool.

Motivation:

* Even though plan compilation/validation should handle corner cases structurally, users may want to understand operational behavior under error conditions.
* Users may want to model hypothetical failure rates for certain element types or parameter configurations to observe how error handling and retry strategies behave.

Dry-run requirements:

* Dry-run simulation can assume failure rates for particular elements or parameter combinations.
* The goal is to observe how retry strategies, barriers, and error policies behave without running full real workloads.
* Dry runs do not modify a committed Execution Plan; they are an analysis mode over a compiled plan.

## 21. Contract-first Architecture

Simplica is designed to be implementable by multiple stacks. The system delivers a set of contract types: when faithfully implemented, they constitute a usable system.

### 21.1 Essential Contract Types (Logical Interfaces)

The following contract types are essential. Implementations may combine them internally, but the API must expose equivalent concepts.

| Contract                        | Responsibility                                                                                                        | Key Operations (Illustrative)                    |
| ------------------------------- | --------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------ |
| TestPlan                        | Declarative study spec authored by user.                                                                              | validate(), commit(), reorder_axes()             |
| ExecutionPlan                   | Immutable compiled plan with atomic steps, barriers, policies.                                                        | graph(), steps(), version()                      |
| PlanValidator                   | Validates Test Plan and compiled plan for executability and ambiguity.                                                | validate_test_plan(), validate_execution_plan()  |
| PlanCompiler (ExecutionPlanner) | Compiles Test Plan into Execution Plan; resolves scheduling/resources.                                                | compile(test_plan) -> execution_plan             |
| Scheduler                       | Interprets the Execution Plan’s schedule semantics at runtime (without rewriting).                                    | next_ready_steps(), apply_barriers()             |
| TrialExecutor                   | Executes a single trial; manages within-trial orchestration.                                                          | run_trial(), retry_trial_action()                |
| ResourceOrchestrator            | Manages lifecycle of instantiable elements whose scope exceeds a single trial; supports overlaps across dependencies. | start_element(), stop_element(), health_status() |
| HealthMonitor                   | Detects liveness/health/failure for elements and emits lifecycle events.                                              | check(), subscribe_events()                      |
| ResultStore (ResultReporter)    | Persists structured metrics and unstructured artifacts with provenance.                                               | write_trial_result(), export(format)             |
| ArtifactStore                   | Stores and retrieves unstructured files associated with runs/trials.                                                  | put_artifact(), get_artifact()                   |
| ExecutionController             | Run control: start/pause/stop; supports operational intervention policies.                                            | start_run(), pause(mode), stop(mode)             |
| RunStateService                 | Provides real-time snapshots of execution state and progress.                                                         | snapshot(), active_elements(), step_progress()   |
| EventBus / NotificationHook     | Publishes execution events and supports callbacks/notifications.                                                      | publish(event), register_hook()                  |
| TelemetryRecorder               | Records operational telemetry for later cost estimation.                                                              | record_duration(), record_resource_use()         |
| CostEstimator                   | Infers estimates based on historical telemetry and similarity.                                                        | estimate(plan) -> confidence + costs             |
| SimulationEngine (DryRun)       | Executes a simulated run with assumed failure rates and policy behavior.                                              | simulate(plan, assumptions)                      |
| AccessControlService            | Enforces multi-user permissions and sharing semantics.                                                                | authorize(user, action)                          |
| VersionRegistry                 | Persists and resolves plan versions and lineage.                                                                      | register(plan), resolve(name, version)           |
| ProvenanceService               | Generates and validates provenance envelopes and fingerprints.                                                        | envelope(), fingerprint(config)                  |

Note: method names above are illustrative. The actual API may differ, but the responsibilities and observable behavior must match.

### 21.2 Standard API Requirements

* All basic concepts and types must be made tangible through a standard API.
* The API must support rich web clients for interactive Test Plan modeling, dependency visualization, dry runs, and verification.
* API operations must respect immutability and versioning: committed plans are read-only; new versions require new commit.

## 22. Multi-language Stub Libraries

To enable multiple implementing stacks, Simplica will provide stub libraries (type definitions and interfaces) for:

* Java
* Rust

These libraries must encode the contract types and standard API surface so implementers can build conforming systems.

## 23. Out-of-scope and Future Considerations

The following are recognized but not specified in this draft:

* Full data warehouse schema design and analytics UX.
* Post-execution analysis hooks (beyond persistence to system storage).
* Specific distributed compute designs (e.g., exact cluster scheduler choices).
* Detailed role-based access control models (RBAC/ABAC) beyond “share without mutate unless granted.”
* Formal similarity metrics for cost estimation (the planner must support “congruent parameter set” inference but the metric is implementation-defined).

## 24. Appendix: Illustrative Result Record Schema

### 24.1 Provenance Envelope (example)

```json
{
  "study_id": "study-...",
  "test_plan_id": "tp-...",
  "execution_plan": {"name": "SimplicaPlanA", "version": "3"},
  "run_id": "run-...",
  "trial_id": "trial-...",
  "axis_values": {"axisA": "low", "axisB": 3, "axisC": "v2"},
  "config_fingerprint": "sha256:...",
  "timestamps": {"started": "...", "ended": "..."}
}
```

### 24.2 Trial Result Record (example)

```json
{
  "provenance": { /* see provenance envelope */ },
  "metrics": {
    "accuracy": 0.913,
    "latency_ms_p95": 127.4
  },
  "artifacts": [
    {"name": "logs.tar.gz", "uri": "s3://.../logs.tar.gz", "content_type": "application/gzip"},
    {"name": "model.bin", "uri": "s3://.../model.bin", "content_type": "application/octet-stream"}
  ],
  "status": "SUCCEEDED|FAILED|SKIPPED",
  "attempt": 1
}
```
