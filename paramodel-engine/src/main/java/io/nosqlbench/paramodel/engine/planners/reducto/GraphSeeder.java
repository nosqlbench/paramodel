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
package io.nosqlbench.paramodel.engine.planners.reducto;

///
/// Stage Two: creates the initial flat graph with one {@link ReductoNodeType#TRIAL_SEED}
/// node per trial. No edges are added at this stage.
///
public final class GraphSeeder {

    private GraphSeeder() {}

    /// Seeds the graph with one TRIAL_SEED node per trial.
    ///
    /// @param graph      the empty graph to populate
    /// @param totalTrials number of trials
    public static void seed(ReductoGraph graph, long totalTrials) {
        for (long t = 0; t < totalTrials; t++) {
            ReductoNode node = new ReductoNode("trial_seed_" + t, ReductoNodeType.TRIAL_SEED);
            node.setTrialIndex((int) t);
            graph.addNode(node);
        }
    }
}
