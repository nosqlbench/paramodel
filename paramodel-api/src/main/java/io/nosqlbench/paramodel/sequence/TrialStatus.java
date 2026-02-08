package io.nosqlbench.paramodel.sequence;

///
/// Execution status of a trial.
///
/// ## Concept
///
/// {@code TrialStatus} tracks where a trial is in its lifecycle from
/// pending execution to final outcome.
///
/// ## State Diagram
///
/// ```
///        PENDING
///           ↓
///      IN_PROGRESS
///           ↓
///      ┌────┴────┬────────┬────────┐
///      ↓         ↓        ↓        ↓
///  COMPLETED  FAILED  SKIPPED  CANCELLED
/// ```
///
/// ## State Semantics
///
/// ```
/// PENDING:      Trial created, not yet started
/// IN_PROGRESS:  Trial currently executing
/// COMPLETED:    Trial finished successfully
/// FAILED:       Trial finished with errors
/// SKIPPED:      Trial intentionally not executed
/// CANCELLED:    Trial execution stopped before completion
/// ```
///
/// ## Terminal States
///
/// ```
/// Terminal (execution complete):
///   - COMPLETED
///   - FAILED
///   - SKIPPED
///   - CANCELLED
///
/// Non-Terminal (may transition):
///   - PENDING
///   - IN_PROGRESS
/// ```
///
/// ## Status Transitions
///
/// ### Happy Path
/// ```
/// PENDING → IN_PROGRESS → COMPLETED
/// ```
///
/// ### Failure Path
/// ```
/// PENDING → IN_PROGRESS → FAILED
/// ```
///
/// ### Skip Path
/// ```
/// PENDING → SKIPPED
/// ```
///
/// ### Cancel Path
/// ```
/// PENDING → CANCELLED
/// IN_PROGRESS → CANCELLED
/// ```
///
/// ## Usage Example
///
/// ```java
/// Trial trial = ...;
/// TrialResult result = executor.execute(trial);
///
/// switch (result.status()) {
///     case COMPLETED -> {
///         System.out.println("Success!");
///         processMetrics(result.metrics());
///     }
///     case FAILED -> {
///         System.err.println("Failed: " + result.error().orElse("Unknown"));
///         handleFailure(result);
///     }
///     case SKIPPED -> {
///         System.out.println("Skipped: " + result.skipReason().orElse("No reason"));
///     }
///     case CANCELLED -> {
///         System.out.println("Cancelled by user");
///     }
///     case PENDING, IN_PROGRESS -> {
///         throw new IllegalStateException("Trial not finished");
///     }
/// }
/// ```
///
/// ## Retry Semantics
///
/// Failed trials may be retried:
///
/// ```
/// PENDING → IN_PROGRESS → FAILED → IN_PROGRESS → COMPLETED
///                          ↑______________|
///                             Retry
/// ```
///
/// @see TrialResult
/// @see com.paramodel.api.execution.TrialExecutor
/// @since 0.1.0
///
public enum TrialStatus {
    ///
    /// Trial has been created but not yet started execution.
    ///
    /// ## Characteristics
    ///
    /// - Initial state of all trials
    /// - No execution has been attempted
    /// - May transition to IN_PROGRESS or SKIPPED
    ///
    /// ## Example
    ///
    /// ```java
    /// Sequence seq = builder.build();
    /// for (Trial trial : seq) {
    ///     // All trials start as PENDING
    ///     assert getCurrentStatus(trial) == TrialStatus.PENDING;
    /// }
    /// ```
    ///
    PENDING,

    ///
    /// Trial is currently executing.
    ///
    /// ## Characteristics
    ///
    /// - Execution in progress
    /// - Resources may be allocated
    /// - May transition to COMPLETED, FAILED, or CANCELLED
    ///
    /// ## Example
    ///
    /// ```java
    /// executor.executeAsync(trial, result -> {
    ///     if (result.status() == IN_PROGRESS) {
    ///         // Still running
    ///     }
    /// });
    /// ```
    ///
    IN_PROGRESS,

