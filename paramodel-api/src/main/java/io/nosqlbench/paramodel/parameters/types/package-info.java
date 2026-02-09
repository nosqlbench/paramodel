///
/// Built-in concrete {@link io.nosqlbench.paramodel.parameters.Parameter} implementations
/// for common primitive types.
///
/// ## Overview
///
/// This package provides ready-to-use parameter types that eliminate the need to manually
/// construct mock domains and parameters for common scenarios. Each type encapsulates
/// the appropriate {@link io.nosqlbench.paramodel.parameters.Domain} implementation
/// as a package-private inner class.
///
/// ## Available Types
///
/// ```
/// IntegerParameter
///   +-- range(name, min, max)    - Contiguous integer range [min, max]
///   +-- of(name, Set<Integer>)   - Discrete set of integers
///
/// DoubleParameter
///   +-- range(name, min, max)    - Continuous double range [min, max]
///
/// BooleanParameter
///   +-- of(name)                 - Boolean {true, false}
///
/// SelectionParameter
///   +-- of(name, Set<String>)    - Built-in set of valid values
///   +-- external(name, resolver) - Externally resolved valid values
///   +-- maxSelections(n)         - Allow multi-select
/// ```
///
/// ## Usage Examples
///
/// ```java
/// // Integer range
/// Parameter<Integer> threads = IntegerParameter.range("threads", 1, 64);
///
/// // Discrete integers
/// Parameter<Integer> batchSize = IntegerParameter.of("batch_size", Set.of(32, 64, 128));
///
/// // Double range
/// Parameter<Double> temperature = DoubleParameter.range("temperature", 0.0, 1.0);
///
/// // Boolean
/// Parameter<Boolean> enableCache = BooleanParameter.of("enable_cache");
///
/// // Single-select from a set
/// Parameter<List<String>> region =
///     SelectionParameter.of("region", Set.of("us-east-1", "us-west-2"));
///
/// // Multi-select with constraint
/// Parameter<List<String>> tags = SelectionParameter
///     .of("tags", Set.of("fast", "accurate", "cheap"))
///     .maxSelections(2);
/// ```
///
/// @see io.nosqlbench.paramodel.parameters.Parameter
/// @see io.nosqlbench.paramodel.parameters.Domain
/// @since 0.1.0
///
package io.nosqlbench.paramodel.parameters.types;
