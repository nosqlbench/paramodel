package io.nosqlbench.paramodel.plan.policies;

import io.nosqlbench.paramodel.plan.ExecutionPlan;

import java.time.Duration;
import java.util.Optional;

///
/// Collection of execution policies defining retry strategies, timeouts, and error handling.
///
/// ## Concept
///
/// {@code ExecutionPolicies} aggregates all policy decisions that affect how
/// trials and elements are executed. These policies are:
/// - **Declared in TestPlan**: Part of plan specification
/// - **Immutable after commitment**: Cannot change during execution
/// - **Enforced by executor**: Runtime follows policies strictly
///
/// ## Policy Structure
///
/// ```
/// ExecutionPolicies
/// ├── trialRetryPolicy: RetryPolicy
/// │   └── How to retry failed trials
/// │
/// ├── elementDeploymentRetryPolicy: RetryPolicy
/// │   └── How to retry element startup failures
/// │
/// ├── trialTimeout: Duration
/// │   └── Maximum time for single trial
/// │
/// ├── elementStartTimeout: Duration
/// │   └── Maximum time for element to become ready
/// │
/// ├── interventionMode: InterventionMode
/// │   └── How pause/stop operations behave
/// │
/// └── partialRunBehavior: PartialRunBehavior
///     └── What to do when some trials fail
/// ```
///
/// ## Default Policies
///
/// Simplica provides sensible defaults:
///
/// ```java
/// ExecutionPolicies defaults = ExecutionPolicies.defaults();
///
/// // Equivalent to:
/// ExecutionPolicies.builder()
///     .trialRetryPolicy(RetryPolicy.builder()
///         .maxAttempts(3)
///         .backoff(BackoffStrategy.exponential(2.0, Duration.ofSeconds(5)))
///         .build())
///     .elementDeploymentRetryPolicy(RetryPolicy.builder()
///         .maxAttempts(5)
///         .backoff(BackoffStrategy.exponential(2.0, Duration.ofSeconds(10)))
///         .build())
///     .trialTimeout(Duration.ofMinutes(30))
///     .elementStartTimeout(Duration.ofMinutes(5))
///     .interventionMode(InterventionMode.AFTER_ACTIVE_TRIALS)
///     .partialRunBehavior(PartialRunBehavior.RETAIN_RESULTS)
///     .build();
/// ```
///
/// ## Usage Example: Custom Policies
///
/// ```java
/// ExecutionPolicies policies = ExecutionPolicies.builder()
///     // Aggressive trial retries
///     .trialRetryPolicy(RetryPolicy.builder()
///         .maxAttempts(5)
///         .backoff(BackoffStrategy.immediate())  // No delay
///         .retryOnError(ErrorType.TIMEOUT)
///         .retryOnError(ErrorType.NETWORK)
///         .build())
///
///     // Conservative element deployment
///     .elementDeploymentRetryPolicy(RetryPolicy.builder()
///         .maxAttempts(10)
///         .backoff(BackoffStrategy.linear(Duration.ofSeconds(30)))
///         .build())
///
///     // Short timeouts for fast feedback
///     .trialTimeout(Duration.ofMinutes(5))
///     .elementStartTimeout(Duration.ofMinutes(2))
///
///     // Pause immediately on user request
///     .interventionMode(InterventionMode.IMMEDIATE)
///
///     // Always retain partial results
///     .partialRunBehavior(PartialRunBehavior.RETAIN_RESULTS)
///
///     .build();
/// ```
///
/// ## Usage Example: Fail-Fast Policies
///
/// ```java
/// ExecutionPolicies failFast = ExecutionPolicies.builder()
///     .trialRetryPolicy(RetryPolicy.none())              // No retries
///     .elementDeploymentRetryPolicy(RetryPolicy.none())  // No retries
///     .trialTimeout(Duration.ofSeconds(30))              // Short timeout
///     .interventionMode(InterventionMode.IMMEDIATE)      // Stop fast
///     .partialRunBehavior(PartialRunBehavior.FAIL_RUN)   // No partial results
///     .build();
/// ```
///
/// ## Usage Example: Resilient Policies
///
/// ```java
/// ExecutionPolicies resilient = ExecutionPolicies.builder()
///     // Many retries with exponential backoff
///     .trialRetryPolicy(RetryPolicy.builder()
///         .maxAttempts(10)
///         .backoff(BackoffStrategy.exponential(2.0, Duration.ofSeconds(1)))
///         .retryOnAnyError()  // Retry all transient errors
///         .build())
///
///     // Long timeouts for slow environments
///     .trialTimeout(Duration.ofHours(1))
///     .elementStartTimeout(Duration.ofMinutes(15))
///
///     // Graceful intervention
///     .interventionMode(InterventionMode.AFTER_ACTIVE_TRIALS)
///
///     // Always keep what we can
///     .partialRunBehavior(PartialRunBehavior.RETAIN_RESULTS)
///
///     .build();
/// ```
///
/// ## Policy Validation
///
/// Policies are validated during TestPlan validation:
///
/// ```java
/// TestPlan plan = TestPlan.builder()
///     // ... axes, elements ...
///     .policies(customPolicies)
///     .build();
///
/// ValidationResult result = planValidator.validate(plan);
/// // Checks:
/// //   - Retry counts reasonable (not too high)
/// //   - Timeouts sensible (not too short/long)
/// //   - Policies internally consistent
/// ```
///
/// ## Policy Application
///
/// Policies apply at different execution levels:
///
/// ```
/// Trial Level:
///   - trialRetryPolicy → How many times to retry failed trial
///   - trialTimeout → Max time for single trial
///
/// Element Level:
///   - elementDeploymentRetryPolicy → Retry element startup
///   - elementStartTimeout → Max time to wait for ready
///
/// Run Level:
///   - interventionMode → How pause/stop behaves
///   - partialRunBehavior → What to do with partial results
/// ```
///
/// ## Policy Compilation
///
/// ExecutionPlan embeds policies in atomic steps:
///
/// ```
/// TestPlan with policies
///       ↓ commit()
/// ExecutionPlan
///   ├── TrialStep(trial, retryPolicy=trialRetryPolicy)
///   ├── ElementStartStep(element, retryPolicy=elementRetryPolicy)
///   ├── Barrier(timeout=elementStartTimeout)
///   └── ...
/// ```
///
/// @see io.nosqlbench.paramodel.plan.TestPlan
/// @see RetryPolicy
/// @see io.nosqlbench.paramodel.plan.ExecutionPlan
/// @since 0.1.0
///
public interface ExecutionPolicies {

