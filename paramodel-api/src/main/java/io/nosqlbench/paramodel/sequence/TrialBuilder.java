package io.nosqlbench.paramodel.sequence;

import io.nosqlbench.paramodel.parameters.Constraint;
import io.nosqlbench.paramodel.parameters.Value;

import java.util.Map;

///
/// Builder for constructing {@link Trial} instances.
///
/// ## Usage
///
/// ```java
/// Trial trial = trialBuilder
///     .id("trial-001")
///     .assignment("server", "threads", threadValue)
///     .assignment("server", "heap", heapValue)
///     .assignment("client", "mode", modeValue)
///     .constraint(assignments -> {
///         int threads = (Integer) assignments.get("threads").value();
///         return threads >= 1;
///     })
///     .build();
/// ```
///
/// @see Trial
/// @since 0.1.0
///
public interface TrialBuilder {

    ///
    /// Sets the unique identifier for the trial being built.
    ///
    /// @param id trial identifier
    /// @return this builder for chaining
    ///
    TrialBuilder id(String id);

    ///
    /// Adds a parameter assignment to the trial, scoped to an element.
    ///
    /// @param elementName the element this parameter belongs to
    /// @param parameterName parameter name within the element
    /// @param value assigned value
    /// @return this builder for chaining
    ///
    TrialBuilder assignment(String elementName, String parameterName, Value<?> value);

    ///
    /// Adds a cross-parameter constraint to the trial.
    ///
    /// Constraints receive a flat map of parameter names to values
    /// (without element prefixes) for validation.
    ///
    /// @param constraint constraint to apply
    /// @return this builder for chaining
    ///
    TrialBuilder constraint(Constraint<Map<String, Value<?>>> constraint);

    ///
    /// Builds the immutable trial.
    ///
    /// @return the constructed trial
    /// @throws IllegalStateException if required fields are missing
    ///
    Trial build();
}
