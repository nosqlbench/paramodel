package io.nosqlbench.paramodel.tck.mock;

import io.nosqlbench.paramodel.core.*;
import io.nosqlbench.paramodel.mock.core.*;
import io.nosqlbench.paramodel.mock.plan.*;
import io.nosqlbench.paramodel.mock.sequence.*;
import io.nosqlbench.paramodel.plan.*;
import io.nosqlbench.paramodel.sequence.*;
import io.nosqlbench.paramodel.tck.ImplementationProvider;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation provider for mock implementation TCK validation.
 */
public class MockImplementationProvider implements ImplementationProvider {

    @Override
    public <T> Parameter<T> createParameter(String name, Domain<T> domain) {
        return MockParameter.of(name, domain);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Domain<T> createDiscreteDomain(Iterable<T> values) {
        List<T> list = new ArrayList<>();
        values.forEach(list::add);
        return (Domain<T>) MockDomain.of(list.toArray());
    }

    @Override
    public <T> Value<T> createValue(T value, String parameterName) {
        return MockValue.of(value, parameterName);
    }

    @Override
    public ValidationResult createValidationResult(boolean valid, String message) {
        if (valid) {
            return MockValidationResult.passed();
        } else {
            return MockValidationResult.failed(message);
        }
    }

    @Override
    public Trial createTrial(String id) {
        return MockTrial.builder().id(id).build();
    }

    @Override
    public TrialBuilder createTrialBuilder() {
        return new MockTrialBuilder();
    }

    @Override
    public Sequence createSequence(Iterable<Trial> trials) {
        List<Trial> list = new ArrayList<>();
        trials.forEach(list::add);
        return MockSequence.of(list);
    }

    @Override
    public SequenceBuilder createSequenceBuilder() {
        return MockSequenceBuilder.create();
    }

    @Override
    public TestPlan createTestPlan() {
        return MockTestPlan.builder().build();
    }

    @Override
    public TestPlanBuilder createTestPlanBuilder() {
        return new MockTestPlanBuilder();
    }

    @Override
    public Axis createAxis(String name, Iterable<Element> elements) {
        List<Element> list = new ArrayList<>();
        elements.forEach(list::add);
        return MockAxis.of(name, list.toArray(new Element[0]));
    }

    @Override
    public Element createElement(String parameterName) {
        return MockElement.exhaustive(parameterName);
    }

    @Override
    public ExecutionPlan createExecutionPlan(TestPlan testPlan) {
        return new MockExecutionPlan(testPlan);
    }

    @Override
    public AtomicStep createAtomicStep(String id, Trial trial) {
        return MockAtomicStep.of(id, trial);
    }

    @Override
    public ExecutionGraph createExecutionGraph() {
        return new MockExecutionGraph();
    }

    /**
     * Mock TrialBuilder implementation.
     */
    private static class MockTrialBuilder implements TrialBuilder {
        private final MockTrial.Builder delegate = MockTrial.builder();

        @Override
        public TrialBuilder id(String id) {
            delegate.id(id);
            return this;
        }

        @Override
        public TrialBuilder assignment(String name, Value<?> value) {
            delegate.assignment(name, value);
            return this;
        }

        @Override
        public TrialBuilder constraint(Constraint<java.util.Map<String, Value<?>>> constraint) {
            delegate.constraint(constraint);
            return this;
        }

        @Override
        public Trial build() {
            return delegate.build();
        }
    }

    /**
     * Mock TestPlanBuilder implementation.
     */
    private static class MockTestPlanBuilder implements TestPlanBuilder {
        private final MockTestPlan.Builder delegate = MockTestPlan.builder();

        @Override
        public TestPlanBuilder parameter(Parameter<?> parameter) {
            delegate.parameter(parameter);
            return this;
        }

        @Override
        public TestPlanBuilder axis(Axis axis) {
            delegate.axis(axis);
            return this;
        }

        @Override
        public TestPlanBuilder constraint(Constraint<java.util.Map<String, Value<?>>> constraint) {
            delegate.constraint(constraint);
            return this;
        }

        @Override
        public TestPlanBuilder optimizationStrategy(OptimizationStrategy strategy) {
            delegate.optimizationStrategy(strategy);
            return this;
        }

        @Override
        public TestPlan build() {
            return delegate.build();
        }
    }
}
