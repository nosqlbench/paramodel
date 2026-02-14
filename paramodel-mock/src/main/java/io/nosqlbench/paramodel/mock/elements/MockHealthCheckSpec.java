package io.nosqlbench.paramodel.mock.elements;

import io.nosqlbench.paramodel.elements.Element;

import java.time.Duration;
import java.util.Objects;

///
/// Simple implementation of {@link Element.HealthCheckSpec} for testing.
///
/// The host system owns the health check mechanism (protocol, endpoint,
/// acceptance criteria). This mock only carries the timing parameters
/// that paramodel needs for coordination.
///
/// ## Usage
///
/// ```java
/// // Health check with default timeout
/// HealthCheckSpec hc = MockHealthCheckSpec.withTimeout(Duration.ofSeconds(30));
///
/// // Fully customized health check
/// HealthCheckSpec custom = new MockHealthCheckSpec(Duration.ofSeconds(60), 5, Duration.ofSeconds(10));
/// ```
///
/// @see Element.HealthCheckSpec
/// @since 0.1.0
///
public class MockHealthCheckSpec implements Element.HealthCheckSpec {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final Duration DEFAULT_RETRY_INTERVAL = Duration.ofSeconds(5);

    private final Duration timeout;
    private final int maxRetries;
    private final Duration retryInterval;

    ///
    /// Creates a health check specification with the given timing configuration.
    ///
    /// @param timeout       maximum time to wait for health check to pass
    /// @param maxRetries    maximum number of retry attempts
    /// @param retryInterval interval between retry attempts
    ///
    public MockHealthCheckSpec(Duration timeout, int maxRetries, Duration retryInterval) {
        this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must be >= 0, got " + maxRetries);
        }
        this.maxRetries = maxRetries;
        this.retryInterval = Objects.requireNonNull(retryInterval, "retryInterval must not be null");
    }

    ///
    /// Creates a health check specification with the given timeout and default retry settings.
    ///
    /// @param timeout maximum time to wait for health check to pass
    /// @return health check specification with default retries and interval
    ///
    public static MockHealthCheckSpec withTimeout(Duration timeout) {
        return new MockHealthCheckSpec(timeout, DEFAULT_MAX_RETRIES, DEFAULT_RETRY_INTERVAL);
    }

    @Override
    public Duration timeout() {
        return timeout;
    }

    @Override
    public int maxRetries() {
        return maxRetries;
    }

    @Override
    public Duration retryInterval() {
        return retryInterval;
    }

    @Override
    public String toString() {
        return "MockHealthCheckSpec{timeout=" + timeout +
            ", maxRetries=" + maxRetries + ", retryInterval=" + retryInterval + '}';
    }
}
