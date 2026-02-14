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
package io.nosqlbench.paramodel.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CompactIdTest {

    @BeforeEach
    void resetState() {
        CompactId.reset();
    }

    @Test
    void nextProduces12CharBase36Id() {
        String id = CompactId.next();
        assertThat(id).hasSize(12);
        assertThat(id).matches("[0-9A-Z]{12}");
    }

    @Test
    void nextDefaultSuffixIs00() {
        CompactId.reset();
        String id = CompactId.next();
        assertThat(id).endsWith("00");
    }

    @Test
    void consecutiveCallsProduceUniqueIds() {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            ids.add(CompactId.next());
        }
        assertThat(ids).hasSize(100);
    }

    @Test
    void sameMillisecondIncrementsSuffix() {
        // Two rapid calls should have incrementing suffixes if within same ms
        String id1 = CompactId.next();
        String id2 = CompactId.next();
        // They must differ
        assertThat(id1).isNotEqualTo(id2);
        // Both must be valid 12-char base-36
        assertThat(id1).matches("[0-9A-Z]{12}");
        assertThat(id2).matches("[0-9A-Z]{12}");
    }

    @Test
    void encodeTimeProduces10Chars() {
        String encoded = CompactId.encodeTime(System.currentTimeMillis());
        assertThat(encoded).hasSize(10);
        assertThat(encoded).matches("[0-9A-Z]{10}");
    }

    @Test
    void encodeTimeIsStableForSameInput() {
        long millis = 1_700_000_000_000L;
        String a = CompactId.encodeTime(millis);
        String b = CompactId.encodeTime(millis);
        assertThat(a).isEqualTo(b);
    }

    @Test
    void encodeTimePreservesOrdering() {
        String earlier = CompactId.encodeTime(1_000_000_000_000L);
        String later = CompactId.encodeTime(2_000_000_000_000L);
        assertThat(earlier.compareTo(later)).isLessThan(0);
    }

    @Test
    void encodeSuffixProduces2Chars() {
        assertThat(CompactId.encodeSuffix(0)).isEqualTo("00");
        assertThat(CompactId.encodeSuffix(1)).isEqualTo("01");
        assertThat(CompactId.encodeSuffix(35)).isEqualTo("0Z");
        assertThat(CompactId.encodeSuffix(36)).isEqualTo("10");
        assertThat(CompactId.encodeSuffix(1295)).isEqualTo("ZZ");
    }
}
