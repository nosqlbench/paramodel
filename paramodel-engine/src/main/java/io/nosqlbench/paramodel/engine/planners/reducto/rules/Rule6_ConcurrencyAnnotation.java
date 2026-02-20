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
package io.nosqlbench.paramodel.engine.planners.reducto.rules;

import io.nosqlbench.paramodel.elements.Element;
import io.nosqlbench.paramodel.engine.plan.DefaultElement;
import io.nosqlbench.paramodel.engine.planners.reducto.ReductoGraph;
import io.nosqlbench.paramodel.engine.planners.reducto.ReductoNode;
import io.nosqlbench.paramodel.engine.planners.reducto.ReductoNodeType;

///
/// Rule 6: Concurrency annotation.
///
/// Annotates {@link ReductoNodeType#ACTIVATE} nodes with concurrency metadata
/// for elements that declare a concurrency limit. No structural changes.
///
public final class Rule6_ConcurrencyAnnotation implements Rule {

    @Override
    public String name() { return "Rule6_ConcurrencyAnnotation"; }

    @Override
    public void apply(ReductoGraph graph, RuleContext context) {
        for (Element elem : context.sortedElements()) {
            int maxConc = getMaxConcurrency(elem);
            if (maxConc <= 0) continue;

            for (ReductoNode node : graph.nodesForElement(elem.name())) {
                if (node.type() == ReductoNodeType.ACTIVATE) {
                    node.putMetadata("max_concurrency", maxConc);
                }
            }
        }
    }

    private int getMaxConcurrency(Element element) {
        if (element instanceof DefaultElement de) {
            return de.maxConcurrency().orElse(0);
        }
        String val = element.tags().get("max_concurrency");
        if (val == null || val.isBlank()) return 0;
        return Integer.parseInt(val);
    }
}