    ///
    /// Trial finished successfully with valid results.
    ///
    /// ## Characteristics
    ///
    /// - Terminal state (no further transitions)
    /// - Results and metrics available
    /// - Post-conditions satisfied
    ///
    /// ## Example
    ///
    /// ```java
    /// TrialResult result = executor.execute(trial);
    /// if (result.status() == COMPLETED) {
    ///     double accuracy = (Double) result.metrics().get("accuracy");
    ///     System.out.println("Accuracy: " + accuracy);
    /// }
    /// ```
    ///
    COMPLETED,

    ///
    /// Trial finished with errors or unmet post-conditions.
    ///
    /// ## Characteristics
    ///
    /// - Terminal state (unless retry configured)
    /// - Error information available
    /// - Partial results may be available
    ///
    /// ## Failure Types
    ///
    /// - **Execution error**: Exception during trial
    /// - **Timeout**: Trial exceeded time limit
    /// - **Resource error**: Unable to allocate resources
    /// - **Validation error**: Results invalid
    ///
    /// ## Example
    ///
    /// ```java
    /// TrialResult result = executor.execute(trial);
    /// if (result.status() == FAILED) {
    ///     result.error().ifPresent(err -> {
    ///         System.err.println("Trial failed: " + err);
    ///         if (shouldRetry(err)) {
    ///             executor.retry(trial);
    ///         }
    ///     });
    /// }
    /// ```
    ///
    FAILED,

    ///
    /// Trial intentionally not executed.
    ///
    /// ## Characteristics
    ///
    /// - Terminal state
    /// - No execution attempted
    /// - Reason for skipping may be recorded
    ///
    /// ## Skip Reasons
    ///
    /// - **Constraint violation**: Trial doesn't satisfy constraints
    /// - **Dependency failure**: Required trials failed
    /// - **User directive**: Explicitly skipped by user
    /// - **Conditional skip**: Skip rule triggered
    ///
    /// ## Example
    ///
    /// ```java
    /// TrialResult result = executor.execute(trial);
    /// if (result.status() == SKIPPED) {
    ///     String reason = result.skipReason()
    ///         .orElse("Unknown reason");
    ///     System.out.println("Skipped: " + reason);
    /// }
    /// ```
    ///
    SKIPPED,

    ///
    /// Trial execution was cancelled before completion.
    ///
    /// ## Characteristics
    ///
    /// - Terminal state
    /// - Execution started but not finished
    /// - Partial results may be available
    /// - Resources should be cleaned up
    ///
    /// ## Cancellation Triggers
    ///
    /// - **User intervention**: Stop button / signal
    /// - **Timeout**: Global timeout exceeded
    /// - **Emergency stop**: Critical error in other trials
    /// - **Resource pressure**: System overload
    ///
    /// ## Example
    ///
    /// ```java
    /// ExecutorService executor = ...;
    /// Future<TrialResult> future = executor.submit(() ->
    ///     trialExecutor.execute(trial)
    /// );
    ///
    /// // User cancels
    /// if (userClickedStop()) {
    ///     future.cancel(true);  // Interrupt if running
    /// }
    ///
    /// TrialResult result = future.get();
    /// if (result.status() == CANCELLED) {
    ///     System.out.println("Trial was cancelled");
    /// }
    /// ```
    ///
    CANCELLED;

    ///
    /// Checks if this status represents a terminal state.
    ///
    /// Terminal states indicate execution is complete (no further transitions).
    ///
    /// @return true if terminal (COMPLETED, FAILED, SKIPPED, or CANCELLED)
    ///
    public boolean isTerminal() {
        return this != PENDING && this != IN_PROGRESS;
    }

    ///
    /// Checks if this status represents successful completion.
    ///
    /// @return true if COMPLETED
    ///
    public boolean isSuccess() {
        return this == COMPLETED;
    }

    ///
    /// Checks if this status represents a failure.
    ///
    /// @return true if FAILED
    ///
    public boolean isFailure() {
        return this == FAILED;
    }
}
