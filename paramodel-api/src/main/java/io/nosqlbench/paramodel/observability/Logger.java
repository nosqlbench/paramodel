package io.nosqlbench.paramodel.observability;

import java.util.Map;

///
/// # Logger
///
/// Provides structured logging with execution context for debugging and auditing.
/// The logger automatically correlates log entries with executions, trials, and steps.
///
/// ## Log Levels
///
/// ```
/// TRACE - Very detailed information for deep debugging
/// DEBUG - Detailed information for debugging
/// INFO  - Informational messages about normal operation
/// WARN  - Warning messages about potential issues
/// ERROR - Error messages about failures
/// FATAL - Critical errors that cause execution to stop
/// ```
///
/// ## Structured Logging
///
/// ```
/// Structured Log Format:
///
/// {
///   "timestamp": "2025-01-15T14:30:45.123Z",
///   "level": "ERROR",
///   "message": "Trial execution failed",
///   "context": {
///     "execution_id": "exec_xyz789",
///     "trial_id": "trial_42",
///     "step_id": "execute_trial_42"
///   },
///   "fields": {
///     "error_type": "TimeoutException",
///     "duration_ms": 300000
///   },
///   "tags": {
///     "cache_size": "256",
///     "concurrency": "50"
///   }
/// }
/// ```
///
/// ## Usage Examples
///
/// ### Example 1: Basic Logging
///
/// ```java
/// Logger logger = Logger.create();
///
/// logger.info("Execution started");
/// logger.error("Trial failed: {}", error.getMessage());
/// logger.warn("High memory usage: {} GB", memoryUsage);
/// ```
///
/// ### Example 2: Structured Logging
///
/// ```java
/// logger.info("Trial completed",
///     Map.of(
///         "trial_id", trial.id(),
///         "duration_ms", duration.toMillis(),
///         "status", result.status()
///     ));
/// ```
///
/// ### Example 3: Context Logging
///
/// ```java
/// LogContext context = logger.withContext()
///     .executionId(executionId)
///     .trialId(trialId)
///     .build();
///
/// context.info("Starting trial");
/// context.debug("Deploying elements");
/// context.error("Execution failed", error);
/// ```
///
public interface Logger {

    ///
    /// Creates a logger with default configuration.
    ///
    /// @return Logger instance
    ///
    static Logger create() {
        throw new UnsupportedOperationException(
            "Logger.create() requires a concrete implementation");
    }

    ///
    /// Logs at TRACE level.
    ///
    /// @param message Log message
    /// @param args Message arguments
    ///
    void trace(String message, Object... args);

    ///
    /// Logs at DEBUG level.
    ///
    /// @param message Log message
    /// @param args Message arguments
    ///
    void debug(String message, Object... args);

    ///
    /// Logs at INFO level.
    ///
    /// @param message Log message
    /// @param args Message arguments
    ///
    void info(String message, Object... args);

    ///
    /// Logs at WARN level.
    ///
    /// @param message Log message
    /// @param args Message arguments
    ///
    void warn(String message, Object... args);

    ///
    /// Logs at ERROR level.
    ///
    /// @param message Log message
    /// @param args Message arguments
    ///
    void error(String message, Object... args);

    ///
    /// Logs at ERROR level with exception.
    ///
    /// @param message Log message
    /// @param error Exception
    ///
    void error(String message, Throwable error);

    ///
    /// Logs at FATAL level.
    ///
    /// @param message Log message
    /// @param args Message arguments
    ///
    void fatal(String message, Object... args);

    ///
    /// Logs with structured fields.
    ///
    /// @param level Log level
    /// @param message Log message
    /// @param fields Structured fields
    ///
    void log(LogLevel level, String message, Map<String, Object> fields);

    ///
    /// Creates a logger with context.
    ///
    /// @return Context builder
    ///
    LogContextBuilder withContext();

    ///
    /// Log level.
    ///
    enum LogLevel {
        TRACE,
        DEBUG,
        INFO,
        WARN,
        ERROR,
        FATAL
    }

    ///
    /// Log context builder.
    ///
    interface LogContextBuilder {
        LogContextBuilder executionId(String executionId);
        LogContextBuilder trialId(String trialId);
        LogContextBuilder stepId(String stepId);
        LogContextBuilder tag(String key, String value);
        LogContext build();
    }

    ///
    /// Log context.
    ///
    interface LogContext {
        void trace(String message, Object... args);
        void debug(String message, Object... args);
        void info(String message, Object... args);
        void warn(String message, Object... args);
        void error(String message, Object... args);
        void error(String message, Throwable error);
        void fatal(String message, Object... args);
    }
}
