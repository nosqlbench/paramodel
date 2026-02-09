package io.nosqlbench.paramodel.mock.plan;

import io.nosqlbench.paramodel.parameters.Parameter;
import io.nosqlbench.paramodel.plan.Axis;

import java.util.*;
import java.util.Map;

/**
 * Simple axis implementation.
 */
public class MockAxis<T> implements Axis<T> {
    private final String name;
    private final List<T> values;
    private final String description;

    public MockAxis(String name, List<T> values, String description) {
        this.name = Objects.requireNonNull(name);
        this.values = new ArrayList<>(values);
        this.description = description;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Map<String, String> tags() {
        return Map.of("name", name);
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

    @Override
    public Optional<Parameter<T>> underlyingParameter() {
        return Optional.empty();
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

        public MockAxis<T> build() {
            return new MockAxis<>(name, values, description);
        }
    }
}
