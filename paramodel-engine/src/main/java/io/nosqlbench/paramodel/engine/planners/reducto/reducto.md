# Reducto Step Planner

This step planner is meant to be the default planner once it is complete.

# Trial Semantics

A trial is defined by the lifecycle of stacked element instances having a specific and unique set of
parameter values. The parameter values are related to a trial number which is unique to that trial.
The parameter values and the trial numbers are stable and determined by a deterministic mapping
method described below.

## Trial Groups

All trials fall into a set of nested groups at different levels. Level 0 is the outermost group
and contains all trials. Each subsequent level subdivides by one additional parameter value
(in depth-first element-and-axis order). The first non-fixed parameter defines level 1: each
distinct value of that parameter partitions the trials into level-1 groups. The second
parameter defines level 2: each distinct combination of the first two parameter values defines
a level-2 group. In general, level K is defined by the first K parameters, and each level-K
group contains all trials sharing the same values for those K parameters.

## Trial Scope

Although all the stacked instances which are active during a trial are in scope for the trial, there
are specific instances which define the window of time for which the trial itself is active. These
are called the "trial elements" and are the instances which are determined to be the most dependent,
or inner-most instances of the stacked element instances. These are generally inferred from the
dependency relationships between the stacked elements. However, users may explicitly define trial
elements by flagging them as trial elements on the element stack. No element may be flagged as a
trial element if there are other elements which have it as a dependency, as the lifetime and scope
of such elements does not define a clean cut across a stack of elements having specific parameter
values.

All element instances in the stack are considered participants of the trial scope for the duration
of the trial elements' lifetimes. The trial elements specifically set the boundaries of the trial
window (the trial lifetime). However, the state and behavior of all elements in the stack during the
trial window are meaningful and thus included in the view of a trial.

The non-trial elements (all the elements in the instance stack which are not trial elements) are
notified after all non-trial elements are active and before trial elements begin activation. This
notification marks the opening of the trial window.

Then, when the trial elements are all deactivated, the non-trial elements are notified of the
trial deactivation. This allows any elements which participated in the trial window,
but which exist before or after it, to mark important data points in the trial lifetime, like
metrics windows, etc.

### Trial Element Identification Algorithm

Trial elements are identified by `StepGenerationUtils.identifyTrialElements()` using a
scope-aware, override-respecting algorithm:

1. **Explicit overrides:** Elements may declare `trialElement(true)` (forced on) or
   `trialElement(false)` (forced off). Forced-on elements are always included in the result.
   Forced-off elements are excluded from all candidate pools.

2. **Candidate pool construction:**
   - When axes define trial-scoped bindings (at least one element has a non-run-scoped axis
     binding), the trial-scoped elements form the candidate pool.
   - When no axes are present, the candidate pool is all non-floating elements. A "floating"
     element is one with no outgoing dependencies and no incoming dependencies (nothing depends
     on it and it depends on nothing). Floating elements are excluded because they have no
     relationship to the rest of the plan and should not participate in trial scope.

3. **Leaf selection:** From the candidate pool, trial elements are the *leaf nodes* — elements
   that no other candidate depends on. An element is a leaf if no other element in the candidate
   pool lists it as a dependency target. This selects the most dependent (innermost) elements
   as the trial boundary.

The result is the union of forced-on elements and inferred leaf elements, excluding any
forced-off elements.

## Trial Outcomes

Each trial has a set of outcomes which are defined by the elements in the trial. The trial outcome
is the union of all the element outcomes. The element outcomes are defined as the set of artifacts
and values produced by each element during a trial window.

The notification for a trial deactivation is an active request made to trial stack elements. In
response to this call, each element in the stack may produce a set of artifacts which represent the
observed state for that element during that trial instance. The system then persists these artifacts
for later analysis.

Command elements may also produce artifacts, but since they determine their own stopping criteria
and stop on their own, the Element implementations must be responsible for determining how these
artifacts are collected as part of observing the completion of a command.

# Execution Graph Semantics

The execution graph is a control structure which captures the order in which operations should be
executed. It is a directed acyclic graph (DAG) of operations, where each operation is a node in the
graph and each edge represents a dependency between operations.

When a node in the graph has multiple incoming edges, these represent a dependency on multiple
operations, and thus an operational barrier. In other words, for a node to be considered ready to
execute, all of its incoming edges must be satisfied. An edge is satisfied when the operation
represented by its upstream node has been completed.

Nodes may be completed only in successful normative terms. In other words, if a side-effect process
fails, the node should be considered failed and thus its out edges should not be satisfied.

## Error Handling Semantics

Each node in the execution graph can have a defined error policy, from a set of standard options
like "tries(3)" or "pause", where the policy is defined by the node itself and originates from a
setting on the element prototypes. The error handler policy is a sequence of actions — not just a
single action — to take when executing a node fails.

When an error handler policy is used, each of the steps in the policy should be applied by the graph
executor. Each policy verb is defined as being final or intermediate. When an intermediate verb is
encountered, the graph executor should wait for the previous step to complete before proceeding.
When a final verb is encountered, the graph executor should process it and then stop processing the
policy. If a policy chain is specified that has no final verb at the end, this is considered a
configuration error.

- "tries(3)" means a step may be attempted up to that many times before going to the next verb
  in the policy chain. (intermediate)
- "inherit" means that the error policy should be inherited from the node's dependency, tracing up
  to the first node whose policy is not 'inherit' and using that policy, even if that means the
  start node. If there are multiple such nodes, then the first such one should be used in declared
  order. (final) Inherit must appear alone or as the last verb in the error-handling chain.
- "pause" means that active scheduling of new steps should be suspended for the whole graph.
  (intermediate)
    - Existing steps which are already in process should continue to run. The pause handler should
      wait for all active step actions to complete before proceeding.
    - This means that the executor has no in-process steps and that none are actively being
      scheduled.
    - It means that the executor has reached a state of quiescence and that no new steps will be
      scheduled.
    - The pause policy is not intended to do anything proactive on its own. It allows for error
      handling policies to be defined based on an imperative sequence of synchronous error handler
      actions, but with a known state of the graph.
- "salvage" means that the graph should continue to run, but the executor should only attempt to run
  the remaining nodes which are not otherwise blocked by a failed node in its transitive dependence
  graph. This means marking an errored node as failed and continuing to run the rest of the graph. (
  final)
- "scrap" means that the graph execution should be halted and any active elements should be
  deactivated in normal dependency order (reverse of activation). (final)
- "abort" means that the graph should be aborted immediately upon encountering an error. This
  includes ignoring the state of nodes which have already been activated and avoiding any cleanup
  efforts of state which may have been created. This is a rip cord option which is not to be used
  except in extreme circumstances due to the resource leaks it may incur. (final)

When not otherwise specified, the default error policy for an element is "inherit", and the default
error policy on the start node (the entry point of the graph) is "tries(3); salvage".

## Element Stack Semantics

The element stack is the set of elements and axes which define the goals of the user. This is the
primary input into execution graph construction. The element stack contains a prototypical set of
element definitions, their parameters, and the axes and related values for each element. Crucially,
the element stack also contains an internal set of directed dependency relationships between the
prototypical elements.

Descriptively, the elements described in the element stack are not real instances of the elements,
but rather prototypes which are used to create them. Since an Element may have an axis with multiple
values, the prototypical element may be instantiated multiple times, once for each value in the
axis. Further it may be instantiated many more times if it has a dependency on some other element (
direct or transitive) which also has its own axes.

The execution graph is concerned with the lifecycle of the elements, and the dependency
relationships between them. Activating (deploying) an instance of an element which was described on
the element stack is one of the fundamental operations of the execution graph. Conversely,
deactivating (undeploying) an instance of an element is another fundamental operation. The lifecycle
of an element is defined by the following rules:

- An element instance is activated when it is successfully deployed. From this time forward, it is
  considered "active"

- An element instance is deactivated when it is successfully undeployed. From this time forward, it
  is considered "inactive".

The full lifecycle of an element instance is the span between successful activation and successful
deactivation.

Elements in the stack will be either service (fixtures which can be started and stopped), or
commands (fixtures which can be started, but which typically complete on their own). The type of
element (service or command) must be provided as part of the element's definition. This determines
which step is appropriate to mark the end of an element's lifecycle — when it is deactivated
or deactivates itself.

A command element must be waited for by a specific "wait for" execution step. Other _service_
elements must be stopped by a specific "deactivate" step.

### Topological Sorting and Floating Elements

