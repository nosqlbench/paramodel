package io.nosqlbench.paramodel.mock.elements;

import io.nosqlbench.paramodel.elements.Element;

import java.time.Duration;
import java.util.Objects;

///
/// Simple implementation of {@link Element.HealthCheckSpec} for testing.
///
/// Provides factory methods for common health check types (HTTP, TCP)
/// with sensible defaults for timeout, retries, and retry interval.
///
/// ## Usage
///
/// ```java
/// // HTTP health check with default timeout
/// HealthCheckSpec http = MockHealthCheckSpec.http(Duration.ofSeconds(30));
///
/// // TCP health check with custom timeout
/// HealthCheckSpec tcp = MockHealthCheckSpec.tcp(Duration.ofSeconds(15));
///
/// // Fully customized health check
/// HealthCheckSpec custom = new MockHealthCheckSpec("COMMAND", Duration.ofSeconds(60), 5, Duration.ofSeconds(10));
/// ```
///
/// @see Element.HealthCheckSpec
/// @since 0.1.0
///
public class MockHealthCheckSpec implements Element.HealthCheckSpec {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final Duration DEFAULT_RETRY_INTERVAL = Duration.ofSeconds(5);

    private final String type;
    private final Duration timeout;
    private final int maxRetries;
    private final Duration retryInterval;

    ///
    /// Creates a health check specification with the given configuration.
    ///
    /// @param type          health check type (e.g. "HTTP", "TCP", "COMMAND", "CUSTOM")
    /// @param timeout       maximum time to wait for health check to pass
    /// @param maxRetries    maximum number of retry attempts
    /// @param retryInterval interval between retry attempts
    ///
    public MockHealthCheckSpec(String type, Duration timeout, int maxRetries, Duration retryInterval) {
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must be >= 0, got " + maxRetries);
        }
        this.maxRetries = maxRetries;
        this.retryInterval = Objects.requireNonNull(retryInterval, "retryInterval must not be null");
    }

    ///
    /// Creates an HTTP health check with the given timeout and default retry settings.
    ///
    /// @param timeout maximum time to wait for HTTP endpoint to respond
    /// @return HTTP health check specification
    ///
    public static MockHealthCheckSpec http(Duration timeout) {
        return new MockHealthCheckSpec("HTTP", timeout, DEFAULT_MAX_RETRIES, DEFAULT_RETRY_INTERVAL);
    }

    ///
    /// Creates a TCP health check with the given timeout and default retry settings.
    ///
    /// @param timeout maximum time to wait for TCP port to be open
    /// @return TCP health check specification
    ///
    public static MockHealthCheckSpec tcp(Duration timeout) {
        return new MockHealthCheckSpec("TCP", timeout, DEFAULT_MAX_RETRIES, DEFAULT_RETRY_INTERVAL);
    }

    @Override
    public String type() {
        return type;
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
        return "MockHealthCheckSpec{type='" + type + "', timeout=" + timeout +
            ", maxRetries=" + maxRetries + ", retryInterval=" + retryInterval + '}';
    }
}
