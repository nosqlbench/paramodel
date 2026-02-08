# Paramodel Examples

Practical examples demonstrating Paramodel usage patterns.

## Running Examples

All examples are standalone Java classes that can be executed directly:

```bash
# Compile
javac -cp "target/classes:paramodel-api.jar:paramodel-mock.jar:paramodel-engine.jar" examples/BasicUsageExample.java

# Run
java -cp ".:target/classes:paramodel-api.jar:paramodel-mock.jar:paramodel-engine.jar" examples.BasicUsageExample
```

Or use your IDE to run the main methods directly.

## Examples

### BasicUsageExample.java
Introduction to core concepts and basic workflow.

**Demonstrates:**
- Defining domains and parameters
- Building test plans
- Validation
- Committing to execution plans
- Creating sample trials

**Run:**
```bash
java examples.BasicUsageExample
```

**Output:**
```
Parameters defined:
  - operation: operation
  - threads: threads

TestPlan created:
  - Parameters: 2
  - Axes: 2

Validation: PASSED

ExecutionPlan created:
  - Estimated trials: 15
  - Plan is committed: true

Sample trials:
  Trial ...: operation=read, threads=1
  Trial ...: operation=read, threads=4
  ...
```

### CompilationPipelineExample.java
Deep dive into the 8-stage compilation pipeline.

**Demonstrates:**
- Building custom compilers
- Configuring compilation stages
- Compiling TestPlan → ExecutionPlan
- Accessing compilation metadata
- Optimization strategies

**Run:**
```bash
java examples.CompilationPipelineExample
```

**Output:**
```
=== Compilation Pipeline Example ===

1. TestPlan created
   Parameters: 3
   Axes: 3

2. Compiler built with 8-stage pipeline:
   - Validation
   - Normalization
   - Trial Enumeration
   ...

3. Compiling...
   ✓ Compilation successful!

4. ExecutionPlan details:
   Estimated trials: 36
   Compilation version: 1.0
   ...
```

### ExecutionExample.java
Concurrent execution with the engine runtime.

**Demonstrates:**
- Executor configuration
- Resource management
- Parallel trial execution
- Result collection and analysis
- Performance metrics

**Run:**
```bash
java examples.ExecutionExample
```

**Output:**
```
=== Execution Example ===

1. ExecutionPlan ready:
   Estimated trials: 6

2. Executor configured:
   Max concurrency: 8

3. Executing trials...
   ✓ Execution complete!

4. Results:
   Total trials: 6
   Duration: 254ms
   Throughput: 23.62 trials/sec
   Success: 6
   Failed: 0
   Success rate: 100.0%
```

### ConstraintsExample.java
Constraint definition and composition.

**Demonstrates:**
- Simple predicates
- Constraint composition (AND, OR, NOT)
- Cross-parameter constraints
- TestPlan constraint integration
- Complex constraint logic

**Run:**
```bash
java examples.ConstraintsExample
```

**Output:**
```
=== Constraints Example ===

1. Simple constraints:
   positive.test(5): true
   positive.test(-5): false
   even.test(4): true
   even.test(5): false

2. Constraint composition:
   positiveEven.test(4): true
   positiveEven.test(3): false
   ...

3. Cross-parameter constraints:
   threads=4, batch=100 (valid): true && true
   threads=5, batch=100 (invalid threads): false
   ...
```

## Common Patterns

### Pattern 1: Define Parameter Space
```java
// Create domains
MockDomain<String> ops = MockDomain.of("read", "write", "scan");
MockDomain<Integer> threads = MockDomain.of(1, 2, 4, 8, 16);

// Create parameters
MockParameter<String> operation = MockParameter.of("operation", ops);
MockParameter<Integer> concurrency = MockParameter.of("threads", threads);
```

### Pattern 2: Build Test Plan
```java
TestPlan plan = MockTestPlan.builder()
    .parameter(operation)
    .parameter(concurrency)
    .axis(MockAxis.of("ops", MockElement.exhaustive("operation")))
    .axis(MockAxis.of("conc", MockElement.boundary("threads")))
    .constraint(myConstraint)
    .optimizationStrategy(OptimizationStrategy.PRUNE_REDUNDANT)
    .build();
```

### Pattern 3: Compile
```java
Compiler compiler = DefaultCompiler.builder()
    .standardPipeline()
    .build();

ExecutionPlan execPlan = compiler.compile(plan);
```

### Pattern 4: Execute
```java
Executor executor = DefaultExecutor.builder()
    .maxConcurrency(8)
    .build();

List<TrialResult> results = executor.execute(execPlan, trial -> {
    // Your execution logic
    return executeMyTrial(trial);
});

executor.shutdown();
```

### Pattern 5: Add Constraints
```java
// Simple constraint
Constraint<Integer> positive = n -> n > 0;

// Composed constraint
Constraint<Integer> inRange = positive.and(n -> n < 100);

// Cross-parameter constraint
Constraint<Map<String, Value<?>>> batchLimit = assignment -> {
    Integer threads = (Integer) assignment.get("threads").value();
    Integer batch = (Integer) assignment.get("batch").value();
    return batch <= threads * 100;
};
```

## Next Steps

1. **Explore mock implementations** - See `paramodel-mock/` for simple implementations
2. **Run TCK tests** - Validate custom implementations against `paramodel-tck/`
3. **Use the engine** - Production-ready execution in `paramodel-engine/`
4. **Read Javadocs** - Detailed contract specifications in `paramodel-api/`

## Tips

- **Start simple**: Begin with BasicUsageExample
- **Use mocks for testing**: Mock implementations are perfect for unit tests
- **Validate early**: Run `plan.validate()` before compilation
- **Profile execution**: Use observability features for performance analysis
- **Leverage constraints**: Filter invalid trials early in the pipeline