Elements in the stack are topologically sorted before processing. The sort places "floating"
elements first — elements that have no dependencies in either direction (no outgoing dependencies
and no incoming dependencies from other elements). Floating elements are placed at the top of the
element stack in their original insertion order, followed by connected elements in dependency order
(upstream before downstream). This sorting is performed both in `DefaultTestPlan` (for the stored
element order) and in `StepGenerationUtils.topologicalSort()` (for the reducto pipeline input).

### Instance Lifecycle Invariant

Every element instance in the execution graph must have exactly one activation step and exactly one
deactivation step (or await step, for command elements). This is a structural invariant of the
graph — no instance may be activated without a corresponding deactivation, and no instance may be
deactivated more than once. Coalescing (Rule 3) must preserve this invariant: when per-trial
lifecycle nodes are merged into group-level nodes, the result must be a single activation and a
single deactivation per group-level instance.

The one exception is lifeline dependencies. When element X has a LIFELINE dependency on element Y,
X's deactivation is subsumed by Y's — tearing down Y implicitly tears down X. In this case, X's
explicit deactivation step is removed from the graph (by Rule 2), and X has only an activation step.
Having only an activation step for an instance is valid if and only if that instance is connected to
another instance via a lifeline dependency. In all other cases, an instance without a matching
deactivation step indicates a bug in the planner.

This invariant is enforced by a validation check in `ReductoStepGenerationStrategy` that runs after
all rules have been applied. For each element, the validator counts ACTIVATE nodes and
DEACTIVATE/AWAIT nodes in the finalized graph and verifies they are equal — unless the element has
a LIFELINE dependency, in which case its deactivation count must be zero. A violation throws an
`IllegalStateException` with details of the mismatched element.

### Coalescing Congruence Principle

Any rule that coalesces element instances must also produce a congruent coalescing of the
activations and deactivations of those instances. When multiple per-trial instances are merged into
a single group-level instance, all per-trial lifecycle nodes (activate, deactivate/await) and all
edges referencing those nodes must be correspondingly merged into group-level equivalents. This
means:

1. **Activation congruence:** If K per-trial activate nodes are coalesced into one group activate,
   all incoming and outgoing edges of the removed activate nodes must be remapped to the surviving
   group activate node.

