package io.nosqlbench.paramodel.tck.mock;

import io.nosqlbench.paramodel.tck.ImplementationProvider;
import io.nosqlbench.paramodel.tck.parameters.ValidationResultTCK;

/**
 * Validates mock ValidationResult implementation against TCK.
 */
public class MockValidationResultTest extends ValidationResultTCK {
    @Override
    protected ImplementationProvider getProvider() {
        return new MockImplementationProvider();
    }
}
