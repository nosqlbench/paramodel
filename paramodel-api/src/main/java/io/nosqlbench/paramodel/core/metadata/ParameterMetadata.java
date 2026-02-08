package io.nosqlbench.paramodel.core.metadata;

import io.nosqlbench.paramodel.core.Parameter;
import io.nosqlbench.paramodel.core.Value;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

///
/// Descriptive metadata about a parameter's definition, creation, and characteristics.
///
/// ## Concept
///
/// {@code ParameterMetadata} captures information about the parameter itself,
/// not about specific values it generates. This metadata is used for:
/// - **Documentation**: Describing the parameter's purpose
/// - **Provenance**: Tracking who created it and when
/// - **Debugging**: Understanding parameter configuration
/// - **Tooling**: Supporting IDEs, UIs, and analysis tools
///
/// ## Structure
///
/// ```
/// ParameterMetadata
/// ├── createdAt: Instant              - When parameter was defined
/// ├── createdBy: Optional<String>     - Who/what created it
/// ├── description: Optional<String>   - Human-readable description
/// ├── tags: Map<String, String>       - Arbitrary key-value labels
/// ├── generationStrategy: String      - How values are generated
/// └── version: Optional<String>       - Parameter definition version
/// ```
///
/// ## Metadata Lifecycle
///
/// ```
/// Parameter Definition:
///   User/System defines parameter
///         ↓
///   ParameterMetadata created
///   ├── createdAt = now
///   ├── createdBy = current user/system
///   ├── description = user-provided
///   └── tags = contextual labels
///
/// Parameter Usage:
///   Metadata remains immutable
///         ↓
///   Used for documentation/debugging
///         ↓
///   Serialized with parameter definition
/// ```
///
/// ## Use Cases
///
/// ### Documentation Generation
///
/// ```java
/// ParameterMetadata meta = parameter.metadata();
/// String doc = """
///     Parameter: %s
///     Description: %s
///     Strategy: %s
///     Created: %s by %s
///     """.formatted(
///         parameter.name(),
///         meta.description().orElse("No description"),
///         meta.generationStrategy(),
///         meta.createdAt(),
///         meta.createdBy().orElse("unknown")
///     );
/// ```
///
/// ### Filtering and Querying
///
/// ```java
/// // Find all parameters tagged as "performance-critical"
/// parameters.stream()
///     .filter(p -> p.metadata().tags().containsKey("performance-critical"))
///     .collect(toList());
/// ```
///
/// ### Provenance Tracking
///
/// ```java
/// // Track parameter lineage
/// ParameterMetadata meta = parameter.metadata();
/// log.info("Parameter {} created {} by {}",
///     parameter.name(),
///     meta.createdAt(),
///     meta.createdBy().orElse("system")
/// );
/// ```
///
/// ## Relationship to Value Metadata
///
/// ```
/// ParameterMetadata         : Describes the parameter definition
/// Value.generatorMetadata() : Describes a specific value generation
///
/// Example:
///   ParameterMetadata says:
///     "This parameter uses random generation"
///
///   Value.generatorMetadata() says:
///     "This specific value was generated with seed 42, iteration 7"
/// ```
///
/// ## Immutability
///
/// Metadata is immutable once created. To change metadata, create a new
/// parameter version with updated metadata.
///
/// @see Parameter
/// @see Value
/// @since 0.1.0
///
public interface ParameterMetadata {

    ///
    /// Returns when this parameter was created/defined.
    ///
    /// ## Contract
    ///
    /// - MUST return non-null timestamp
    /// - SHOULD be close to actual creation time
    /// - MUST remain constant
    ///
    /// @return creation timestamp, never null
    ///
    Instant createdAt();