2. **Deactivation congruence:** If K per-trial deactivate/await nodes are coalesced into one group
   deactivate, all incoming and outgoing edges of the removed deactivate nodes must be remapped to
   the surviving group deactivate node. This includes the first trial's deactivate — coalescing
   loops that start at i=1 (to preserve the first trial's activate as the group activate) must
   still explicitly handle the first trial's deactivate.

3. **Dependency congruence:** Any rule that establishes edges to or from lifecycle nodes must
   account for the coalesced group structure. If a non-trial element's deactivation depends on
   trial-scoped events (e.g. notify_trial_end), the dependency must cover ALL trials in the group,
   not just the last one, since trials within a group may execute concurrently.

Violations of this principle produce orphaned lifecycle nodes, missing edge remappings, or
incomplete dependency coverage — all of which manifest as incorrect execution ordering or
invariant violations at validation time.

No explicit steps are used to represent a trial as such in the execution graph. Instead, a trial is
an inferred boundary around the trial element instances which have fully bound parameters. Every
set of fully bound parameters implies a trial, and the window or lifespan of a trial is defined as
enclosing the lifecycle of all elements which are identified as the trial elements.

Semantically, _activate_ and _deactivate_ are also described as "deploy" and "tear down" elsewhere
in the paramodel documentation.

Each element may have a global concurrency limit, which is the maximum number of instances (of that
element prototype) which may be active at any given time. This is expressed as the "max concurrency"
parameter of the element definition. They may also have a group concurrency limit, which is the
maximum number of instances (of that element prototype) which may be active at any given time for a
given group of trials. This is expressed as the "max group concurrency" parameter of the element
definition.

### Shared Element Dependencies

An element prototype X may be dependent on element prototype Y such that it doesn't mind sharing
instances of element Y with other element instances. This is expressed as "Element X shares Element
Y". Element X instances are not aware of what other element instances share Y, only that there may
be some.

This is for scenarios where you have a common service, for example, which is necessary to enable
many other elements to operate, but which is not critical to the operation of the other elements in
terms of measured outputs. This might be an authentication service, or a telemetry aggregator, or a
database of config values.

### Exclusive Element Dependencies

An element prototype X may be dependent on element prototype Y such that instances of X must not
share instances of Y with any other element instances. This is expressed as "Element X isolates
Element Y." Element X will only be activated when Y is able to be completely exclusive to it for the
duration of its lifetime. Once an instance of X is active, other element instances which have a
dependency on Y will not be activated until the X instance is no longer active.

This is for scenarios where you have a common fixture which is necessary for your test, but which
should not be shared concurrently, but which may be shared serially. Maybe it is part of a system
under test which is expensive to stand up and tear down, but persisting this shared fixture comes
with no worry of residue, measurement perturbation, or interference from other test elements.

### Dedicated Element Dependencies

An element prototype X may be dependent on element prototype Y such that any instance of Y is solely
and completely dedicated to an instance of element X. This means that an instance of Y must be
activated for each instance of X, and that X must not be activated until its dedicated instance of Y
is active.

This is for scenarios where you know that starting a new instance of Y is necessary to prevent
previous state, caching, or usage residue from interfering with the operation of X or the accurate
measurement thereof.

### Lifeline Element Dependencies

An element prototype X may be dependent on element prototype Y such that when the associated
instance of Y is deactivated, the associated instance of X will automatically be deactivated as a
side-effect. This is said as "X has a lifeline to Y". When element Y is deactivated, the system must
take this as both instances being deactivated. This is true for any such strictly connected lifeline
cluster. In other words, instances which are deactivated that have other instances depending on them
for a lifeline, subsume the deactivation state for all of those in a single deactivation event or
transactional update.

This is for scenarios where, for example, a container runs on a cloud host, and stopping the
container before stopping the cloud host serves no operational purpose. Forcing the LIFO stack
ordering here would simply delay the deactivation of the cloud host until the container was stopped,
which is not desirable.

### Linear Element Dependencies

An element prototype X may be dependent on another element Y such that an instance of X must be
activated only after the corresponding instance of Y has been activated and deactivated — whether
by an explicit service deactivation or by awaiting a command element's natural completion.
This is expressed as "X comes after Y". This constraint applies only when X and Y share the same
trial scope — that is, when both elements are bound to the same configuration group (same
fingerprint). When X and Y are in different trial scopes, the linear constraint does not apply and
their lifecycles are independent.

In this sense, this dependency represents causal ordering more strongly than the other dependency
types, which are more focused on concurrent activation constraints. The full lifecycle ordering
means that Y's resources are fully released before X begins, preventing any residual state,
measurement perturbation, or resource contention between them.

This is for scenarios where you want to have a sequence of ordered operations, or where instances
may actually work as a pipeline over data from one to the next. The first case is merely
_serialized_, but the _linear_ relationship works for either case.

The full lifecycle ordering defined here (Y activated and deactivated before X starts, within
shared trial scope) is the authoritative semantic for `LINEAR` going forward. The existing
`RelationshipType.LINEAR` javadoc describes a weaker constraint where both elements can be
co-active with only their operative actions serialized. That weaker model is superseded — the
`RelationshipType.LINEAR` javadoc and any planner implementations that reference it should be
updated to enforce the stronger full lifecycle ordering within trial scope as the standard.

### Relationship Type Composition

An element may have dependencies on multiple other elements, and each dependency may use a
different relationship type. For example, element X may SHARE element A and have a LINEAR
dependency on element B. Each relationship is independent and its rules are applied separately
during Rule 2 (dependency edge materialization).

However, only one relationship type may exist between a given pair of elements. Multiple
relationship types on the same pair (e.g., X has both SHARED and EXCLUSIVE on Y) is a
configuration error. Certain combinations are inherently contradictory:

- **LIFELINE + LINEAR**: LIFELINE subsumes deactivation of X into Y's deactivation, but
  LINEAR requires Y to fully deactivate before X activates. These are incompatible.
- **DEDICATED + SHARED**: DEDICATED requires a private instance of Y per X, while SHARED
  permits concurrent access. These are mutually exclusive by definition.
- **EXCLUSIVE + DEDICATED**: EXCLUSIVE means Y is shared serially; DEDICATED means Y is never
  shared. DEDICATED already implies stronger isolation than EXCLUSIVE.

The planner validates that each element pair has exactly one relationship type and rejects
configurations with conflicting types.

### Transitive Dependency Chains (A → B → C)

When three elements form a chain — A depends on B via relationship R1, and B depends on C via
relationship R2 — the combined behavior follows from the independent application of each
relationship's rules. This section enumerates all 25 combinations and describes their validity,
semantics, and any notable interactions with the reducto rules.

In each combination below, A is the outermost dependent, B is the intermediary, and C is the
innermost dependency target. The dependency direction is: A depends on B, B depends on C.

#### SHARED → SHARED: A shares B, B shares C

**Valid.** Standard transitive sharing. C is activated first, then B, then A. All three may have
concurrent instances sharing C and B. This is the simplest composition — no serialization
constraints beyond activation ordering.

*Example:* A is a test workload, B is a monitoring agent, C is a telemetry backend. Multiple
workloads share the monitoring agent, which shares the telemetry backend.

#### SHARED → EXCLUSIVE: A shares B, B exclusively accesses C

**Valid.** B serializes on C — only one instance of B may use C at a time. A shares B normally,
so multiple A instances may reference the same B instance. The exclusivity constraint is between
B and C only; A is unaffected. If B has multiple instances (e.g., from parameterization), each
must wait for exclusive access to C.

*Example:* A is a test client, B is a benchmark driver, C is a database instance. Multiple
clients share the driver, but the driver requires exclusive access to the database.

#### SHARED → DEDICATED: A shares B, B has dedicated C

**Valid.** Each instance of B gets its own dedicated instance of C. A shares B normally. The
dedicated C instances are scoped to B's lifecycle, not A's. If B is coalesced across a group,
its dedicated C is also coalesced across that group.

*Example:* A is a test workload, B is a compute node, C is a local scratch volume. Multiple
workloads share the node, but each node gets its own scratch volume.

#### SHARED → LINEAR: A shares B, B comes after C

**Valid.** C must fully activate and deactivate before B activates (within shared trial scope).
A depends on B via sharing, so A activates after B. The effective ordering within a trial is:
`activate(C) → deactivate(C) → activate(B) → activate(A)`.

*Example:* A is an analysis step, B is a data pipeline, C is a data preparation step. The
pipeline only starts after preparation completes, and multiple analyses share the pipeline.

#### SHARED → LIFELINE: A shares B, B has lifeline to C

**Valid.** B dies when C dies — B's deactivation is subsumed by C's. A shares B and must
deactivate before B (which means before C). When C deactivates, B is implicitly deactivated
as a side-effect.

*Example:* A is a monitoring dashboard, B is a container, C is a cloud host. The dashboard
shares the container. When the host stops, the container dies automatically.

#### EXCLUSIVE → SHARED: A exclusively accesses B, B shares C

**Valid.** A serializes on B — only one A instance at a time may use B. B shares C normally.
The exclusivity is between A and B only; C is unaffected by the serialization.

*Example:* A is a test scenario, B is a rate-limited API gateway, C is a backend database.
Scenarios need exclusive gateway access but the gateway shares the database.

#### EXCLUSIVE → EXCLUSIVE: A exclusively accesses B, B exclusively accesses C

**Valid.** Double serialization chain. A serializes on B, and B serializes on C. This creates
a fully serialized pipeline: only one A instance is active at a time (exclusive on B), and
within that instance, only one B is active at a time (exclusive on C). The serialization
constraints are applied independently by Rule 2.

*Example:* A is a benchmark run, B is a test driver, C is a target cluster. Each run needs
exclusive access to the driver, and each driver needs exclusive access to the cluster.

#### EXCLUSIVE → DEDICATED: A exclusively accesses B, B has dedicated C

**Valid.** Each B instance gets its own dedicated C. A serializes on B. Since B already gets
exclusive access from A, and C is dedicated to B, the three-element chain is fully isolated
during A's lifetime.

*Example:* A is a load test, B is a compute instance, C is a local SSD. The load test
exclusively accesses the compute instance, which has its own SSD.

#### EXCLUSIVE → LINEAR: A exclusively accesses B, B comes after C

**Valid.** C completes before B activates, and A serializes on B. Within a trial:
`activate(C) → deactivate(C) → activate(B) → activate(A)`.

#### EXCLUSIVE → LIFELINE: A exclusively accesses B, B has lifeline to C

**Valid.** A exclusively accesses B, and B dies when C dies. The exclusive serialization on B
still applies — A instances take turns using B. When C deactivates, B dies as a side-effect.

#### DEDICATED → SHARED: A has dedicated B, B shares C

**Valid.** Each A instance gets its own dedicated B. All B instances share C. C is activated
before any B, and deactivated after all B instances. Since B is dedicated to A, B's lifecycle
mirrors A's. If A is a trial element, B gets per-trial instances (not coalesced). Each
per-trial B depends on C via SHARED edges.

*Example:* A is a benchmark command, B is a dedicated driver process, C is a shared
configuration service. Each command gets its own driver, but all drivers share the config
service.

#### DEDICATED → EXCLUSIVE: A has dedicated B, B exclusively accesses C

**Valid, with notable Rule 4 interaction.** Each A instance gets its own B, and each B must
have exclusive access to C. If A is a trial element, B is non-trial but gets per-trial
instances (Rule 3 does not coalesce DEDICATED targets with trial-element owners).

Rule 2 creates serialization edges between B instances: `deactivate(B, Ti) → activate(B, Ti+1)`
when C is the same instance at both trials. This ensures only one B uses C at a time.

**Critical Rule 4 interaction:** The exclusive serialization rerouting in Rule 4 applies only
to trial elements. Since B is non-trial, its serialization edges are left intact as direct
edges. This is essential — for non-trial elements, the notify wiring direction is reversed
(`activate(B) → notify_start`, `notify_end → deactivate(B)`), so rerouting through notify
boundaries would allow B instances to overlap. See the "Non-trial elements are not rerouted"
note in Rule 4.

*Example:* A is a test command (trial element), B is a dedicated compute node (non-trial),
C is a shared storage cluster. Each command gets its own node, but nodes take turns accessing
the shared storage.

#### DEDICATED → DEDICATED: A has dedicated B, B has dedicated C

**Valid.** Full isolation chain. Each A gets its own B, and each B gets its own C. The three
form a completely private stack per trial (or per group, depending on coalescing). No sharing
occurs at any level.

*Example:* A is a benchmark, B is a dedicated app server, C is a dedicated database. Each
benchmark gets its own server with its own database.

#### DEDICATED → LINEAR: A has dedicated B, B comes after C

**Valid.** Each A gets its own B, and B activates only after C completes. C's lifecycle is
independent of the DEDICATED relationship between A and B.

#### DEDICATED → LIFELINE: A has dedicated B, B has lifeline to C

**Valid.** Each A gets its own B, and B dies when C dies. When C deactivates, all B instances
that have a lifeline to C are implicitly deactivated, which in turn affects their owning A
instances (A must deactivate before B dies, per the DEDICATED dependency ordering).

#### LINEAR → SHARED: A comes after B, B shares C

**Valid.** B completes before A starts, and B shares C during its lifetime. A's dependency on B
is purely sequential — A activates after B deactivates. C may still be active when A starts
(if other elements also share C).

#### LINEAR → EXCLUSIVE: A comes after B, B exclusively accesses C

**Valid.** B completes before A starts. During B's lifetime, B has exclusive access to C. Once
B deactivates and releases C, A can start. A has no direct relationship with C.

#### LINEAR → DEDICATED: A comes after B, B has dedicated C

**Valid.** B completes before A starts. During B's lifetime, B has its own dedicated C. When B
deactivates, its dedicated C also deactivates. Then A activates.

#### LINEAR → LINEAR: A comes after B, B comes after C

**Valid.** Fully sequential chain: `activate(C) → deactivate(C) → activate(B) → deactivate(B)
→ activate(A)`. Each element's full lifecycle completes before the next begins.

*Example:* C is a data import, B is a data transformation, A is an analysis step. They run
in strict sequence.

#### LINEAR → LIFELINE: A comes after B, B has lifeline to C

**Valid.** B completes before A starts. B dies when C dies. Since LINEAR requires B to
deactivate before A activates, and B's deactivation is subsumed by C's, the effective ordering
is: C deactivates (killing B) → A activates.

#### LIFELINE → SHARED: A has lifeline to B, B shares C

**Valid.** A dies when B dies. B shares C. A has no explicit deactivation step — it is
subsumed by B's deactivation. B's SHARED relationship with C is independent.

#### LIFELINE → EXCLUSIVE: A has lifeline to B, B exclusively accesses C

**Valid.** A dies when B dies. B serializes on C. The lifeline means A's lifecycle is bounded
by B's, and B's lifecycle is constrained by exclusive access to C.

#### LIFELINE → DEDICATED: A has lifeline to B, B has dedicated C

**Valid.** A dies when B dies. Each B gets its own dedicated C. When B deactivates (taking A
with it as a side-effect), the dedicated C also deactivates.

#### LIFELINE → LINEAR: A has lifeline to B, B comes after C

**Valid.** A dies when B dies, and B only activates after C completes. The effective chain
is: C completes → B activates (A implicitly activates with B) → B deactivates (A dies).

#### LIFELINE → LIFELINE: A has lifeline to B, B has lifeline to C

**Valid.** Transitive lifeline cluster. When C deactivates, B dies (lifeline to C), and A dies
(lifeline to B). All three form a lifeline cluster where C is the root. All deactivation edges
are remapped to C's deactivation node. Only C has an explicit deactivation step in the graph;
A and B have only activation steps.

*Example:* A is a container process, B is a container, C is a cloud host. Stopping the host
kills the container, which kills the process. All three are deactivated as one event.

#### Summary of Notable Interactions

| Chain Pattern | Key Behavior |
|--------------|-------------|
| DEDICATED → EXCLUSIVE | Non-trial B serializes on C via direct edges (NOT rerouted through notify) |
| EXCLUSIVE → EXCLUSIVE | Double serialization — fully sequential pipeline |
| DEDICATED → DEDICATED | Full isolation — no sharing at any level |
| LINEAR → LINEAR | Fully sequential chain of lifecycles |
| LIFELINE → LIFELINE | Transitive lifeline cluster — single root deactivation |
| DEDICATED → LIFELINE | Dedicated B dies with C — A loses its resource when C stops |
| SHARED → EXCLUSIVE | Shared intermediary serializes on inner target |

All 25 combinations are structurally valid. No transitive chain produces a configuration error
on its own. However, operational warnings may still be emitted (e.g., W001 for broad-scope
exclusivity) depending on the specific element scoping.

## Element Graph Semantics

The rules above describe how elements may be related to each other, but they do not describe how
they are related to the execution graph. The execution graph's goal is to construct the element
graph and mutate it over time to allow all the trials to be completed. Thus, the execution graph
will represent all activation and deactivation steps which are needed, and in which order, to
fulfill the trial configurations, dependency relationship, and concurrency limits provided by the
element stack. At any point during the execution of an execution graph, it is possible to synthesize
a view of the element graph which represents the current state of the execution graph. This is an
invaluable diagnostic tool. When truing up an operational state of a real-world system, it is the
synthetic element graph which is used to determine any deltas which need to be applied to the
real-world system to make it correct and concurrent before resuming the execution graph.

## Graph Execution

A special node type called 'start' in the execution graph represents the entry point of the graph.
It is implicitly connected to all nodes in the graph which would otherwise have no incoming edges.

A special node type called 'end' in the execution graph represents the exit point of the graph. It
is implicitly connected to all nodes in the graph which would otherwise have no outgoing edges.

As part of graph planning, the start and end nodes are materialized into the plan by the step
planner, and these are used by the graph runner to know where to explicitly start and how to know
when the graph is complete.

The goal of the graph executor is to activate each node in the graph for which all inputs are
satisfied, concurrently, until the end node is reached and all its inputs are satisfied.

When there are concurrency limits for an element prototype (over its instances) whether global
limits or group limits, the planner will express these limits as concurrency directives annotated on
the relevant nodes in the graph. It is up to the graph executor to observe these directives and
enforce the limits at runtime.

This is a deliberate design choice: encoding concurrency limits as structural dependency edges in
the graph (e.g. sliding window dependencies between deploy and teardown steps) becomes explosively
complex when execution occurs out of order or across a large number of elements. Such structural
encoding risks creating arbitrary blocking conditions that deoptimize the entire execution graph. By
expressing concurrency limits as declarative directives rather than structural edges, the planner
keeps graph complexity manageable and the executor retains the flexibility to enforce limits
dynamically without artificial serialization.

# Method

The reducto step planner is a multi-stage process which creates a view of the full cartesian product
of the axes and trials, then incrementally applies graph reduction rules to simplify the execution
graph until no further reduction is possible.

Here is a basic example of a set of axes for use in explanations below:

```yaml
elements:
  a:
    axes:
      param_x: [ 1,2,3 ]
      param_y: [ 10,20 ]
  b:
    axes:
      param_u: [ "asm", "dra", "ghi" ]
```

Axes are defined on parameters, and parameters belong to elements. The canonical structural
representation reflects this natural ownership: axes are scoped within the element that owns the
parameter they vary. This is the authoritative model going forward.

The system may also provide parametric views that present axes in a flattened or plan-level
arrangement when that is more convenient for a particular use case (e.g. enumeration across all axes
regardless of owning element). Such views are derived projections and do not change the underlying
ownership model. Internal links between the parametric view and the element-scoped model are
maintained by the system as needed.

The existing `TestPlan` model defines axes at the plan level with an optional `Axis.targetElement()`
reference. The element-scoped ownership model defined above inverts this: axes live on their owning
element's parameter, and the plan-level view becomes a derived projection. The `TestPlan` and `Axis`
APIs should be updated to reflect this ownership model once reducto is adopted.

Here is how it should work:

## Stage One, axis, and trial enumeration:

The cardinality of trials is taken as the product of all axis cardinalities across all elements. The
rank of a parameter in the plan is determined by its position in the list of axes, depth first
across all ordered elements.

Parameters that do not have an axis (fixed parameters) have a single constant value across all
trials. They do not contribute a rank to the enumeration, do not affect the cardinality of the
trial space, and do not participate in the mixed-radix decomposition. However, fixed parameter
values are part of the element prototype's identity and are included in every instance's
configuration when activated. Two element prototypes that differ only in fixed parameter values
are distinct prototypes in the element stack. For binding state purposes, fixed parameters are
always bound — they do not affect the group level at which an element becomes concretely bound.

A stable and unique identifier for each bound coordinate within the parameter space is computed
using mixed-radix enumeration (also known as the combinatorial number system for cartesian
products). This method provides a bijective mapping between a single trial number and the
vector of parameter value offsets.

Given N parameters with cardinalities C0, C1, ..., C(N-1) (in depth-first element-and-axis
order), the total number of trials is the product C0 × C1 × ... × C(N-1). Each trial number T
in the range [0, total) maps to a vector of parameter offsets by mixed-radix decomposition:

```
stride[i] = C[i+1] × C[i+2] × ... × C[N-1]    (stride[N-1] = 1)
offset[i] = (T / stride[i]) % C[i]
```

The reverse mapping from offsets to trial number is:

```
T = offset[0] × stride[0] + offset[1] × stride[1] + ... + offset[N-1] × stride[N-1]
```

This is the standard mixed-radix positional numeral system, where each parameter's cardinality
acts as the radix for that digit position. The outermost parameter (rank 0) is the most
significant digit and changes the least frequently; the innermost parameter (rank N-1) is the
least significant and changes every trial. This is analogous to odometer ordering.

The vector of offsets for a given trial is called the _parameter offsets_, and the scalar
identifier is the _trial number_. These two representations are interconvertible without
traversing the parameter space, and both are stable across runs given the same element stack
definition.

Since axes are owned by parameters on elements, the trial number and parameter offsets directly
determine the binding state of every element at any group level in the trial enumeration.
Because parameter ranks are assigned depth-first across the ordered element prototype stack, all
of an element's parameters occupy contiguous ranks (R through R+K-1 for an element with K
parameters). This means an element transitions directly from **unbound** to **concretely bound**
at a single group level — there is no intermediate partially-bound state. For any given group
level, each element is in one of two binding states:

