/*
 * Copyright (c) nosqlbench
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.nosqlbench.paramodel.elements;

import io.nosqlbench.paramodel.sequence.Trial;

import java.time.Instant;
import java.util.Objects;

///
/// Context provided to elements during trial boundary notifications.
///
/// Carries the trial identity and parameter assignments so that elements
/// can correlate their observations (metrics, logs, artifacts) to the
/// specific trial timeframe, and the timestamp of the boundary event
/// so that elements can bracket their collection windows precisely.
///
/// ## Usage
///
/// ```java
/// @Override
/// public void onTrialStarting(TrialContext ctx) {
///     metricsCollector.openWindow(ctx.trial().id(), ctx.timestamp());
/// }
///
/// @Override
/// public void onTrialEnding(TrialContext ctx) {
///     MetricsSlice slice = metricsCollector.closeWindow(ctx.trial().id(), ctx.timestamp());
///     // attach slice to trial result
/// }
/// ```
///
/// @param trial     the trial that is starting or ending
/// @param timestamp the instant at which the boundary event occurred
/// @see TrialLifecycleParticipant
/// @since 0.1.0
///
public record TrialContext(Trial trial, Instant timestamp) {

    /// Validates that neither field is null.
    public TrialContext {
        Objects.requireNonNull(trial, "trial must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
    }

    /// Creates a context for the given trial stamped at the current instant.
    ///
    /// @param trial the trial
    /// @return a new context with {@link Instant#now()} as the timestamp
    public static TrialContext now(Trial trial) {
        return new TrialContext(trial, Instant.now());
    }
}
