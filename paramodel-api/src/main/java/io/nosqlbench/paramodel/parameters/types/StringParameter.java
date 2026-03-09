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

import io.nosqlbench.paramodel.parameters.Constraint;
import io.nosqlbench.paramodel.parameters.Domain;
import io.nosqlbench.paramodel.parameters.Parameter;
import io.nosqlbench.paramodel.parameters.ValidationResult;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

///
/// A built-in parameter for arbitrary string values.
///
/// ## Factories
///
/// ```java
/// // Accept any string
/// Parameter<String> host = StringParameter.of("host");
///
/// // Accept any string, with a default
/// Parameter<String> host = StringParameter.of("host").withDefault("localhost");
/// ```
///
/// ## Domain
///
/// Uses {@link Domain.Custom} with an accept-all predicate. Cardinality is
/// {@link Optional#empty()} (unbounded). The domain is not enumerable.
///
/// @see Parameter
/// @see Domain.Custom
/// @since 0.1.0
///
public final class StringParameter implements Parameter<String> {

    private final String name;
    private final StringDomain domain;
    private final List<Constraint<String>> constraints;
    private String defaultValue;

    private StringParameter(String name) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.domain = new StringDomain();
        this.constraints = new ArrayList<>();
    }

    ///
    /// Creates a string parameter that accepts any non-null string value.
    ///
    /// @param name parameter name
    /// @return string parameter
    ///
    public static StringParameter of(String name) {
        return new StringParameter(name);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String type() {
        return "string";
    }

    @Override
    public Domain<String> domain() {
        return domain;
    }

    ///
    /// Sets the default value for this parameter. Returns this parameter for chaining.
    ///
    /// @param value the default value
    /// @return this parameter
    ///
    public StringParameter withDefault(String value) {
        this.defaultValue = Objects.requireNonNull(value, "default value must not be null");
        return this;
    }

    @Override
    public Optional<String> defaultValue() {
        return Optional.ofNullable(defaultValue);
    }

    @Override
    public String generate() {
        return "generated-" + name + "-" + ThreadLocalRandom.current().nextInt(10000);
    }

    @Override
    public String generateBoundary() {
        return "";
    }

    @Override
    public String generateRandom() {
        return generate();
    }

    @Override
    public ValidationResult validate(String value) {
        if (value == null) {
            return new ValidationResult.Failed("value must not be null", List.of("null value"));
        }
        List<String> violations = new ArrayList<>();
        for (Constraint<String> c : constraints) {
            if (!c.test(value)) {
                violations.add("constraint failed: " + c.description());
            }
        }
        if (!violations.isEmpty()) {
            return new ValidationResult.Failed(
                "value '" + value + "' violates constraints", violations);
        }
        return new ValidationResult.Passed();
    }

    @Override
    public boolean satisfies(Constraint<String> constraint) {
        return constraint.test("") || constraint.test("test") || constraint.test(generate());
    }

    ///
    /// Adds a constraint to this parameter. Returns this parameter for chaining.
    ///
    /// @param constraint the constraint to add
    /// @return this parameter
    ///
    public StringParameter withConstraint(Constraint<String> constraint) {
        this.constraints.add(Objects.requireNonNull(constraint));
        return this;
    }

    // --- Package-private domain implementation ---

    private static final class StringDomain implements Domain.Custom<String> {
        @Override
        public Predicate<String> membership() {
            return s -> s != null;
        }

        @Override
        public String description() {
            return "Any non-null string";
        }

        @Override
        public boolean contains(String value) {
            return value != null;
        }

        @Override
        public Optional<Long> cardinality() {
            return Optional.empty();
        }

        @Override
        public String sample(Random rng) {
            return "sample-" + rng.nextInt(10000);
        }

        @Override
        public Iterator<String> enumerate() {
            throw new UnsupportedOperationException(
                "String domains are unbounded and cannot be enumerated");
        }

        @Override
        public Set<String> boundaryValues() {
            return Set.of("");
        }
    }
}