- **Concretely bound**: All of the element's parameters have bound values at this group level
  (the group level is >= R+K). The element's full configuration is determined and it can be
  activated.
- **Unbound**: Not all of the element's parameters have received bound values at this group level
  (the group level is < R+K). The element cannot be activated.

These binding states are computable directly from the element prototype stack and the trial number
without any additional tracking structures. This is the basis for determining element lifecycle
scope — an element's activation and deactivation boundaries in the execution graph are defined by
the group level at which it becomes concretely bound.

### Binding Level Propagation

An element's effective binding level may be higher than what its own parameters dictate. The
`BindingStateComputer` performs a forward propagation pass (in topological order) to ensure that
elements inherit the binding level of their enclosing dependency chain for SHARED and EXCLUSIVE
dependencies. Specifically, an element's effective binding level is the **maximum** of its own
computed level and the binding levels of all its SHARED/EXCLUSIVE upstream dependencies.

This prevents an interstitial element with no axes of its own from collapsing into a single
group when its upstream dependencies are parameterized. For example, if element B depends on
element A (SHARED), and A has a binding level of 2 but B has no parameters (level 0), B's
effective binding level is raised to 2 so it coalesces at the same granularity as A.

DEDICATED and LIFELINE dependencies do not participate in this propagation because their
coalescing is handled separately (DEDICATED targets coalesce with their owner via
`resolveEffectiveBindingLevel`, and LIFELINE targets have their deactivation subsumed).

