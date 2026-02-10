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

import io.nosqlbench.paramodel.engine.sequence.DefaultValue;
import io.nosqlbench.paramodel.parameters.*;

import java.time.Instant;
import java.util.*;

///
/// Standard implementation of {@link ParameterBinder}.
///
/// Implements the full binding algorithm including:
/// - Default value resolution
/// - Type coercion (String → Integer, Double, Boolean, etc.)
/// - Domain and constraint validation
/// - Derived parameter evaluation
/// - Passthrough value handling
/// - Deterministic ordering (parameter definition order)
///
/// @see ParameterBinder
/// @see BindingPolicy
/// @since 0.1.0
///
public class DefaultParameterBinder implements ParameterBinder {

    private final BindingPolicy policy;

    ///
    /// Creates a binder with the specified policy.
    ///
    /// @param policy the binding policy
    ///
    public DefaultParameterBinder(BindingPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
    }

    ///
    /// Creates a binder with the default lenient policy.
    ///
    public DefaultParameterBinder() {
        this(BindingPolicy.LENIENT);
    }

    @Override
    public ParameterBinding bind(List<Parameter<?>> parameters, Map<String, Object> inputs) {
        LinkedHashMap<String, Value<?>> assignments = new LinkedHashMap<>();
        LinkedHashMap<String, Object> passthroughValues = new LinkedHashMap<>();
        List<String> violations = new ArrayList<>();
        Set<String> consumedInputKeys = new HashSet<>();

        // Phase 1: Bind independent (non-derived) parameters
        Map<String, Object> boundValueMap = new LinkedHashMap<>();
        for (Parameter<?> param : parameters) {
            if (param instanceof DerivedParameter<?>) {
                continue;
            }
            bindParameter(param, inputs, assignments, boundValueMap, consumedInputKeys, violations);
            if (policy.failFast() && !violations.isEmpty()) {
                return buildResult(assignments, passthroughValues, parameters, violations);
            }
        }

        // Phase 2: Evaluate derived parameters
        for (Parameter<?> param : parameters) {
            if (param instanceof DerivedParameter<?> derived) {
                bindDerivedParameter(derived, boundValueMap, assignments, violations);
                if (policy.failFast() && !violations.isEmpty()) {
                    return buildResult(assignments, passthroughValues, parameters, violations);
                }
            }
        }

        // Phase 3: Handle remaining inputs
        for (Map.Entry<String, Object> entry : inputs.entrySet()) {
            if (!consumedInputKeys.contains(entry.getKey())) {
                if (policy.allowPassthrough()) {
                    passthroughValues.put(entry.getKey(), entry.getValue());
                } else {
                    violations.add("Unknown input parameter '" + entry.getKey() + "'");
                    if (policy.failFast()) {
                        return buildResult(assignments, passthroughValues, parameters, violations);
                    }
                }
            }
        }

        return buildResult(assignments, passthroughValues, parameters, violations);
    }

