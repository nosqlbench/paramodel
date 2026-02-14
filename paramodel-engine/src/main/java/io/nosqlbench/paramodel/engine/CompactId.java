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

/// Generates compact, time-ordered identifiers using base-36 encoding.
///
/// Each ID is 12 characters: a 10-character base-36 representation of epoch
/// milliseconds followed by a 2-character base-36 suffix for collision avoidance.
/// The character set is `0-9, A-Z` (uppercase), giving 36^10 ≈ 3.6 × 10^15
/// distinct time slots and 36^2 = 1296 sequential IDs per millisecond.
///
/// ### Collision avoidance
///
/// The generator remembers the last time-derived value. When two calls occur
/// within the same millisecond, the 2-character suffix is incremented by 1
/// instead of resetting to "00". This guarantees uniqueness for up to 1296
/// calls per millisecond within a single JVM.
///
/// ### Examples
///
/// ```
/// CompactId.next()  // → "1BQ7K9X00P00"
/// CompactId.next()  // → "1BQ7K9X00P01"  (same ms, suffix incremented)
/// ```
///
/// ### Thread safety
///
/// All methods are synchronized on the class-level lock, making the generator
/// safe for concurrent use from multiple threads.
public final class CompactId {

    /// Base-36 digit characters (uppercase).
    private static final char[] DIGITS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();

    /// Radix used for encoding.
    private static final int RADIX = 36;

    /// Number of characters for the time portion.
    private static final int TIME_CHARS = 10;

    /// Number of characters for the suffix portion.
    private static final int SUFFIX_CHARS = 2;

    /// Maximum suffix value (36^2 - 1 = 1295).
    private static final int MAX_SUFFIX = (RADIX * RADIX) - 1;

    /// Last time-derived value to detect same-millisecond calls.
    private static String lastTimeValue = "";

    /// Current suffix counter, incremented on same-millisecond calls.
    private static int suffixCounter = 0;

    private CompactId() {}

    /// Generates the next compact ID.
    ///
    /// @return a 12-character base-36 identifier
    /// @throws IllegalStateException if more than 1296 IDs are requested
    ///         within the same millisecond
    public static synchronized String next() {
        String timePart = encodeTime(System.currentTimeMillis());
        if (timePart.equals(lastTimeValue)) {
            suffixCounter++;
            if (suffixCounter > MAX_SUFFIX) {
                throw new IllegalStateException(
                    "Exhausted " + (MAX_SUFFIX + 1) + " IDs within a single millisecond");
            }
        } else {
            lastTimeValue = timePart;
            suffixCounter = 0;
        }
        return timePart + encodeSuffix(suffixCounter);
    }

    /// Encodes epoch milliseconds as a fixed-width base-36 string.
    ///
    /// @param epochMillis the epoch millisecond value
    /// @return a {@value #TIME_CHARS}-character base-36 string, zero-padded
    static String encodeTime(long epochMillis) {
        char[] buf = new char[TIME_CHARS];
        long value = epochMillis;
        for (int i = TIME_CHARS - 1; i >= 0; i--) {
            buf[i] = DIGITS[(int) (value % RADIX)];
            value /= RADIX;
        }
        return new String(buf);
    }

    /// Encodes a suffix value as a fixed-width base-36 string.
    ///
    /// @param suffix the suffix counter value (0 .. 1295)
    /// @return a {@value #SUFFIX_CHARS}-character base-36 string, zero-padded
    static String encodeSuffix(int suffix) {
        char[] buf = new char[SUFFIX_CHARS];
        int value = suffix;
        for (int i = SUFFIX_CHARS - 1; i >= 0; i--) {
            buf[i] = DIGITS[value % RADIX];
            value /= RADIX;
        }
        return new String(buf);
    }

    /// Resets the internal state. Intended for testing only.
    static synchronized void reset() {
        lastTimeValue = "";
        suffixCounter = 0;
    }
}
