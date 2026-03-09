package io.nosqlbench.paramodel.mock.plan;

import io.nosqlbench.paramodel.mock.parameters.MockParameter;
import io.nosqlbench.paramodel.parameters.Domain;
import io.nosqlbench.paramodel.parameters.Parameter;
import io.nosqlbench.paramodel.plan.AttachedParameter;
import io.nosqlbench.paramodel.plan.Axis;
import io.nosqlbench.paramodel.plan.DefaultAttachedParameter;

import java.util.*;

///
/// Simple axis implementation for testing.
///
/// When no explicit {@link AttachedParameter} is provided, a stub
/// binding is created using a {@link MockParameter} and a
/// {@link MockElement} named {@code "mock"}.
///
public class MockAxis<T> implements Axis<T> {
    private final String name;
    private final List<T> values;
    private final String description;
    private final AttachedParameter<T> attachedParameter;

    @SuppressWarnings("unchecked")
    public MockAxis(String name, List<T> values, String description) {
        this(name, values, description, null);
    }

    @SuppressWarnings("unchecked")
    public MockAxis(String name, List<T> values, String description,
                    AttachedParameter<T> attachedParameter) {
        this.name = Objects.requireNonNull(name);
        this.values = new ArrayList<>(values);
        this.description = description;
        this.attachedParameter = attachedParameter != null
                ? attachedParameter
                : defaultAttachedParameter(name);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> AttachedParameter<T> defaultAttachedParameter(String name) {
        Parameter param = MockParameter.of(name, new AcceptAllDomain<>());
        return (AttachedParameter<T>) new DefaultAttachedParameter<>(param, MockElement.of("mock"));
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public AttachedParameter<T> attachedParameter() {
        return attachedParameter;
    }

    @Override
    public List<T> values() {
        return Collections.unmodifiableList(values);
    }

    @Override
    public List<T> boundaryValues() {
        if (values.isEmpty()) {
            return List.of();
        }
        if (values.size() == 1) {
            return List.of(values.get(0));
        }
        return List.of(values.get(0), values.get(values.size() - 1));
    }

    @Override
    public Optional<String> description() {
        return Optional.ofNullable(description);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <T> MockAxis<T> of(String name, T... values) {
        return new MockAxis<>(name, Arrays.asList(values), null);
    }

    public static <T> MockAxis<T> of(String name, List<T> values) {
        return new MockAxis<>(name, values, null);
    }

    public static <T> Builder<T> builder(String name) {
        return new Builder<>(name);
    }

    public static class Builder<T> {
        private final String name;
        private final List<T> values = new ArrayList<>();
        private String description;
        private AttachedParameter<T> attachedParameter;

        public Builder(String name) {
            this.name = name;
        }

        public Builder<T> value(T value) {
            this.values.add(value);
            return this;
        }

        @SafeVarargs
        @SuppressWarnings("varargs")
        public final Builder<T> values(T... values) {
            this.values.addAll(Arrays.asList(values));
            return this;
        }

        public Builder<T> values(List<T> values) {
            this.values.addAll(values);
            return this;
        }

        public Builder<T> description(String description) {
            this.description = description;
            return this;
        }

        /// Sets an explicit attached parameter binding.
        public Builder<T> attachedParameter(AttachedParameter<T> attachedParameter) {
            this.attachedParameter = attachedParameter;
            return this;
        }

        public MockAxis<T> build() {
            return new MockAxis<>(name, values, description, attachedParameter);
        }
    }

    /// Minimal accept-all domain for mock parameters.
    private static final class AcceptAllDomain<T> implements Domain.Custom<T> {
        @Override
        public java.util.function.Predicate<T> membership() {
            return _ -> true;
        }

        @Override
        public String description() {
            return "accept-all";
        }

        @Override
        public boolean contains(T value) {
            return true;
        }

        @Override
        public Optional<Long> cardinality() {
            return Optional.empty();
        }

        @Override
        public T sample(Random rng) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Iterator<T> enumerate() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Set<T> boundaryValues() {
            return Set.of();
        }
    }
}
