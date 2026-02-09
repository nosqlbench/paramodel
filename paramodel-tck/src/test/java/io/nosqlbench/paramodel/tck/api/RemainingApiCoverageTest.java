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
package io.nosqlbench.paramodel.tck.api;

import io.nosqlbench.paramodel.compilation.Compiler;
import io.nosqlbench.paramodel.compilation.OptimizationPass;
import io.nosqlbench.paramodel.execution.ArtifactCollector;
import io.nosqlbench.paramodel.execution.ArtifactCollector.*;
import io.nosqlbench.paramodel.persistence.ArtifactStore;
import io.nosqlbench.paramodel.persistence.CheckpointStore;
import io.nosqlbench.paramodel.persistence.ExecutionRepository;
import io.nosqlbench.paramodel.persistence.MetadataStore;
import io.nosqlbench.paramodel.persistence.ResultStore;
import io.nosqlbench.paramodel.plan.TestPlanBuilder;
import io.nosqlbench.paramodel.plan.policies.ExecutionPolicies;
import io.nosqlbench.paramodel.plan.policies.ExecutionPolicies.*;
import io.nosqlbench.paramodel.security.AccessControl;
import io.nosqlbench.paramodel.security.AuditLog;
import io.nosqlbench.paramodel.security.CredentialManager;
import io.nosqlbench.paramodel.util.ConfigurationManager;
import io.nosqlbench.paramodel.util.SerializationUtil;
import io.nosqlbench.paramodel.util.ValidationUtil;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

///
/// Tests for remaining uncovered concrete types: static factory stubs,
/// enums, and default methods across compilation, execution policies,
/// artifact collector, util, and plan builder.
///
class RemainingApiCoverageTest {

    // ── ValidationUtil ────────────────────────────────────────────────

