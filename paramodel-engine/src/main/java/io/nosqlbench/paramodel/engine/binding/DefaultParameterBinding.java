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
package io.nosqlbench.paramodel.engine.binding;

import io.nosqlbench.paramodel.parameters.Parameter;
import io.nosqlbench.paramodel.parameters.ParameterBinding;
import io.nosqlbench.paramodel.parameters.ValidationResult;
import io.nosqlbench.paramodel.parameters.Value;

import java.util.*;

///
/// Standard implementation of {@link ParameterBinding}.
///
/// Uses {@link LinkedHashMap} to maintain deterministic parameter ordering.
/// Immutable after construction — all maps are wrapped with unmodifiable views.
///
/// @see ParameterBinding
/// @since 0.1.0
///
public class DefaultParameterBinding implements ParameterBinding {

    private final Map<String, Value<?>> assignments;
    private final Map<String, Object> passthroughValues;
    private final List<Parameter<?>> parameters;
    private final ValidationResult validationResult;

    ///
    /// Constructs a new binding result.
    ///
    /// @param assignments       bound parameter values in definition order
    /// @param passthroughValues unmatched input values
    /// @param parameters        the original parameter definitions
    /// @param validationResult  aggregate validation outcome
    ///
    public DefaultParameterBinding(
            Map<String, Value<?>> assignments,
            Map<String, Object> passthroughValues,
            List<Parameter<?>> parameters,
            ValidationResult validationResult) {
        this.assignments = Collections.unmodifiableMap(new LinkedHashMap<>(assignments));
        this.passthroughValues = Collections.unmodifiableMap(new LinkedHashMap<>(passthroughValues));
        this.parameters = List.copyOf(parameters);
        this.validationResult = validationResult;
    }

    @Override
    public Map<String, Value<?>> assignments() {
        return assignments;
    }

    @Override
    public Map<String, Object> toValueMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        for (var entry : assignments.entrySet()) {
            result.put(entry.getKey(), entry.getValue().value());
        }
        return Collections.unmodifiableMap(result);
    }

    @Override
    public Map<String, Object> passthroughValues() {
        return passthroughValues;
    }

    @Override
    public Optional<Value<?>> get(String parameterName) {
        return Optional.ofNullable(assignments.get(parameterName));
    }

    @Override
    public <T> T getValue(String parameterName, Class<T> type) {
        Value<?> value = assignments.get(parameterName);
        if (value == null) {
            throw new IllegalArgumentException(
                "Parameter '" + parameterName + "' is not bound");
        }
        Object raw = value.value();
        if (!type.isInstance(raw)) {
            throw new IllegalArgumentException(
                "Parameter '" + parameterName + "' value is " +
                raw.getClass().getName() + ", not " + type.getName());
        }
        return type.cast(raw);
    }

    @Override
    public List<Parameter<?>> parameters() {
        return parameters;
    }

    @Override
    public ValidationResult validationResult() {
        return validationResult;
    }
}
