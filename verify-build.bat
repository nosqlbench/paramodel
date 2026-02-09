@echo off
REM Build verification script for Paramodel project (Windows)
REM Runs a complete build and validation cycle

setlocal enabledelayedexpansion

echo ======================================
echo   Paramodel Build Verification
echo ======================================
echo.

REM Check Java version
echo Checking Java version...
java -version 2>&1 | findstr /C:"version" > nul
if errorlevel 1 (
    echo [ERROR] Java not found
    exit /b 1
)
echo [OK] Java found
echo.

REM Check Maven version
echo Checking Maven version...
where mvn > nul 2>&1
if errorlevel 1 (
    echo [ERROR] Maven not found
    exit /b 1
)
echo [OK] Maven found
echo.

REM Clean previous builds
echo Cleaning previous builds...
call mvn clean -q
if errorlevel 1 (
    echo [ERROR] Clean failed
    exit /b 1
)
echo [OK] Clean complete
echo.

REM Compile all modules
echo Compiling all modules...
call mvn compile -q
if errorlevel 1 (
    echo [ERROR] Compilation failed
    exit /b 1
)
echo [OK] Compilation successful
echo.

REM Run tests
echo Running tests...
call mvn test
if errorlevel 1 (
    echo [ERROR] Tests failed
    exit /b 1
)
echo [OK] All tests passed
echo.

REM Package all modules
echo Packaging modules...
call mvn package -DskipTests -q
if errorlevel 1 (
    echo [ERROR] Packaging failed
    exit /b 1
)
echo [OK] Packaging successful
echo.

REM Install to local repository
echo Installing to local Maven repository...
call mvn install -DskipTests -q
if errorlevel 1 (
    echo [ERROR] Installation failed
    exit /b 1
)
echo [OK] Installation successful
echo.

REM Verify module structure
echo Verifying module structure...
set MODULES=paramodel-api paramodel-mock paramodel-tck paramodel-engine
for %%m in (%MODULES%) do (
    if exist %%m\target (
        echo [OK] %%m built
    ) else (
        echo [ERROR] %%m missing
        exit /b 1
    )
)
echo.

REM Check for key artifacts
echo Checking artifacts...
if exist paramodel-api\target\paramodel-api-0.1.0-SNAPSHOT.jar (
    echo [OK] paramodel-api JAR found
) else (
    echo [ERROR] paramodel-api JAR missing
    exit /b 1
)

if exist paramodel-mock\target\paramodel-mock-0.1.0-SNAPSHOT.jar (
    echo [OK] paramodel-mock JAR found
) else (
    echo [ERROR] paramodel-mock JAR missing
    exit /b 1
)

if exist paramodel-tck\target\paramodel-tck-0.1.0-SNAPSHOT.jar (
    echo [OK] paramodel-tck JAR found
) else (
    echo [ERROR] paramodel-tck JAR missing
    exit /b 1
)

if exist paramodel-engine\target\paramodel-engine-0.1.0-SNAPSHOT.jar (
    echo [OK] paramodel-engine JAR found
) else (
    echo [ERROR] paramodel-engine JAR missing
    exit /b 1
)
echo.

REM Run TCK validation
echo Running TCK validation...
call mvn test -pl paramodel-tck -q
if errorlevel 1 (
    echo [WARN] TCK validation had issues
) else (
    echo [OK] TCK validation passed
)
echo.

REM Summary
echo ======================================
echo [OK] Build verification PASSED
echo ======================================
echo.
echo Next steps:
echo   - View artifacts in target\ directories
echo   - Read documentation: type README.md
echo.

endlocal
