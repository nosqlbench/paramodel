package io.nosqlbench.paramodel.mock.plan;

import io.nosqlbench.paramodel.plan.Axis;
import io.nosqlbench.paramodel.plan.Element;

import java.util.*;

/**
 * Simple axis implementation.
 */
public class MockAxis implements Axis {
    private final String name;
    private final List<Element> elements;
    private final AxisType type;

    public MockAxis(String name, List<Element> elements, AxisType type) {
        this.name = Objects.requireNonNull(name);
        this.elements = new ArrayList<>(elements);
        this.type = Objects.requireNonNull(type);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public List<Element> elements() {
        return Collections.unmodifiableList(elements);
    }

    @Override
    public AxisType type() {
        return type;
    }

    @Override
    public int size() {
        return elements.size();
    }

    public static MockAxis of(String name, Element... elements) {
        return new MockAxis(name, Arrays.asList(elements), AxisType.CARTESIAN);
    }

    public static MockAxis of(String name, AxisType type, Element... elements) {
        return new MockAxis(name, Arrays.asList(elements), type);
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static class Builder {
        private final String name;
        private final List<Element> elements = new ArrayList<>();
        private AxisType type = AxisType.CARTESIAN;

        public Builder(String name) {
            this.name = name;
        }

        public Builder element(Element element) {
            this.elements.add(element);
            return this;
        }

        public Builder type(AxisType type) {
            this.type = type;
            return this;
        }

        public MockAxis build() {
            return new MockAxis(name, elements, type);
        }
    }
}
