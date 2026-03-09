package io.nosqlbench.paramodel.mock.sequence;

import io.nosqlbench.paramodel.parameters.Constraint;
import io.nosqlbench.paramodel.parameters.ValidationResult;
import io.nosqlbench.paramodel.parameters.Value;
import io.nosqlbench.paramodel.mock.parameters.MockValidationResult;
import io.nosqlbench.paramodel.sequence.Trial;

import java.util.*;

/**
 * Simple trial implementation with element-structured assignments.
 */
public class MockTrial implements Trial {
    private final String id;
    private final Map<String, Map<String, Value<?>>> assignments;
    private final List<Constraint<Map<String, Value<?>>>> constraints;

    public MockTrial(String id, Map<String, Map<String, Value<?>>> assignments) {
        this(id, assignments, List.of());
    }

    public MockTrial(String id, Map<String, Map<String, Value<?>>> assignments,
                     List<Constraint<Map<String, Value<?>>>> constraints) {
        this.id = Objects.requireNonNull(id);
        this.assignments = deepCopy(assignments);
        this.constraints = new ArrayList<>(constraints);
    }

    private static Map<String, Map<String, Value<?>>> deepCopy(Map<String, Map<String, Value<?>>> source) {
        Map<String, Map<String, Value<?>>> outer = new LinkedHashMap<>();
        for (var entry : source.entrySet()) {
            outer.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
        }
        return outer;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Map<String, Map<String, Value<?>>> assignments() {
        Map<String, Map<String, Value<?>>> result = new LinkedHashMap<>();
        for (var entry : assignments.entrySet()) {
            result.put(entry.getKey(), Collections.unmodifiableMap(entry.getValue()));
        }
        return Collections.unmodifiableMap(result);
    }

    @Override
    public Optional<Value<?>> assignment(String elementName, String parameterName) {
        return Optional.ofNullable(
            assignments.getOrDefault(elementName, Map.of()).get(parameterName));
    }

    @Override
    public List<Constraint<Map<String, Value<?>>>> constraints() {
        return Collections.unmodifiableList(constraints);
    }

    @Override
    public ValidationResult validate() {
        // Flatten assignments for constraint evaluation (constraints use bare param names)
        Map<String, Value<?>> flat = new LinkedHashMap<>();
        for (var elementEntry : assignments.entrySet()) {
            flat.putAll(elementEntry.getValue());
        }
        for (Constraint<Map<String, Value<?>>> constraint : constraints) {
            if (!constraint.test(flat)) {
                return MockValidationResult.failed("Constraint validation failed");
            }
        }
        return MockValidationResult.passed();
    }

    @Override
    public Optional<TrialMetadata> metadata() {
        return Optional.empty();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        public Builder() {}

        private String id;
        private final Map<String, Map<String, Value<?>>> assignments = new LinkedHashMap<>();
        private final List<Constraint<Map<String, Value<?>>>> constraints = new ArrayList<>();

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        /// Adds a parameter assignment scoped to an element.
        ///
        /// @param elementName   the element this parameter belongs to
        /// @param parameterName parameter name within the element
        /// @param value         assigned value
        /// @return this builder for chaining
        public Builder assignment(String elementName, String parameterName, Value<?> value) {
            this.assignments.computeIfAbsent(elementName, k -> new LinkedHashMap<>())
                .put(parameterName, value);
            return this;
        }

        public Builder constraint(Constraint<Map<String, Value<?>>> constraint) {
            this.constraints.add(constraint);
            return this;
        }

        public MockTrial build() {
            if (id == null) {
                id = UUID.randomUUID().toString();
            }
            return new MockTrial(id, assignments, constraints);
        }
    }
}
