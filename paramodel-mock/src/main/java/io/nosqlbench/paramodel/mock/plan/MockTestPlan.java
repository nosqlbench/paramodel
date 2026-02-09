package io.nosqlbench.paramodel.mock.plan;

import io.nosqlbench.paramodel.core.Constraint;
import io.nosqlbench.paramodel.core.Parameter;
import io.nosqlbench.paramodel.core.ValidationResult;
import io.nosqlbench.paramodel.core.Value;
import io.nosqlbench.paramodel.mock.core.MockValidationResult;
import io.nosqlbench.paramodel.plan.*;
import io.nosqlbench.paramodel.plan.policies.ExecutionPolicies;

import java.util.*;

/**
 * Simple test plan implementation.
 */
public class MockTestPlan implements TestPlan {
    private final String name;
    private final List<Axis<?>> axes;
    private final List<Element> elements;
    private final ExecutionPolicies policies;
    private final OptimizationStrategy optimizationStrategy;
    private final TestPlan.TestPlanMetadata metadata;
    private final boolean committed;

    public MockTestPlan(String name,
                       List<Axis<?>> axes,
                       List<Element> elements,
                       ExecutionPolicies policies,
                       OptimizationStrategy optimizationStrategy,
                       TestPlan.TestPlanMetadata metadata,
                       boolean committed) {
        this.name = name;
        this.axes = new ArrayList<>(axes);
        this.elements = new ArrayList<>(elements);
        this.policies = policies;
        this.optimizationStrategy = optimizationStrategy;
        this.metadata = metadata;
        this.committed = committed;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public List<Axis<?>> axes() {
        return Collections.unmodifiableList(axes);
    }

    @Override
    public List<Element> elements() {
        return Collections.unmodifiableList(elements);
    }

    @Override
    public Map<TestPlan.ElementPair, RelationshipType> relationships() {
        // Return empty map for simple mock
        return Map.of();
    }

    @Override
    public Optional<RelationshipType> relationshipBetween(Element element1, Element element2) {
        // No relationships in simple mock
        return Optional.empty();
    }

    @Override
    public ExecutionPolicies policies() {
        return policies;
    }

    @Override
    public OptimizationStrategy optimizationStrategy() {
        return optimizationStrategy;
    }

    @Override
    public TestPlan.TestPlanMetadata metadata() {
        return metadata;
    }

    @Override
    public long trialSpaceSize() {
        // Calculate trial space size as product of all axis cardinalities
        long size = 1;
        for (Axis<?> axis : axes) {
            size *= axis.cardinality();
        }
        return size;
    }

    @Override
    public ValidationResult validate() {
        // Basic validation
        if (name == null || name.isEmpty()) {
            return MockValidationResult.failed("TestPlan must have a name");
        }
        if (axes.isEmpty()) {
            return MockValidationResult.failed("TestPlan must have at least one axis");
        }
        return MockValidationResult.passed();
    }

    @Override
    public TestPlan reorderAxes(List<String> axisNames) {
        if (committed) {
            throw new IllegalStateException("Cannot reorder axes on committed plan");
        }
        // Create new list with reordered axes
        List<Axis<?>> reordered = new ArrayList<>();
        for (String name : axisNames) {
            axes.stream()
                .filter(a -> a.name().equals(name))
                .findFirst()
                .ifPresent(reordered::add);
        }
        return new MockTestPlan(name, reordered, elements, policies, optimizationStrategy, metadata, false);
    }

    @Override
    public ExecutionPlan commit() {
        if (committed) {
            throw new IllegalStateException("TestPlan already committed");
        }
        // Return a basic execution plan
        return new MockExecutionPlan(this);
    }

    @Override
    public boolean isCommitted() {
        return committed;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private final List<Axis<?>> axes = new ArrayList<>();
        private final List<Element> elements = new ArrayList<>();
        private ExecutionPolicies policies;
        private OptimizationStrategy optimizationStrategy = OptimizationStrategy.NONE;
        private TestPlan.TestPlanMetadata metadata;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder axis(Axis<?> axis) {
            this.axes.add(axis);
            return this;
        }

        public Builder element(Element element) {
            this.elements.add(element);
            return this;
        }

        public Builder policies(ExecutionPolicies policies) {
            this.policies = policies;
            return this;
        }

        public Builder optimizationStrategy(OptimizationStrategy strategy) {
            this.optimizationStrategy = strategy;
            return this;
        }

        public Builder metadata(TestPlan.TestPlanMetadata metadata) {
            this.metadata = metadata;
            return this;
        }

        public MockTestPlan build() {
            if (metadata == null) {
                metadata = MockTestPlanMetadata.empty();
            }
            if (policies == null) {
                // Create default policies - this would need an actual implementation
                policies = null; // Placeholder
            }
            return new MockTestPlan(name, axes, elements, policies, optimizationStrategy, metadata, false);
        }
    }
}
