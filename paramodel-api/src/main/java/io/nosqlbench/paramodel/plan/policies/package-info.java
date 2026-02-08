///
/// Execution policy contracts defining retry strategies and error handling behavior.
///
/// ## Overview
///
/// Execution policies control **how** the system responds to failures, timeouts,
/// and other exceptional conditions. Policies are:
/// - **Declared in TestPlan**: Policies are part of the plan specification
/// - **Compiled into ExecutionPlan**: Policies become immutable execution rules
/// - **Enforced at runtime**: Executor follows policies without interpretation
///
/// ## Policy Categories
///
/// ```
/// ExecutionPolicies
/// ├── RetryPolicy
/// │   └── When and how to retry failed operations
/// │
/// ├── TimeoutPolicy
/// │   └── Maximum execution times
/// │
/// ├── ErrorHandlingPolicy
/// │   └── How to handle different error types
/// │
/// └── InterventionPolicy
///     └── User intervention controls (pause, stop)
/// ```
///
/// ## Retry Policies
///
/// Control retry behavior for failed operations:
///
/// ```
/// RetryPolicy
/// ├── maxAttempts: int
/// │   └── Maximum total attempts (1 = no retries)
/// │
/// ├── backoffStrategy: BackoffStrategy
/// │   └── How to space retry attempts
/// │
/// └── retryableErrors: Set<ErrorType>
///     └── Which error types are retryable
/// ```
///
/// ## Backoff Strategies
///
/// ```
/// BackoffStrategy         Delay Pattern
/// ─────────────────────────────────────────────────────
/// IMMEDIATE               0s, 0s, 0s, ...
/// FIXED(d)                d, d, d, ...
/// LINEAR(d)               d, 2d, 3d, 4d, ...
/// EXPONENTIAL(base, d)    d, d*base, d*base², ...
/// ```
///
/// ## Example: Retry Policies
///
/// ```java
/// // No retries (fail fast)
/// RetryPolicy noRetry = RetryPolicy.none();
///
/// // Fixed retries with immediate retry
/// RetryPolicy immediate = RetryPolicy.builder()
///     .maxAttempts(3)
///     .backoff(BackoffStrategy.immediate())
///     .retryableErrors(ErrorType.NETWORK, ErrorType.TIMEOUT)
///     .build();
///
/// // Exponential backoff
/// RetryPolicy exponential = RetryPolicy.builder()
///     .maxAttempts(5)
///     .backoff(BackoffStrategy.exponential(2.0, Duration.ofSeconds(1)))
///     // Delays: 1s, 2s, 4s, 8s
///     .retryableErrors(ErrorType.NETWORK, ErrorType.TIMEOUT)
///     .build();
/// ```
///
/// ## Timeout Policies
///
/// Set maximum execution times:
///
/// ```java
/// TimeoutPolicy policy = TimeoutPolicy.builder()
///     .trialTimeout(Duration.ofMinutes(5))        // Per trial
///     .elementStartTimeout(Duration.ofMinutes(2)) // Element startup
///     .runTimeout(Duration.ofHours(24))           // Entire run
///     .build();
/// ```
///
/// ## Error Handling Policies
///
/// Define behavior for different error types:
///
/// ```java
/// ErrorHandlingPolicy policy = ErrorHandlingPolicy.builder()
///     .onError(ErrorType.TIMEOUT)
///         .retry(3)
///         .then(ErrorAction.FAIL_TRIAL)
///     .onError(ErrorType.VALIDATION)
///         .action(ErrorAction.SKIP_TRIAL)
///     .onError(ErrorType.RESOURCE_UNAVAILABLE)
///         .retry(10)
///         .then(ErrorAction.PAUSE_RUN)
///     .build();
/// ```
///
/// ## Intervention Policies
///
/// Control how user interventions behave:
///
/// ```java
/// InterventionPolicy policy = InterventionPolicy.builder()
///     .pauseMode(PauseMode.AFTER_ACTIVE_TRIALS)  // Wait for trials to finish
///     .stopMode(StopMode.GRACEFUL)                // Clean shutdown
///     .allowEmergencyStop(true)                   // Allow immediate stop
///     .build();
/// ```
///
/// ## Policy Compilation
///
/// Policies are compiled into ExecutionPlan:
///
/// ```
/// TestPlan (with policies)
///       ↓ commit()
/// ExecutionPlan
///   ├── Every atomic step has retry policy
///   ├── Every barrier has timeout
///   ├── Error handlers baked in
///   └── Intervention controls defined
/// ```
///
/// ## Policy Immutability
///
/// Once TestPlan is committed:
/// - Policies become immutable
/// - Cannot be changed during execution
/// - Any change requires new TestPlan version
///
/// @see io.nosqlbench.paramodel.plan.TestPlan
/// @see io.nosqlbench.paramodel.plan.ExecutionPlan
/// @since 0.1.0
///
package io.nosqlbench.paramodel.plan.policies;
