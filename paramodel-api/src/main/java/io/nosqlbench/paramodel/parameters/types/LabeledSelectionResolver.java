/*
 * Copyright (c) nosqlbench
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.nosqlbench.paramodel.parameters.types;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/// A [SelectionResolver] that also provides human-readable display names
/// for each valid value.
///
/// Where [SelectionResolver] treats values as opaque strings, this
/// extension pairs each value with a display label suitable for UI
/// dropdowns and diagnostic output.
///
/// ## Example
///
/// ```java
/// LabeledSelectionResolver resolver = new LabeledSelectionResolver() {
///     @Override
///     public List<Entry> labeledValues() {
///         return List.of(
///             new Entry("us-east-1", "US East (N. Virginia)"),
///             new Entry("eu-west-1", "EU West (Ireland)")
///         );
///     }
/// };
///
/// Set<String> valid = resolver.validValues();
/// // {"us-east-1", "eu-west-1"}
/// ```
///
/// @see SelectionResolver
/// @see SelectionParameter#external(String, SelectionResolver)
/// @since 0.1.0
public interface LabeledSelectionResolver extends SelectionResolver {

    /// A value paired with its human-readable display label.
    ///
    /// @param value the machine-readable value stored when selected
    /// @param label the human-readable label shown in the UI
    record Entry(String value, String label) {}

    /// Returns the current set of selectable values with display labels.
    ///
    /// This method may return different results on successive calls if
    /// the backing data source is dynamic.
    ///
    /// @return the current labeled entries, never null
    List<Entry> labeledValues();

    /// Default implementation derived from [labeledValues].
    @Override
    default Set<String> validValues() {
        return labeledValues().stream()
            .map(Entry::value)
            .collect(Collectors.toSet());
    }

    /// Default implementation derived from [validValues].
    @Override
    default boolean isValid(String value) {
        return validValues().contains(value);
    }
}