### Trial Codes

There is also a "trial code" which corresponds to the trial number but which makes the radix
structure and offsets more human-readable. This is used in error messages and logs. The format
should essentially be a hex-string. If the maximum number of values of any given parameter is 16
or less, then each character in the trial code is just the hexadecimal character for the offset
of each axis. If there are more than 16 values for any parameter, then the resolution each
position is 16 bits instead of 8, which makes each axis represented by a two-digit hex string.
Examples:
- The 5th trial (id=4) of a plan with axes `a=[1,2,3] b=[asm,dra,ghi] c=yo` will have values
  `a=2 b=dra c=yo`, and trial code `0x110`, including the third parameter as if it were an axis
  with a single value (which is valid).
- The 11th trial (id=10) of a plan with axes=`v1=[a,b,c],v2=[u,v],v3=[w,x,y,z]` will have values
  `v1=b,v2=u,v3=y`, and trial code `0x102`.
- The 38th trial (id=37) of a plan with axes=`a=[0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16] b=[what,
up]` will have values `a=2,b=up` and trial code `0x0200`, because you can't
  encode 17 unique values into 4 bits with hex.

The trial code should be passed back along with the trial number in any execution planning
results, since providing the trial code makes it easy to show users where there is structural
congruency for simple cross-checking and combinatoric exploration.

Trial codes are stamped onto graph nodes as metadata (`trial_code`) after all rules have been
applied but before linearization. The `GraphLinearizer` propagates this metadata into the
resulting `AtomicStep` records. `NotifyTrialStart` and `NotifyTrialEnd` steps carry the trial
code as an explicit field (`Optional<String> trialCode`), which downstream consumers use to
associate trials with their human-readable identifiers.

## Stage Two, naive graph seeding

In this stage, a graph structure is created as the working space (data structure) for the graph
transform process. This graph starts out as a one-deep list of all trials, with each node in the
graph containing the trial number and the parameter offsets for that trial.

At this point the graph is merely expressing the trials, or parameter space coordinates of each
trial. It does not yet know about any rules which must be applied to make it executable in a
practical sense, or optimal from an operational standpoint.

The `GraphSeeder` creates one `TRIAL_SEED` node per trial, each carrying its trial index. No
edges are added at this stage.

## Stage Three, graph structuring

At this point, the graph is ready to be transformed by named graph transformation rules. The
element axes and relationship types imply the rules which should be used to transform the graph.
These transformations are applied in a defined order, and may be iterated until no further reduction
is possible.

Each transformation rule is described below. The rules are applied in the order listed. All rules
share a `RuleContext` that provides access to the topologically sorted elements, the
`BindingStateComputer`, the `MixedRadixEnumerator`, trial element names, lifeline clusters, and
DEDICATED reverse dependency mappings.

### Rule 1: Element lifecycle expansion

Each trial node from Stage Two is replaced by a subgraph of element lifecycle nodes. For each
element E in the element stack and each trial Ti, the following nodes are created:

- **activate(E, Ti)**: Represents deploying an instance of element E with the parameter values
  determined by trial Ti.
- **deactivate(E, Ti)**: Represents undeploying that instance. For **command** elements that are
  trial elements, this is replaced by **await(E, Ti)**, which waits for the command to complete
  naturally rather than issuing a shutdown. Command trial elements receive no separate deactivate
  node because they self-terminate.

Within each trial, the per-element nodes are ordered:

```
Service trial element:   activate(E, Ti) → deactivate(E, Ti)
Command trial element:   activate(E, Ti) → await(E, Ti)
Non-trial element:       activate(E, Ti)  ...  deactivate(E, Ti)
                         (edges connecting these are added by Rules 2–4)
```

For service trial elements, the trial's work phase is the span between activation and
deactivation — no separate node is needed. Non-trial element activation must be complete before
the trial's notification phase (Rule 4), and their deactivation occurs at group boundaries or
at graph end.

After this rule, the graph contains N × E lifecycle subgraphs (where N is the number of trials and
E is the number of elements), with no inter-element or inter-trial edges yet. The original
`TRIAL_SEED` nodes are removed from the graph.

**Node naming convention:** Activate nodes are named `activate_{elementName}_t{trialIndex}`,
deactivate nodes `deactivate_{elementName}_t{trialIndex}`, and await nodes
`await_{elementName}_t{trialIndex}`. These predictable IDs are relied upon by subsequent rules
for node lookup.

### Rule 2: Dependency edge materialization

For each dependency relationship declared on the element prototypes, edges are added between element
lifecycle nodes. The edge pattern depends on the relationship type. In the patterns below,
`deactivate(E, Ti)` refers to whichever termination node applies for element E — a deactivate node
for service elements, or an await node for command trial elements.

**SHARED**: Element X shares element Y. Within each trial Ti:

```
activate(Y, Ti) → activate(X, Ti)
deactivate(X, Ti) → deactivate(Y, Ti)
```

X cannot activate until Y is active. X must deactivate before Y deactivates. Multiple elements may
share the same Y instance concurrently.

**EXCLUSIVE**: Element X isolates element Y. The same edges as SHARED, plus a serialization
constraint: no two exclusive dependents of Y may be active at the same time. For distinct element
prototypes X and Z that both exclusively depend on Y, serialization edges are added between their
lifecycles in trial order:

```
deactivate(X, Ti) → activate(Z, Tj)   where Tj is the next trial requiring Z after Ti
```

If two different element prototypes X and Z both exclusively depend on Y within the same trial,
this is a configuration error — mutual exclusivity cannot be satisfied when both must be active
simultaneously. The planner emits warning **W002** (see Planner Warnings Catalog) in this case.

For the same element prototype appearing across consecutive trials, the serialization ensures
each instance completes before the next begins. The serialization order follows trial order.

**Instance-scoped serialization:** Serialization edges are only added between consecutive trials
where the exclusive target Y is the **same instance** — i.e., trials T and T+1 are in the same
group for Y. When Y has different parameter values at T vs T+1 (different group), they are
distinct physical instances with no resource conflict, and the dependent elements may run in
parallel. For example, if `command-1` exclusively depends on `node-for-command-1` and the node
has an `instance_type` axis with values `[i4i-4xlarge, m5d-4xlarge]`, each trial uses a different
node instance — no serialization is needed between them.

Because Rule 2 runs before Rule 3 (group coalescing), all EXCLUSIVE serialization edges are
established against the per-trial nodes. When Rule 3 subsequently coalesces the target Y across
a group, the serialization edges are lifted to the coalesced group-level nodes, preserving the
constraint: only one exclusive dependent of Y may be active at a time within Y's coalesced
span. For elements in different groups (where Y is deactivated and reactivated at the group
boundary), exclusivity is naturally satisfied by the teardown/reactivation cycle — no
cross-group enforcement is needed. When an element exclusively depends on a target that
coalesces at a much broader scope (e.g., a run-scoped target), the serialization effectively
spans the entire run. The planner emits warning **W001** in this case.

**DEDICATED**: Element X has a dedicated instance of Y. Y's lifecycle is coupled to X's lifecycle.
Each instance of X gets its own instance of Y, and that Y instance is never shared with other
elements:

```
activate(Y_for_X, Ti) → activate(X, Ti)
deactivate(X, Ti) → deactivate(Y_for_X, Ti)
```

The dedicated Y instance is tagged as belonging to X (via `dedicated_to` metadata on the activate
node) and participates in X's group coalescing (Rule 3). If X is coalesced across multiple trials,
X's dedicated Y is also coalesced with it.

**LINEAR**: Element X comes after element Y (full lifecycle ordering within shared trial scope). Y
must be activated and deactivated before X is activated:

