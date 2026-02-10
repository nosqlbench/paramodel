package io.nosqlbench.paramodel.parameters;

///
/// Defines how values are sampled from a parameter axis during trial enumeration.
///
/// Three strategies are supported:
///
/// - **Grid**: Use all values (full Cartesian product)
/// - **Random**: Select k random samples with a deterministic seed
/// - **Linspace**: Generate n evenly-spaced points across the value range
///
/// ## Usage
///
/// ```java
/// SamplingStrategy grid = SamplingStrategy.grid();
/// SamplingStrategy random = SamplingStrategy.random(10, 42L);
/// SamplingStrategy linspace = SamplingStrategy.linspace(5);
/// ```
///
/// @see io.nosqlbench.paramodel.plan.Axis
///
public sealed interface SamplingStrategy
    permits SamplingStrategy.Grid,
            SamplingStrategy.Random,
            SamplingStrategy.Linspace {

    /// Full Cartesian product — use every value in the axis.
    record Grid() implements SamplingStrategy {}

    /// Random sampling — select {@code count} values using a deterministic seed.
    ///
    /// @param count  number of samples to draw
    /// @param seed   random seed for reproducibility
    record Random(int count, long seed) implements SamplingStrategy {
        public Random {
            if (count < 1) throw new IllegalArgumentException("count must be >= 1, got " + count);
        }
    }

    /// Linear spacing — generate {@code count} evenly-spaced points across the value range.
    ///
    /// For numeric axes, interpolates between min and max preserving the original
    /// numeric type (Integer, Long, Double). For non-numeric axes, selects evenly-spaced
    /// indices from the value list.
    ///
    /// @param count number of points to generate
    record Linspace(int count) implements SamplingStrategy {
        public Linspace {
            if (count < 1) throw new IllegalArgumentException("count must be >= 1, got " + count);
        }
    }

    /// Creates a Grid strategy (all values).
    static SamplingStrategy grid() {
        return new Grid();
    }

    /// Creates a Random strategy with the given sample count and seed.
    ///
    /// @param count  number of samples
    /// @param seed   random seed
    /// @return random sampling strategy
    static SamplingStrategy random(int count, long seed) {
        return new Random(count, seed);
    }

    /// Creates a Linspace strategy with the given point count.
    ///
    /// @param count number of evenly-spaced points
    /// @return linspace sampling strategy
    static SamplingStrategy linspace(int count) {
        return new Linspace(count);
    }
}
