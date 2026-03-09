package io.nosqlbench.paramodel.plan;

import io.nosqlbench.paramodel.elements.Element;
import io.nosqlbench.paramodel.parameters.Parameter;

import java.util.Objects;

///
/// Record implementation of {@link AttachedParameter}.
///
/// @param parameter the parameter being varied
/// @param element   the element this parameter is attached to
/// @param <T>       the parameter value type
/// @since 0.1.0
///
public record DefaultAttachedParameter<T>(Parameter<T> parameter, Element element)
        implements AttachedParameter<T> {

    /// Creates a new attached parameter binding.
    ///
    /// @param parameter the parameter, must not be null
    /// @param element   the element, must not be null
    public DefaultAttachedParameter {
        Objects.requireNonNull(parameter, "parameter must not be null");
        Objects.requireNonNull(element, "element must not be null");
    }
}
