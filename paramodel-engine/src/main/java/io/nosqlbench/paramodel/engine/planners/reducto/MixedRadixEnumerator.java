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

import io.nosqlbench.paramodel.elements.Element;
import io.nosqlbench.paramodel.plan.Axis;
import io.nosqlbench.paramodel.plan.TestPlan;

import java.util.*;

///
/// Bijective mapping between a scalar trial number and a vector of parameter offsets
/// using mixed-radix decomposition.
///
/// Given N parameters with cardinalities C0..C(N-1) (in depth-first element-and-axis
/// order, skipping fixed parameters), the total trial count is C0 × C1 × ... × C(N-1).
/// Each trial number T maps to offsets via:
///
/// ```
/// stride[i] = C[i+1] × C[i+2] × ... × C[N-1]    (stride[N-1] = 1)
/// offset[i] = (T / stride[i]) % C[i]
/// ```
///
public final class MixedRadixEnumerator {

    private final int[] cardinalities;
    private final long[] strides;
    private final long totalTrials;
    private final List<String> parameterNames;
    private final List<String> elementNames;

    /// Creates an enumerator from explicit cardinalities.
    ///
    /// @param cardinalities   per-parameter cardinalities (in rank order)
    /// @param parameterNames  per-parameter names (in rank order)
    /// @param elementNames    per-parameter owning element names (in rank order)
    public MixedRadixEnumerator(int[] cardinalities, List<String> parameterNames,
                                 List<String> elementNames) {
        this.cardinalities = cardinalities.clone();
        this.parameterNames = List.copyOf(parameterNames);
        this.elementNames = List.copyOf(elementNames);

        this.strides = new long[cardinalities.length];
        long total = 1;
        for (int i = cardinalities.length - 1; i >= 0; i--) {
            strides[i] = total;
            total *= cardinalities[i];
        }
        this.totalTrials = total;
    }

    /// Creates an enumerator from elements and a test plan, deriving cardinalities
    /// in depth-first element-and-axis order, skipping fixed parameters.
    ///
    /// @param sortedElements topologically sorted elements
    /// @param plan           the test plan providing axes
    /// @return the enumerator
    public static MixedRadixEnumerator fromElements(List<Element> sortedElements, TestPlan plan) {
        List<Integer> cards = new ArrayList<>();
        List<String> paramNames = new ArrayList<>();
        List<String> elemNames = new ArrayList<>();

        Map<String, Map<String, Axis<?>>> axisByElementAndParam = new LinkedHashMap<>();
        for (Axis<?> axis : plan.axes()) {
            String elementName = axis.targetElement().orElse(null);
            String paramName = axis.name();
            if (elementName != null) {
                axisByElementAndParam
                    .computeIfAbsent(elementName, k -> new LinkedHashMap<>())
                    .put(paramName, axis);
            } else {
                for (Element elem : sortedElements) {
                    for (var param : elem.parameters()) {
                        if (param.name().equals(paramName)) {
                            axisByElementAndParam
                                .computeIfAbsent(elem.name(), k -> new LinkedHashMap<>())
                                .put(paramName, axis);
                            break;
                        }
                    }
                }
            }
        }

        for (Element elem : sortedElements) {
            Map<String, Axis<?>> elementAxes = axisByElementAndParam.getOrDefault(elem.name(), Map.of());
            Set<String> matched = new LinkedHashSet<>();

            // Match declared parameters to axes
            for (var param : elem.parameters()) {
                Axis<?> axis = elementAxes.get(param.name());
                if (axis != null && axis.values().size() > 1) {
                    cards.add(axis.values().size());
                    paramNames.add(param.name());
                    elemNames.add(elem.name());
                    matched.add(param.name());
                }
            }

            // Include axes targeting this element that didn't match a declared parameter
            for (var entry : elementAxes.entrySet()) {
                if (!matched.contains(entry.getKey()) && entry.getValue().values().size() > 1) {
                    cards.add(entry.getValue().values().size());
                    paramNames.add(entry.getKey());
                    elemNames.add(elem.name());
                }
            }
        }

        if (cards.isEmpty()) {
            return new MixedRadixEnumerator(new int[]{1}, List.of("__single"), List.of("__single"));
        }

        int[] cardArray = cards.stream().mapToInt(Integer::intValue).toArray();
        return new MixedRadixEnumerator(cardArray, paramNames, elemNames);
    }

    /// Decomposes a trial number into parameter offsets.
    ///
    /// @param trialNumber trial number in [0, totalTrials)
    /// @return array of parameter offsets
    /// @throws IllegalArgumentException if trialNumber is out of range
    public int[] decompose(long trialNumber) {
        if (trialNumber < 0 || trialNumber >= totalTrials) {
            throw new IllegalArgumentException("Trial number " + trialNumber
                + " out of range [0, " + totalTrials + ")");
        }
        int[] offsets = new int[cardinalities.length];
        for (int i = 0; i < cardinalities.length; i++) {
            offsets[i] = (int) ((trialNumber / strides[i]) % cardinalities[i]);
        }
        return offsets;
    }

