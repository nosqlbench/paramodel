package io.nosqlbench.paramodel.parameters.types;

import io.nosqlbench.paramodel.parameters.Constraint;
import io.nosqlbench.paramodel.parameters.Domain;
import io.nosqlbench.paramodel.parameters.Parameter;
import io.nosqlbench.paramodel.parameters.ValidationResult;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

///
/// A built-in parameter for selecting one or more values from a set of valid options.
///
/// ## Construction Modes
///
/// **Built-in values** — the valid set is specified at construction time:
///
/// ```java
/// Parameter<List<String>> region =
///     SelectionParameter.of("region", Set.of("us-east-1", "us-west-2", "eu-west-1"));
/// ```
///
/// **External resolver** — valid values are provided by a {@link SelectionResolver}:
///
/// ```java
/// Parameter<List<String>> model =
///     SelectionParameter.external("model", modelResolver);
/// ```
///
/// ## Multi-Select
///
/// By default, selections allow a single value ({@code maxSelections = 1}).
/// Use {@link #maxSelections(int)} to allow multiple selections:
///
/// ```java
/// Parameter<List<String>> tags = SelectionParameter
///     .of("tags", Set.of("fast", "accurate", "cheap", "reliable"))
///     .maxSelections(3);
/// ```
///
/// ## Value Type
///
/// The value type is always {@code List<String>}, even for single-select parameters.
/// This provides a consistent API regardless of selection cardinality.
///
/// @see Parameter
/// @see SelectionResolver
/// @since 0.1.0
///
public final class SelectionParameter implements Parameter<List<String>> {

    private final String name;
    private final SelectionDomainWrapper domain;
    private final List<Constraint<List<String>>> constraints;
    private int maxSelections;
    private List<String> defaultValue;

    /// The underlying string-level domain (built-in or external).
    private final Domain<String> stringDomain;

