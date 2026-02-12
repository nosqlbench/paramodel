package io.nosqlbench.paramodel.mock.util;

import io.nosqlbench.paramodel.util.ConfigurationManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

///
/// In-memory configuration manager for testing.
///
/// Stores configuration values in a simple map. The `load()` and
/// `save()` methods are no-ops since no file system is involved.
///
/// @see ConfigurationManager
/// @since 0.1.0
///
public class MockConfigurationManager implements ConfigurationManager {
    private final Map<String, Object> config = new HashMap<>();

    /// Creates a new empty configuration manager.
    public MockConfigurationManager() {}

    @Override
    public void set(String key, Object value) {
        config.put(key, value);
    }

    @Override
    public Optional<Object> get(String key) {
        return Optional.ofNullable(config.get(key));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(String key, Class<T> type) {
        Object value = config.get(key);
        if (value != null && type.isInstance(value)) {
            return Optional.of((T) value);
        }
        return Optional.empty();
    }

    @Override
    public Map<String, Object> getAll() {
        return Map.copyOf(config);
    }

    @Override
    public void load(String path) {
        // no-op for mock
    }

    @Override
    public void save(String path) {
        // no-op for mock
    }
}
