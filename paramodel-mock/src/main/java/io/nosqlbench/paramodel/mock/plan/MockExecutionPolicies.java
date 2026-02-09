package io.nosqlbench.paramodel.mock.plan;

import io.nosqlbench.paramodel.plan.policies.ExecutionPolicies;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

///
/// Simple execution policies implementation for testing.
///
/// Returns sensible defaults for all policy fields: 3 trial retries,
/// 5 element deployment retries, 30-minute trial timeout, 5-minute
/// element start timeout, after-active-trials intervention mode, and
/// retain-results partial run behavior.
///
/// ## Usage
///
/// ```java
/// ExecutionPolicies policies = MockExecutionPolicies.defaults();
/// ```
///
/// @see ExecutionPolicies
/// @since 0.1.0
///
public class MockExecutionPolicies implements ExecutionPolicies {
    private final RetryPolicy trialRetryPolicy;
    private final RetryPolicy elementDeploymentRetryPolicy;
    private final Duration trialTimeout;
    private final Duration elementStartTimeout;
    private final InterventionMode interventionMode;
    private final PartialRunBehavior partialRunBehavior;

    private MockExecutionPolicies(RetryPolicy trialRetryPolicy,
                                  RetryPolicy elementDeploymentRetryPolicy,
                                  Duration trialTimeout,
                                  Duration elementStartTimeout,
                                  InterventionMode interventionMode,
                                  PartialRunBehavior partialRunBehavior) {
        this.trialRetryPolicy = trialRetryPolicy;
        this.elementDeploymentRetryPolicy = elementDeploymentRetryPolicy;
        this.trialTimeout = trialTimeout;
        this.elementStartTimeout = elementStartTimeout;
        this.interventionMode = interventionMode;
        this.partialRunBehavior = partialRunBehavior;
    }

    @Override
    public RetryPolicy trialRetryPolicy() {
        return trialRetryPolicy;
    }

    @Override
    public RetryPolicy elementDeploymentRetryPolicy() {
        return elementDeploymentRetryPolicy;
    }

    @Override
    public Optional<Duration> trialTimeout() {
        return Optional.ofNullable(trialTimeout);
    }

    @Override
    public Optional<Duration> elementStartTimeout() {
        return Optional.ofNullable(elementStartTimeout);
    }

    @Override
    public InterventionMode interventionMode() {
        return interventionMode;
    }

    @Override
    public PartialRunBehavior partialRunBehavior() {
        return partialRunBehavior;
    }

    ///
    /// Creates default execution policies with sensible values.
    ///
    /// @return default policies
    ///
    public static MockExecutionPolicies defaults() {
        return new MockExecutionPolicies(
            new MockRetryPolicy(3),
            new MockRetryPolicy(5),
            Duration.ofMinutes(30),
            Duration.ofMinutes(5),
            InterventionMode.AFTER_ACTIVE_TRIALS,
            PartialRunBehavior.RETAIN_RESULTS
        );
    }

    ///
    /// Simple retry policy with configurable max attempts.
    ///
    private static class MockRetryPolicy implements RetryPolicy {
        private final int maxAttempts;

        MockRetryPolicy(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        @Override
        public int maxAttempts() {
            return maxAttempts;
        }

        @Override
        public BackoffStrategy backoff() {
            return new MockBackoffStrategy();
        }

        @Override
        public Set<String> retryableErrors() {
            return Set.of("TimeoutException", "NetworkException");
        }
    }

    ///
    /// Simple backoff strategy returning linearly increasing delays.
    ///
    private static class MockBackoffStrategy implements BackoffStrategy {
        @Override
        public Duration delayForAttempt(int attemptNumber) {
            return Duration.ofSeconds(attemptNumber);
        }
    }
}
