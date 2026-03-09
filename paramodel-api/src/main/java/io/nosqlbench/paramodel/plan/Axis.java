package io.nosqlbench.paramodel.plan;

import io.nosqlbench.paramodel.parameters.Parameter;
import io.nosqlbench.paramodel.sequence.Trial;

import java.util.List;
import java.util.Optional;

///
/// A named parameter dimension in a study with ordered discrete values.
///
/// ## Concept
///
/// An {@code Axis<T>} is a pure model type representing one dimension of the
/// trial space in a Test Plan. It captures the parameter being varied, the
/// element it targets, and the concrete values to sweep.
///
/// ## Structure
///
/// ```
/// Axis<T>
/// ├── name: String
/// │   └── Unique identifier in study
/// │
/// ├── values: List<T>
/// │   └── Ordered, discrete values to test
/// │
/// ├── attachedParameter: AttachedParameter<T>
/// │   ├── parameter: Parameter<T>
/// │   └── element: Element
/// │
/// └── description: Optional<String>
///     └── Human-readable description
/// ```
///
/// ## Trial Space Calculation
///
/// Given axes A1, A2, ..., An:
///
/// ```
/// Trial Space = A1 x A2 x ... x An
///
/// |Trial Space| = |A1.values| x |A2.values| x ... x |An.values|
/// ```
///
/// @param <T> the type of values along this axis
/// @see TestPlan
/// @see Parameter
/// @see AttachedParameter
/// @see Trial
/// @since 0.1.0
///
public interface Axis<T> {

    /// Returns the unique name of this axis within the study.
    ///
    /// @return axis name, never null or empty
    String name();

    /// Returns the ordered list of discrete values along this axis.
    ///
    /// @return immutable, ordered list of values, never null or empty
    List<T> values();

    /// Returns the attached parameter binding for this axis.
    ///
    /// The attached parameter captures the relationship between the
    /// parameter being varied and the element it targets.
    ///
    /// @return the attached parameter, never null
    AttachedParameter<T> attachedParameter();

    /// Returns the number of values along this axis.
    ///
    /// @return number of values, always >= 1
    default int cardinality() {
        return values().size();
    }

    /// Returns boundary values (extrema) of this axis.
    ///
    /// For an ordered axis, boundaries are typically the first and last values.
    ///
    /// @return boundary values, never null
    List<T> boundaryValues();

    /// Returns optional description of this axis's purpose and semantics.
    ///
    /// @return axis description if provided, empty otherwise
    Optional<String> description();

    /// Checks if a value is present along this axis.
    ///
    /// @param value the value to check
    /// @return true if value is in axis values
    default boolean contains(T value) {
        return values().contains(value);
    }

    /// Returns the index of a value along this axis.
    ///
    /// @param value the value to find
    /// @return index (0-based) or -1 if not found
    default int indexOf(T value) {
        return values().indexOf(value);
    }

    /// Returns the name of the element this axis targets.
    ///
    /// Convenience delegate to {@code attachedParameter().elementName()}.
    ///
    /// @return the target element name, never null
    default String targetElement() {
        return attachedParameter().elementName();
    }

    /// Returns the underlying parameter this axis is based on.
    ///
    /// Convenience delegate to {@code attachedParameter().parameter()}.
    ///
    /// @return the underlying parameter, never null
    default Parameter<T> underlyingParameter() {
        return attachedParameter().parameter();
    }
}
