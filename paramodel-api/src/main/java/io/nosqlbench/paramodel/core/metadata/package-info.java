///
/// Metadata types for describing parameters, sequences, and execution context.
///
/// ## Overview
///
/// This package provides metadata contracts that attach descriptive information
/// to core paramodel concepts. Metadata supports:
/// - **Documentation**: Human-readable descriptions
/// - **Provenance**: Tracking creation and lineage
/// - **Discovery**: Finding and filtering by attributes
/// - **Tooling**: IDE support, UI generation, analysis
///
/// ## Metadata Types
///
/// ```
/// Metadata Hierarchy
/// │
/// ├── ParameterMetadata
/// │   └── Describes parameter definitions
/// │       (who, when, how, why)
/// │
/// └── SequenceMetadata
///     └── Describes sequence generation
///         (strategy, size, validation)
/// ```
///
/// ## Metadata vs Data
///
/// ```
/// Data:        The actual values and structures
///              Example: Parameter<Integer> with domain [0, 100]
///
/// Metadata:    Information ABOUT the data
///              Example: "Created by user@example.com on 2026-02-08"
/// ```
///
/// ## Usage Pattern
///
/// All metadata-carrying types provide a {@code metadata()} accessor:
///
/// ```java
/// Parameter<Integer> param = ...;
/// ParameterMetadata meta = param.metadata();
/// System.out.println("Created: " + meta.createdAt());
/// System.out.println("Strategy: " + meta.generationStrategy());
///
/// Sequence seq = ...;
/// SequenceMetadata seqMeta = seq.metadata();
/// System.out.println("Trials: " + seqMeta.totalTrials());
/// System.out.println("Ordering: " + seqMeta.orderingStrategy());
/// ```
///
/// ## Immutability
///
/// All metadata is immutable. To "change" metadata, create a new version
/// of the parent object with updated metadata.
///
/// ## Serialization
///
/// Metadata should serialize alongside its parent object to preserve
/// provenance and context.
///
/// @see io.nosqlbench.paramodel.core.Parameter
/// @see io.nosqlbench.paramodel.sequence.Sequence
/// @since 0.1.0
///
package io.nosqlbench.paramodel.core.metadata;
