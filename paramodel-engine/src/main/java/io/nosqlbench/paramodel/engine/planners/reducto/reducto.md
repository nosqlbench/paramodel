# Reducto Step Planner

This step planner is meant to be the default planner once it is complete.

# Execution Graph Semantics

The execution graph is a control structure which captures the order in which operations should be executed. It is a directed acyclic graph (DAG) of operations, where each operation is a node in the graph and each edge represents a dependency between operations.

When a node in the graph has multiple incoming edges, these represent a dependency on multiple operations, and thus an operational barrier. In other words, for a node to be considered ready to execute, all of its incoming edges must be satisfied. An edge is satisfied when the operation represented by its upstream node has been completed.

Nodes may be completed only in successful normative terms. In other words, if a side-effect process fails, the node should be considered failed and thus its out edges should not be satisfied. Handling errors, is thus an internal concern to the nodes within the graph.

## Element Stack Semantics

The element stack is the set of elements and axes which define the goals of the user. This is the primary input into execution graph construction. The element stack contains a prototypical set of element definitions, their parameters, and the axes and related values for each element. Crucially, the element stack also contains an internal set of directed dependency relationships between the prototypical elements. 

Descriptively, the elements described in the element stack are not real instances of the elements, but rather prototypes which are used to create them. Since an Element may have an axis with multiple values, the prototypical element may be instantiated multiple times, once for each value in the axis. Further it may be instanced many more times if it has a dependency on some other element (direct or transitive) which also has its own axes.

The execution graph is concerned with the lifecycle of the elements, and the dependency relationships between them. Activating (deploying) an instance of an element which was described on the element stack is one of the fundamental operations of the execution graph. Conversely, Deactivating (undeploying) an instance of an element is another fundamental operation. The lifecycle of an element is defined by the following rules:

- An element instance is activated when it is successfully. From this time forward, it is considered "active"
- An element instance is deactivated when it is is successfully undeployed. From this time forward, it is considered "inactive".

The full lifecycle of an element instances is the span between successful activation and successful deactivation.

Elements in the stack will be either service (fixtures which can be started and stopped), or commands (fixtures which can be started, but which typically complete on their own). The type of element (service or command) must be provided as part of the element's definition. This determines which step is appropriate to mark the end of an element's lifecycle, AKA when it is deactivated. (or deactivates itself)

A command element must be waited for by a specific "wait for" execution step. Other _service_ elements must be stopped by a specific "deactivate" step.

Semantically, _activate_ and _deactivate_ are also described as "deploy" and "tear down" elsewhere in the paramodel documentation.

Each element may have a global concurrency limit, which is the maximum number of instances (of that element prototype) which may be active at any given time. This is expressed as the "max concurrency" parameter of the element definition. They may also have a group concurrency limit, which is the maximum number of instances (of that element prototype) which may be active at any given time for a given group of trials. This is expressed as the "max group concurrency" parameter of the element definition.

### Shared Element Dependencies

An element prototype X may be dependent on element prototype Y such that it doesn't mind sharing instances of element Y with other element instances. This is expressed as "Element X shares Element Y". Element X instances are not aware of what other elements instances share Y, only that it knows there may be some.

This is for scenarios where you have a common service, for example, which is necessary to enable many other elements to operate, but which is not critical to the operation of the other elements in terms of measured outputs. This might be an authentication service, or a telemetry aggregator, or a database of config values.

### Exclusive Element Dependencies

An element prototype X may be dependent on element prototype Y such that instances of X must not share instances of Y with any other element instances. This is expressed as "Element X isolates Element Y." Element X will only be activated when Y is able to be completely exclusive to it for the duration of its lifetime. Once an instance of X is active, other element instances which have a dependency on Y will not be activated until the X instance is no longer active.

This is for scenarios where you have a common fixture which is necessary for your test, but which should not be shared concurrently, but which may be shared serially. Maybe it is part of a system under test which is expensive to stand up and tear down, but persisting this shared fixture comes with no worry of residue, measurement perturbation, or interference from other test elements.

### Dedicated Element Dependencies

An element prototype X may be dependent on element prototype Y such that any instance of Y is solely and completely dedicated to an instance of element X. This means that an instance of Y must be activated for each instance of X, and that X must not be activated until it's dedicated instance of Y is active.

This is for scenarios where you know that starting a new instance of Y is necessary to prevent previous state, caching, or usage residue from interfering with the operation of X or the accurate measurement thereof.

### Lifeline Element Dependencies

