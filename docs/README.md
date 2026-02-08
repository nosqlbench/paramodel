# Paramodel Documentation

**Agent Navigation Index**

This documentation describes the Paramodel framework: a contract-first specification for pseudo-formal parameter modeling, algebraic type systems, and systematic test sequence execution. The framework is implemented as both Java interfaces and Rust traits.

## Quick Navigation

### 🎯 Start Here
- [00-OVERVIEW.md](00-OVERVIEW.md) - Project purpose, scope, and goals
- [01-QUICK-START.md](01-QUICK-START.md) - Installation and first examples

### 📐 Core Design
- [10-CORE-CONCEPTS.md](10-CORE-CONCEPTS.md) - Parameters, domains, constraints, sequences
- [11-ALGEBRAIC-LAWS.md](11-ALGEBRAIC-LAWS.md) - Type algebra and composition rules
- [12-TYPE-SYSTEM.md](12-TYPE-SYSTEM.md) - Java and Rust type hierarchies

### 🏗️ Architecture
- [20-ARCHITECTURE.md](20-ARCHITECTURE.md) - System layers and module organization
- [21-CONTRACT-TYPES.md](21-CONTRACT-TYPES.md) - Interface/trait specifications
- [22-EXECUTION-MODEL.md](22-EXECUTION-MODEL.md) - Runtime behavior and sequence execution

### 🔬 Simplica Application
- [30-SIMPLICA-OVERVIEW.md](30-SIMPLICA-OVERVIEW.md) - Study execution system built on paramodel
- [31-TEST-PLANS.md](31-TEST-PLANS.md) - Declarative parameter space specifications
- [32-EXECUTION-PLANS.md](32-EXECUTION-PLANS.md) - Compiled, immutable execution graphs
- [33-DEPENDENCIES.md](33-DEPENDENCIES.md) - Relationship semantics and scheduling
- [34-DURABILITY.md](34-DURABILITY.md) - Idempotency, resumability, checkpointing

### 🛠️ Implementation
- [40-JAVA-IMPLEMENTATION.md](40-JAVA-IMPLEMENTATION.md) - Java interface contracts
- [41-RUST-IMPLEMENTATION.md](41-RUST-IMPLEMENTATION.md) - Rust trait contracts
- [42-INTEROP.md](42-INTEROP.md) - Cross-language protocols

### 📊 Advanced Topics
- [50-RESULT-PERSISTENCE.md](50-RESULT-PERSISTENCE.md) - Provenance and artifact storage
- [51-OBSERVABILITY.md](51-OBSERVABILITY.md) - Logging, monitoring, telemetry
- [52-COST-ESTIMATION.md](52-COST-ESTIMATION.md) - Resource prediction from telemetry
- [53-TRIAL-ORDERING.md](53-TRIAL-ORDERING.md) - Edge-first and other strategies

### 🔐 Operations
- [60-ACCESS-CONTROL.md](60-ACCESS-CONTROL.md) - Multi-user collaboration
- [61-VERSIONING.md](61-VERSIONING.md) - Plan lineage and immutability
- [62-ERROR-HANDLING.md](62-ERROR-HANDLING.md) - Retry policies and partial failures

### 📚 Reference
- [70-API-REFERENCE.md](70-API-REFERENCE.md) - Complete API surface
- [71-EXAMPLES.md](71-EXAMPLES.md) - Code examples and patterns
- [72-GLOSSARY.md](72-GLOSSARY.md) - Terms and definitions

## Design Facet Index

For agent navigation, here are the primary design facets and where to find them:

| Facet | Primary Docs | Related Docs |
|-------|--------------|--------------|
| **Parameter Modeling** | 10-CORE-CONCEPTS, 12-TYPE-SYSTEM | 11-ALGEBRAIC-LAWS, 71-EXAMPLES |
| **Algebraic Properties** | 11-ALGEBRAIC-LAWS | 10-CORE-CONCEPTS, 12-TYPE-SYSTEM |
| **Test Plans** | 31-TEST-PLANS | 30-SIMPLICA-OVERVIEW, 33-DEPENDENCIES |
| **Execution Plans** | 32-EXECUTION-PLANS | 22-EXECUTION-MODEL, 34-DURABILITY |
| **Dependencies & Scheduling** | 33-DEPENDENCIES | 32-EXECUTION-PLANS, 53-TRIAL-ORDERING |
| **Contract Types** | 21-CONTRACT-TYPES | 40-JAVA-IMPLEMENTATION, 41-RUST-IMPLEMENTATION |
| **Immutability** | 32-EXECUTION-PLANS, 61-VERSIONING | 31-TEST-PLANS, 34-DURABILITY |
| **Idempotency** | 34-DURABILITY | 32-EXECUTION-PLANS, 62-ERROR-HANDLING |
| **Provenance** | 50-RESULT-PERSISTENCE | 51-OBSERVABILITY, 61-VERSIONING |
| **Trial Ordering** | 53-TRIAL-ORDERING | 33-DEPENDENCIES, 32-EXECUTION-PLANS |
| **Error Handling** | 62-ERROR-HANDLING | 34-DURABILITY, 22-EXECUTION-MODEL |
| **Observability** | 51-OBSERVABILITY | 50-RESULT-PERSISTENCE, 52-COST-ESTIMATION |
| **Cross-Language** | 42-INTEROP | 40-JAVA-IMPLEMENTATION, 41-RUST-IMPLEMENTATION |

## Document Conventions

### Structure
- **00-09**: Getting started and overview
- **10-19**: Core framework concepts
- **20-29**: Architecture and design
- **30-39**: Simplica application layer
- **40-49**: Implementation guides
- **50-59**: Advanced features
- **60-69**: Operational concerns
- **70-79**: Reference materials

### Cross-References
Documents use relative links like `[Type System](12-TYPE-SYSTEM.md)` for navigation.

### Code Examples
Examples show both Java and Rust implementations side-by-side for comparison.

### Terminology
See [72-GLOSSARY.md](72-GLOSSARY.md) for precise definitions of all terms.

## Relationship to Simplica

**Paramodel** is the foundational framework providing:
- Parameter type system and algebra
- Constraint composition
- Sequence generation primitives
- Cross-language contract specifications

**Simplica** is an application built on paramodel providing:
- Complete study execution system
- Test Plan → Execution Plan compilation
- Resource scheduling and orchestration
- Result persistence with provenance
- Operational durability guarantees

Think of it as: Paramodel provides the algebraic "atoms" and composition rules; Simplica assembles them into a complete study execution "molecule."
