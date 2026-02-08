package io.nosqlbench.paramodel.mock.plan;

import io.nosqlbench.paramodel.plan.AtomicStep;
import io.nosqlbench.paramodel.plan.Barrier;

import java.util.*;

/**
 * Simple barrier implementation for synchronization points.
 */
public class MockBarrier implements Barrier {
    private final String id;
    private final Set<AtomicStep> predecessors;
    private final Set<AtomicStep> successors;
    private final BarrierType type;

    public MockBarrier(String id, Set<AtomicStep> predecessors,
                      Set<AtomicStep> successors, BarrierType type) {
        this.id = Objects.requireNonNull(id);
        this.predecessors = new HashSet<>(predecessors);
        this.successors = new HashSet<>(successors);
        this.type = Objects.requireNonNull(type);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Set<AtomicStep> predecessors() {
        return Collections.unmodifiableSet(predecessors);
    }

    @Override
    public Set<AtomicStep> successors() {
        return Collections.unmodifiableSet(successors);
    }

    @Override
    public BarrierType type() {
        return type;
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static class Builder {
        private final String id;
        private final Set<AtomicStep> predecessors = new HashSet<>();
        private final Set<AtomicStep> successors = new HashSet<>();
        private BarrierType type = BarrierType.WAIT_ALL;

        public Builder(String id) {
            this.id = id;
        }

        public Builder predecessor(AtomicStep step) {
            this.predecessors.add(step);
            return this;
        }

        public Builder successor(AtomicStep step) {
            this.successors.add(step);
            return this;
        }

        public Builder type(BarrierType type) {
            this.type = type;
            return this;
        }

        public MockBarrier build() {
            return new MockBarrier(id, predecessors, successors, type);
        }
    }
}
