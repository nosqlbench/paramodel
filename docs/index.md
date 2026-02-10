<!--
  ~ Licensed to the Apache Software Foundation (ASF) under one
  ~ or more contributor license agreements.  See the NOTICE file
  ~ distributed with this work for additional information
  ~ regarding copyright ownership.  The ASF licenses this file
  ~ to you under the Apache License, Version 2.0 (the
  ~ "License"); you may not use this file except in compliance
  ~ with the License.  You may obtain a copy of the License at
  ~
  ~   http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing,
  ~ software distributed under the License is distributed on an
  ~ "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  ~ KIND, either express or implied.  See the License for the
  ~ specific language governing permissions and limitations
  ~ under the License.
  -->

# Paramodel Documentation

Contract-first framework for pseudo-formal parameter modeling and systematic test sequence execution.

## Concepts

Mental models and foundational ideas for understanding Paramodel.

- [Parameters and Domains](concepts/parameters-and-domains.md) — What parameters and domains are, their types and algebraic properties
- [Constraints and Validation](concepts/constraints-and-validation.md) — Boolean algebra of constraints, ValidationResult, composition
- [Trials and Sequences](concepts/trials-and-sequences.md) — Trials as points in parameter space, sequences as ordered collections
- [Test Plans and Axes](concepts/test-plans-and-axes.md) — Declarative study specification, axes, ordering strategies, barriers
- [Execution Plans](concepts/execution-plans.md) — Compiled plans, atomic steps, execution graphs, policies
- [Elements and Relationships](concepts/elements-and-relationships.md) — Deployable resources, relationship types, dependency semantics

## Tutorials

Step-by-step learning guides.

- [Getting Started](tutorials/getting-started.md) — Prerequisites, build, and first run
- [First Test Plan](tutorials/first-test-plan.md) — Build a simple two-parameter test plan with mock implementations
- [Compilation Pipeline](tutorials/compilation-pipeline.md) — Walk through the 8-stage compilation pipeline
- [Running Trials](tutorials/running-trials.md) — Execute an ExecutionPlan with the engine

## How-To Guides

Task-oriented recipes for common goals.

- [Define Parameters](howto/define-parameters.md) — Create domains, typed parameters, and boundary values
- [Compose Constraints](howto/compose-constraints.md) — Build and combine constraints with AND/OR/NOT
- [Build a Test Plan](howto/build-test-plan.md) — Construct a TestPlan with axes, elements, and policies
- [Implement a Contract](howto/implement-a-contract.md) — Create your own Paramodel implementation
- [Validate with TCK](howto/validate-with-tck.md) — Run TCK tests against your implementation

## Reference

Technical lookup material.

- [API Packages](reference/api-packages.md) — Package-by-package listing of all contracts in paramodel-api
- [Contract Types](reference/contract-types.md) — Detailed specification of each contract interface
- [Compilation Stages](reference/compilation-stages.md) — The 8 stages from TestPlan to ExecutionPlan
- [Build and Quality Gates](reference/build-and-quality-gates.md) — Maven build, coverage thresholds, javadoc enforcement
- [Glossary](reference/glossary.md) — Alphabetical term definitions

## Explanation

Understanding-oriented material about design decisions and architecture.

- [Architecture](explanation/architecture.md) — Module structure, layered architecture, dependency flow
- [Design Principles](explanation/design-principles.md) — Contract-first, algebraic foundations, why formal modeling
- [Simplica](explanation/simplica.md) — Complete study execution system built on Paramodel
- [Immutability and Reproducibility](explanation/immutability-and-reproducibility.md) — Mutable TestPlan, immutable ExecutionPlan, fingerprinting
