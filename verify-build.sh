#!/bin/bash
#
# Build verification script for Paramodel project
# Runs a complete build and validation cycle
#

set -e  # Exit on error

echo "======================================"
echo "  Paramodel Build Verification"
echo "======================================"
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check Java version
echo "Checking Java version..."
JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt "25" ]; then
    echo -e "${RED}✗ Java 25 or higher required (found: $JAVA_VERSION)${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Java version: $JAVA_VERSION${NC}"
echo ""

# Check Maven version
echo "Checking Maven version..."
if ! command -v mvn &> /dev/null; then
    echo -e "${RED}✗ Maven not found${NC}"
    exit 1
fi
MVN_VERSION=$(mvn -version | head -n 1 | cut -d' ' -f3)
echo -e "${GREEN}✓ Maven version: $MVN_VERSION${NC}"
echo ""

# Clean previous builds
echo "Cleaning previous builds..."
mvn clean -q
echo -e "${GREEN}✓ Clean complete${NC}"
echo ""

# Compile all modules
echo "Compiling all modules..."
if mvn compile -q; then
    echo -e "${GREEN}✓ Compilation successful${NC}"
else
    echo -e "${RED}✗ Compilation failed${NC}"
    exit 1
fi
echo ""

# Run tests
echo "Running tests..."
if mvn test; then
    echo -e "${GREEN}✓ All tests passed${NC}"
else
    echo -e "${RED}✗ Tests failed${NC}"
    exit 1
fi
echo ""

# Package all modules
echo "Packaging modules..."
if mvn package -DskipTests -q; then
    echo -e "${GREEN}✓ Packaging successful${NC}"
else
    echo -e "${RED}✗ Packaging failed${NC}"
    exit 1
fi
echo ""

# Install to local repository
echo "Installing to local Maven repository..."
if mvn install -DskipTests -q; then
    echo -e "${GREEN}✓ Installation successful${NC}"
else
    echo -e "${RED}✗ Installation failed${NC}"
    exit 1
fi
echo ""

# Verify module structure
echo "Verifying module structure..."
MODULES=("paramodel-api" "paramodel-mock" "paramodel-tck" "paramodel-engine")
for module in "${MODULES[@]}"; do
    if [ -d "$module/target" ]; then
        echo -e "${GREEN}✓ $module built${NC}"
    else
        echo -e "${RED}✗ $module missing${NC}"
        exit 1
    fi
done
echo ""

# Check for key artifacts
echo "Checking artifacts..."
ARTIFACTS=(
    "paramodel-api/target/paramodel-api-0.1.0-SNAPSHOT.jar"
    "paramodel-mock/target/paramodel-mock-0.1.0-SNAPSHOT.jar"
    "paramodel-tck/target/paramodel-tck-0.1.0-SNAPSHOT.jar"
    "paramodel-engine/target/paramodel-engine-0.1.0-SNAPSHOT.jar"
)
for artifact in "${ARTIFACTS[@]}"; do
    if [ -f "$artifact" ]; then
        SIZE=$(du -h "$artifact" | cut -f1)
        echo -e "${GREEN}✓ $artifact ($SIZE)${NC}"
    else
        echo -e "${RED}✗ $artifact missing${NC}"
        exit 1
    fi
done
echo ""

# Run TCK validation
echo "Running TCK validation..."
if mvn test -pl paramodel-tck -q; then
    echo -e "${GREEN}✓ TCK validation passed${NC}"
else
    echo -e "${YELLOW}⚠ TCK validation had issues${NC}"
fi
echo ""

# Generate reports
echo "Generating reports..."
if mvn site -DskipTests -q 2>/dev/null; then
    echo -e "${GREEN}✓ Reports generated${NC}"
else
    echo -e "${YELLOW}⚠ Report generation skipped${NC}"
fi
echo ""

# Summary
echo "======================================"
echo -e "${GREEN}✓ Build verification PASSED${NC}"
echo "======================================"
echo ""
echo "Next steps:"
echo "  - View artifacts in target/ directories"
echo "  - Run examples: java examples.BasicUsageExample"
echo "  - Read documentation: cat README.md"
echo ""
