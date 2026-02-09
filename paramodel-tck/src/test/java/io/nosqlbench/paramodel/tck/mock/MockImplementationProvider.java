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
        return MockElement.service(parameterName);
    }

    @Override
    public ExecutionPlan createExecutionPlan(TestPlan testPlan) {
        return new MockExecutionPlan(
            java.util.UUID.randomUUID().toString(),
            java.util.UUID.randomUUID().toString());
    }

    @Override
    public AtomicStep createAtomicStep(String id, Trial trial) {
        return MockAtomicStep.executeTrial(id, trial.id());
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
        private MockTestPlan.Builder delegate = MockTestPlan.builder();

        @Override
        public TestPlanBuilder name(String name) {
            delegate.name(name);
            return this;
        }

        @Override
        public <T> TestPlanBuilder withAxis(Axis<T> axis) {
            delegate.axis(axis);
            return this;
        }

        @Override
        public <T> TestPlanBuilder withAxisFromParameter(Parameter<T> parameter, int sampleSize) {
            return this;
        }

        @Override
        public TestPlanBuilder withAxes(List<Axis<?>> axes) {
            for (Axis<?> axis : axes) {
                delegate.axis(axis);
            }
            return this;
        }

        @Override
        public TestPlanBuilder withElement(Element element) {
            delegate.element(element);
            return this;
        }

        @Override
        public TestPlanBuilder withElements(List<Element> elements) {
            for (Element element : elements) {
                delegate.element(element);
            }
            return this;
        }

        @Override
        public TestPlanBuilder relationship(String element1, String element2, RelationshipType type) {
            return this;
        }

        @Override
        public TestPlanBuilder relationship(Element element1, Element element2, RelationshipType type) {
            return this;
        }

        @Override
        public TestPlanBuilder policies(io.nosqlbench.paramodel.plan.policies.ExecutionPolicies policies) {
            delegate.policies(policies);
            return this;
        }

        @Override
        public TestPlanBuilder policies(java.util.function.Consumer<io.nosqlbench.paramodel.plan.policies.ExecutionPolicies.Builder> configurator) {
            return this;
        }

        @Override
        public TestPlanBuilder optimizationStrategy(OptimizationStrategy strategy) {
            delegate.optimizationStrategy(strategy);
            return this;
        }

        @Override
        public TestPlanBuilder basedOn(TestPlan source) {
            return this;
        }

        @Override
        public TestPlanBuilder axisOrder(List<String> axisNames) {
            return this;
        }

        @Override
        public TestPlanBuilder metadata(String key, Object value) {
            return this;
        }

        @Override
        public TestPlanBuilder metadata(java.util.Map<String, Object> metadata) {
            return this;
        }

        @Override
        public io.nosqlbench.paramodel.core.ValidationResult validate() {
            return io.nosqlbench.paramodel.mock.core.MockValidationResult.passed();
        }

        @Override
        public long estimateTrialSpaceSize() {
            return 0;
        }

        @Override
        public List<Axis<?>> currentAxes() {
            return List.of();
        }

        @Override
        public List<Element> currentElements() {
            return List.of();
        }

        @Override
        public java.util.Map<TestPlan.ElementPair, RelationshipType> currentRelationships() {
            return java.util.Map.of();
        }

        @Override
        public java.util.Optional<io.nosqlbench.paramodel.plan.policies.ExecutionPolicies> currentPolicies() {
            return java.util.Optional.empty();
        }

        @Override
        public TestPlan build() {
            return delegate.build();
        }

        @Override
        public TestPlanBuilder reset() {
            delegate = MockTestPlan.builder();
            return this;
        }
    }
}
