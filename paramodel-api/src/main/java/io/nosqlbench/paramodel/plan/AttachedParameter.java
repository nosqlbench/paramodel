package io.nosqlbench.paramodel.plan;

import io.nosqlbench.paramodel.elements.Element;
import io.nosqlbench.paramodel.parameters.Parameter;

///
/// An axis-scoped binding of a parameter model to a specific element.
///
/// Parameters are composable, element-agnostic models. When an axis
/// varies a parameter on a particular element, this type captures that
/// relationship so the axis is fully qualified in the type system.
///
/// @param <T> the parameter value type
/// @see Axis
/// @see Parameter
/// @see Element
/// @since 0.1.0
///
public interface AttachedParameter<T> {

    /// Returns the parameter being varied.
    ///
    /// @return the parameter, never null
    Parameter<T> parameter();

    /// Returns the element this parameter is attached to.
    ///
    /// @return the element, never null
    Element element();

    /// Returns the name of the parameter.
    ///
    /// @return the parameter name, never null
    default String parameterName() {
        return parameter().name();
    }

    /// Returns the name of the element.
    ///
    /// @return the element name, never null
    default String elementName() {
        return element().name();
    }
}
