# Paramodel

Contract-first framework for pseudo-formal parameter modeling and systematic test sequence execution.

## Overview

Paramodel provides a rigorous foundation for defining, compiling, and executing parametric studies. Built on algebraic principles with an emphasis on correctness, composability, and reproducibility.

**Key Features:**
- **Pure contract interfaces** - Zero implementation coupling
- **8-stage compilation pipeline** - TestPlan → ExecutionPlan transformation
- **Concurrent execution engine** - Resource-aware parallel execution with DAG scheduling
- **Technology Compatibility Kit (TCK)** - Validates implementation conformance
- **Java 25** - Leverages sealed interfaces, records, and pattern matching
- **Diátaxis Documentation** - Comprehensive tutorials, how-tos, reference, and explanation

## Quick Start

### 1. Define Parameters and Domains

```java
import io.nosqlbench.paramodel.mock.parameters.*;
import io.nosqlbench.paramodel.parameters.types.*;

// Create domains and parameters
var ops = IntegerParameter.range("threads", 1, 64);
var model = StringParameter.of("model", "gpt-.*");
```

### 2. Build TestPlan

```java
import io.nosqlbench.paramodel.mock.plan.*;
import io.nosqlbench.paramodel.plan.*;

TestPlan plan = MockTestPlan.builder()
    .name("performance-study")
    .axis(MockAxis.of("concurrency", 1, 4, 16, 64))
    .axis(MockAxis.of("model", "gpt-4", "gpt-4o"))
    .optimizationStrategy(OptimizationStrategy.PRUNE_REDUNDANT)
    .build();
```

### 3. Compile to ExecutionPlan

```java
import io.nosqlbench.paramodel.engine.compiler.*;

Compiler compiler = DefaultCompiler.builder()
    .standardPipeline()
    .build();

ExecutionPlan executionPlan = compiler.compile(plan).executionPlan().orElseThrow();
System.out.println("Execution Graph Nodes: " + executionPlan.executionGraph().steps().size());
```

### 4. Execute

```java
import io.nosqlbench.paramodel.engine.execution.*;
import io.nosqlbench.paramodel.execution.Executor;

DefaultExecutor executor = DefaultExecutor.builder().build();
Executor.ExecutionResult result = executor.execute(executionPlan);

System.out.println("Success: " + result.isSuccess());
result.trialResults().forEach(tr -> 
    System.out.println("Trial " + tr.trial().id() + ": " + tr.status()));
```

## Documentation

The documentation is organized following the [Diátaxis](https://diataxis.fr/) framework:

### 📚 [Concepts](docs/concepts/)
Mental models and foundational ideas:
- [Parameters and Domains](docs/concepts/parameters-and-domains.md)
- [Execution Plans](docs/concepts/execution-plans.md)
- [Elements and Relationships](docs/concepts/elements-and-relationships.md)

### 🚀 [Tutorials](docs/tutorials/)
Step-by-step learning guides:
- [Getting Started](docs/tutorials/getting-started.md)
- [First Test Plan](docs/tutorials/first-test-plan.md)
- [Running Trials](docs/tutorials/running-trials.md)

### 🛠️ [How-To Guides](docs/howto/)
Task-oriented recipes:
- [Define Parameters](docs/howto/define-parameters.md)
- [Build a Test Plan](docs/howto/build-test-plan.md)
- [Validate with TCK](docs/howto/validate-with-tck.md)

### 📖 [Reference](docs/reference/)
Technical lookup material:
- [API Packages](docs/reference/api-packages.md)
- [Contract Types](docs/reference/contract-types.md)
- [Compilation Stages](docs/reference/compilation-stages.md)

### 💡 [Explanation](docs/explanation/)
Design decisions and architecture:
- [Architecture](docs/explanation/architecture.md)
- [Design Principles](docs/explanation/design-principles.md)
- [Immutability and Reproducibility](docs/explanation/immutability-and-reproducibility.md)

## Project Structure

- **`paramodel-api`**: Pure contract interfaces (~52 contracts). Stable center of the system.
- **`paramodel-mock`**: Lightweight in-memory implementation for prototyping and testing.
- **`paramodel-engine`**: Production-grade compiler and executor with 8-stage pipeline.
- **`paramodel-tck`**: Technology Compatibility Kit for validating implementations.

## Requirements

- **Java**: 25+
- **Maven**: 3.9.0+

```bash
mvn clean install   # Build all modules
mvn clean verify    # Run tests + coverage + javadoc checks
```

## License

Apache License 2.0
