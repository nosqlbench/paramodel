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

# Getting Started

This tutorial walks you through installing, building, and verifying Paramodel
so that you have a working development environment ready for the rest of the
tutorial series.

## Prerequisites

You need three tools on your machine before you begin:

| Tool | Minimum version | Download |
|------|----------------|----------|
| Java (OpenJDK) | 25+ | [jdk.java.net/25](https://jdk.java.net/25/) |
| Apache Maven | 3.9.0+ | [maven.apache.org](https://maven.apache.org/download.cgi) |
| Git | any recent | [git-scm.com](https://git-scm.com/) |

Verify that each tool is available and at the required version:

```bash
java -version   # Should show 25 or higher
mvn -version    # Should show 3.9.0 or higher
git --version
```

If `java -version` reports an older release, install OpenJDK 25 and make sure it
appears first on your `PATH`.

## Step 1: Clone the Repository

```bash
git clone https://github.com/nosqlbench/paramodel.git
cd paramodel
```

The repository contains four Maven modules:

| Module | Purpose |
|--------|---------|
| `paramodel-api` | Contract interfaces that every implementation must satisfy |
| `paramodel-mock` | Lightweight mock implementations for testing and learning |
| `paramodel-engine` | Production compiler and executor |
| `paramodel-tck` | Technology Compatibility Kit -- tests that validate any implementation |

## Step 2: Build All Modules

```bash
mvn clean install
```

This compiles every module, runs unit tests, and installs the artifacts into your
local Maven repository so that downstream modules (and your own projects) can
resolve them.

## Step 3: Verify the Build

Run the full verification suite to make sure everything is healthy:

```bash
mvn clean verify
```

Verification goes beyond a basic build. It checks:

- **Compilation** -- Java 25 source and target
- **Unit tests** -- JUnit 5 via Surefire
- **Code coverage** -- JaCoCo thresholds enforced per module
- **Javadoc correctness** -- build fails on Javadoc warnings
- **TCK conformance** -- mock implementations pass contract tests

A clean `BUILD SUCCESS` message means your environment is ready.

## Step 4 (alternative): Add Maven Dependencies

If you want to use Paramodel in an existing project instead of building from
source, add the following to your `pom.xml`:

```xml
<dependencies>
    <!-- API contracts -->
    <dependency>
        <groupId>io.nosqlbench</groupId>
        <artifactId>paramodel-api</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </dependency>

    <!-- Mock implementations for testing and prototyping -->
    <dependency>
        <groupId>io.nosqlbench</groupId>
        <artifactId>paramodel-mock</artifactId>
        <version>0.1.0-SNAPSHOT</version>
        <scope>test</scope>
    </dependency>

    <!-- Production compiler and executor -->
    <dependency>
        <groupId>io.nosqlbench</groupId>
        <artifactId>paramodel-engine</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </dependency>
</dependencies>
```

All artifacts use **groupId** `io.nosqlbench` and **version** `0.1.0-SNAPSHOT`.

## Step 5: Run the Examples

The `examples/` directory at the project root contains four self-contained
programs that demonstrate the major features:

| Example | What it shows |
|---------|---------------|
| `BasicUsageExample.java` | Defining parameters, building a test plan, creating trials |
| `CompilationPipelineExample.java` | Compiling a test plan through the 8-stage pipeline |
| `ExecutionExample.java` | Executing trials concurrently and collecting results |
| `ConstraintsExample.java` | Applying constraints to filter the parameter space |

Browse these files to get a feel for the API before continuing to the next
tutorial.

## What You Learned

- How to verify that your Java and Maven versions meet the requirements.
- How to clone, build, and verify the Paramodel project.
- How to add Paramodel as a Maven dependency in your own project.
- Where the example programs live and what each one demonstrates.

## Next Steps

- **[Your First Test Plan](first-test-plan.md)** -- build a simple 2-parameter
  test plan from scratch.
- **[Parameters and Domains](../concepts/parameters-and-domains.md)** -- learn
  the conceptual model behind parameters, domains, and values.
- **[The Compilation Pipeline](compilation-pipeline.md)** -- understand how a
  TestPlan becomes an ExecutionPlan through 8 stages.