    private <T> void bindParameter(
            Parameter<T> param,
            Map<String, Object> inputs,
            LinkedHashMap<String, Value<?>> assignments,
            Map<String, Object> boundValueMap,
            Set<String> consumedInputKeys,
            List<String> violations) {

        String name = param.name();
        Object rawInput = inputs.get(name);

        if (rawInput != null) {
            consumedInputKeys.add(name);
            T coerced = coerceValue(rawInput, param, violations);
            if (coerced != null) {
                ValidationResult vr = param.validate(coerced);
                if (vr.isFailed()) {
                    violations.addAll(vr.violations());
                } else {
                    Value<T> value = new DefaultValue<>(
                        coerced, name, Instant.now(), Optional.of("user-provided"));
                    assignments.put(name, value);
                    boundValueMap.put(name, coerced);
                }
            }
        } else if (inputs.containsKey(name)) {
            // Key exists but value is null
            consumedInputKeys.add(name);
            violations.add("Parameter '" + name + "' has null input value");
        } else {
            // No input — try default
            Optional<T> defaultVal = param.defaultValue();
            if (defaultVal.isPresent()) {
                T defVal = defaultVal.get();
                Value<T> value = new DefaultValue<>(
                    defVal, name, Instant.now(), Optional.of("default"));
                assignments.put(name, value);
                boundValueMap.put(name, defVal);
            } else {
                violations.add("Required parameter '" + name + "' has no input and no default value");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T coerceValue(Object rawInput, Parameter<T> param, List<String> violations) {
        Domain<T> domain = param.domain();

        // If already correct type, return as-is
        try {
            T cast = (T) rawInput;
            if (domain.contains(cast)) {
                return cast;
            }
        } catch (ClassCastException ignored) {
            // Need coercion
        }

        if (!policy.coerceTypes()) {
            violations.add("Parameter '" + param.name() + "': type coercion disabled, " +
                "cannot convert " + rawInput.getClass().getSimpleName() + " to target type");
            return null;
        }

        return attemptCoercion(rawInput, param, violations);
    }

    @SuppressWarnings("unchecked")
    private <T> T attemptCoercion(Object rawInput, Parameter<T> param, List<String> violations) {
        Domain<T> domain = param.domain();
        String paramName = param.name();

        try {
            // Determine target type from domain
            if (domain instanceof Domain.Range<?> range) {
                Object min = range.min();
                if (min instanceof Integer) {
                    Integer coerced = coerceToInteger(rawInput);
                    if (coerced != null) return (T) coerced;
                } else if (min instanceof Double) {
                    Double coerced = coerceToDouble(rawInput);
                    if (coerced != null) return (T) coerced;
                }
            } else if (domain instanceof Domain.Discrete<?> discrete) {
                Set<?> values = discrete.values();
                if (!values.isEmpty()) {
                    Object sample = values.iterator().next();
                    if (sample instanceof Integer) {
                        Integer coerced = coerceToInteger(rawInput);
                        if (coerced != null) return (T) coerced;
                    } else if (sample instanceof Boolean) {
                        if (rawInput instanceof String s) {
                            return (T) Boolean.valueOf(Boolean.parseBoolean(s));
                        }
                    }
                }
            } else if (domain instanceof Domain.Custom<?>) {
                // For Custom domains (like StringParameter or SelectionParameter),
                // try string and list coercion
                if (rawInput instanceof String s) {
                    // Try as-is String
                    try {
                        T asString = (T) s;
                        if (domain.contains(asString)) return asString;
                    } catch (ClassCastException ignored) {
                    }
                    // Try as single-element list (for SelectionParameter)
                    try {
                        T asList = (T) List.of(s);
                        if (domain.contains(asList)) return asList;
                    } catch (ClassCastException ignored) {
                    }
                }
            }
        } catch (NumberFormatException e) {
            violations.add("Parameter '" + paramName + "': cannot coerce '" + rawInput + "' — " + e.getMessage());
            return null;
        }

        violations.add("Parameter '" + paramName + "': cannot coerce " +
            rawInput.getClass().getSimpleName() + " value '" + rawInput + "' to target type");
        return null;
    }

    private Integer coerceToInteger(Object rawInput) {
        if (rawInput instanceof String s) {
            return Integer.parseInt(s);
        }
        if (rawInput instanceof Number n) {
            return n.intValue();
        }
        return null;
    }

    private Double coerceToDouble(Object rawInput) {
        if (rawInput instanceof String s) {
            return Double.parseDouble(s);
        }
        if (rawInput instanceof Number n) {
            return n.doubleValue();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private <T> void bindDerivedParameter(
            DerivedParameter<T> derived,
            Map<String, Object> boundValueMap,
            LinkedHashMap<String, Value<?>> assignments,
            List<String> violations) {

        String name = derived.name();
        try {
            T computedValue = derived.compute(Collections.unmodifiableMap(boundValueMap));
            ValidationResult vr = derived.validate(computedValue);
            if (vr.isFailed()) {
                violations.addAll(vr.violations());
            } else {
                Value<T> value = new DefaultValue<>(
                    computedValue, name, Instant.now(),
                    Optional.of("derived: " + derived.expression()));
                assignments.put(name, value);
                boundValueMap.put(name, computedValue);
            }
        } catch (Exception e) {
            violations.add("Derived parameter '" + name + "' computation failed: " + e.getMessage());
        }
    }

    private DefaultParameterBinding buildResult(
            LinkedHashMap<String, Value<?>> assignments,
            LinkedHashMap<String, Object> passthroughValues,
            List<Parameter<?>> parameters,
            List<String> violations) {

        ValidationResult result = violations.isEmpty()
            ? new ValidationResult.Passed()
            : new ValidationResult.Failed("Binding validation failed", List.copyOf(violations));

        return new DefaultParameterBinding(assignments, passthroughValues, parameters, result);
    }
}