    @Test
    void validationUtilValidateThrows() {
        assertThatThrownBy(() -> ValidationUtil.validate(new Object()))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void validationUtilIsValidThrows() {
        assertThatThrownBy(() -> ValidationUtil.isValid(new Object()))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    // ── ConfigurationManager ──────────────────────────────────────────

    @Test
    void configurationManagerCreateThrows() {
        assertThatThrownBy(ConfigurationManager::create)
            .isInstanceOf(UnsupportedOperationException.class);
    }

    // ── SerializationUtil ─────────────────────────────────────────────

    @Test
    void serializationUtilCreateThrows() {
        assertThatThrownBy(SerializationUtil::create)
            .isInstanceOf(UnsupportedOperationException.class);
    }

    // ── Compiler static factories ─────────────────────────────────────

    @Test
    void compilerCreateThrows() {
        assertThatThrownBy(Compiler::create)
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void compilerCreateWithOptionsThrows() {
        assertThatThrownBy(() -> Compiler.create(null))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    // ── OptimizationPass default methods ──────────────────────────────

    @Test
    void optimizationPassDescriptionDefault() {
        OptimizationPass pass = new StubOptimizationPass("barrier-fusion");
        assertThat(pass.description()).contains("barrier-fusion");
    }

    @Test
    void optimizationPassEstimateSavingsDefault() {
        OptimizationPass pass = new StubOptimizationPass("test");
        assertThat(pass.estimateSavings(null)).isEmpty();
    }

    @Test
    void optimizationPassEnabledForLevelDefault() {
        OptimizationPass pass = new StubOptimizationPass("test");
        assertThat(pass.enabledForLevel(Compiler.OptimizationLevel.NONE)).isFalse();
        assertThat(pass.enabledForLevel(Compiler.OptimizationLevel.BASIC)).isTrue();
        assertThat(pass.enabledForLevel(Compiler.OptimizationLevel.STANDARD)).isTrue();
        assertThat(pass.enabledForLevel(Compiler.OptimizationLevel.AGGRESSIVE)).isTrue();
    }

    @Test
    void optimizationPassCategoryDefault() {
        OptimizationPass pass = new StubOptimizationPass("test");
        assertThat(pass.category()).isEqualTo(OptimizationPass.OptimizationCategory.OTHER);
    }

    // ── OptimizationPass.OptimizationCategory enum ────────────────────

    @Test
    void optimizationCategoryValues() {
        assertThat(OptimizationPass.OptimizationCategory.values())
            .containsExactlyInAnyOrder(
                OptimizationPass.OptimizationCategory.REDUCTION,
                OptimizationPass.OptimizationCategory.REORDERING,
                OptimizationPass.OptimizationCategory.STRUCTURAL,
                OptimizationPass.OptimizationCategory.SPECULATIVE,
                OptimizationPass.OptimizationCategory.OTHER);
    }

    // ── TestPlanBuilder.create() ──────────────────────────────────────

    @Test
    void testPlanBuilderCreateThrows() {
        assertThatThrownBy(TestPlanBuilder::create)
            .isInstanceOf(UnsupportedOperationException.class);
    }

    // ── ExecutionPolicies static factory ───────────────────────────────

    @Test
    void executionPoliciesDefaultsThrows() {
        assertThatThrownBy(ExecutionPolicies::defaults)
            .isInstanceOf(UnsupportedOperationException.class);
    }

    // ── ExecutionPolicies.RetryPolicy.none() ──────────────────────────

    @Test
    void retryPolicyNoneThrows() {
        assertThatThrownBy(RetryPolicy::none)
            .isInstanceOf(UnsupportedOperationException.class);
    }

    // ── ExecutionPolicies.BackoffStrategy static factories ────────────

    @Test
    void backoffStrategyImmediateThrows() {
        assertThatThrownBy(BackoffStrategy::immediate)
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void backoffStrategyFixedThrows() {
        assertThatThrownBy(() -> BackoffStrategy.fixed(Duration.ofSeconds(1)))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void backoffStrategyLinearThrows() {
        assertThatThrownBy(() -> BackoffStrategy.linear(Duration.ofSeconds(1)))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void backoffStrategyExponentialThrows() {
        assertThatThrownBy(() -> BackoffStrategy.exponential(2.0, Duration.ofSeconds(1)))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    // ── ExecutionPolicies.InterventionMode enum ───────────────────────

    @Test
    void interventionModeValues() {
        assertThat(InterventionMode.values()).containsExactlyInAnyOrder(
            InterventionMode.IMMEDIATE,
            InterventionMode.AFTER_ACTIVE_TRIALS);
    }

    // ── ExecutionPolicies.PartialRunBehavior enum ─────────────────────

    @Test
    void partialRunBehaviorValues() {
        assertThat(PartialRunBehavior.values()).containsExactlyInAnyOrder(
            PartialRunBehavior.RETAIN_RESULTS,
            PartialRunBehavior.FAIL_RUN);
    }

    // ── ArtifactCollector static factory ──────────────────────────────

    @Test
    void artifactCollectorCreateThrows() {
        assertThatThrownBy(ArtifactCollector::create)
            .isInstanceOf(UnsupportedOperationException.class);
    }

    // ── ArtifactCollector.ArtifactType enum ───────────────────────────

    @Test
    void artifactTypeValues() {
        assertThat(ArtifactType.values()).containsExactlyInAnyOrder(
            ArtifactType.STDOUT,
            ArtifactType.STDERR,
            ArtifactType.LOG,
            ArtifactType.METRIC,
            ArtifactType.TRACE,
            ArtifactType.PROFILE,
            ArtifactType.SCREENSHOT,
            ArtifactType.VIDEO,
            ArtifactType.REPORT,
            ArtifactType.RESULT,
            ArtifactType.STACK_TRACE,
            ArtifactType.CORE_DUMP,
            ArtifactType.MEMORY_DUMP,
            ArtifactType.NETWORK_TRACE,
            ArtifactType.CPU_PROFILE,
            ArtifactType.MEMORY_PROFILE,
            ArtifactType.CUSTOM);
    }

    // ── ArtifactCollector builder stubs ────────────────────────────────

    @Test
    void collectionPolicyBuilderThrows() {
        assertThatThrownBy(CollectionPolicy::builder)
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void artifactQueryBuilderThrows() {
        assertThatThrownBy(ArtifactQuery::builder)
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void retentionPolicyBuilderThrows() {
        assertThatThrownBy(RetentionPolicy::builder)
            .isInstanceOf(UnsupportedOperationException.class);
    }

    // ── Persistence static factory stubs ─────────────────────────────

    @Test
    void artifactStoreCreateThrows() {
        assertThatThrownBy(ArtifactStore::create)
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void checkpointStoreCreateThrows() {
        assertThatThrownBy(CheckpointStore::create)
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void executionRepositoryCreateThrows() {
        assertThatThrownBy(ExecutionRepository::create)
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void metadataStoreCreateThrows() {
        assertThatThrownBy(MetadataStore::create)
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void resultStoreCreateThrows() {
        assertThatThrownBy(ResultStore::create)
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void resultStoreQueryBuilderThrows() {
        assertThatThrownBy(ResultStore.Query::builder)
            .isInstanceOf(UnsupportedOperationException.class);
    }

    // ── Security static factory stubs ─────────────────────────────────

    @Test
    void accessControlCreateThrows() {
        assertThatThrownBy(AccessControl::create)
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void auditLogCreateThrows() {
        assertThatThrownBy(AuditLog::create)
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void auditLogQueryBuilderThrows() {
        assertThatThrownBy(AuditLog.AuditQuery::builder)
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void credentialManagerCreateThrows() {
        assertThatThrownBy(CredentialManager::create)
            .isInstanceOf(UnsupportedOperationException.class);
    }

    // ── Stub for testing OptimizationPass defaults ────────────────────

    private record StubOptimizationPass(String name) implements OptimizationPass {
        @Override
        public boolean shouldApply(io.nosqlbench.paramodel.compilation.CompilationContext context) {
            return false;
        }

        @Override
        public void apply(io.nosqlbench.paramodel.compilation.CompilationContext context) {
            // no-op
        }
    }
}
