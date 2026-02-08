package io.nosqlbench.paramodel.observability;

import java.time.Duration;
import java.util.List;
import java.util.Map;

///
/// # Dashboard
///
/// Provides real-time visualization of execution progress, metrics, and status.
/// The dashboard aggregates data from the observer for display in UI or terminal.
///
/// ## Dashboard Widgets
///
/// ```
/// Widget Types:
///
/// Progress Bar
///   ├─ Overall execution progress (%)
///   ├─ Phase progress (deploying, executing)
///   ├─ Trial completion progress
///   └─ Time remaining estimate
///
/// Metrics Panel
///   ├─ Active trials
///   ├─ Success/failure counts
///   ├─ Resource utilization
///   └─ Throughput rates
///
/// Timeline
///   ├─ Recent events
///   ├─ Phase transitions
///   ├─ Error occurrences
///   └─ Checkpoint markers
///
/// Resource Graph
///   ├─ CPU usage over time
///   ├─ Memory usage over time
///   ├─ Network I/O
///   └─ Storage usage
///
/// Trial Table
///   ├─ Trial ID
///   ├─ Status
///   ├─ Duration
///   └─ Parameters
/// ```
///
/// ## Usage Examples
///
/// ### Example 1: Console Dashboard
///
/// ```java
/// Dashboard dashboard = ConsoleDashboard.create();
/// dashboard.attach(observer);
///
/// // Dashboard updates automatically
/// // Output:
/// // ╔════════════════════════════════════════╗
/// // ║  Execution: exec_xyz789                ║
/// // ╠════════════════════════════════════════╣
/// // ║  Progress: [████████░░] 80% (80/100)   ║
/// // ║  Active: 8 trials                      ║
/// // ║  Success: 75 | Failed: 5 | Pending: 20 ║
/// // ║  CPU: 67.5% | Memory: 24.3 GB          ║
/// // ║  ETA: 5m 30s                           ║
/// // ╚════════════════════════════════════════╝
/// ```
///
/// ### Example 2: Web Dashboard
///
/// ```java
/// Dashboard dashboard = WebDashboard.create(8080);
/// dashboard.attach(observer);
///
/// // Dashboard available at http://localhost:8080/
/// // Real-time updates via WebSocket
/// ```
///
public interface Dashboard {

    ///
    /// Attaches the dashboard to an observer.
    ///
    /// @param observer Observer to attach to
    ///
    void attach(Observer observer);

    ///
    /// Detaches the dashboard from the observer.
    ///
    void detach();

    ///
    /// Updates the dashboard with current state.
    ///
    /// @param state Dashboard state
    ///
    void update(DashboardState state);

    ///
    /// Dashboard state.
    ///
    interface DashboardState {
        String executionId();
        double progress();
        int activeTrials();
        int completedTrials();
        int failedTrials();
        int totalTrials();
        Map<String, Double> metrics();
        List<String> recentEvents();
        Duration estimatedTimeRemaining();
    }
}