    /// Composes parameter offsets into a trial number.
    ///
    /// @param offsets parameter offsets (one per rank)
    /// @return the trial number
    /// @throws IllegalArgumentException if offsets length or values are invalid
    public long compose(int[] offsets) {
        if (offsets.length != cardinalities.length) {
            throw new IllegalArgumentException("Expected " + cardinalities.length
                + " offsets, got " + offsets.length);
        }
        long result = 0;
        for (int i = 0; i < offsets.length; i++) {
            if (offsets[i] < 0 || offsets[i] >= cardinalities[i]) {
                throw new IllegalArgumentException("Offset[" + i + "]=" + offsets[i]
                    + " out of range [0, " + cardinalities[i] + ")");
            }
            result += offsets[i] * strides[i];
        }
        return result;
    }

    /// Returns the total number of trials.
    public long totalTrials() { return totalTrials; }

    /// Returns the number of parameters (ranks).
    public int rankCount() { return cardinalities.length; }

    /// Returns the cardinality at the given rank.
    ///
    /// @param rank parameter rank
    /// @return cardinality
    public int cardinality(int rank) { return cardinalities[rank]; }

    /// Returns the parameter name at the given rank.
    ///
    /// @param rank parameter rank
    /// @return parameter name
    public String parameterName(int rank) { return parameterNames.get(rank); }

    /// Returns the element name owning the parameter at the given rank.
    ///
    /// @param rank parameter rank
    /// @return element name
    public String elementName(int rank) { return elementNames.get(rank); }

    /// Returns the cardinalities array (defensive copy).
    public int[] cardinalities() { return cardinalities.clone(); }

    /// Returns the stride at the given rank.
    ///
    /// @param rank parameter rank
    /// @return stride value
    public long stride(int rank) { return strides[rank]; }

    /// Returns an unmodifiable view of the parameter names.
    public List<String> parameterNames() { return parameterNames; }

    /// Returns an unmodifiable view of the element names.
    public List<String> elementNames() { return elementNames; }

    /// Returns {@code true} if two trial numbers share the same offset values
    /// for all ranks up to (but not including) {@code level}.
    ///
    /// @param t1    first trial number
    /// @param t2    second trial number
    /// @param level the group level (number of leading ranks that must match)
    /// @return true if they belong to the same group at that level
    public boolean sameGroup(long t1, long t2, int level) {
        int[] o1 = decompose(t1);
        int[] o2 = decompose(t2);
        for (int i = 0; i < level && i < o1.length; i++) {
            if (o1[i] != o2[i]) return false;
        }
        return true;
    }

    /// Computes the group index at the given level for a trial number.
    ///
    /// The group index is the trial number with ranks at and after {@code level}
    /// zeroed out, normalized by the product of cardinalities at those ranks.
    ///
    /// @param trialNumber trial number
    /// @param level       group level
    /// @return group index
    public int groupIndex(long trialNumber, int level) {
        if (level == 0) return 0;
        if (level >= cardinalities.length) return (int) trialNumber;
        int[] offsets = decompose(trialNumber);
        int result = 0;
        long groupStride = 1;
        for (int i = level - 1; i >= 0; i--) {
            result += offsets[i] * (int) groupStride;
            groupStride *= cardinalities[i];
        }
        return result;
    }

    /// Returns the number of groups at the given level.
    ///
    /// @param level group level
    /// @return number of groups
    public int groupCount(int level) {
        if (level == 0) return 1;
        int count = 1;
        for (int i = 0; i < level && i < cardinalities.length; i++) {
            count *= cardinalities[i];
        }
        return count;
    }

    /// Computes the trial code for the given trial number.
    ///
    /// The trial code is a human-readable hex string encoding the parameter offsets.
    /// If the maximum cardinality across all parameters is 16 or less, each offset
    /// is encoded as a single hex digit (4 bits). If any parameter has more than 16
    /// values, all offsets are encoded as two hex digits (8 bits). The result is
    /// prefixed with {@code 0x}.
    ///
    /// @param trialNumber trial number in [0, totalTrials)
    /// @return the trial code, e.g. {@code "0x110"} or {@code "0x0201"}
    /// @throws IllegalArgumentException if trialNumber is out of range
    public String trialCode(long trialNumber) {
        int[] offsets = decompose(trialNumber);
        boolean wide = false;
        for (int c : cardinalities) {
            if (c > 16) {
                wide = true;
                break;
            }
        }
        StringBuilder sb = new StringBuilder("0x");
        for (int offset : offsets) {
            if (wide) {
                sb.append(String.format("%02x", offset));
            } else {
                sb.append(Integer.toHexString(offset));
            }
        }
        return sb.toString();
    }

    /// Returns the number of trials per group at the given level.
    ///
    /// @param level group level
    /// @return trials per group
    public long trialsPerGroup(int level) {
        if (level >= cardinalities.length) return 1;
        long count = 1;
        for (int i = level; i < cardinalities.length; i++) {
            count *= cardinalities[i];
        }
        return count;
    }
}
