package io.nosqlbench.paramodel.mock.plan;

import io.nosqlbench.paramodel.core.Value;
import io.nosqlbench.paramodel.plan.Element;

import java.util.*;

/**
 * Simple element implementation.
 */
public class MockElement implements Element {
    private final String parameterName;
    private final Optional<Value<?>> fixedValue;
    private final SamplingStrategy samplingStrategy;

    public MockElement(String parameterName, Optional<Value<?>> fixedValue, SamplingStrategy samplingStrategy) {
        this.parameterName = Objects.requireNonNull(parameterName);
        this.fixedValue = Objects.requireNonNull(fixedValue);
        this.samplingStrategy = Objects.requireNonNull(samplingStrategy);
    }

    @Override
    public String parameterName() {
        return parameterName;
    }

    @Override
    public Optional<Value<?>> fixedValue() {
        return fixedValue;
    }

    @Override
    public SamplingStrategy samplingStrategy() {
        return samplingStrategy;
    }

    public static MockElement fixed(String parameterName, Value<?> value) {
        return new MockElement(parameterName, Optional.of(value), SamplingStrategy.FIXED);
    }

    public static MockElement boundary(String parameterName) {
        return new MockElement(parameterName, Optional.empty(), SamplingStrategy.BOUNDARY);
    }

    public static MockElement random(String parameterName) {
        return new MockElement(parameterName, Optional.empty(), SamplingStrategy.RANDOM);
    }

    public static MockElement exhaustive(String parameterName) {
        return new MockElement(parameterName, Optional.empty(), SamplingStrategy.EXHAUSTIVE);
    }
}
