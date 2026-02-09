# Quick Start Guide

Get up and running with Paramodel in 5 minutes.

## Prerequisites

- **Java 25+** - Download from [OpenJDK](https://jdk.java.net/25/)
- **Maven 3.9.0+** - Download from [Maven](https://maven.apache.org/download.cgi)
- **Git** - For cloning the repository

Verify installations:
```bash
java -version   # Should show 25 or higher
mvn -version    # Should show 3.9.0 or higher
```

## Installation

### Option 1: Clone and Build

```bash
# Clone repository
git clone https://github.com/nosqlbench/paramodel.git
cd paramodel

# Build all modules
mvn clean install

# Verify build
./verify-build.sh  # On Linux/macOS
verify-build.bat   # On Windows
```

### Option 2: Use as Maven Dependency

Add to your `pom.xml`:

```xml
<dependencies>
    <!-- API contracts -->
    <dependency>
        <groupId>io.nosqlbench</groupId>
        <artifactId>paramodel-api</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </dependency>

    <!-- Mock implementations for testing -->
    <dependency>
        <groupId>io.nosqlbench</groupId>
        <artifactId>paramodel-mock</artifactId>
        <version>0.1.0-SNAPSHOT</version>
        <scope>test</scope>
    </dependency>

    <!-- Production engine -->
    <dependency>
        <groupId>io.nosqlbench</groupId>
        <artifactId>paramodel-engine</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </dependency>
</dependencies>
```

## Your First Paramodel Program

Create `HelloParamodel.java`:

```java
import io.nosqlbench.paramodel.parameters.*;
import io.nosqlbench.paramodel.mock.parameters.*;
import io.nosqlbench.paramodel.mock.plan.*;
import io.nosqlbench.paramodel.plan.*;

public class HelloParamodel {
    public static void main(String[] args) {
        // 1. Define parameter space
        MockDomain<String> operations = MockDomain.of("read", "write", "scan");
        MockDomain<Integer> threads = MockDomain.of(1, 2, 4, 8);

        MockParameter<String> operation = MockParameter.of("operation", operations);
        MockParameter<Integer> concurrency = MockParameter.of("threads", threads);

        // 2. Create test plan
        TestPlan plan = MockTestPlan.builder()
            .parameter(operation)
            .parameter(concurrency)
            .axis(MockAxis.of("ops", MockElement.exhaustive("operation")))
            .axis(MockAxis.of("conc", MockElement.exhaustive("threads")))
            .build();

        // 3. Validate and commit
        if (plan.validate().isValid()) {
            ExecutionPlan execPlan = plan.commit();
            System.out.println("Created execution plan with " +
                execPlan.estimatedTrialCount() + " trials");
        }
    }
}
```

Compile and run:
```bash
javac -cp "target/classes:paramodel-api/target/*:paramodel-mock/target/*" HelloParamodel.java
java -cp ".:target/classes:paramodel-api/target/*:paramodel-mock/target/*" HelloParamodel
```

Expected output:
```
Created execution plan with 12 trials
```

## Next Steps

### Run Examples

The project includes 4 working examples:

```bash
# Navigate to examples directory
cd examples

# Run basic example
java -cp "../paramodel-api/target/*:../paramodel-mock/target/*:." examples.BasicUsageExample

# Run compilation pipeline example
java -cp "../paramodel-api/target/*:../paramodel-mock/target/*:../paramodel-engine/target/*:." examples.CompilationPipelineExample

# Run execution example
java -cp "../paramodel-api/target/*:../paramodel-mock/target/*:../paramodel-engine/target/*:." examples.ExecutionExample

# Run constraints example
java -cp "../paramodel-api/target/*:../paramodel-mock/target/*:." examples.ConstraintsExample
```

### Explore Modules

**paramodel-api** - Contract interfaces
```bash
cd paramodel-api
ls src/main/java/io/nosqlbench/paramodel/
# Explore: core/, sequence/, plan/, compilation/, execution/
```

**paramodel-mock** - Simple implementations
```bash
cd paramodel-mock
cat README.md
# Use for: testing, prototyping, learning
```

**paramodel-tck** - Validation tests
```bash
cd paramodel-tck
mvn test
# Validates: contract compliance
```

**paramodel-engine** - Production engine
```bash
cd paramodel-engine
cat README.md
# Use for: production workloads, full features
```

### Read Documentation

```bash
# Project overview
cat README.md

# Contributing guidelines
cat CONTRIBUTING.md

# Module documentation
cat paramodel-mock/README.md
cat paramodel-tck/README.md
cat paramodel-engine/README.md

# Examples guide
cat examples/README.md
```

### Verify Your Setup

Run the verification script:
```bash
./verify-build.sh  # Linux/macOS
verify-build.bat   # Windows
```

This checks:
- ✓ Java version
- ✓ Maven version
- ✓ Compilation
- ✓ Tests
- ✓ Packaging
- ✓ Artifacts
- ✓ TCK validation

## Common Tasks

### Add a New Parameter

```java
// Define domain
MockDomain<Integer> batchSizes = MockDomain.of(10, 100, 1000);

// Create parameter
MockParameter<Integer> batchSize = MockParameter.of("batchSize", batchSizes);

// Add to plan
TestPlan plan = MockTestPlan.builder()
    .parameter(batchSize)
    .axis(MockAxis.of("batching", MockElement.boundary("batchSize")))
    .build();
```

### Add a Constraint

```java
// Define constraint: threads must be power of 2
Constraint<Map<String, Value<?>>> powerOf2 = assignment -> {
    Integer threads = (Integer) assignment.get("threads").value();
    return threads > 0 && (threads & (threads - 1)) == 0;
};

// Add to plan
TestPlan plan = MockTestPlan.builder()
    .parameter(threads)
    .constraint(powerOf2)
    .build();
```

### Compile and Execute

```java
// Compile
Compiler compiler = DefaultCompiler.builder()
    .standardPipeline()
    .build();
ExecutionPlan execPlan = compiler.compile(testPlan);

// Execute
Executor executor = DefaultExecutor.builder()
    .maxConcurrency(4)
    .build();

List<TrialResult> results = executor.execute(execPlan, trial -> {
    // Your execution logic
    return executeMyTrial(trial);
});

executor.shutdown();
```

## Troubleshooting

### Java Version Error
```
Error: Java 25 or higher required
```
**Solution**: Install Java 25+ from [OpenJDK](https://jdk.java.net/25/)

### Maven Not Found
```
Error: mvn: command not found
```
**Solution**: Install Maven and add to PATH

### Compilation Errors
```
Error: package io.nosqlbench.paramodel does not exist
```
**Solution**: Run `mvn clean install` from project root

### Test Failures
```
Error: Tests failed
```
**Solution**: Check test output, ensure all dependencies installed

## Getting Help

- **Documentation**: See module READMEs
- **Examples**: Check `examples/` directory
- **Issues**: https://github.com/nosqlbench/paramodel/issues
- **Discussions**: https://github.com/nosqlbench/paramodel/discussions

## What's Next?

1. **Read the main README** - Understand architecture and design
2. **Explore examples** - See working code patterns
3. **Run TCK tests** - Validate implementations
4. **Build something** - Create your own test plans
5. **Contribute** - See CONTRIBUTING.md

---

**Congratulations!** You're now ready to use Paramodel. Start with the examples and explore the API documentation.