```
deactivate(Y, Ti) → activate(X, Ti)
```

This constraint applies only when X and Y share the same trial scope (same configuration group). If
X and Y are in different trial scopes, no linear edge is added and their lifecycles are independent.

**Implementation note:** The current code in `Rule2_DependencyEdges.applyLinear()` applies the
linear edge unconditionally for every trial. The same-group-scope check described above is the
intended design but is not yet implemented. The `BindingStateComputer.sameGroupForElement()` method
(already used by `applyExclusive()`) would be the correct mechanism to add this check.

**LIFELINE**: Element X has a lifeline to element Y. X's deactivation is subsumed by Y's
deactivation — when Y deactivates, X is automatically deactivated as a side-effect:

```
activate(Y, Ti) → activate(X, Ti)
```

The deactivate(X, Ti) node is removed from the graph, and any edges that targeted deactivate(X, Ti)
are remapped onto deactivate(Y, Ti). This is an optimization, not a delay: since X's lifetime is
bounded by Y's, skipping X's explicit deactivation removes a redundant step rather than postponing
anything. When Y's deactivation is processed by the executor, X is implicitly deactivated as a
side-effect. For lifeline clusters (multiple elements connected by lifeline relationships), all
members are deactivated as a single transactional event when the cluster root deactivates, and all
deactivation edges targeting any cluster member are remapped onto the cluster root's deactivation
node.

### Rule 3: Group coalescing (the reduction)

This is the core of the reducto algorithm. For each non-trial element, consecutive trials where the
element's configuration is identical are coalesced: the per-trial activate/deactivate pairs are
replaced by a single activate/deactivate pair that spans the entire group.

The coalescing rule uses the binding state model from Stage One. An element E is concretely bound at
group level K if all of E's parameters have determined values at that level. Within each level-K
group, E's configuration is constant, so E needs only one activation for the group:

```
Before coalescing (element a, 3 trials in a level-2 group):
  activate(a, T0) → ... → deactivate(a, T0)
  activate(a, T1) → ... → deactivate(a, T1)
  activate(a, T2) → ... → deactivate(a, T2)

After coalescing:
  activate(a, G0) → ... → deactivate(a, G0)
  (single instance spanning T0, T1, T2)
```

All nodes within the group that previously depended on per-trial activate/deactivate nodes for E
now depend on the group-level activate/deactivate nodes.

**Coalescing constraints:**

- **Trial elements are never coalesced.** Each trial must have its own trial element instances
  because the trial element lifecycle defines the trial boundary. Even if a trial element's
  configuration is identical across consecutive trials, it must be deactivated and reactivated for
  each trial so that trial notifications and outcome collection occur per-trial.

- **DEDICATED targets coalesce with their owner.** If X dedicatedly depends on Y, then Y's
  coalescing follows X's coalescing. If X is coalesced across a group, Y is also coalesced across
  that same group. If X is a trial element (not coalesced), Y also gets per-trial instances.
  The effective binding level for a DEDICATED target is resolved by walking up the DEDICATED
  ownership chain and taking the **maximum** binding level found along the way. This is necessary
  because the varying parameter may live on an interior element of the chain rather than the root.
  For example, if the chain is `testclient → database → victoria → globalconfig` (all DEDICATED)
  and only `database` owns a varying axis at binding level 1, then all DEDICATED targets must use
  level 1 so they produce the correct number of per-group instances.

- **Elements with no axes (or no varying parameters) are run-scoped.** They coalesce to a single
  activate/deactivate spanning the entire graph (group level 0). Their activation appears at graph
  start and their deactivation at graph end.

- **Elements are only activatable when concretely bound.** Because an element's parameters
  occupy contiguous ranks (R through R+K-1), it becomes concretely bound at group level R+K and
  coalesces at that level. In-place reconfiguration is not supported; an element must be
  deactivated and reactivated when its parameter values change at a group boundary.

**At group boundaries** — where the next group has different parameter values for a coalesced
element — the element's deactivate node is placed after the last trial in the outgoing group, and a
new activate node is placed before the first trial in the incoming group. Dependency ordering
applies to these boundary transitions: if element A depends on element B, and both change at the
same group boundary, A is deactivated before B, and B is activated before A (reverse dependency
order for teardown, forward dependency order for activation).

**Coalescing mechanics:** The first trial's activate node in a group becomes the group activate.
The last trial's deactivate/await node becomes the group deactivate. The first trial's
deactivate node (which is NOT the group deactivate) is explicitly removed and its edges remapped
to the group deactivate. Then for each subsequent trial (i=1..K-1) in the group, both the
per-trial activate and per-trial deactivate nodes are removed and their edges remapped to the
corresponding group-level nodes. This ensures the Coalescing Congruence Principle is maintained.

### Rule 4: Trial notification insertion

For each trial Ti, two notification nodes are inserted:

- **notify-trial-start(Ti)**: Signals to all active non-trial elements that a trial is about to
  begin. This node depends on all non-trial element activations being complete (either per-trial
  activations from Rule 1 or group-level activations from Rule 3).

- **notify-trial-end(Ti)**: Signals that the trial has concluded. This node depends on all trial
  element deactivations/await completions for Ti.

The ordering constraint is:

```
all non-trial activate(E, ...) → notify-trial-start(Ti) → all trial activate(E, Ti)
all trial deactivate/await(E, Ti) → notify-trial-end(Ti)
```

Trial elements are activated only after notify-trial-start, ensuring that non-trial elements are
assembled and aware of the trial before the trial window opens. Non-trial elements receive the
start notification before trial elements begin, and the end notification after trial elements
complete, allowing them to bracket metrics windows and other trial-scoped observations.

**Coalesced activate resolution:** When Rule 3 has coalesced a non-trial element, the per-trial
activate node no longer exists. Rule 4's `findActivateForTrial()` resolves this by:
1. Looking up the per-trial node directly (handles un-coalesced elements).
2. If not found, collecting all remaining activate nodes for the element.
3. If only one activate node remains (single-group or run-scoped), returning it directly.
4. If multiple groups exist, matching by `groupIndex` using `BindingStateComputer.groupIndexForElement()`.

**Group deactivation ordering:** When a coalesced non-trial element is deactivated at a group
boundary, its deactivate node must depend on the `notify_trial_end` of EVERY trial in the
outgoing group, not just the last one. Without this, when trials run concurrently within a
group, earlier trials' `notify_trial_end` events could still be in-flight when the non-trial
element begins deactivation, creating a race condition for any synchronous work or data
collection triggered by the notify-end event. The constraint is:

```
notify_trial_end(Ti) → deactivate(E_coalesced, G)    for every trial Ti in group G
```

This applies to ALL trials in the group, not just the final one. For sequential trials the
edges to earlier notify_end nodes are redundant (since each trial completes before the next
starts), but the edges are still inserted to maintain correctness when trials can overlap.

The wiring method handles coalesced elements by walking forward from a trial's expected
deactivation node position to find the group's actual deactivation node at the last trial
in the group.

**DEDICATED target handling:** DEDICATED targets whose owner is a trial element are not coalesced
by Rule 3 (each trial gets its own dedicated instance). This means per-trial deactivation nodes
remain in the graph rather than being merged into a single group deactivation. Each per-trial
deactivation must depend on its corresponding `notify_trial_end`:

```
notify_trial_end(Ti) → deactivate(dedicated_target, Ti)    for each trial Ti
```

The wiring method detects un-coalesced per-trial deactivation nodes and wires them individually,
rather than searching only for the group-level deactivation at the last trial. This is an instance
of the coalescing congruence principle: since DEDICATED targets are not coalesced, their notify_end
wiring must match the per-trial granularity.

Note that notification nodes are operational actions that signal the trial boundary to non-trial
elements — they do not represent the trial itself (which remains an inferred boundary around the
trial element lifecycles). The key invariant is that the trial scope begins before any trial
element is activated and ends after all trial elements are inactive. Any mechanism that preserves
this invariant — whether dedicated notification nodes, callbacks on activation/deactivation
steps, or another approach — is valid. The planner should use whichever form produces the
clearest graph structure for the executor.

**Exclusive serialization rerouting:** Rule 2 creates direct exclusive serialization edges of the
form `deactivate/await(X, Ti) → activate(X, Ti+1)` (and cross-element variants) for elements with
EXCLUSIVE dependencies. After inserting the notify nodes, Rule 4 detects these direct edges between
**trial elements** and reroutes them through the notify boundaries. The direct edge is removed and
replaced by `notify_end(Ti) → notify_start(Ti+1)`, since `deactivate/await(X, Ti) → notify_end(Ti)`
and `notify_start(Ti+1) → activate(X, Ti+1)` already exist from Rule 4's normal wiring. This
ensures that the trial notification lifecycle is properly coupled to the exclusive serialization
order — a control path cannot pass through multiple notify-start steps concurrently when the
downstream trial elements are mutually exclusive. The resulting path becomes:

```
deactivate/await(X, Ti) → notify_end(Ti) → notify_start(Ti+1) → activate(X, Ti+1)
```

This rerouting applies to both self-serialization edges (same element across consecutive trials)
and cross-element serialization edges (different elements that exclusively depend on the same
target).

**Non-trial elements are not rerouted.** For non-trial elements with EXCLUSIVE dependencies
(such as a DEDICATED target whose owner is a trial element), the notify wiring direction is
the opposite of trial elements: `activate(B) → notify_start` (B activates before the trial
starts) and `notify_end → deactivate(B)` (B deactivates after the trial ends). If the
serialization edge `deactivate(B, Ti) → activate(B, Ti+1)` were rerouted through notify
boundaries, the next trial's B activation could run in parallel with the current trial's B
deactivation — both happen "before" `notify_start(Ti+1)` without any ordering between them.
This would violate the exclusive constraint by allowing two B instances to be active
simultaneously. Therefore, non-trial serialization edges are left intact as direct edges.

**Non-trial deactivation enforcement:** After wiring all notify_end nodes to group deactivations,
Rule 4 removes any direct edges from trial element termination nodes to non-trial element
deactivation nodes. These direct edges (originating from Rule 2's dependency wiring, possibly
remapped by Rule 3's coalescing) would allow a non-trial element to begin deactivation
concurrently with or before `notify_trial_end` processing. The correct control path is:

```
trial_terminate(X, Ti) → notify_end(Ti) → deactivate(non_trial, G)
```

The indirect path through `notify_end` is established by the group deactivation ordering
(all notify_ends in the group precede the deactivation) and the main wiring loop (trial
terminations precede notify_end). The direct edges are redundant and potentially unsafe,
so they are removed.

### Rule 5: Health check readiness gates

For each activate node where the element defines a health check (via `Element.healthCheck()`), a
**readiness-gate** node is inserted between the activation and any nodes that depend on the element
being ready:

```
activate(E, ...) → readiness-gate(E, ...) → [dependent nodes]
```

The readiness gate represents the health check loop: the executor retries the health check according
to the element's `HealthCheckSpec` (timeout, max retries, retry interval) until the element reports
ready. No node that depends on E may proceed until the readiness gate is satisfied.

The readiness gate is NOT inserted between the activate node and the element's own deactivation
or await node. The deactivation/await edge remains directly connected to the activate node so
that the element can be torn down even if the health check has not yet passed.

If the health check fails (exhausts retries or times out), the readiness gate node fails and the
element's error handling policy is invoked.

### Rule 6: Concurrency annotation

For each element that declares a global concurrency limit (`max_concurrency`) or a group concurrency
limit (`max_group_concurrency`), the planner annotates the relevant activate nodes with concurrency
directives. These directives are metadata on the nodes, not structural edges.

The annotation is stored as a `max_concurrency` metadata key on `ACTIVATE` nodes. The value is
read from `DefaultElement.maxConcurrency()` or from an element's `max_concurrency` tag as a
fallback.

The executor is responsible for observing these annotations and ensuring that no more than the
specified number of instances of the element are active at any given time. When the limit would be
exceeded, the executor delays activation of new instances until existing instances have been
deactivated.

### Rule 7: Start and end materialization

Two sentinel nodes are added to finalize the graph:

- **start**: Connected to all nodes that have no incoming edges. This is the entry point of the
  graph. The executor begins by marking the start node as complete, which makes all root nodes
  eligible for execution.

- **end**: All nodes that have no outgoing edges are connected to end. The graph is complete when
  the end node's inputs are all satisfied.

After materialization, the graph is validated as a proper DAG (no cycles). A cycle indicates a
configuration error in the element dependencies.

### Rule 8: Transitive reduction

After all structural rules have been applied (including start/end sentinel connections from Rule 7),
the graph may contain transitive edges — direct edges A → C where an indirect path A → B → C also
exists. These transitive edges are redundant because `DefaultExecutionGraph` already provides
`transitiveDependencies()` for runtime queries.

Rule 8 performs a standard transitive reduction on the DAG:

```
For each node N in topological order:
  For each successor S of N:
    If S is reachable from any other successor of N (via BFS/DFS):
      Remove the direct edge N → S
```

This reduces visual clutter and ensures that the graph contains only the essential ordering
constraints. The transitive reduction preserves the reachability relation — the set of nodes
reachable from any given node is unchanged.

## Post-Rule Validation and Metadata Stamping

After all eight rules have been applied:

1. **Lifecycle invariant validation:** `ReductoStepGenerationStrategy.validateLifecycleInvariant()`
   checks that every element has equal ACTIVATE and DEACTIVATE/AWAIT counts (or zero deactivations
   for LIFELINE-subsumed elements). This catches any coalescing or edge-remapping bugs.

2. **Trial code stamping:** For every node with a valid `trialIndex`, the enumerator's
   `trialCode()` is computed and stored as `trial_code` metadata on the node. This propagates
   through linearization into the resulting `AtomicStep` records.

## Stage Four: Graph Linearization

The finalized graph is converted into a flat list of `AtomicStep` records by `GraphLinearizer`.
The graph is topologically sorted, and each `ReductoNode` is mapped to the appropriate
`AtomicStep` subtype based on its `ReductoNodeType`:

| ReductoNodeType | AtomicStep Subtype | Notes |
|----------------|-------------------|-------|
| `START` | `CheckpointState` | metadata `type=start` |
| `END` | `CheckpointState` | metadata `type=end` |
| `ACTIVATE` | `DeployElement` | Carries instance number, configuration, element deps metadata |
| `DEACTIVATE` | `TeardownElement` | Carries instance number |
| `AWAIT` | `AwaitElement` | Carries trial ID and element binding snapshot |
| `NOTIFY_TRIAL_START` | `NotifyTrialStart` | Carries trial code, trial element names |
| `NOTIFY_TRIAL_END` | `NotifyTrialEnd` | Carries trial code, shutdown reason |
| `READINESS_GATE` | `BarrierSync` | Also produces a `Barrier` record |
| `TRIAL_SEED` | *(error)* | Should have been expanded by Rule 1 |

**Instance tracking:** The linearizer maintains a per-element instance counter. Each `ACTIVATE`
node increments the counter to produce a new instance number. `DEACTIVATE` and `AWAIT` nodes
reference the current (most recent) instance number for their element. This produces monotonically
increasing instance numbers that uniquely identify each element instance within the plan.

**Configuration overlay:** For `ACTIVATE` nodes, the linearizer starts with the element's static
configuration and overlays trial-specific parameter assignments from the trial's `assignments()`
map. Parameters are matched either by qualified name (`elementName.paramName`) or by bare
parameter name.

**Element dependency metadata:** `ACTIVATE` nodes carry an `element_deps` metadata key listing
the names of all elements the activated element directly depends on. This metadata is consumed
by `DefaultElementInstanceGraph` to reconstruct the element-level dependency graph from
linearized steps and compute transitive instance-level edges.

## Element Instance Graph

After linearization, the `ElementInstanceGraph` can be derived from the step list to provide a
static view of the instance-level topology. This graph is synthesized by
`DefaultElementInstanceGraph` and captures which element instances exist and which instance
depends on which other instance, without requiring runtime state.

**Derivation algorithm:**

1. Scan steps for `DeployElement` to discover all `(elementId, instanceNumber)` pairs and their
   configurations.

2. Build a trial-index → trial-code lookup from `NotifyTrialStart` steps. Each `InstanceNode`
   carries an `Optional<String> trialCode` resolved from the deploy step's `trial_index` metadata
   cross-referenced against this lookup.

3. Extract element-level transitive dependencies from `element_deps` metadata on deploy steps.
   This uses a BFS transitive closure to build a set of all transitively depended-on element names
   for each element.

4. For each deploy step, walk dependencies via BFS, passing through barriers and non-deploy steps,
   until upstream `DeployElement` steps are found. These produce instance-level edges, filtered
   against the transitive element dependency set to prevent spurious edges from notification fan-in.
   When a deploy step for the **same** element is encountered (serial reuse), the BFS walks
   through it to find the upstream element instances behind it.

5. Compute topological order via Kahn's algorithm on the resulting instance-level edge set.

The `ElementInstanceGraph` is available via `ExecutionPlan.elementInstanceGraph()` for UI
rendering, validation, and static analysis of the compiled plan.

### Worked example

Using the example from above:

```yaml
elements:
  a:
    axes:
      param_x: [1,2,3]
      param_y: [10,20]
  b:
    axes:
      param_u: ["asm", "dra", "ghi"]
```

Assume `a` is a service element, `b` is a command element, `b` depends on `a` (SHARED), and `b`
is the trial element (most dependent, innermost).

**Stage One output:**

Parameter ranks (depth-first): param_x=rank 0, param_y=rank 1, param_u=rank 2.
Total trials: 3 × 2 × 3 = 18.

Element binding analysis (an element with parameters spanning ranks R through R+K-1 becomes
concretely bound at level R+K, i.e. after all K parameters are determined):
- Element `a`: parameters at ranks 0 and 1 (K=2). Concretely bound at level 2.
- Element `b`: parameter at rank 2 (K=1). Concretely bound at level 3 (R+K = 2+1 = 3, meaning
  all parameters across all elements are determined — this is the per-trial level).

Group structure:
- Level 0: 1 group of 18 trials
- Level 1: 3 groups of 6 (by param_x: 1, 2, 3)
- Level 2: 6 groups of 3 (by param_x × param_y)
- Level 3: 18 individual trials

**Stage Two output:** Flat list of 18 trial nodes: T0..T17.

**Rule 1 (expansion):** Each trial gets lifecycle nodes for both elements. For `a` (service):
18 activate + 18 deactivate. For `b` (command trial element): 18 activate + 18 await. Total:
72 nodes.

**Rule 2 (dependencies):** SHARED edges added. For each trial Ti:
`activate(a, Ti) → activate(b, Ti)` and `await(b, Ti) → deactivate(a, Ti)`.

**Rule 3 (coalescing):**
- Element `a` is concretely bound at level 2. It coalesces to 6 group-level instances (one per
  param_x × param_y combination). 36 activate/deactivate pairs for `a` reduce to 6.
- Element `b` is a trial element, so it is **not** coalesced. 18 instances remain.
- Net reduction: 72 nodes → 12 (for `a`) + 36 (for `b`: 18 activate + 18 await) = 48 nodes.

At each level-2 group boundary (every 3 trials), `a` is deactivated and a new `a` instance with
different param_x/param_y values is activated.

**Rule 4 (notifications):** 18 notify-trial-start and 18 notify-trial-end nodes inserted. Each
notify-trial-start depends on the group-level activate(a) being complete. Each trial's activate(b)
depends on the corresponding notify-trial-start. Each group's deactivation depends on ALL
notify-trial-end nodes within that group:
`notify-trial-end(T0), notify-trial-end(T1), notify-trial-end(T2) → deactivate(a, G0)`,
`notify-trial-end(T3), notify-trial-end(T4), notify-trial-end(T5) → deactivate(a, G1)`, etc.
This ensures `a` has processed every trial-end notification in the group before being torn down,
even when trials within a group run concurrently. Direct edges from trial terminations to
non-trial deactivations (from Rule 2) are also removed, forcing the control path through
notify_end.

**Rule 5 (health checks):** If `a` defines a health check, 6 readiness-gate nodes are inserted
(one per group-level activation).

**Rule 6 (concurrency):** If `b` has max_concurrency=2, the executor will ensure no more than 2
instances of `b` are active simultaneously (delaying activations as needed).

**Rule 7 (start/end):** Start node connects to the first activate(a) and any other root nodes. End
node receives edges from the last deactivate(a) and any other leaf nodes.

**Rule 8 (transitive reduction):** Any transitive edges are removed. For example, if
`start → activate(a, G0)` and `start → notify_trial_start_0` both exist, but
`activate(a, G0) → notify_trial_start_0` also exists, then the direct
`start → notify_trial_start_0` edge is removed since it is transitively implied. This
produces a minimal graph with only the essential ordering constraints.

**Final graph summary:**
- 6 activate/deactivate pairs for `a` (group-coalesced)
- 18 activate/await pairs for `b` (per-trial)
- 18 notify-trial-start + 18 notify-trial-end
- 6 readiness gates for `a` (if health check defined)
- 1 start + 1 end
- Total: ~92 nodes (48 after coalescing + 36 notification + 6 health gates + 2 sentinels)

The key reduction is element `a`: instead of 18 deploy/teardown cycles, it has 6 — one per unique
configuration. Within each level-2 group of 3 trials, `a` stays active while `b` cycles through its
3 param_u values.

# Source File Map

The reducto planner implementation is spread across the following files:

| File | Role |
|------|------|
| `ReductoStepGenerationStrategy` | Entry point; orchestrates the pipeline, validates lifecycle invariant, stamps trial codes |
| `MixedRadixEnumerator` | Stage One: mixed-radix decomposition, trial codes, group index/count computation |
| `BindingStateComputer` | Computes per-element binding levels with SHARED/EXCLUSIVE propagation |
| `GraphSeeder` | Stage Two: creates `TRIAL_SEED` nodes |
| `ReductoGraph` / `ReductoNode` | Mutable DAG data structure |
| `ReductoNodeType` | Enum of node types (TRIAL_SEED, ACTIVATE, DEACTIVATE, AWAIT, NOTIFY_*, READINESS_GATE, START, END) |
| `rules/Rule` | Rule interface |
| `rules/RuleContext` | Shared context: sorted elements, binding state, enumerator, trial element names, lifeline clusters, DEDICATED dependents |
| `rules/Rule1_LifecycleExpansion` | Replaces TRIAL_SEED with activate/deactivate/await node pairs |
| `rules/Rule2_DependencyEdges` | Adds edges for SHARED, EXCLUSIVE, DEDICATED, LINEAR, LIFELINE |
| `rules/Rule3_GroupCoalescing` | Core reduction: merges per-trial nodes into group-level nodes; resolves DEDICATED chains |
| `rules/Rule4_TrialNotifications` | Inserts NOTIFY_TRIAL_START/END, reroutes exclusive serialization, enforces non-trial deactivation ordering |
| `rules/Rule5_HealthCheckGates` | Inserts READINESS_GATE after activations with health checks |
| `rules/Rule6_ConcurrencyAnnotation` | Annotates ACTIVATE nodes with `max_concurrency` metadata |
| `rules/Rule7_StartEndMaterialization` | Adds START/END sentinels, validates acyclicity |
| `rules/Rule8_TransitiveReduction` | Removes redundant transitive edges |
| `GraphLinearizer` | Stage Four: topological sort, node-to-AtomicStep mapping, instance tracking |
| `StepGenerationUtils` | Shared utilities: topological sort, trial element identification, fingerprinting, lifeline clustering |
| `DefaultElementInstanceGraph` | Post-linearization: derives instance-level topology from AtomicStep list |
| `ElementInstanceGraph` | API interface for static instance topology (in paramodel-api) |

# Planner Warnings Catalog

The reducto planner emits warnings during graph construction to alert users to configurations
that are semantically valid but may produce unexpected operational behavior. Each warning has a
code, severity, and description.

## Severity Levels

- **INFO**: Informational. The configuration is fine but has a notable property the user should
  be aware of.
- **WARN**: Warning. The configuration is valid but likely produces behavior the user did not
  intend. The plan is still generated but the user should review.
- **ERROR**: Error. The configuration is valid in isolation but creates a condition that the
  planner cannot resolve safely. The plan may still be generated, but the flagged section is
  likely to perform poorly or incorrectly.

## Warnings

| Code | Severity | Condition | Message |
|------|----------|-----------|---------|
| W001 | WARN | An element exclusively depends on a target that coalesces at a much broader scope (e.g., element at level 3 exclusively depends on a run-scoped element at level 0). | "Element '{X}' exclusively depends on '{Y}' which is scoped to level {K}. This serializes all {N} exclusive dependents of '{Y}' across the entire level-{K} group. Consider adding intermediate elements to narrow the exclusivity scope, or verify that full serialization is intended." |
| W002 | ERROR | Two different element prototypes both exclusively depend on the same target within the same trial. | "Elements '{X}' and '{Z}' both exclusively depend on '{Y}' within trial {Ti}. Mutual exclusivity cannot be satisfied when both must be active simultaneously. Restructure dependencies so that at most one element exclusively depends on '{Y}' per trial." |