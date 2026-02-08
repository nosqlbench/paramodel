///
/// Sequence generation and execution contracts for systematic parameter space exploration.
///
/// ## Overview
///
/// This package provides contracts for creating and managing sequences of trials.
/// A sequence is an ordered collection of parameter assignments (trials) that
/// systematically explores a parameter space.
///
/// ## Core Concepts
///
/// ```
/// Parameter Space
///       ↓ generates
/// Sequence (ordered trials)
///       ↓ contains
/// Trial (single assignment)
///       ↓ executes to produce
/// TrialResult (metrics + artifacts)
/// ```
///
/// ## Type Relationships
///
/// ```
/// SequenceBuilder
///       ↓ builds
/// Sequence
///   ├── trials(): List<Trial>
///   ├── validate(): ValidationResult
///   └── metadata(): SequenceMetadata
///
/// Trial
///   ├── id: String
///   ├── assignments: Map<String, Value<?>>
///   └── constraints: List<Constraint<?>>
///
/// TrialResult
///   ├── trial: Trial
///   ├── status: TrialStatus
///   ├── metrics: Map<String, Object>
///   └── artifacts: List<Artifact>
/// ```
///
/// ## Sequence Generation Strategies
///
/// ```
/// Strategy         Coverage              Trials    Use Case
/// ──────────────────────────────────────────────────────────────────
/// Exhaustive       100% combinations     n^k       Small spaces
/// Random           Probabilistic         N         Quick estimates
/// Pairwise         All pairs             ~O(n^2)   Interaction testing
/// Boundary         Edge values only      2k        Edge case bugs
/// Edge-First       Scaffold then fill    n^k       Progressive refinement
/// Adaptive         Result-driven         Variable  Optimization
/// ```
///
/// Where:
/// - n = average domain size per parameter
/// - k = number of parameters
/// - N = user-specified sample count
///
/// ## Usage Flow
///
/// ```
/// 1. Define Parameters:
///    Parameter<Integer> age = ...;
///    Parameter<String> platform = ...;
///
/// 2. Build Sequence:
///    Sequence seq = Sequence.builder()
///        .withParameter(age)
///        .withParameter(platform)
///        .generatePairwise()
///        .build();
///
/// 3. Validate:
///    ValidationResult result = seq.validate();
///    if (result.isFailed()) {
///        throw new InvalidSequenceException(result);
///    }
///
/// 4. Execute:
///    for (Trial trial : seq.trials()) {
///        TrialResult result = executor.execute(trial);
///        store.save(result);
///    }
/// ```
///
/// ## Sequence Properties
///
/// ### Immutability
/// Sequences are immutable once built. Trial order is fixed.
///
/// ### Determinism
/// Same parameters + same strategy = same sequence (if seeded).
///
/// ### Validation
/// Sequences SHOULD be validated before execution to ensure:
/// - All trials satisfy global constraints
/// - No impossible parameter combinations
/// - Dependencies are satisfiable
///
/// ### Metadata
/// Every sequence carries metadata describing:
/// - Generation strategy used
/// - Total trial count
/// - Validation status
/// - Estimated duration
///
/// ## Example: Edge-First Sequence
///
/// ```java
/// // Define 2D parameter space
/// Parameter<Integer> x = DiscreteParameter.range("x", 0, 10);  // 11 values
/// Parameter<Integer> y = DiscreteParameter.range("y", 0, 10);  // 11 values
/// // Total space: 11 × 11 = 121 trials
///
/// // Generate edge-first sequence
/// Sequence seq = Sequence.builder()
///     .withParameter(x)
///     .withParameter(y)
///     .generateEdgeFirst()
///     .build();
///
/// // Trial order:
/// // 1. Corners: (0,0), (0,10), (10,0), (10,10)
/// // 2. Edges: (0,5), (5,0), (5,10), (10,5), ...
/// // 3. Interior: (5,5), (2,7), (8,3), ...
/// ```
///
/// ## Relationship to Simplica
///
/// In Simplica, sequences become execution plans:
///
/// ```
/// Paramodel Layer:
///   Sequence (abstract trial ordering)
///
///   ↓ compiled by
///
/// Simplica Layer:
///   ExecutionPlan (concrete steps, barriers, resources)
/// ```
///
/// @see Sequence
/// @see SequenceBuilder
/// @see Trial
/// @see TrialResult
/// @see io.nosqlbench.paramodel.plan.ExecutionPlan
/// @since 0.1.0
///
package io.nosqlbench.paramodel.sequence;
