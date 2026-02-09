package io.nosqlbench.paramodel.parameters.types;

import java.util.Set;

///
/// Provides externalized resolution of valid values for a {@link SelectionParameter}.
///
/// ## Concept
///
/// A {@code SelectionResolver} externalizes the valid-values source for selection
/// parameters, allowing the host system to supply values dynamically. This supports
/// scenarios where the valid set is not known at parameter construction time, or
/// where validity depends on runtime state (e.g., available databases, registered
/// models, deployed services).
///
/// ## Example
///
/// ```java
/// SelectionResolver modelResolver = new SelectionResolver() {
///     @Override
///     public Set<String> validValues() {
///         return modelRegistry.registeredModelNames();
///     }
///
///     @Override
///     public boolean isValid(String value) {
///         return modelRegistry.hasModel(value);
///     }
/// };
///
/// Parameter<List<String>> modelParam =
///     SelectionParameter.external("model", modelResolver);
/// ```
///
/// @see SelectionParameter
/// @since 0.1.0
///
public interface SelectionResolver {

    ///
    /// Returns the current set of valid values.
    ///
    /// This method may return different values on successive calls if the
    /// valid set is dynamic.
    ///
    /// @return the current valid values, never null
    ///
    Set<String> validValues();

    ///
    /// Validates a single value against the current valid set.
    ///
    /// @param value the value to validate
    /// @return true if the value is currently valid
    ///
    boolean isValid(String value);
}