    ///
    /// Returns who or what created this parameter.
    ///
    /// ## Common Values
    ///
    /// ```
    /// User:              "user@example.com"
    /// System:            "simplica-planner-v1.2"
    /// Tool:              "jupyter-notebook"
    /// Programmatic:      "test-fixture-generator"
    /// ```
    ///
    /// ## Contract
    ///
    /// - MUST return non-null Optional
    /// - MAY be empty if creator unknown
    /// - SHOULD identify creator uniquely
    ///
    /// @return creator identifier if known
    ///
    Optional<String> createdBy();

    ///
    /// Returns a human-readable description of this parameter's purpose.
    ///
    /// ## Description Guidelines
    ///
    /// Good descriptions explain:
    /// - **What** the parameter controls
    /// - **Why** it exists
    /// - **How** it affects behavior
    ///
    /// Example:
    /// ```
    /// "Model temperature controlling randomness in text generation.
    ///  Higher values (closer to 1.0) produce more creative but less
    ///  predictable outputs. Lower values produce more deterministic results."
    /// ```
    ///
    /// ## Contract
    ///
    /// - MUST return non-null Optional
    /// - MAY be empty if no description provided
    /// - SHOULD be concise but informative
    ///
    /// @return parameter description if available
    ///
    Optional<String> description();

    ///
    /// Returns arbitrary key-value tags for categorization and filtering.
    ///
    /// ## Common Tag Uses
    ///
    /// ```
    /// Category:          "category" → "performance", "functional", "security"
    /// Priority:          "priority" → "critical", "normal", "low"
    /// Team:              "owner" → "ml-team", "backend-team"
    /// Environment:       "env" → "production", "staging", "dev"
    /// Sensitivity:       "sensitive" → "true", "false"
    /// Impact:            "impact" → "high", "medium", "low"
    /// ```
    ///
    /// ## Tag Namespacing
    ///
    /// Consider using namespaced keys for clarity:
    /// ```
    /// "simplica:category" → "system"
    /// "org:owner" → "platform-team"
    /// "custom:billing-code" → "PROJECT-42"
    /// ```
    ///
    /// ## Contract
    ///
    /// - MUST return non-null, unmodifiable map
    /// - MAY be empty
    /// - Keys MUST be non-null, non-empty
    /// - Values MUST be non-null
    ///
    /// @return immutable tag map, never null
    ///
    Map<String, String> tags();

    ///
    /// Returns a description of how values are generated for this parameter.
    ///
    /// ## Strategy Descriptions
    ///
    /// ```
    /// "random"                    - Uniform random sampling
    /// "boundary"                  - Extrema (min/max) values
    /// "exhaustive"                - Complete enumeration
    /// "pairwise"                  - Combinatorial pairwise coverage
    /// "edge-first"                - Boundaries first, then interior
    /// "user-defined"              - Explicit value list
    /// "gaussian(μ=50, σ=10)"      - Normal distribution
    /// "uniform[0, 100]"           - Uniform distribution
    /// ```
    ///
    /// ## Contract
    ///
    /// - MUST return non-null, non-empty string
    /// - SHOULD be descriptive and parseable
    /// - SHOULD match actual generation behavior
    ///
    /// @return generation strategy description, never null or empty
    ///
    String generationStrategy();

    ///
    /// Returns an optional version identifier for this parameter definition.
    ///
    /// ## Versioning Use Cases
    ///
    /// - Track parameter definition changes over time
    /// - Support A/B testing (version A vs version B)
    /// - Enable rollback to previous definitions
    /// - Link results to specific parameter versions
    ///
    /// ## Version Formats
    ///
    /// ```
    /// Semantic:          "1.2.3"
    /// Date-based:        "2026-02-08"
    /// Commit-based:      "a3f9c2e"
    /// UUID:              "550e8400-e29b-41d4-a716-446655440000"
    /// ```
    ///
    /// ## Contract
    ///
    /// - MUST return non-null Optional
    /// - MAY be empty if versioning not used
    /// - SHOULD be unique within parameter history
    ///
    /// @return parameter version if tracked
    ///
    Optional<String> version();
}
