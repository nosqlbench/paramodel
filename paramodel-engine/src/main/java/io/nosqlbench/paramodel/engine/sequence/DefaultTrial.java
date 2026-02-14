package io.nosqlbench.paramodel.engine.sequence;

import io.nosqlbench.paramodel.parameters.Constraint;
import io.nosqlbench.paramodel.parameters.ValidationResult;
import io.nosqlbench.paramodel.parameters.Value;
import io.nosqlbench.paramodel.sequence.Trial;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import io.nosqlbench.paramodel.engine.CompactId;

public class DefaultTrial implements Trial {

    private final String id;
    private final Map<String, Value<?>> assignments;
    private final List<Constraint<Map<String, Value<?>>>> constraints;
    private final TrialMetadata metadata;

    public DefaultTrial(String id, Map<String, Value<?>> assignments,
                        List<Constraint<Map<String, Value<?>>>> constraints,
                        TrialMetadata metadata) {
        this.id = id != null ? id : CompactId.next();
        this.assignments = assignments != null ? Map.copyOf(assignments) : Map.of();
        this.constraints = constraints != null ? List.copyOf(constraints) : List.of();
        this.metadata = metadata;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Map<String, Value<?>> assignments() {
        return assignments;
    }

    @Override
    public Optional<Value<?>> assignment(String parameterName) {
        return Optional.ofNullable(assignments.get(parameterName));
    }

    @Override
    public List<Constraint<Map<String, Value<?>>>> constraints() {
        return constraints;
    }

    @Override
    public ValidationResult validate() {
        // Validation logic can be expanded. For now, we assume construction validity
        // or check basic assignments.
        return new ValidationResult.Passed();
    }

    @Override
    public Optional<TrialMetadata> metadata() {
        return Optional.ofNullable(metadata);
    }
}
