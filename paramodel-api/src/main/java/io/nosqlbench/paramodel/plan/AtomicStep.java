package io.nosqlbench.paramodel.plan;

import io.nosqlbench.paramodel.sequence.TrialResult;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

///
/// # AtomicStep
///
/// Represents a single, indivisible unit of work within an {@link ExecutionPlan}.
/// Atomic steps are the fundamental building blocks of execution, designed to be
/// independently executable, checkpointable, and recoverable.
///
/// ## Atomicity Guarantees
///
/// An atomic step is "atomic" in the sense that it:
/// - Executes as a single logical unit
/// - Either completes fully or fails completely (no partial completion)
/// - Can be checkpointed before and after (not during)
/// - Can be retried independently if it fails
/// - Has well-defined preconditions and postconditions
///
/// ```
/// Atomic Step Lifecycle:
///
/// PENDING → IN_PROGRESS → {COMPLETED, FAILED}
///    │          │              │
///    │          └─→ RETRYING ──┘
///    │                 │
///    └─→ SKIPPED      (retry logic)
///
/// State Transitions:
///   - PENDING: Step is queued, waiting for dependencies
///   - IN_PROGRESS: Step is actively executing
///   - RETRYING: Step failed, retry policy active
///   - COMPLETED: Step finished successfully
///   - FAILED: Step failed after exhausting retries
///   - SKIPPED: Step bypassed due to policy or conditions
/// ```
///
/// ## Step Types
///
/// Seven step types support the complete execution lifecycle:
///
/// ```
/// Step Type Hierarchy:
///
/// AtomicStep
///   │
///   ├─ DeployElement       (Provision infrastructure)
///   │    ├─ element_id
///   │    ├─ configuration
///   │    └─ health_checks
///   │
///   ├─ NotifyTrialStart    (Trial lifecycle: start notification)
///   │    ├─ trial_id
///   │    └─ element_names
///   │
///   ├─ TrialStep           (Operative action of the Trial Element)
///   │    ├─ trial_id
///   │    ├─ element_bindings
///   │    └─ trial_logic
///   │
///   ├─ AwaitElement        (Natural completion of COMMAND element)
///   │    ├─ element_id
///   │    └─ trial_id
///   │
///   ├─ NotifyTrialEnd      (Trial lifecycle: end notification)
///   │    ├─ trial_id
///   │    ├─ element_names
///   │    └─ planned_reason
///   │
///   ├─ TeardownElement     (Clean up resources)
///   │    ├─ element_id
///   │    └─ artifact_collection
///   │
///   ├─ BarrierSync         (Synchronization point)
///   │    ├─ barrier_id
///   │    └─ dependencies
///   │
///   └─ CheckpointState     (Persist execution state)
///        ├─ checkpoint_id
///        └─ state_snapshot
/// ```
///
/// ## Dependency Model
///
/// Steps declare explicit dependencies that determine execution order:
///
/// ```
/// Dependency Graph Example:
///
///   deploy_db (Step 1)
///       │
///       ├─→ deploy_cache (Step 2)
///       │       │
///       │       ├─→ trial_step_1 (Step 3)
///       │       └─→ trial_step_2 (Step 4)
///       │
///       └─→ barrier_db_ready (Step 5)
///               │
///               └─→ teardown_db (Step 6)
///
/// Dependency Rules:
///   - Step 2 cannot start until Step 1 completes
///   - Steps 3 and 4 can run in parallel (both depend only on Step 2)
///   - Step 6 cannot start until Step 5 completes
///   - Step 5 depends on all trials completing
/// ```
///
/// ## Idempotency and Retry
///
/// Atomic steps should be designed for idempotency to support retry logic:
///
/// ```
/// Idempotency Pattern:
///
/// DeployElement("database", config):
///   1. Check if element already exists
///   2. If exists and healthy → SUCCESS (idempotent)
///   3. If exists but unhealthy → teardown and redeploy
///   4. If not exists → deploy fresh
///   5. Wait for health checks
///   6. Return SUCCESS or FAILURE
///
/// TrialStep(trial_id, bindings):
///   1. Check if trial already performed (result exists)
///   2. If result exists → verify integrity
///   3. If valid result → SUCCESS (idempotent)
///   4. If no result or corrupt → perform trial action
///   5. Store result atomically
///   6. Return SUCCESS or FAILURE
///
/// Retry Strategy:
///   Attempt 1: Run
///   Attempt 2 (if failed): Wait 1s, retry
///   Attempt 3 (if failed): Wait 2s, retry
///   Attempt 4 (if failed): Wait 4s, retry
///   After max attempts: FAILED (final)
/// ```
///
/// ## Resource Binding
///
/// Steps capture resource requirements and bindings:
///
/// ```
/// Resource Binding Example:
///
/// TrialStep("trial_42", bindings={
///   "database": "db_instance_prod_1",
///   "cache": "cache_instance_10",
///   "app": "app_instance_42"
/// })
///
/// Resource Allocation:
///   cpu: 2.0 cores
///   memory: 4096 MB
///   network: 100 Mbps
///   storage: 10 GB temporary
///
/// Before execution:
///   - Verify all bindings resolve to healthy instances
///   - Check resource availability
///   - Acquire resource locks
///
/// After execution:
///   - Release resource locks
///   - Collect metrics
///   - Persist results
/// ```
///
/// ## Execution Context
///
/// Each step executes within a context providing access to shared state:
///
/// ```
/// Execution Context Contents:
///
/// ExecutionContext {
///   execution_plan_id: "exec_plan_abc123"
///   test_plan_fingerprint: "sha256:def456..."
///   environment: {
///     region: "us-west-2"
///     account: "prod"
///     namespace: "study-42"
///   }
///   element_registry: {
///     "database": ElementInstance(...)
///     "cache": ElementInstance(...)
///   }
///   result_store: ResultRepository(...)
///   metric_collector: MetricsCollector(...)
/// }
///
/// Context Usage:
///   - Steps read element bindings from registry
///   - Steps write results to result_store
///   - Steps emit metrics to metric_collector
///   - Steps access shared configuration
/// ```
///
/// ## Progress and Observability
///
/// Steps emit events for monitoring and debugging:
///
/// ```
/// Event Stream:
///
/// StepStarted(step_id="deploy_db", timestamp=T0)
///   ├─ progress: 0%
///   └─ message: "Provisioning database instance"
///
/// StepProgress(step_id="deploy_db", timestamp=T1)
///   ├─ progress: 30%
///   └─ message: "Instance created, starting health checks"
///
/// StepProgress(step_id="deploy_db", timestamp=T2)
///   ├─ progress: 80%
///   └─ message: "Health checks passing, waiting for warmup"
///
/// StepCompleted(step_id="deploy_db", timestamp=T3, duration=45s)
///   ├─ progress: 100%
///   ├─ message: "Database ready"
///   └─ metrics: {cpu_seconds: 90, memory_mb_seconds: 184320}
/// ```
///
/// ## Usage Examples
///
/// ### Example 1: Inspecting Step Details
///
/// ```java
/// ExecutionPlan plan = testPlan.commit();
/// List<AtomicStep> steps = plan.steps();
///
/// for (AtomicStep step : steps) {
///     System.out.printf("Step: %s [%s]%n", step.id(), step.type());
///     System.out.printf("  Description: %s%n", step.description());
///     System.out.printf("  Estimated duration: %s%n",
///         step.estimatedDuration().orElse(Duration.ZERO));
///     System.out.printf("  Dependencies: %d%n", step.dependencies().size());
///
///     if (step instanceof AtomicStep.TrialStep trialStep) {
///         System.out.printf("  Trial ID: %s%n", trialStep.trialId());
///         System.out.printf("  Element bindings: %s%n",
///             trialStep.elementBindings());
///     }
/// }
/// ```
///
/// ### Example 2: Filtering Steps by Type
///
/// ```java
/// List<AtomicStep> deploySteps = plan.steps().stream()
///     .filter(s -> s.type() == StepType.DEPLOY_ELEMENT)
///     .toList();
///
/// List<AtomicStep> trialSteps = plan.steps().stream()
///     .filter(s -> s.type() == StepType.TRIAL_STEP)
///     .toList();
///
/// System.out.printf("Deployment steps: %d%n", deploySteps.size());
/// System.out.printf("Trial steps: %d%n", trialSteps.size());
///
/// // Estimate deployment time
/// Duration totalDeployTime = deploySteps.stream()
///     .map(s -> s.estimatedDuration().orElse(Duration.ZERO))
///     .reduce(Duration.ZERO, Duration::plus);
/// ```
///
/// ### Example 3: Analyzing Dependencies
///
/// ```java
/// AtomicStep step = plan.steps().get(5);
///
/// System.out.printf("Step: %s%n", step.id());
/// System.out.printf("Direct dependencies: %d%n", step.dependencies().size());
///
/// // Find all transitive dependencies (recursive)
/// Set<String> allDeps = findTransitiveDependencies(step, plan);
/// System.out.printf("Transitive dependencies: %d%n", allDeps.size());
///
/// // Find steps that depend on this step
/// List<AtomicStep> dependents = plan.steps().stream()
///     .filter(s -> s.dependencies().contains(step.id()))
///     .toList();
/// System.out.printf("Dependent steps: %d%n", dependents.size());
/// ```
///
/// ### Example 4: Custom Step Execution with Retry
///
/// ```java
/// AtomicStep step = /* ... */;
/// RetryPolicy retryPolicy = RetryPolicy.exponentialBackoff(3);
/// int attempt = 0;
///
/// while (attempt < retryPolicy.maxAttempts()) {
///     try {
///         attempt++;
///         System.out.printf("Attempt %d: Running %s%n", attempt, step.id());
///
///         StepResult result = step.execute(executionContext);
///
///         if (result.isSuccess()) {
///             System.out.printf("Success after %d attempt(s)%n", attempt);
///             break;
///         } else {
///             System.err.printf("Failed: %s%n", result.error().getMessage());
///         }
///     } catch (Exception e) {
///         System.err.printf("Exception on attempt %d: %s%n", attempt, e.getMessage());
///     }
///
///     if (attempt < retryPolicy.maxAttempts()) {
///         Duration backoff = retryPolicy.backoffDelay(attempt);
///         System.out.printf("Retrying in %s...%n", backoff);
///         Thread.sleep(backoff.toMillis());
///     }
/// }
/// ```
///
/// ### Example 5: Critical Path Analysis
///
/// ```java
/// // Find critical path (longest dependency chain)
/// List<AtomicStep> criticalPath = plan.executionGraph().criticalPath();
///
/// System.out.println("Critical Path:");
/// Duration totalDuration = Duration.ZERO;
///
/// for (int i = 0; i < criticalPath.size(); i++) {
///     AtomicStep step = criticalPath.get(i);
///     Duration stepDuration = step.estimatedDuration().orElse(Duration.ZERO);
///     totalDuration = totalDuration.plus(stepDuration);
///
///     System.out.printf("%d. %s (%s) - %s%n",
///         i + 1,
///         step.id(),
///         step.type(),
///         stepDuration);
/// }
///
/// System.out.printf("Total critical path duration: %s%n", totalDuration);
/// System.out.printf("Speedup from parallelism: %.2fx%n",
///     totalDuration.toMillis() / (double) plan.estimatedDuration().orElse(Duration.ZERO).toMillis());
/// ```
///
/// ## Contract Requirements
///
/// ### Immutability
/// - AtomicStep instances MUST be immutable
/// - All collections MUST be unmodifiable
///
/// ### Execution Semantics
/// - Steps MUST be idempotent where possible
/// - Steps MUST complete or fail entirely (no partial completion)
/// - Steps MUST respect dependencies (execution order)
///
/// ### Resource Management
/// - Steps MUST declare resource requirements accurately
/// - Steps MUST release resources after completion
/// - Steps MUST handle resource unavailability gracefully
///
/// ### Observability
/// - Steps SHOULD emit progress events during execution
/// - Steps MUST report final success or failure status
/// - Steps SHOULD capture execution metrics (duration, resource usage)
///
/// ### Error Handling
/// - Steps MUST provide descriptive error messages on failure
/// - Steps SHOULD support retry with exponential backoff
/// - Steps MUST distinguish transient from permanent failures
///
/// @see ExecutionPlan
/// @see ExecutionGraph
/// @see Barrier
///
public sealed interface AtomicStep
    permits AtomicStep.DeployElement,
            AtomicStep.TrialStep,
            AtomicStep.AwaitElement,
            AtomicStep.TeardownElement,
            AtomicStep.BarrierSync,
            AtomicStep.CheckpointState,
            AtomicStep.NotifyTrialStart,
            AtomicStep.NotifyTrialEnd {

    ///
    /// Returns the unique identifier for this step.
    ///
    /// @return Step ID (non-null, unique within execution plan)
    ///
    String id();

    ///
    /// Returns the type of this step.
    ///
    /// @return Step type
    ///
    StepType type();

    ///
    /// Returns a human-readable description of this step.
    ///
    /// @return Step description
    ///
    String description();

    ///
    /// Returns the IDs of steps that must complete before this step can execute.
    ///
    /// @return Dependency step IDs (unmodifiable)
    ///
    List<String> dependencies();

    ///
    /// Returns estimated duration for this step to execute.
    ///
    /// Empty if duration cannot be estimated.
    ///
    /// @return Estimated duration if known
    ///
    Optional<Duration> estimatedDuration();

    ///
    /// Returns resource requirements for executing this step.
    ///
    /// @return Resource requirements
    ///
    ResourceRequirements resourceRequirements();

    ///
    /// Returns retry policy for this step if it fails.
    ///
    /// Empty if step should not be retried.
    ///
    /// @return Retry policy if applicable
    ///
    Optional<RetryPolicy> retryPolicy();

    ///
    /// Returns arbitrary metadata attached to this step.
    ///
    /// @return Step metadata (unmodifiable)
    ///
    Map<String, Object> metadata();

    ///
    /// Executes this step within the provided execution context.
    ///
    /// This method should be idempotent where possible, checking for
    /// prior completion and avoiding duplicate work.
    ///
    /// @param context Execution context providing shared state
    /// @return Step execution result
    /// @throws StepExecutionException if step execution fails
    ///
    StepResult execute(ExecutionContext context) throws StepExecutionException;

    ///
    /// Step for deploying an element (infrastructure, service, etc.).
    ///
    /// Health checking is owned by the host system via
    /// {@link io.nosqlbench.paramodel.elements.Element#healthCheck()
    /// Element.healthCheck()} and
    /// {@link io.nosqlbench.paramodel.elements.OperationalStateObservable
    /// OperationalStateObservable}. The deploy step does not carry
    /// health check details; instead, the executor waits for the
    /// element to reach {@code READY} state via state observation.
    ///
    /// @param id Step identifier
    /// @param elementId Element to deploy
    /// @param instanceNumber Monotonically increasing instance number for this element deployment
    /// @param configuration Element configuration (may include parameter bindings)
    /// @param dependencies Prerequisite step IDs
    /// @param estimatedDuration Estimated deployment time
    /// @param resourceRequirements Resource needs
    /// @param retryPolicy Retry strategy on failure
    /// @param metadata Additional metadata
    ///
    record DeployElement(
        String id,
        String elementId,
        int instanceNumber,
        Map<String, Object> configuration,
        List<String> dependencies,
        Optional<Duration> estimatedDuration,
        ResourceRequirements resourceRequirements,
        Optional<RetryPolicy> retryPolicy,
        Map<String, Object> metadata
    ) implements AtomicStep {

        @Override
        public StepType type() {
            return StepType.DEPLOY_ELEMENT;
        }

        @Override
        public String description() {
            return "Deploy element: " + elementId + " #" + instanceNumber;
        }

        @Override
        public StepResult execute(ExecutionContext context) throws StepExecutionException {
            throw new UnsupportedOperationException(
                "DeployElement.execute() requires a concrete implementation");
        }
    }

    ///
    /// Step for performing the operative action of the Trial Element.
    ///
    /// @param id Step identifier
    /// @param trialId Trial this step belongs to
    /// @param elementBindings Mapping from element names to instance IDs
    /// @param dependencies Prerequisite step IDs
    /// @param estimatedDuration Estimated trial duration
    /// @param resourceRequirements Resource needs
    /// @param retryPolicy Retry strategy on failure
    /// @param metadata Additional metadata
    ///
    record TrialStep(
        String id,
        String trialId,
        Map<String, String> elementBindings,
        List<String> dependencies,
        Optional<Duration> estimatedDuration,
        ResourceRequirements resourceRequirements,
        Optional<RetryPolicy> retryPolicy,
        Map<String, Object> metadata
    ) implements AtomicStep {

        @Override
        public StepType type() {
            return StepType.TRIAL_STEP;
        }

        @Override
        public String description() {
            return "Trial step: " + trialId;
        }

        @Override
        public StepResult execute(ExecutionContext context) throws StepExecutionException {
            throw new UnsupportedOperationException(
                "TrialStep.execute() requires a concrete implementation");
        }
    }

    ///
    /// Step for awaiting natural completion of a COMMAND element.
    ///
    /// Emitted instead of {@link TrialStep} when the trial element
    /// has {@link io.nosqlbench.paramodel.elements.Element.ShutdownSemantics#COMMAND
    /// COMMAND} shutdown semantics. The element runs to completion on
    /// its own; the scheduler waits for it to terminate naturally rather
    /// than issuing a stop signal.
    ///
    /// The trial element's teardown step is also omitted — the element
    /// has already exited when this step completes.
    ///
    /// @param id Step identifier
    /// @param elementId The COMMAND element being awaited
    /// @param instanceNumber Instance number of the element being awaited
    /// @param trialId Trial this await belongs to
    /// @param elementBindings Mapping from element names to instance IDs
    /// @param dependencies Prerequisite step IDs
    /// @param estimatedDuration Estimated completion time
    /// @param resourceRequirements Resource needs
    /// @param retryPolicy Retry strategy on failure
    /// @param metadata Additional metadata
    ///
    record AwaitElement(
        String id,
        String elementId,
        int instanceNumber,
        String trialId,
        Map<String, String> elementBindings,
        List<String> dependencies,
        Optional<Duration> estimatedDuration,
        ResourceRequirements resourceRequirements,
        Optional<RetryPolicy> retryPolicy,
        Map<String, Object> metadata
    ) implements AtomicStep {

        @Override
        public StepType type() {
            return StepType.AWAIT_ELEMENT;
        }

        @Override
        public String description() {
            return "Await element: " + elementId + " #" + instanceNumber
                   + " (trial " + trialId + ")";
        }

        @Override
        public StepResult execute(ExecutionContext context) throws StepExecutionException {
            throw new UnsupportedOperationException(
                "AwaitElement.execute() requires a concrete implementation");
        }
    }

    ///
    /// Step for tearing down an element and collecting artifacts.
    ///
    /// @param id Step identifier
    /// @param elementId Element to teardown
    /// @param instanceNumber Instance number of the element instance being torn down
    /// @param collectArtifacts Whether to collect artifacts before teardown
    /// @param dependencies Prerequisite step IDs
    /// @param estimatedDuration Estimated teardown time
    /// @param resourceRequirements Resource needs
    /// @param retryPolicy Retry strategy on failure
    /// @param metadata Additional metadata
    ///
    record TeardownElement(
        String id,
        String elementId,
        int instanceNumber,
        boolean collectArtifacts,
        List<String> dependencies,
        Optional<Duration> estimatedDuration,
        ResourceRequirements resourceRequirements,
        Optional<RetryPolicy> retryPolicy,
        Map<String, Object> metadata
    ) implements AtomicStep {

        @Override
        public StepType type() {
            return StepType.TEARDOWN_ELEMENT;
        }

        @Override
        public String description() {
            return "Teardown element: " + elementId + " #" + instanceNumber;
        }

        @Override
        public StepResult execute(ExecutionContext context) throws StepExecutionException {
            throw new UnsupportedOperationException(
                "TeardownElement.execute() requires a concrete implementation");
        }
    }

    ///
    /// Step for synchronization barrier, waiting for dependencies.
    ///
    /// @param id Step identifier
    /// @param barrierId Barrier identifier
    /// @param dependencies Prerequisite step IDs (all must complete)
    /// @param estimatedDuration Estimated wait time
    /// @param resourceRequirements Resource needs (typically minimal)
    /// @param retryPolicy Retry strategy on failure
    /// @param metadata Additional metadata
    ///
    record BarrierSync(
        String id,
        String barrierId,
        List<String> dependencies,
        Optional<Duration> estimatedDuration,
        ResourceRequirements resourceRequirements,
        Optional<RetryPolicy> retryPolicy,
        Map<String, Object> metadata
    ) implements AtomicStep {

        @Override
        public StepType type() {
            return StepType.BARRIER_SYNC;
        }

        @Override
        public String description() {
            return "Barrier: " + barrierId + " (" + dependencies.size() + " dependencies)";
        }

        @Override
        public StepResult execute(ExecutionContext context) throws StepExecutionException {
            throw new UnsupportedOperationException(
                "BarrierSync.execute() requires a concrete implementation");
        }
    }

    ///
    /// Step for checkpointing execution state.
    ///
    /// @param id Step identifier
    /// @param checkpointId Checkpoint identifier
    /// @param dependencies Prerequisite step IDs
    /// @param estimatedDuration Estimated checkpoint time
    /// @param resourceRequirements Resource needs
    /// @param retryPolicy Retry strategy on failure
    /// @param metadata Additional metadata
    ///
    record CheckpointState(
        String id,
        String checkpointId,
        List<String> dependencies,
        Optional<Duration> estimatedDuration,
        ResourceRequirements resourceRequirements,
        Optional<RetryPolicy> retryPolicy,
        Map<String, Object> metadata
    ) implements AtomicStep {

        @Override
        public StepType type() {
            return StepType.CHECKPOINT_STATE;
        }

        @Override
        public String description() {
            return "Checkpoint: " + checkpointId;
        }

        @Override
        public StepResult execute(ExecutionContext context) throws StepExecutionException {
            throw new UnsupportedOperationException(
                "CheckpointState.execute() requires a concrete implementation");
        }
    }

    ///
    /// Step for notifying all elements in the trial scope that a trial
    /// is about to start. Emitted just before the trial element is
    /// deployed, after all other elements are ready.
    ///
    /// At runtime, the executor delivers
    /// {@link io.nosqlbench.paramodel.elements.TrialLifecycleParticipant#onTrialStarting
    /// onTrialStarting} to each element instance named in {@code elementNames},
    /// in dependency order (outermost first).
    ///
    /// @param id Step identifier
    /// @param trialId Trial about to start
    /// @param elementNames Elements to notify (all elements in the trial scope)
    /// @param dependencies Prerequisite step IDs
    /// @param estimatedDuration Estimated notification time
    /// @param resourceRequirements Resource needs (typically none)
    /// @param retryPolicy Retry strategy on failure
    /// @param metadata Additional metadata
    ///
    record NotifyTrialStart(
        String id,
        String trialId,
        List<String> elementNames,
        List<String> dependencies,
        Optional<Duration> estimatedDuration,
        ResourceRequirements resourceRequirements,
        Optional<RetryPolicy> retryPolicy,
        Map<String, Object> metadata
    ) implements AtomicStep {

        @Override
        public StepType type() {
            return StepType.NOTIFY_TRIAL_START;
        }

        @Override
        public String description() {
            return "Notify trial start: " + trialId + " (" + elementNames.size() + " elements)";
        }

        @Override
        public StepResult execute(ExecutionContext context) throws StepExecutionException {
            throw new UnsupportedOperationException(
                "NotifyTrialStart.execute() requires a concrete implementation");
        }
    }

    ///
    /// Step for notifying all elements in the trial scope that a trial
    /// has ended. Emitted just after the trial element is torn down or
    /// the trial execution completes.
    ///
    /// At runtime, the executor delivers
    /// {@link io.nosqlbench.paramodel.elements.TrialLifecycleParticipant#onTrialEnding
    /// onTrialEnding} to each element instance named in {@code elementNames},
    /// in reverse dependency order (innermost first).
    ///
    /// The {@code plannedReason} indicates the expected shutdown mode.
    /// The executor may override this at runtime based on actual outcome
    /// (e.g. switching from {@code NORMAL} to {@code ERROR} if the trial
    /// failed).
    ///
    /// @param id Step identifier
    /// @param trialId Trial that has ended
    /// @param elementNames Elements to notify (all elements in the trial scope)
    /// @param plannedReason Expected shutdown reason (runtime may override)
    /// @param dependencies Prerequisite step IDs
    /// @param estimatedDuration Estimated notification time
    /// @param resourceRequirements Resource needs (typically none)
    /// @param retryPolicy Retry strategy on failure
    /// @param metadata Additional metadata
    ///
    record NotifyTrialEnd(
        String id,
        String trialId,
        List<String> elementNames,
        ShutdownReason plannedReason,
        List<String> dependencies,
        Optional<Duration> estimatedDuration,
        ResourceRequirements resourceRequirements,
        Optional<RetryPolicy> retryPolicy,
        Map<String, Object> metadata
    ) implements AtomicStep {

        @Override
        public StepType type() {
            return StepType.NOTIFY_TRIAL_END;
        }

        @Override
        public String description() {
            return "Notify trial end: " + trialId + " (" + plannedReason + ")";
        }

        @Override
        public StepResult execute(ExecutionContext context) throws StepExecutionException {
            throw new UnsupportedOperationException(
                "NotifyTrialEnd.execute() requires a concrete implementation");
        }
    }

    ///
    /// Reason for trial shutdown, carried by {@link NotifyTrialEnd}.
    ///
    /// At compile time the planner sets {@link #NORMAL}. At runtime the
    /// executor determines the actual reason based on trial outcome.
    ///
    enum ShutdownReason {
        /// Trial completed successfully.
        NORMAL,
        /// Trial stopped by operator or control plane (graceful).
        MANAGED,
        /// Trial failed with an error.
        ERROR
    }

    ///
    /// Step type enumeration.
    ///
    enum StepType {
        DEPLOY_ELEMENT,
        NOTIFY_TRIAL_START,
        TRIAL_STEP,
        AWAIT_ELEMENT,
        NOTIFY_TRIAL_END,
        TEARDOWN_ELEMENT,
        BARRIER_SYNC,
        CHECKPOINT_STATE
    }

    ///
    /// Resource requirements for step execution.
    ///
    record ResourceRequirements(
        double cpu,
        long memoryMb,
        long storageGb,
        double networkGbps
    ) {
        public static ResourceRequirements minimal() {
            return new ResourceRequirements(0.1, 128, 1, 0.01);
        }

        public static ResourceRequirements none() {
            return new ResourceRequirements(0, 0, 0, 0);
        }
    }


    ///
    /// Retry policy for failed steps.
    ///
    interface RetryPolicy {
        int maxAttempts();
        Duration backoffDelay(int attemptNumber);
        boolean shouldRetry(Throwable error);

        static RetryPolicy exponentialBackoff(int maxAttempts) {
            throw new UnsupportedOperationException(
                "RetryPolicy.exponentialBackoff() requires a concrete implementation");
        }

        static RetryPolicy fixedDelay(int maxAttempts, Duration delay) {
            throw new UnsupportedOperationException(
                "RetryPolicy.fixedDelay() requires a concrete implementation");
        }

        static RetryPolicy noRetry() {
            throw new UnsupportedOperationException(
                "RetryPolicy.noRetry() requires a concrete implementation");
        }
    }

    ///
    /// Execution context providing shared state and services.
    ///
    interface ExecutionContext {
        String executionPlanId();
        String testPlanFingerprint();
        Map<String, Object> environment();
        ElementRegistry elementRegistry();
        ResultStore resultStore();
        MetricCollector metricCollector();
    }

    ///
    /// Registry of deployed element instances.
    ///
    interface ElementRegistry {
        Optional<ElementInstance> getInstance(String elementId);
        void registerInstance(String elementId, ElementInstance instance);
        void unregisterInstance(String elementId);
        List<ElementInstance> allInstances();
    }

    ///
    /// Element instance information.
    ///
    record ElementInstance(
        String instanceId,
        String elementId,
        String endpoint,
        Map<String, Object> configuration,
        InstanceStatus status
    ) {}

    ///
    /// Instance status.
    ///
    enum InstanceStatus {
        DEPLOYING,
        HEALTHY,
        UNHEALTHY,
        TEARDOWN
    }

    ///
    /// Store for trial results.
    ///
    interface ResultStore {
        void storeResult(String trialId, TrialResult result);
        Optional<TrialResult> getResult(String trialId);
        List<TrialResult> allResults();
    }

    ///
    /// Collector for execution metrics.
    ///
    interface MetricCollector {
        void recordMetric(String name, double value);
        void recordMetric(String name, double value, Map<String, String> tags);
    }

    ///
    /// Result of step execution.
    ///
    interface StepResult {
        boolean isSuccess();
        Optional<Throwable> error();
        Duration duration();
        Map<String, Object> outputs();

        static StepResult success(Duration duration, Map<String, Object> outputs) {
            throw new UnsupportedOperationException(
                "StepResult.success() requires a concrete implementation");
        }

        static StepResult failure(Duration duration, Throwable error) {
            throw new UnsupportedOperationException(
                "StepResult.failure() requires a concrete implementation");
        }
    }

    ///
    /// Exception thrown during step execution.
    ///
    class StepExecutionException extends Exception {
        private final String stepId;
        private final boolean isTransient;

        public StepExecutionException(String stepId, String message, boolean isTransient) {
            super(message);
            this.stepId = stepId;
            this.isTransient = isTransient;
        }

        public StepExecutionException(String stepId, String message, Throwable cause, boolean isTransient) {
            super(message, cause);
            this.stepId = stepId;
            this.isTransient = isTransient;
        }

        public String stepId() {
            return stepId;
        }

        public boolean isTransient() {
            return isTransient;
        }
    }
}