    private SelectionParameter(String name, Domain<String> stringDomain) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.stringDomain = stringDomain;
        this.maxSelections = 1;
        this.domain = new SelectionDomainWrapper();
        this.constraints = new ArrayList<>();
    }

    ///
    /// Creates a selection parameter with a built-in set of valid values.
    ///
    /// @param name        parameter name
    /// @param validValues the set of valid string values
    /// @return single-select selection parameter
    /// @throws IllegalArgumentException if validValues is null or empty
    ///
    public static SelectionParameter of(String name, Set<String> validValues) {
        if (validValues == null || validValues.isEmpty()) {
            throw new IllegalArgumentException("validValues must not be null or empty");
        }
        return new SelectionParameter(name, new BuiltInSelectionDomain(validValues));
    }

    ///
    /// Creates a selection parameter backed by an external resolver.
    ///
    /// @param name     parameter name
    /// @param resolver the resolver providing valid values
    /// @return single-select selection parameter
    ///
    public static SelectionParameter external(String name, SelectionResolver resolver) {
        Objects.requireNonNull(resolver, "resolver must not be null");
        return new SelectionParameter(name, new ExternalSelectionDomain(resolver));
    }

    ///
    /// Sets the maximum number of selections allowed.
    ///
    /// @param n maximum selections (must be >= 1)
    /// @return this parameter for chaining
    /// @throws IllegalArgumentException if n < 1
    ///
    public SelectionParameter maxSelections(int n) {
        if (n < 1) {
            throw new IllegalArgumentException("maxSelections must be >= 1, got " + n);
        }
        this.maxSelections = n;
        return this;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Map<String, String> tags() {
        return Map.of(
            "name", name,
            "type", "selection",
            "maxSelections", String.valueOf(maxSelections));
    }

    @Override
    public Domain<List<String>> domain() {
        return domain;
    }

    @Override
    public List<String> generate() {
        return domain.sample(ThreadLocalRandom.current());
    }

    @Override
    public List<String> generateBoundary() {
        Set<String> boundaries = stringDomain.boundaryValues();
        if (boundaries.isEmpty()) {
            return generate();
        }
        List<String> list = new ArrayList<>(boundaries);
        return List.of(list.get(ThreadLocalRandom.current().nextInt(list.size())));
    }

    @Override
    public List<String> generateRandom() {
        return domain.sample(ThreadLocalRandom.current());
    }

    @Override
    public ValidationResult validate(List<String> value) {
        if (value == null) {
            return new ValidationResult.Failed("value must not be null", List.of("null value"));
        }
        List<String> violations = new ArrayList<>();
        if (value.size() > maxSelections) {
            violations.add("selection count " + value.size() +
                " exceeds maximum " + maxSelections);
        }
        for (String v : value) {
            if (!stringDomain.contains(v)) {
                violations.add("'" + v + "' is not a valid selection");
            }
        }
        for (Constraint<List<String>> c : constraints) {
            if (!c.test(value)) {
                violations.add("constraint failed: " + c.description());
            }
        }
        if (!violations.isEmpty()) {
            return new ValidationResult.Failed(
                "selection validation failed", violations);
        }
        return new ValidationResult.Passed();
    }

    @Override
    public boolean satisfies(Constraint<List<String>> constraint) {
        // Test with single boundary values
        for (String boundary : stringDomain.boundaryValues()) {
            if (constraint.test(List.of(boundary))) {
                return true;
            }
        }
        return false;
    }

    ///
    /// Sets the default value for this parameter. Returns this parameter for chaining.
    ///
    /// All selections in the default value must be valid selections within the domain.
    ///
    /// @param value the default value (list of selected strings)
    /// @return this parameter
    /// @throws IllegalArgumentException if any selection is not in the valid set
    ///
    public SelectionParameter withDefault(List<String> value) {
        Objects.requireNonNull(value, "default value must not be null");
        for (String v : value) {
            if (!stringDomain.contains(v)) {
                throw new IllegalArgumentException(
                    "Default selection '" + v + "' is not a valid value for parameter '" + name + "'");
            }
        }
        if (value.size() > maxSelections) {
            throw new IllegalArgumentException(
                "Default selection count " + value.size() +
                " exceeds maximum " + maxSelections + " for parameter '" + name + "'");
        }
        this.defaultValue = List.copyOf(value);
        return this;
    }

    @Override
    public Optional<List<String>> defaultValue() {
        return Optional.ofNullable(defaultValue);
    }

    ///
    /// Adds a constraint to this parameter. Returns this parameter for chaining.
    ///
    /// @param constraint the constraint to add
    /// @return this parameter
    ///
    public SelectionParameter withConstraint(Constraint<List<String>> constraint) {
        this.constraints.add(Objects.requireNonNull(constraint));
        return this;
    }

    // --- Package-private domain implementations ---

    ///
    /// Wraps the string-level domain to produce {@code List<String>} values
    /// for the parameter interface.
    ///
    private final class SelectionDomainWrapper implements Domain.Custom<List<String>> {

        @Override
        public Predicate<List<String>> membership() {
            return list -> {
                if (list == null || list.size() > maxSelections) return false;
                for (String v : list) {
                    if (!stringDomain.contains(v)) return false;
                }
                return true;
            };
        }

        @Override
        public String description() {
            return "Selection from " + stringDomain + " (max " + maxSelections + ")";
        }

        @Override
        public boolean contains(List<String> value) {
            return membership().test(value);
        }

        @Override
        public Optional<Long> cardinality() {
            // Combinatorial; not easily countable for multi-select
            if (maxSelections == 1) {
                return stringDomain.cardinality();
            }
            return Optional.empty();
        }

        @Override
        public List<String> sample(Random rng) {
            List<String> pool = new ArrayList<>(stringDomain.boundaryValues());
            // For domains that expose more values, use them
            if (stringDomain instanceof Domain.Discrete<?> discrete) {
                @SuppressWarnings("unchecked")
                Set<String> vals = ((Domain.Discrete<String>) discrete).values();
                pool = new ArrayList<>(vals);
            }
            Collections.shuffle(pool, rng);
            int count = Math.min(maxSelections, pool.size());
            return List.copyOf(pool.subList(0, count));
        }

        @Override
        public Iterator<List<String>> enumerate() {
            if (maxSelections == 1) {
                Iterator<String> inner = stringDomain.enumerate();
                return new Iterator<>() {
                    @Override
                    public boolean hasNext() {
                        return inner.hasNext();
                    }

                    @Override
                    public List<String> next() {
                        return List.of(inner.next());
                    }
                };
            }
            throw new UnsupportedOperationException(
                "Multi-select selection parameters cannot be enumerated");
        }

        @Override
        public Set<List<String>> boundaryValues() {
            Set<List<String>> result = new LinkedHashSet<>();
            for (String boundary : stringDomain.boundaryValues()) {
                result.add(List.of(boundary));
            }
            return Collections.unmodifiableSet(result);
        }
    }

    private static final class BuiltInSelectionDomain implements Domain.Discrete<String> {
        private final Set<String> values;
        private final List<String> sortedValues;

        BuiltInSelectionDomain(Set<String> values) {
            this.values = Set.copyOf(values);
            this.sortedValues = new ArrayList<>(this.values);
            Collections.sort(this.sortedValues);
        }

        @Override
        public Set<String> values() {
            return values;
        }

        @Override
        public boolean contains(String value) {
            return value != null && values.contains(value);
        }

        @Override
        public Optional<Long> cardinality() {
            return Optional.of((long) values.size());
        }

        @Override
        public String sample(Random rng) {
            return sortedValues.get(rng.nextInt(sortedValues.size()));
        }

        @Override
        public Iterator<String> enumerate() {
            return sortedValues.iterator();
        }

        @Override
        public Set<String> boundaryValues() {
            if (sortedValues.size() == 1) {
                return Set.of(sortedValues.get(0));
            }
            return Set.of(sortedValues.get(0), sortedValues.get(sortedValues.size() - 1));
        }
    }

    private static final class ExternalSelectionDomain implements Domain.Custom<String> {
        private final SelectionResolver resolver;

        ExternalSelectionDomain(SelectionResolver resolver) {
            this.resolver = resolver;
        }

        @Override
        public Predicate<String> membership() {
            return resolver::isValid;
        }

        @Override
        public String description() {
            return "External selection via " + resolver.getClass().getSimpleName();
        }

        @Override
        public boolean contains(String value) {
            return value != null && resolver.isValid(value);
        }

        @Override
        public Optional<Long> cardinality() {
            Set<String> vals = resolver.validValues();
            return vals.isEmpty() ? Optional.empty() : Optional.of((long) vals.size());
        }

        @Override
        public String sample(Random rng) {
            List<String> vals = new ArrayList<>(resolver.validValues());
            if (vals.isEmpty()) {
                throw new IllegalStateException("No valid values available from resolver");
            }
            return vals.get(rng.nextInt(vals.size()));
        }

        @Override
        public Iterator<String> enumerate() {
            return resolver.validValues().iterator();
        }

        @Override
        public Set<String> boundaryValues() {
            Set<String> vals = resolver.validValues();
            if (vals.isEmpty()) return Set.of();
            List<String> sorted = new ArrayList<>(vals);
            Collections.sort(sorted);
            if (sorted.size() == 1) return Set.of(sorted.get(0));
            return Set.of(sorted.get(0), sorted.get(sorted.size() - 1));
        }
    }
}
