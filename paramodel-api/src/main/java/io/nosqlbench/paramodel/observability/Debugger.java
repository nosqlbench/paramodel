package io.nosqlbench.paramodel.observability;

import io.nosqlbench.paramodel.sequence.Trial;
import io.nosqlbench.paramodel.plan.AtomicStep;
import io.nosqlbench.paramodel.execution.Executor;

import java.util.List;
import java.util.Optional;

///
/// # Debugger
///
/// Provides debugging capabilities for execution, including breakpoints, step execution,
/// and state inspection. The debugger enables interactive troubleshooting of test plans.
///
/// ## Debugging Features
///
/// ```
/// Breakpoints
///   ├─ Trial breakpoints (pause before/after trial)
///   ├─ Step breakpoints (pause before/after step)
///   ├─ Conditional breakpoints (pause if condition)
///   └─ Error breakpoints (pause on failure)
///
/// Step Execution
///   ├─ Step over (execute single trial)
///   ├─ Step into (execute single step)
///   ├─ Continue (run until breakpoint)
///   └─ Run to completion
///
/// State Inspection
///   ├─ Variable values
///   ├─ Element states
///   ├─ Resource usage
///   └─ Call stack
///
/// Replay
///   ├─ Replay single trial
///   ├─ Replay from checkpoint
///   ├─ Time-travel debugging
///   └─ Modify parameters
/// ```
///
/// ## Usage Examples
///
/// ### Example 1: Trial Breakpoint
///
/// ```java
/// Debugger debugger = Debugger.create();
/// debugger.attach(executor);
///
/// // Set breakpoint before specific trial
/// debugger.setBreakpoint(trial -> trial.id().equals("trial_42"));
///
/// executor.execute(plan); // Pauses at trial_42
///
/// // Inspect state
/// DebugContext ctx = debugger.context();
/// System.out.println("Trial: " + ctx.currentTrial());
/// System.out.println("Elements: " + ctx.elementStates());
///
/// // Continue execution
/// debugger.continueExecution();
/// ```
///
/// ### Example 2: Conditional Breakpoint
///
/// ```java
/// debugger.setBreakpoint(trial -> {
///     Value<?> cacheSize = trial.assignment("cache_size").orElse(null);
///     return cacheSize != null && (int) cacheSize.value() > 512;
/// });
///
/// // Pauses for trials with cache_size > 512
/// ```
///
/// ### Example 3: Error Debugging
///
/// ```java
/// debugger.setErrorBreakpoint(); // Pause on any error
///
/// executor.execute(plan); // Pauses on first error
///
/// DebugContext ctx = debugger.context();
/// System.out.println("Error: " + ctx.lastError());
/// System.out.println("Trial: " + ctx.currentTrial());
///
/// // Replay trial with modifications
/// Trial modified = ctx.currentTrial().withParameter("timeout", "10m");
/// debugger.replayTrial(modified);
/// ```
///
public interface Debugger {

    ///
    /// Creates a debugger with default configuration.
    ///
    /// @return Debugger instance
    ///
    static Debugger create() {
        throw new UnsupportedOperationException(
            "Debugger.create() requires a concrete implementation");
    }

    ///
    /// Attaches debugger to executor.
    ///
    /// @param executor Executor to debug
    ///
    void attach(Executor executor);

    ///
    /// Sets a breakpoint.
    ///
    /// @param condition Breakpoint condition
    /// @return Breakpoint ID
    ///
    String setBreakpoint(BreakpointCondition condition);

    ///
    /// Sets an error breakpoint (pause on any error).
    ///
    void setErrorBreakpoint();

    ///
    /// Removes a breakpoint.
    ///
    /// @param breakpointId Breakpoint ID
    ///
    void removeBreakpoint(String breakpointId);

    ///
    /// Continues execution until next breakpoint.
    ///
    void continueExecution();

    ///
    /// Steps over (executes next trial).
    ///
    void stepOver();

    ///
    /// Steps into (executes next step).
    ///
    void stepInto();

    ///
    /// Returns the current debug context.
    ///
    /// @return Debug context
    ///
    DebugContext context();

    ///
    /// Replays a trial.
    ///
    /// @param trial Trial to replay
    ///
    void replayTrial(Trial trial);

    ///
    /// Breakpoint condition.
    ///
    @FunctionalInterface
    interface BreakpointCondition {
        boolean shouldBreak(Trial trial);
    }

    ///
    /// Debug context.
    ///
    interface DebugContext {
        Optional<Trial> currentTrial();
        Optional<AtomicStep> currentStep();
        List<String> elementStates();
        Optional<Throwable> lastError();
        Map<String, Object> variables();
    }

    ///
    /// Import Map for variables.
    ///
    Map<String, Object> Map = java.util.Map.of();
}
