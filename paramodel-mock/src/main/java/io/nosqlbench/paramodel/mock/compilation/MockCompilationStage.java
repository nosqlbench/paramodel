package io.nosqlbench.paramodel.mock.compilation;

import io.nosqlbench.paramodel.compilation.CompilationContext;
import io.nosqlbench.paramodel.compilation.CompilationStage;

import java.util.List;
import java.util.Objects;

///
/// Simple compilation stage implementation for testing.
///
/// Provides a named no-op stage that records its execution in the context.
///
/// @see CompilationStage
/// @since 0.1.0
///
public class MockCompilationStage implements CompilationStage {
    private final String name;
    private final String description;
    private final List<String> dependencies;

    ///
    /// Creates a mock compilation stage with the given name.
    ///
    /// @param name stage name
    ///
    public MockCompilationStage(String name) {
        this(name, "Mock stage: " + name, List.of());
    }

    ///
    /// Creates a mock compilation stage with name, description, and dependencies.
    ///
    /// @param name         stage name
    /// @param description  stage description
    /// @param dependencies prerequisite stage names
    ///
    public MockCompilationStage(String name, String description, List<String> dependencies) {
        this.name = Objects.requireNonNull(name);
        this.description = Objects.requireNonNull(description);
        this.dependencies = List.copyOf(dependencies);
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
    public void execute(CompilationContext context) {
        context.recordMetric("stage_" + name + "_executed", 1);
    }

    @Override
    public List<String> dependencies() {
        return dependencies;
    }

    ///
    /// Creates a mock stage with the given name.
    ///
    /// @param name stage name
    /// @return a new mock compilation stage
    ///
    public static MockCompilationStage of(String name) {
        return new MockCompilationStage(name);
    }
}
