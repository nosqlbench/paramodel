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

## Stage Two, naive graph seeding

In this stage, a graph structure is created as the working space (data structure) for the graph
transform process. This graph starts out as a one-deep list of all trials, with each node in the
graph containing the trial number and the parameter offsets for that trial.

At this point the graph is merely expressing the trials, or parameter space coordinates of each
trial. It does not yet know about any rules which must be applied to make it executable in a
practical sense, or optimal from an operational standpoint.

## Stage Three, graph structuring

At this point, the graph is ready to be transformed by named graph transformation rules. The
element axes and relationship types imply the rules which should be used to transform the graph.
These transformations are applied in a defined order, and may be iterated until no further reduction
is possible.

Each transformation rule is described below. The rules are applied in the order listed.

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
E is the number of elements), with no inter-element or inter-trial edges yet.

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

The dedicated Y instance is tagged as belonging to X and participates in X's group coalescing (Rule
3). If X is coalesced across multiple trials, X's dedicated Y is also coalesced with it.

**LINEAR**: Element X comes after element Y (full lifecycle ordering within shared trial scope). Y
must be activated and deactivated before X is activated:

```
deactivate(Y, Ti) → activate(X, Ti)
```

This constraint applies only when X and Y share the same trial scope (same configuration group). If
X and Y are in different trial scopes, no linear edge is added and their lifecycles are independent.

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

**Group boundary ordering:** When a coalesced non-trial element is deactivated at a group
boundary, its deactivate node must depend on the notify-trial-end of the last trial in the
outgoing group. This ensures that non-trial elements have the opportunity to receive and
process the final trial-end notification (and any synchronous work that results from it)
before they are torn down. The constraint is:

```
notify-trial-end(T_last_in_group) → deactivate(E_coalesced, G_outgoing)
```

Note that notification nodes are operational actions that signal the trial boundary to non-trial
elements — they do not represent the trial itself (which remains an inferred boundary around the
trial element lifecycles). The key invariant is that the trial scope begins before any trial
element is activated and ends after all trial elements are inactive. Any mechanism that preserves
this invariant — whether dedicated notification nodes, callbacks on activation/deactivation
steps, or another approach — is valid. The planner should use whichever form produces the
clearest graph structure for the executor.

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

If the health check fails (exhausts retries or times out), the readiness gate node fails and the
element's error handling policy is invoked.

### Rule 6: Concurrency annotation

For each element that declares a global concurrency limit (`max_concurrency`) or a group concurrency
limit (`max_group_concurrency`), the planner annotates the relevant activate nodes with concurrency
directives. These directives are metadata on the nodes, not structural edges.

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
depends on the corresponding notify-trial-start. At group boundaries, the coalesced deactivation
depends on the last trial-end notification in the group:
`notify-trial-end(T2) → deactivate(a, G0)`, `notify-trial-end(T5) → deactivate(a, G1)`, etc.
This ensures `a` processes all trial-end notifications before being torn down.

**Rule 5 (health checks):** If `a` defines a health check, 6 readiness-gate nodes are inserted
(one per group-level activation).

**Rule 6 (concurrency):** If `b` has max_concurrency=2, the executor will ensure no more than 2
instances of `b` are active simultaneously (delaying activations as needed).

**Rule 7 (start/end):** Start node connects to the first activate(a) and any other root nodes. End
node receives edges from the last deactivate(a) and any other leaf nodes.

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
