package io.nosqlbench.paramodel.tck;

import io.nosqlbench.paramodel.core.*;
import io.nosqlbench.paramodel.sequence.*;
import io.nosqlbench.paramodel.plan.*;

/**
 * Provider interface for supplying implementation instances to TCK tests.
 *
 * Implementations under test must provide a concrete implementation of this
 * interface to enable TCK validation.
 *
 * Example:
 * ```java
 * public class MockImplementationProvider implements ImplementationProvider {
 *     @Override
 *     public <T> Parameter<T> createParameter(String name, Domain<T> domain) {
 *         return new MockParameter<>(name, domain);
 *     }
 *     // ... implement remaining methods
 * }
 * ```
 */
public interface ImplementationProvider {

    // Core contracts
    <T> Parameter<T> createParameter(String name, Domain<T> domain);
    <T> Domain<T> createDiscreteDomain(Iterable<T> values);
    <T> Value<T> createValue(T value, String parameterName);
    ValidationResult createValidationResult(boolean valid, String message);

    // Sequence contracts
    Trial createTrial(String id);
    TrialBuilder createTrialBuilder();
    Sequence createSequence(Iterable<Trial> trials);
    SequenceBuilder createSequenceBuilder();

    // Plan contracts
    TestPlan createTestPlan();
    TestPlanBuilder createTestPlanBuilder();
    Axis createAxis(String name, Iterable<Element> elements);
    Element createElement(String parameterName);
    ExecutionPlan createExecutionPlan(TestPlan testPlan);
    AtomicStep createAtomicStep(String id, Trial trial);
    ExecutionGraph createExecutionGraph();
}
