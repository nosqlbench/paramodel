/*
 * Copyright (c) nosqlbench
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.nosqlbench.paramodel.engine.compiler;

import io.nosqlbench.paramodel.compilation.Compiler;
import io.nosqlbench.paramodel.compilation.Compiler.CompilationError;
import io.nosqlbench.paramodel.compilation.Compiler.CompilationWarning;
import io.nosqlbench.paramodel.compilation.Compiler.ErrorSeverity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/// Mutable accumulator implementing {@link Compiler.ValidationResult}.
///
/// Collects diagnostics at all severity levels (ERROR, WARNING, INFO) during
/// validation. Each diagnostic is represented as a {@link Diagnostic} record
/// carrying optional {@code code} and {@code explanation} metadata from the
/// paramodel compilation API.
///
/// The {@link #downgradeError} method supports draft-mode validation by
/// converting specific error codes to INFO severity, allowing incremental
/// plan construction.
public class DefaultValidationResult implements Compiler.ValidationResult {
    private final List<Diagnostic> diagnostics = new ArrayList<>();

    // --- Accumulation methods ---

    /// Adds an error-level diagnostic.
    public void addError(String code, String message) {
        diagnostics.add(Diagnostic.of(ErrorSeverity.ERROR, code, message, null, null, null));
    }

    /// Adds an error-level diagnostic with explanation.
    public void addError(String code, String message, String explanation) {
        diagnostics.add(Diagnostic.of(ErrorSeverity.ERROR, code, message, null, explanation, null));
    }

    /// Adds an error-level diagnostic with location, explanation, and suggestions.
    public void addError(String code, String message, String location,
                         String explanation, List<String> suggestions) {
        diagnostics.add(Diagnostic.of(ErrorSeverity.ERROR, code, message, location,
                explanation, joinSuggestions(suggestions)));
    }

    /// Adds a warning-level diagnostic.
    public void addWarning(String code, String message) {
        diagnostics.add(Diagnostic.of(ErrorSeverity.WARNING, code, message, null, null, null));
    }

    /// Adds a warning-level diagnostic with explanation.
    public void addWarning(String code, String message, String explanation) {
        diagnostics.add(Diagnostic.of(ErrorSeverity.WARNING, code, message, null, explanation, null));
    }

    /// Adds a warning-level diagnostic with location, explanation, and suggestions.
    public void addWarning(String code, String message, String location,
                           String explanation, List<String> suggestions) {
        diagnostics.add(Diagnostic.of(ErrorSeverity.WARNING, code, message, location,
                explanation, joinSuggestions(suggestions)));
    }

    /// Adds an info-level diagnostic.
    public void addInfo(String code, String message) {
        diagnostics.add(Diagnostic.of(ErrorSeverity.INFO, code, message, null, null, null));
    }

    /// Adds an info-level diagnostic with explanation.
    public void addInfo(String code, String message, String explanation) {
        diagnostics.add(Diagnostic.of(ErrorSeverity.INFO, code, message, null, explanation, null));
    }

    // --- Compiler.ValidationResult interface ---

    @Override
    public boolean isValid() {
        return !hasErrors();
    }

    @Override
    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(d -> d.severity() == ErrorSeverity.ERROR);
    }

    @Override
    public boolean hasWarnings() {
        return diagnostics.stream().anyMatch(d -> d.severity() == ErrorSeverity.WARNING);
    }

    @Override
    public List<CompilationError> errors() {
        return diagnostics.stream()
                .filter(d -> d.severity() == ErrorSeverity.ERROR)
                .<CompilationError>map(d -> d)
                .toList();
    }

    @Override
    public List<CompilationWarning> warnings() {
        return diagnostics.stream()
                .filter(d -> d.severity() == ErrorSeverity.WARNING)
                .<CompilationWarning>map(d -> d)
                .toList();
    }

    @Override
    public List<CompilationError> diagnostics() {
        return diagnostics.stream()
                .<CompilationError>map(d -> d)
                .toList();
    }

    // --- Draft validation and formatting ---

    /// Downgrades all diagnostics with the given error code from ERROR to INFO.
    ///
    /// This allows draft-mode validation to treat specific errors as
    /// informational notes rather than blocking issues.
    ///
    /// @param code the error code to downgrade
    public void downgradeError(String code) {
        List<Diagnostic> downgraded = new ArrayList<>();
        var iterator = diagnostics.iterator();
        while (iterator.hasNext()) {
            Diagnostic d = iterator.next();
            if (d.severity() == ErrorSeverity.ERROR
                    && code.equals(d.code().orElse(null))) {
                iterator.remove();
                downgraded.add(Diagnostic.of(ErrorSeverity.INFO, code, d.message(),
                        d.location().orElse(null),
                        d.explanation().orElse(null),
                        d.suggestion().orElse(null)));
            }
        }
        diagnostics.addAll(downgraded);
    }

    /// Returns a formatted string of all diagnostics.
    public String format() {
        if (diagnostics.isEmpty()) {
            return "Validation passed: no issues found.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Validation Report:\n");
        sb.append("==================\n");

        long errors = diagnostics.stream().filter(d -> d.severity() == ErrorSeverity.ERROR).count();
        long warnings = diagnostics.stream().filter(d -> d.severity() == ErrorSeverity.WARNING).count();
        sb.append(String.format("%d error(s), %d warning(s)%n%n", errors, warnings));

        for (Diagnostic d : diagnostics) {
            String loc = d.location().map(l -> " at " + l).orElse("");
            sb.append(String.format("  [%s] %s: %s%s%n",
                    d.severity(), d.code().orElse(""), d.message(), loc));
        }

        return sb.toString();
    }

    @Override
    public String toString() {
        return format();
    }

    // --- Internal ---

    private static String joinSuggestions(List<String> suggestions) {
        if (suggestions == null || suggestions.isEmpty()) {
            return null;
        }
        return String.join("; ", suggestions);
    }

    /// Diagnostic record implementing both {@link CompilationError} and
    /// {@link CompilationWarning}. All fields beyond {@code severity} and
    /// {@code message} are optional, matching the paramodel interface
    /// contracts.
    record Diagnostic(
            ErrorSeverity severity,
            Optional<String> code,
            String message,
            Optional<String> location,
            Optional<String> explanation,
            Optional<String> suggestion
    ) implements CompilationError, CompilationWarning {

        static Diagnostic of(ErrorSeverity severity, String code, String message,
                             String location, String explanation, String suggestion) {
            return new Diagnostic(severity,
                    Optional.ofNullable(code), message,
                    Optional.ofNullable(location),
                    Optional.ofNullable(explanation),
                    Optional.ofNullable(suggestion));
        }

        @Override
        public String toString() {
            String loc = location.map(l -> " at " + l).orElse("");
            return String.format("[%s] %s: %s%s", severity, code.orElse(""), message, loc);
        }
    }
}
