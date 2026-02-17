package io.nosqlbench.paramodel.tck.elements;

import io.nosqlbench.paramodel.elements.RelationshipType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

///
/// Technology Compatibility Kit tests for RelationshipType enum behavior.
///
/// Validates that the enum's behavior methods correctly reflect the
/// concurrency and barrier semantics defined by each relationship type.
///
/// @see RelationshipType
/// @since 0.1.0
///
public abstract class RelationshipTypeTCK {
    protected RelationshipTypeTCK() {}

    @Test
    public void testSharedDoesNotRequireSerializationBarrier() {
        assertThat(RelationshipType.SHARED.requiresSerializationBarrier()).isFalse();
    }

    @Test
    public void testExclusiveRequiresSerializationBarrier() {
        assertThat(RelationshipType.EXCLUSIVE.requiresSerializationBarrier()).isTrue();
    }

    @Test
    public void testDedicatedRequiresDedicatedInstance() {
        assertThat(RelationshipType.DEDICATED.requiresDedicatedInstance()).isTrue();
        assertThat(RelationshipType.SHARED.requiresDedicatedInstance()).isFalse();
    }

    @Test
    public void testLifelineImpliesLifecycleCoupling() {
        assertThat(RelationshipType.LIFELINE.impliesLifecycleCoupling()).isTrue();
        assertThat(RelationshipType.SHARED.impliesLifecycleCoupling()).isFalse();
    }
}
