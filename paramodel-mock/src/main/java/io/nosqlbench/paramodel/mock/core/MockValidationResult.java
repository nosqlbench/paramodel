package io.nosqlbench.paramodel.mock.core;

import io.nosqlbench.paramodel.core.ValidationResult;

import java.util.List;
import java.util.Optional;

/**
 * Simple validation result implementations.
 */
public sealed interface MockValidationResult extends ValidationResult {

    record Passed() implements MockValidationResult {
        @Override
        public boolean isPassed() {
            return true;
        }

        @Override
        public boolean isFailed() {
            return false;
        }

        @Override
        public Optional<String> message() {
            return Optional.empty();
        }

        @Override
        public List<String> violations() {
            return List.of();
        }
    }

    record Failed(String message, List<String> violations) implements MockValidationResult {
        public Failed(String message) {
            this(message, List.of(message));
        }

        @Override
        public boolean isPassed() {
            return false;
        }

        @Override
        public boolean isFailed() {
            return true;
        }

        @Override
        public Optional<String> message() {
            return Optional.of(message);
        }
    }

    record Warning(String message) implements MockValidationResult {
        @Override
        public boolean isPassed() {
            return true; // Warnings don't fail validation
        }

        @Override
        public boolean isFailed() {
            return false;
        }

        @Override
        public Optional<String> message() {
            return Optional.of(message);
        }

        @Override
        public List<String> violations() {
            return List.of();
        }
    }

    static MockValidationResult passed() {
        return new Passed();
    }

    static MockValidationResult failed(String message) {
        return new Failed(message);
    }

    static MockValidationResult warning(String message) {
        return new Warning(message);
    }
}
