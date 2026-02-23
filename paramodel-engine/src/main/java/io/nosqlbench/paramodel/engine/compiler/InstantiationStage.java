package io.nosqlbench.paramodel.engine.compiler;

import io.nosqlbench.paramodel.compilation.CompilationContext;
import io.nosqlbench.paramodel.compilation.CompilationStage;
import io.nosqlbench.paramodel.elements.Element;
import io.nosqlbench.paramodel.plan.TestPlan;
import io.nosqlbench.paramodel.sequence.Trial;
import io.nosqlbench.paramodel.parameters.Parameter;
import io.nosqlbench.paramodel.plan.Axis;

import java.util.*;

/**
 * Stage 4: Instantiation
 *
 * Creates concrete values from trial specifications:
 * - Generate values from domains
 * - Apply fixed values from elements
 * - Create Trial instances
 * - Apply constraints and filter invalid trials
 * - Resolve element dependencies and link instances
 */
public class InstantiationStage implements CompilationStage {
    public InstantiationStage() {}

    @Override
    public String name() {
        return "Instantiation";
    }

    @Override
    public void execute(CompilationContext context) {
        TestPlan plan = context.testPlan();
        List<Trial> trials = context.trials().orElse(List.of());

        if (trials.isEmpty()) {
            return;
        }

        // Topological sort to ensure dependencies are instantiated first
        List<Element> sortedElements = topologicalSort(plan.elements());

        for (Element element : sortedElements) {
             // Use the definitive binding set from NormalizationStage
             AxisBindingSet binding = NormalizationStage.resolveBinding(context, element);

             boolean dependsOnAxes = binding.depth() > 0;

             if (dependsOnAxes) {
                 // Group level > 0: axis-bound, one instance per trial
                 for (Trial trial : trials) {
                     Set<String> depIds = new HashSet<>();
                     for (Element.Dependency dep : element.dependencies()) {
                         context.getInstanceForTrial(dep.target().name(), trial)
                             .map(CompilationContext.ElementInstance::instanceId)
                             .ifPresent(depIds::add);
                     }
                     context.planInstance(element, List.of(trial), "trial=" + trial.id(), depIds);
                 }
             } else {
                 // Group level 0: global, single instance for entire run
                 Set<String> depIds = new HashSet<>();
                 if (!trials.isEmpty()) {
                     // Resolve dependencies using a representative trial
                     // (Assuming dependencies are available globally or compatible)
                     Trial representative = trials.get(0);
                     for (Element.Dependency dep : element.dependencies()) {
                         context.getInstanceForTrial(dep.target().name(), representative)
                             .map(CompilationContext.ElementInstance::instanceId)
                             .ifPresent(depIds::add);
                     }
                 }
                 context.planInstance(element, trials, "global", depIds);
             }
        }

        context.recordMetric("instances_created", context.elementInstances().map(List::size).orElse(0));
    }

    private boolean matches(Axis<?> axis, Parameter<?> param, Element element) {
        if (axis.underlyingParameter().isPresent() && axis.underlyingParameter().get().equals(param)) {
            return true;
        }
        if (axis.name().equals(element.name() + "." + param.name())) {
            return true;
        }
        if (axis.name().equals(param.name())) {
            return true;
        }
        return false;
    }

    private List<Element> topologicalSort(List<Element> elements) {
        Map<Element, Integer> inDegree = new HashMap<>();
        Map<Element, List<Element>> adj = new HashMap<>();
        
        // Initialize graph
        for (Element e : elements) {
            inDegree.putIfAbsent(e, 0);
            for (Element.Dependency dep : e.dependencies()) {
                // Dependency target must precede e
                // Edge: dep.target() -> e
                adj.computeIfAbsent(dep.target(), k -> new ArrayList<>()).add(e);
                inDegree.merge(e, 1, Integer::sum);
                inDegree.putIfAbsent(dep.target(), 0);
            }
        }
        
        Queue<Element> queue = new LinkedList<>();
        for (Map.Entry<Element, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }
        
        List<Element> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            Element u = queue.poll();
            result.add(u);
            
            if (adj.containsKey(u)) {
                for (Element v : adj.get(u)) {
                    inDegree.put(v, inDegree.get(v) - 1);
                    if (inDegree.get(v) == 0) {
                        queue.add(v);
                    }
                }
            }
        }
        
        return result;
    }
}
