package io.nosqlbench.paramodel.tck.core;

import io.nosqlbench.paramodel.core.Constraint;
import io.nosqlbench.paramodel.core.Value;
import io.nosqlbench.paramodel.tck.ImplementationProvider;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Technology Compatibility Kit tests for Constraint contract.
 *
 * Validates that implementations correctly:
 * - Evaluate predicates
 * - Combine with logical operators (AND, OR, NOT)
 * - Provide error messages
 * - Handle edge cases
 */
public abstract class ConstraintTCK {

    protected abstract ImplementationProvider getProvider();

    @Test
    public void testSimpleConstraintEvaluation() {
        // Create a constraint: value > 10
        Constraint<Integer> constraint = value -> value > 10;

        assertThat(constraint.test(15)).isTrue();
        assertThat(constraint.test(5)).isFalse();
        assertThat(constraint.test(10)).isFalse();
    }

    @Test
    public void testConstraintAnd() {
        Constraint<Integer> greaterThan10 = value -> value > 10;
        Constraint<Integer> lessThan20 = value -> value < 20;

        Constraint<Integer> between = greaterThan10.and(lessThan20);

        assertThat(between.test(15)).isTrue();
        assertThat(between.test(5)).isFalse();
        assertThat(between.test(25)).isFalse();
    }

    @Test
    public void testConstraintOr() {
        Constraint<Integer> lessThan5 = value -> value < 5;
        Constraint<Integer> greaterThan10 = value -> value > 10;

        Constraint<Integer> either = lessThan5.or(greaterThan10);

        assertThat(either.test(3)).isTrue();
        assertThat(either.test(15)).isTrue();
        assertThat(either.test(7)).isFalse();
    }

    @Test
    public void testConstraintNot() {
        Constraint<Integer> isEven = value -> value % 2 == 0;
        Constraint<Integer> isOdd = isEven.negate();

        assertThat(isOdd.test(3)).isTrue();
        assertThat(isOdd.test(5)).isTrue();
        assertThat(isOdd.test(4)).isFalse();
    }

    @Test
    public void testConstraintOnAssignment() {
        // Constraint on trial assignments: threads must be power of 2
        Constraint<Map<String, Value<?>>> powerOf2Threads = assignment -> {
            Value<?> threadsValue = assignment.get("threads");
            if (threadsValue == null) return true;
            Integer threads = (Integer) threadsValue.value();
            return threads > 0 && (threads & (threads - 1)) == 0;
        };

        Value<Integer> threads4 = getProvider().createValue(4, "threads");
        Value<Integer> threads5 = getProvider().createValue(5, "threads");

        assertThat(powerOf2Threads.test(Map.of("threads", threads4))).isTrue();
        assertThat(powerOf2Threads.test(Map.of("threads", threads5))).isFalse();
    }

    @Test
    public void testComplexConstraintCombination() {
        Constraint<Integer> positive = value -> value > 0;
        Constraint<Integer> lessThan100 = value -> value < 100;
        Constraint<Integer> notMultipleOf7 = value -> value % 7 != 0;

        Constraint<Integer> complex = positive.and(lessThan100).and(notMultipleOf7);

        assertThat(complex.test(50)).isTrue();
        assertThat(complex.test(-5)).isFalse();
        assertThat(complex.test(150)).isFalse();
        assertThat(complex.test(14)).isFalse(); // multiple of 7
    }

    @Test
    public void testConstraintWithNullHandling() {
        Constraint<String> notNull = value -> value != null;

        assertThat(notNull.test("something")).isTrue();
        assertThat(notNull.test(null)).isFalse();
    }
}
