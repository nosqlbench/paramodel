package io.nosqlbench.paramodel.mock.sequence;

import io.nosqlbench.paramodel.core.Constraint;
import io.nosqlbench.paramodel.core.ValidationResult;
import io.nosqlbench.paramodel.core.Value;
import io.nosqlbench.paramodel.mock.core.MockValidationResult;
import io.nosqlbench.paramodel.sequence.Trial;

import java.util.*;

/**
 * Simple trial implementation.
 */
public class MockTrial implements Trial {
    private final String id;
    private final Map<String, Value<?>> assignments;
    private final List<Constraint<Map<String, Value<?>>>> constraints;

    public MockTrial(String id, Map<String, Value<?>> assignments) {
        this(id, assignments, List.of());
    }

    public MockTrial(String id, Map<String, Value<?>> assignments,
                     List<Constraint<Map<String, Value<?>>>> constraints) {
        this.id = Objects.requireNonNull(id);
        this.assignments = new HashMap<>(assignments);
        this.constraints = new ArrayList<>(constraints);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Map<String, Value<?>> assignments() {
        return Collections.unmodifiableMap(assignments);
    }

    @Override
    public Optional<Value<?>> assignment(String parameterName) {
        return Optional.ofNullable(assignments.get(parameterName));
    }

    @Override
    public List<Constraint<Map<String, Value<?>>>> constraints() {
        return Collections.unmodifiableList(constraints);
    }

    @Override
    public ValidationResult validate() {
        for (Constraint<Map<String, Value<?>>> constraint : constraints) {
            if (!constraint.test(assignments)) {
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
        private String id;
        private final Map<String, Value<?>> assignments = new HashMap<>();
        private final List<Constraint<Map<String, Value<?>>>> constraints = new ArrayList<>();

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder assignment(String name, Value<?> value) {
            this.assignments.put(name, value);
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