    ///
    /// Returns the retry policy for failed trials.
    ///
    /// ## Application
    ///
    /// When a trial fails:
    /// ```
    /// 1. Check attempt number < maxAttempts
    /// 2. Check error type is retryable
    /// 3. Wait according to backoff strategy
    /// 4. Retry trial execution
    /// ```
    ///
    /// ## Example
    ///
    /// ```java
    /// RetryPolicy policy = policies.trialRetryPolicy();
    /// if (trialResult.status().isFailure() &&
    ///     attemptNumber < policy.maxAttempts()) {
    ///     Duration delay = policy.backoff().delayForAttempt(attemptNumber);
    ///     Thread.sleep(delay.toMillis());
    ///     retry(trial, attemptNumber + 1);
    /// }
    /// ```
    ///
    /// @return trial retry policy, never null
    ///
    RetryPolicy trialRetryPolicy();

    ///
    /// Returns the retry policy for element deployment failures.
    ///
    /// ## Application
    ///
    /// When element fails to start/become ready:
    /// ```
    /// 1. Check attempt number < maxAttempts
    /// 2. Tear down failed element
    /// 3. Wait according to backoff strategy
    /// 4. Retry deployment
    /// ```
    ///
    /// ## Example
    ///
    /// ```java
    /// RetryPolicy policy = policies.elementDeploymentRetryPolicy();
    /// for (int attempt = 1; attempt <= policy.maxAttempts(); attempt++) {
    ///     try {
    ///         orchestrator.startElement(element);
    ///         orchestrator.waitUntilReady(element);
    ///         break;  // Success
    ///     } catch (Exception e) {
    ///         if (attempt == policy.maxAttempts()) throw e;
    ///         Duration delay = policy.backoff().delayForAttempt(attempt);
    ///         Thread.sleep(delay.toMillis());
    ///     }
    /// }
    /// ```
    ///
    /// @return element deployment retry policy, never null
    ///
    RetryPolicy elementDeploymentRetryPolicy();

    ///
    /// Returns the maximum time allowed for a single trial execution.
    ///
    /// ## Timeout Enforcement
    ///
    /// ```
    /// Trial starts at T
    ///   ↓
    /// Timeout at T + trialTimeout
    ///   ↓
    /// If still running: Cancel and mark FAILED
    /// ```
    ///
    /// ## Choosing Timeout
    ///
    /// Consider:
    /// - Expected trial duration
    /// - Variance in duration
    /// - Cost of false positives (killing slow but valid trials)
    ///
    /// Rule of thumb: `timeout = mean + 3*stddev`
    ///
    /// @return trial timeout duration, empty for no timeout
    ///
    Optional<Duration> trialTimeout();

    ///
    /// Returns the maximum time to wait for an element to become ready.
    ///
    /// ## Timeout Enforcement
    ///
    /// ```
    /// Element starts at T
    ///   ↓
    /// Health check polling
    ///   ↓
    /// If not ready by T + elementStartTimeout:
    ///   → Mark element START_FAILED
    ///   → Apply elementDeploymentRetryPolicy
    /// ```
    ///
    /// ## Choosing Timeout
    ///
    /// Consider:
    /// - Element startup time (Docker pull, DB init, etc.)
    /// - Network latency
    /// - Resource contention
    ///
    /// Typical values: 2-10 minutes
    ///
    /// @return element start timeout, empty for no timeout
    ///
    Optional<Duration> elementStartTimeout();

