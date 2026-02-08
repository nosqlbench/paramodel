package io.nosqlbench.paramodel.mock.plan;

import io.nosqlbench.paramodel.core.Constraint;
import io.nosqlbench.paramodel.core.Parameter;
import io.nosqlbench.paramodel.core.ValidationResult;
import io.nosqlbench.paramodel.core.Value;
import io.nosqlbench.paramodel.core.metadata.TestPlanMetadata;
import io.nosqlbench.paramodel.mock.core.MockValidationResult;
import io.nosqlbench.paramodel.plan.*;

import java.util.*;

/**
 * Simple test plan implementation.
 */
public class MockTestPlan implements TestPlan {
    private final Map<String, Parameter<?>> parameters;
    private final List<Axis> axes;
    private final List<Constraint<Map<String, Value<?>>>> constraints;
    private final OptimizationStrategy optimizationStrategy;
    private final TestPlanMetadata metadata;
    private final boolean committed;

    public MockTestPlan(Map<String, Parameter<?>> parameters,
                       List<Axis> axes,
                       List<Constraint<Map<String, Value<?>>>> constraints,
                       OptimizationStrategy optimizationStrategy,
                       TestPlanMetadata metadata,
                       boolean committed) {
        this.parameters = new HashMap<>(parameters);
        this.axes = new ArrayList<>(axes);
        this.constraints = new ArrayList<>(constraints);
        this.optimizationStrategy = optimizationStrategy;
        this.metadata = metadata;
        this.committed = committed;
    }

    @Override
    public Map<String, Parameter<?>> parameters() {
        return Collections.unmodifiableMap(parameters);
    }

    @Override
    public List<Axis> axes() {
        return Collections.unmodifiableList(axes);
    }

    @Override
    public List<Constraint<Map<String, Value<?>>>> constraints() {
        return Collections.unmodifiableList(constraints);
    }

    @Override
    public OptimizationStrategy optimizationStrategy() {
        return optimizationStrategy;
    }

    @Override
    public TestPlanMetadata metadata() {
        return metadata;
    }

    @Override
    public ValidationResult validate() {
        // Basic validation
        if (parameters.isEmpty()) {
            return MockValidationResult.failed("TestPlan must have at least one parameter");
        }

        // Validate all axes reference valid parameters
        for (Axis axis : axes) {
            for (Element element : axis.elements()) {
                if (!parameters.containsKey(element.parameterName())) {
                    return MockValidationResult.failed("Unknown parameter: " + element.parameterName());
                }
            }
        }

        return MockValidationResult.passed();
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
        private final Map<String, Parameter<?>> parameters = new HashMap<>();
        private final List<Axis> axes = new ArrayList<>();
        private final List<Constraint<Map<String, Value<?>>>> constraints = new ArrayList<>();
        private OptimizationStrategy optimizationStrategy = OptimizationStrategy.NONE;
        private TestPlanMetadata metadata;

        public Builder parameter(Parameter<?> parameter) {
            this.parameters.put(parameter.name(), parameter);
            return this;
        }

        public Builder axis(Axis axis) {
            this.axes.add(axis);
            return this;
        }

        public Builder constraint(Constraint<Map<String, Value<?>>> constraint) {
            this.constraints.add(constraint);
            return this;
        }

        public Builder optimizationStrategy(OptimizationStrategy strategy) {
            this.optimizationStrategy = strategy;
            return this;
        }

        public Builder metadata(TestPlanMetadata metadata) {
            this.metadata = metadata;
            return this;
        }

        public MockTestPlan build() {
            if (metadata == null) {
                metadata = MockTestPlanMetadata.empty();
            }
            return new MockTestPlan(parameters, axes, constraints, optimizationStrategy, metadata, false);
        }
    }
}