An element prototype X may be dependent on element prototype Y such that when the associated instance of Y is deactivated, the associated instance of X will be automatically be deactivated as a side-effect. This is said as "X has a lifeline to Y". When element Y is deactivated, the system must take this as both instances being deactivated. This is true for any such strictly. connected lifeline cluster. In other words, instances which are deactivated that have other instances depending on them for a lifeline, subsume the deactivation state for all of those in a single deactivation event or transactional update.

This is for scenarios where, for example, a container runs on a cloud host, and stopping the container before stopping the cloud host serves no operational purpose. Forcing the LIFO stack ordering here would simply delay the deactivation of the cloud host until the container was stopped, which is not desirable.

### Linearized Element Dependencies

An element prototype X may be dependent on another element Y such that instance of X must be activated only after instance of Y has been activated and deactivated. This is expressed as "X must comes after Y". In this sense, this dependency represents causal ordering more strongly than the other dependency types, which are more focused on concurrent activation constraints.

This is for scenarios where you want to have a sequence of ordered operations, or where instances may actually work as a pipeline over data from one to the next. The first case is merely _serialized_, but the _linearized_ relationship works for either case.

## Element Graph Semantics

The rules above describe how elements may be related to each other, but they do not describe how they are related to the execution graph. The execution graph's goal is to construct the element graph and mutate it over time to allow all the trials to be completed. Thus, the execution graph will represent all activation and deactivation steps which are needed, and in which order, to fulfill the trial configurations, dependency relationship, and concurrency limits provided by the element stack. At any point during the execution of an execution graph, it is possible to synthesize a view of the element graph which represents the current state of the execution graph. This is an invaluable diagnostic tool. When truing up an operational state of a real-world system, it is the synthetic element graph which is used to determine any deltas which need to be applied to the real-world system to make it correct and concurrent before resuming the execution graph.

## Graph Execution

A special node type called 'start' in the execution graph represents the entry point of the graph. it is implicitly connected to all nodes in the graph which would otherwise have no incoming edges.

A special node type called 'end' in the execution graph represents the exit point of the graph. Tt is implicitly connected to all nodes in the graph which would otherwise have no outgoing edges.

As part of graph planning, the start and end nodes are materialized into the plan by the step planner, and these are used by the graph runner to know where to explicitly start and how to know when the graph is complete.

The goal of the graph executor is to activate each node in the graph for which all inputs are satisfied, concurrently, until the end node is reached and all its inputs are satisfied. 

When there are concurrency limits for an element prototype (over its instances) whether global limits or group limits, it is up to the graph executor to enforce those limits. These limits will be expressed as a "concurrency limit" step in the plan where necessary to make the graph fully self-contained for execution.

# Method

The reducto step planner is a multi-stage process which creates a view of the full cartesian product of the axes and trials, then incrementally applies graph reduction rules to simplify the execution graph until no further reduction is possible.

Here is a basic example of a set of axes for use in explanations below:

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

Here is how it should work:

## Stage One, axis and trial enumeration:

The cardinality of trials is taken as the product of all axis cardinalities across all elements. The rank of a parameter in the plan is determined by its position in the list of axes, depth first across all ordered elements.

A stable and unique identifier for each bound coordinate within the parameter space is computed using the "enumerated combinations" method, where the number of a trial can easily be used to identify the parameter value offsets by reverse mapping.

There is a computed numerical signature for each trial which is different from its trial number. The numerical signature is essentially a sequence of numbers, each corresponding to one of the numbered parameters, the value of each number an offset into the array of parameters at that rank.

This computed numerical signature is affine to the number of the trial, and converting between the trial number and the parameter values that go with it, the numerical signature is a necessary and compact intermediate representation.

Let's call the enumration of trials the _trial number_, and the numerical signature the _parameter offsets_.

The point of trial enumeration is to provide a computable way to go between any given set of parameter values and the trial number that would produce them and vice-versa, without having to manually count or traverse the parameter space.

## Stage Two, naive graph seeding

In this stage, a graph structure is created as the working space (data structure) for the graph transform process. This graph starts out as a one-deep list of all trials, with each node in the graph containing the trial number and the parameter offsets for that trial.

At this point the graph is merely expressing the trials, or parameter space coordinates of each trial. It does not yet know about any rules which must be applied to make it executable in a practical sense, or optimal from an operational standpoint.

## Stage Three, graph structuring

At this point, the graph is ready to be transformed by graph transformation rules. The element axes and relationship types imply the rules which should be used to transform the graph from stage to stage. These transformations are applied iteratively to the graph until no further reduction is possible.

Each transformation step is specialized and named. Each transformation step is associated with one of the rules or semantics above and should be identified as such.
