package io.nosqlbench.paramodel.mock.compilation;

import io.nosqlbench.paramodel.compilation.CompilationContext;
import io.nosqlbench.paramodel.compilation.OptimizationPass;

import java.util.Objects;
import java.util.Optional;

///
/// Simple optimization pass implementation for testing.
///
/// Provides a named pass with identity transform behavior
/// (applies without modifying the execution plan).
///
/// @see OptimizationPass
/// @since 0.1.0
///
public class MockOptimizationPass implements OptimizationPass {
    private final String name;
    private final String description;
    private final OptimizationCategory category;

    ///
    /// Creates a mock optimization pass with the given name.
    ///
    /// @param name pass name
    ///
    public MockOptimizationPass(String name) {
        this(name, "Mock pass: " + name, OptimizationCategory.OTHER);
    }

    ///
    /// Creates a mock optimization pass with name, description, and category.
    ///
    /// @param name        pass name
    /// @param description pass description
    /// @param category    pass category
    ///
    public MockOptimizationPass(String name, String description, OptimizationCategory category) {
        this.name = Objects.requireNonNull(name);
        this.description = Objects.requireNonNull(description);
        this.category = Objects.requireNonNull(category);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public boolean shouldApply(CompilationContext context) {
        return true;
    }

    @Override
    public void apply(CompilationContext context) {
        context.recordMetric("pass_" + name + "_applied", 1);
    }

    @Override
    public Optional<String> estimateSavings(CompilationContext context) {
        return Optional.of("mock savings");
    }

    @Override
    public OptimizationCategory category() {
        return category;
    }

    ///
    /// Creates a mock optimization pass with the given name.
    ///
    /// @param name pass name
    /// @return a new mock optimization pass
    ///
    public static MockOptimizationPass of(String name) {
        return new MockOptimizationPass(name);
    }
}
