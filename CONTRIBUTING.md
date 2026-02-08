# Contributing to Paramodel

Thank you for your interest in contributing to Paramodel! This guide will help you get started.

## Code of Conduct

Be respectful, professional, and collaborative. Focus on technical merit and constructive feedback.

## Getting Started

### Prerequisites

- **Java 25+** - Required for sealed interfaces, records, pattern matching
- **Maven 3.9.0+** - Build tool
- **Git** - Version control
- **IDE** - IntelliJ IDEA, Eclipse, or VS Code recommended

### Clone and Build

```bash
git clone https://github.com/nosqlbench/paramodel.git
cd paramodel
mvn clean install
```

### Project Structure

```
paramodel/
├── paramodel-api/          # Contract interfaces (DO NOT add implementations)
├── paramodel-mock/         # Simple mock implementations
├── paramodel-tck/          # Technology Compatibility Kit
└── paramodel-engine/       # Production engine implementation
```

## Contribution Guidelines

### 1. Contract-First Design

Paramodel follows a **strict contract-first architecture**:

- ✅ **DO**: Define new functionality as interfaces in `paramodel-api`
- ✅ **DO**: Provide mock implementations in `paramodel-mock`
- ✅ **DO**: Add TCK tests in `paramodel-tck`
- ✅ **DO**: Implement in `paramodel-engine` or other modules
- ❌ **DON'T**: Add implementation code to `paramodel-api`
- ❌ **DON'T**: Create dependencies between implementations

### 2. Documentation Requirements

All public interfaces **must** have triple-slash Javadocs:

```java
///
/// Parameter representing a named generator over a domain.
///
/// A Parameter combines a name with a Domain to produce values.
/// Parameters support validation against their domain constraints.
///
/// Example:
/// ```java
/// Domain<Integer> domain = DiscreteDomain.of(1, 2, 4, 8);
/// Parameter<Integer> threads = Parameter.of("threads", domain);
/// Integer value = threads.generate();
/// ```
///
public interface Parameter<T> {
    String name();
    Domain<T> domain();
    T generate();
}
```

**Documentation must include:**
- Clear description of purpose
- Parameter/return value descriptions
- Usage examples
- ASCII diagrams where helpful
- Mathematical notation for formal specifications

### 3. Testing Requirements

#### All New Code Must Include Tests

**For API contracts:**
- Add TCK tests in `paramodel-tck/src/main/java/.../tck/`
- Tests must validate contract behavior
- Use abstract base classes extending from TCK

**For implementations:**
- Add unit tests in module's `src/test/java/`
- Add integration tests if crossing module boundaries
- Achieve >80% code coverage
- Use AssertJ for assertions

**Test example:**
```java
@Test
@DisplayName("Parameter generates values from domain")
public void testParameterGeneration() {
    Domain<String> domain = MockDomain.of("a", "b", "c");
    Parameter<String> param = MockParameter.of("test", domain);

    String value = param.generate();

    assertThat(value).isIn("a", "b", "c");
}
```

### 4. Code Style

**Follow existing patterns:**

- **Java 25 features**: Use sealed interfaces, records, pattern matching
- **Naming**: `DefaultXxx` for default implementations, `MockXxx` for mocks
- **Immutability**: Prefer immutable data structures
- **Null safety**: Use `Optional<T>` instead of nullable returns
- **Builder pattern**: Use for complex objects
- **Static factories**: Provide `of()` and `create()` methods

**Example:**
```java
public record MockValue<T>(
    T value,
    String parameterName,
    Instant generatedAt,
    Optional<String> generatorMetadata
) implements Value<T> {

    public static <T> MockValue<T> of(T value, String parameterName) {
        return new MockValue<>(
            value,
            parameterName,
            Instant.now(),
            Optional.empty()
        );
    }
}
```

### 5. TCK Compliance

All implementations **must** pass TCK validation:

1. Create an `ImplementationProvider`:
```java
public class MyProvider implements ImplementationProvider {
    @Override
    public <T> Parameter<T> createParameter(String name, Domain<T> domain) {
        return new MyParameter<>(name, domain);
    }
    // ... implement all methods
}
```

2. Extend TCK test classes:
```java
public class MyParameterTest extends ParameterTCK {
    @Override
    protected ImplementationProvider getProvider() {
        return new MyProvider();
    }
}
```

3. Run tests:
```bash
mvn test
```

All TCK tests must pass without modification.

### 6. Commit Messages

Use conventional commits format:

```
<type>(<scope>): <subject>

<body>

<footer>
```

**Types:**
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation only
- `refactor`: Code restructuring
- `test`: Adding tests
- `chore`: Maintenance

**Examples:**
```
feat(api): add CostEstimator contract for resource prediction

Adds new contract interface for estimating execution costs
including CPU, memory, I/O, and monetary costs.

Closes #123
```

```
fix(engine): correct topological sort in dependency analysis

The previous implementation could produce invalid orderings
when cycles were introduced through barrier dependencies.

Fixes #456
```

### 7. Pull Request Process

1. **Fork** the repository
2. **Create** a feature branch: `git checkout -b feat/my-feature`
3. **Write** code following guidelines
4. **Add** tests and documentation
5. **Commit** changes with conventional commits
6. **Push** to your fork
7. **Create** pull request with description

**PR checklist:**
- [ ] Code follows style guidelines
- [ ] Tests added and passing
- [ ] Documentation updated
- [ ] TCK tests pass (if applicable)
- [ ] No breaking changes (or clearly documented)
- [ ] Commit messages follow convention

## What to Contribute

### High Priority

- **New contract implementations** - Implement contracts in new modules
- **TCK improvements** - Additional test coverage
- **Documentation** - Examples, tutorials, guides
- **Bug fixes** - Issues marked "bug"
- **Performance** - Optimizations with benchmarks

### Ideas for Contributions

1. **New execution strategies** - Alternative schedulers, executors
2. **Observability** - Metrics exporters, dashboards
3. **Persistence** - Storage backends (SQL, NoSQL, S3)
4. **Cost estimation** - Cloud provider integrations
5. **Serialization** - JSON, YAML, Protobuf formats
6. **CLI tools** - Command-line interface for execution
7. **IDE plugins** - IntelliJ, VS Code integrations

### Not Accepted

- ❌ Implementation code in `paramodel-api`
- ❌ Breaking changes without discussion
- ❌ Undocumented public APIs
- ❌ Code without tests
- ❌ TCK test modifications

## Development Workflow

### Local Development

```bash
# Build all modules
mvn clean install

# Run tests
mvn test

# Run specific module tests
mvn test -pl paramodel-engine

# Generate coverage report
mvn jacoco:report

# Run TCK validation
mvn test -pl paramodel-tck
```

### Running Examples

```bash
# Compile examples
javac -cp "target/classes:..." examples/*.java

# Run example
java examples.BasicUsageExample
```

### Debugging

Use your IDE's debugger. Set breakpoints in:
- Compilation stages (`paramodel-engine/compiler/`)
- Execution runtime (`paramodel-engine/execution/`)
- TCK tests for validation

## Questions?

- **Issues**: https://github.com/nosqlbench/paramodel/issues
- **Discussions**: https://github.com/nosqlbench/paramodel/discussions
- **Documentation**: See module READMEs

## License

By contributing, you agree that your contributions will be licensed under the Apache License 2.0.

## Recognition

Contributors will be listed in CONTRIBUTORS.md and acknowledged in release notes.

Thank you for contributing to Paramodel! 🎉
