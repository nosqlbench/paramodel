# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.0-SNAPSHOT] - 2026-02-08

### Added

#### paramodel-api
- Initial contract definitions (57 contracts across 11 packages)
- Core contracts: Parameter, Domain, Value, Constraint, ValidationResult, ParameterMetadata
- Sequence contracts: Trial, Sequence, TrialResult, TrialBuilder, SequenceBuilder
- Plan contracts: TestPlan, ExecutionPlan, Axis, Element, ExecutionGraph, AtomicStep, Barrier, TrialOrdering
- Compilation contracts: Compiler, CompilationContext, CompilationStage, OptimizationPass
- Execution contracts: Executor, Runtime, Scheduler, ResourceManager, ArtifactCollector
- Observability contracts: Observer, MetricsExporter, Logger, Dashboard, Profiler, Debugger
- Persistence contracts: ResultStore, ExecutionRepository, CheckpointStore, ArtifactStore, MetadataStore
- Cost contracts: CostEstimator, CostModel, PricingCatalog, BudgetTracker
- Security contracts: CredentialManager, AccessControl, AuditLog
- Versioning contracts: VersionManager, MigrationStrategy, CompatibilityChecker, ChangeLog
- Utility contracts: ConfigurationManager, SerializationUtil, ValidationUtil

#### paramodel-mock
- Mock implementations for all core contracts
- Mock implementations for sequence contracts
- Mock implementations for plan contracts
- Builder patterns for convenient construction
- Static factory methods (of, create)
- README with usage examples

#### paramodel-tck
- Technology Compatibility Kit with 10 abstract test classes
- ParameterTCK, DomainTCK, ValueTCK, ConstraintTCK, ValidationResultTCK
- TrialTCK, SequenceTCK
- TestPlanTCK, ExecutionPlanTCK, ExecutionGraphTCK, AtomicStepTCK
- ImplementationProvider interface for pluggable implementations
- 12 validation tests for mock implementation
- README with compliance requirements

#### paramodel-engine
- DefaultCompiler with 8-stage compilation pipeline
- Compilation stages: Validation, Normalization, Trial Enumeration, Instantiation, Step Generation, Dependency Analysis, Optimization, Code Generation
- DefaultCompilationContext for tracking state through pipeline
- DefaultExecutor with thread pool-based concurrent execution
- DefaultScheduler with priority-based, work-stealing scheduling
- DefaultResourceManager with admission control for CPU/memory/I/O
- Integration tests for end-to-end workflow validation
- README with architecture and usage

#### Documentation
- Main project README with quick start guide
- Architecture diagrams and design principles
- Module-specific READMEs
- CONTRIBUTING.md with development workflow
- 4 runnable examples (Basic, Compilation, Execution, Constraints)
- Examples README with usage patterns

#### Examples
- BasicUsageExample.java - Introduction to core concepts
- CompilationPipelineExample.java - 8-stage pipeline deep dive
- ExecutionExample.java - Concurrent execution demonstration
- ConstraintsExample.java - Constraint composition patterns

#### Build & Tooling
- Maven multi-module project structure
- Java 25 toolchain configuration
- JUnit 5 test framework
- AssertJ assertions library
- SLF4J logging facade
- Apache License 2.0
- .gitignore configuration

### Design Decisions
- Contract-first architecture with pure interfaces
- Zero implementation coupling in paramodel-api
- Algebraic foundations (domains, parameters, constraints)
- Immutability for ExecutionPlan and downstream artifacts
- 8-stage compilation pipeline (TestPlan → ExecutionPlan)
- Resource-aware concurrent execution
- Technology Compatibility Kit for implementation validation

[Unreleased]: https://github.com/nosqlbench/paramodel/compare/v0.1.0...HEAD
[0.1.0-SNAPSHOT]: https://github.com/nosqlbench/paramodel/releases/tag/v0.1.0
