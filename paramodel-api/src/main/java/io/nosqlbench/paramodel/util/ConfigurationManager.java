package io.nosqlbench.paramodel.util;

import java.util.Map;
import java.util.Optional;

///
/// # ConfigurationManager
///
/// Manages configuration settings for the paramodel system.
///
public interface ConfigurationManager {

    static ConfigurationManager create() {
        throw new UnsupportedOperationException(
            "ConfigurationManager.create() requires a concrete implementation");
    }

    void set(String key, Object value);

    Optional<Object> get(String key);

    <T> Optional<T> get(String key, Class<T> type);

    Map<String, Object> getAll();

    void load(String path);

    void save(String path);
}
