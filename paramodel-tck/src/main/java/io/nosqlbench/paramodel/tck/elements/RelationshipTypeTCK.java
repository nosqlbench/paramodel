package io.nosqlbench.paramodel.tck.elements;

import io.nosqlbench.paramodel.elements.RelationshipType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

///
/// Technology Compatibility Kit tests for RelationshipType enum behavior.
///
/// Validates that the enum's behavior methods correctly reflect the
/// concurrency, instance sharing, and barrier semantics defined by
/// each relationship type.
///
/// @see RelationshipType
/// @since 0.1.0
///
public abstract class RelationshipTypeTCK {
    protected RelationshipTypeTCK() {}

    @Test
    public void testMutuallyExclusiveDoesNotAllowConcurrency() {
        assertThat(RelationshipType.MUTUALLY_EXCLUSIVE.allowsConcurrency()).isFalse();
    }

    @Test
    public void testSharedAllowsConcurrency() {
        assertThat(RelationshipType.SHARED.allowsConcurrency()).isTrue();
    }

    @Test
    public void testInstancedPerAllowsConcurrency() {
        assertThat(RelationshipType.INSTANCED_PER.allowsConcurrency()).isTrue();
    }

    @Test
    public void testMutuallyExclusiveRequiresBarriers() {
        assertThat(RelationshipType.MUTUALLY_EXCLUSIVE.requiresBarriers()).isTrue();
        assertThat(RelationshipType.SHARED.requiresBarriers()).isFalse();
        assertThat(RelationshipType.INSTANCED_PER.requiresBarriers()).isFalse();
    }

    @Test
    public void testSharedRequiresSingleInstance() {
        assertThat(RelationshipType.SHARED.requiresSingleInstance()).isTrue();
        assertThat(RelationshipType.MUTUALLY_EXCLUSIVE.requiresSingleInstance()).isTrue();
    }

    @Test
    public void testInstancedPerDoesNotRequireSingleInstance() {
        assertThat(RelationshipType.INSTANCED_PER.requiresSingleInstance()).isFalse();
    }
}
