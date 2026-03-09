package io.nosqlbench.paramodel.engine.sequence;

import io.nosqlbench.paramodel.parameters.Constraint;
import io.nosqlbench.paramodel.parameters.ValidationResult;
import io.nosqlbench.paramodel.parameters.Value;
import io.nosqlbench.paramodel.sequence.Trial;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import io.nosqlbench.paramodel.engine.CompactId;

/// Default implementation of {@link Trial} with element-structured assignments.
///
/// Assignments are stored as a two-level map: element name → parameter name → value.
/// Both the outer and inner maps are immutable after construction.
public class DefaultTrial implements Trial {

    private final String id;
    private final Map<String, Map<String, Value<?>>> assignments;
    private final List<Constraint<Map<String, Value<?>>>> constraints;
    private final TrialMetadata metadata;

    /// Creates a new trial with element-structured assignments.
    ///
    /// @param id          unique identifier (auto-generated if null)
    /// @param assignments element name → parameter name → value (deep-copied)
    /// @param constraints cross-parameter constraints
    /// @param metadata    optional trial metadata
    public DefaultTrial(String id, Map<String, Map<String, Value<?>>> assignments,
                        List<Constraint<Map<String, Value<?>>>> constraints,
                        TrialMetadata metadata) {
        this.id = id != null ? id : CompactId.next();
        this.assignments = deepCopy(assignments);
        this.constraints = constraints != null ? List.copyOf(constraints) : List.of();
        this.metadata = metadata;
    }

    private static Map<String, Map<String, Value<?>>> deepCopy(Map<String, Map<String, Value<?>>> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, Map<String, Value<?>>> outer = new LinkedHashMap<>(source.size());
        for (var entry : source.entrySet()) {
            outer.put(entry.getKey(), Map.copyOf(entry.getValue()));
        }
        return Collections.unmodifiableMap(outer);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Map<String, Map<String, Value<?>>> assignments() {
        return assignments;
    }

    @Override
    public Optional<Value<?>> assignment(String elementName, String parameterName) {
        return Optional.ofNullable(
            assignments.getOrDefault(elementName, Map.of()).get(parameterName));
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
