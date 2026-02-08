package io.nosqlbench.paramodel.mock.plan;

import io.nosqlbench.paramodel.core.Value;
import io.nosqlbench.paramodel.plan.AtomicStep;
import io.nosqlbench.paramodel.sequence.Trial;

import java.util.*;

/**
 * Simple atomic step implementation.
 */
public class MockAtomicStep implements AtomicStep {
    private final String id;
    private final Trial trial;
    private final Map<String, Object> executionContext;

    public MockAtomicStep(String id, Trial trial, Map<String, Object> executionContext) {
        this.id = Objects.requireNonNull(id);
        this.trial = Objects.requireNonNull(trial);
        this.executionContext = new HashMap<>(executionContext);
    }

    public MockAtomicStep(String id, Trial trial) {
        this(id, trial, Map.of());
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Trial trial() {
        return trial;
    }

    @Override
    public Map<String, Object> executionContext() {
        return Collections.unmodifiableMap(executionContext);
    }

    public static MockAtomicStep of(String id, Trial trial) {
        return new MockAtomicStep(id, trial);
    }

    public static MockAtomicStep of(Trial trial) {
        return new MockAtomicStep(trial.id(), trial);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private Trial trial;
        private final Map<String, Object> executionContext = new HashMap<>();

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder trial(Trial trial) {
            this.trial = trial;
            if (this.id == null) {
                this.id = trial.id();
            }
            return this;
        }

        public Builder context(String key, Object value) {
            this.executionContext.put(key, value);
            return this;
        }

        public MockAtomicStep build() {
            if (id == null) {
                id = UUID.randomUUID().toString();
            }
            Objects.requireNonNull(trial, "trial cannot be null");
            return new MockAtomicStep(id, trial, executionContext);
        }
    }
}