    ///
    /// Returns the intervention mode controlling pause/stop behavior.
    ///
    /// ## Intervention Modes
    ///
    /// ```
    /// IMMEDIATE:
    ///   - Pause/stop happens immediately
    ///   - Active trials may be interrupted
    ///   - Fastest response time
    ///
    /// AFTER_ACTIVE_TRIALS:
    ///   - Wait for active trials to complete
    ///   - No new trials started
    ///   - Graceful shutdown
    /// ```
    ///
    /// ## Use Cases
    ///
    /// ```
    /// IMMEDIATE:
    ///   - Emergency situations
    ///   - Cost control (stop NOW)
    ///   - User explicitly cancels
    ///
    /// AFTER_ACTIVE_TRIALS:
    ///   - Normal pause for inspection
    ///   - Preserve partial results
    ///   - Graceful shutdown
    /// ```
    ///
    /// @return intervention mode, never null
    ///
    InterventionMode interventionMode();

    ///
    /// Returns the behavior when a run completes with some failures.
    ///
    /// ## Partial Run Scenarios
    ///
    /// ```
    /// Run with 100 trials:
    ///   - 80 COMPLETED
    ///   - 15 FAILED
    ///   - 5 CANCELLED
    ///
    /// PartialRunBehavior determines:
    ///   - Keep the 80 successful results?
    ///   - Mark run as PARTIAL or FAILED?
    ///   - Allow re-run of only failed trials?
    /// ```
    ///
    /// ## Behaviors
    ///
    /// ```
    /// RETAIN_RESULTS:
    ///   - Keep successful results
    ///   - Mark run PARTIAL
    ///   - Allow idempotent re-run
    ///
    /// FAIL_RUN:
    ///   - Discard all results
    ///   - Mark run FAILED
    ///   - Must re-run all trials
    /// ```
    ///
    /// @return partial run behavior, never null
    ///
    PartialRunBehavior partialRunBehavior();

    ///
    /// Returns default execution policies with sensible defaults.
    ///
    /// ## Defaults
    ///
    /// - Trial retries: 3 attempts, exponential backoff
    /// - Element retries: 5 attempts, exponential backoff
    /// - Trial timeout: 30 minutes
    /// - Element timeout: 5 minutes
    /// - Intervention: After active trials
    /// - Partial runs: Retain results
    ///
    /// @return default policies
    ///
    static ExecutionPolicies defaults() {
        // Implementation provided by concrete class
        throw new UnsupportedOperationException(
            "Default implementation must be provided by implementing class"
        );
    }

    ///
    /// Retry policy specifying retry behavior for failed operations.
    ///
    interface RetryPolicy {
        /// Maximum number of attempts (1 = no retries)
        int maxAttempts();

        /// Backoff strategy for spacing retry attempts
        BackoffStrategy backoff();

        /// Which error types are retryable
        java.util.Set<String> retryableErrors();

        /// No retries (fail immediately)
        static RetryPolicy none() {
            throw new UnsupportedOperationException("Provided by implementation");
        }
    }

    ///
    /// Backoff strategy for spacing retry attempts.
    ///
    interface BackoffStrategy {
        /// Calculate delay for given attempt number (1-based)
        Duration delayForAttempt(int attemptNumber);

        /// Immediate retry (no delay)
        static BackoffStrategy immediate() {
            throw new UnsupportedOperationException("Provided by implementation");
        }

        /// Fixed delay between attempts
        static BackoffStrategy fixed(Duration delay) {
            throw new UnsupportedOperationException("Provided by implementation");
        }

        /// Linear increasing delay: d, 2d, 3d, ...
        static BackoffStrategy linear(Duration baseDelay) {
            throw new UnsupportedOperationException("Provided by implementation");
        }

        /// Exponential backoff: d, d*base, d*base², ...
        static BackoffStrategy exponential(double base, Duration initialDelay) {
            throw new UnsupportedOperationException("Provided by implementation");
        }
    }

    ///
    /// User intervention mode for pause/stop operations.
    ///
    enum InterventionMode {
        /// Pause/stop immediately, interrupt active trials
        IMMEDIATE,

        /// Wait for active trials to complete before pausing/stopping
        AFTER_ACTIVE_TRIALS
    }

    ///
    /// Behavior when run completes with partial success.
    ///
    enum PartialRunBehavior {
        /// Retain successful results, mark run PARTIAL, allow re-run of failures
        RETAIN_RESULTS,

        /// Discard all results, mark run FAILED, must re-run all trials
        FAIL_RUN
    }

    ///
    /// Builder for creating ExecutionPolicies instances.
    ///
    interface Builder {
        /// Sets the trial retry policy
        Builder trialRetryPolicy(RetryPolicy policy);

        /// Sets the element deployment retry policy
        Builder elementDeploymentRetryPolicy(RetryPolicy policy);

        /// Sets the trial timeout
        Builder trialTimeout(Duration timeout);

        /// Sets the element start timeout
        Builder elementStartTimeout(Duration timeout);

        /// Sets the intervention mode
        Builder interventionMode(InterventionMode mode);

        /// Sets the partial run behavior
        Builder partialRunBehavior(PartialRunBehavior behavior);

        /// Builds the ExecutionPolicies instance
        ExecutionPolicies build();
    }
}
