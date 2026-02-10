package io.nosqlbench.paramodel.tck.util;

import io.nosqlbench.paramodel.tck.ImplementationProvider;
import io.nosqlbench.paramodel.util.ConfigurationManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

///
/// TCK tests for {@link ConfigurationManager} implementations.
///
/// Validates set, get, getAll, and typed get operations
/// for configuration management.
///
/// @since 0.1.0
///
public abstract class ConfigurationManagerTCK {

    /// Creates a new TCK test instance.
    protected ConfigurationManagerTCK() {}

    /// Returns the implementation provider under test.
    protected abstract ImplementationProvider getProvider();

    private ConfigurationManager configManager;

    @BeforeEach
    void setUp() {
        configManager = getProvider().createConfigurationManager();
    }

    @Test
    void testSetAndGet() {
        configManager.set("key1", "value1");

        Optional<Object> result = configManager.get("key1");
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo("value1");
    }

    @Test
    void testGetNonExistent() {
        Optional<Object> result = configManager.get("nonexistent");
        assertThat(result).isEmpty();
    }

    @Test
    void testGetAll() {
        configManager.set("a", 1);
        configManager.set("b", "two");
        configManager.set("c", true);

        Map<String, Object> all = configManager.getAll();
        assertThat(all).hasSize(3);
        assertThat(all).containsEntry("a", 1);
        assertThat(all).containsEntry("b", "two");
        assertThat(all).containsEntry("c", true);
    }

    @Test
    void testTypedGet() {
        configManager.set("name", "test-value");
        configManager.set("count", 42);

        Optional<String> name = configManager.get("name", String.class);
        assertThat(name).isPresent();
        assertThat(name.get()).isEqualTo("test-value");

        Optional<Integer> count = configManager.get("count", Integer.class);
        assertThat(count).isPresent();
        assertThat(count.get()).isEqualTo(42);

        Optional<Integer> wrongType = configManager.get("name", Integer.class);
        assertThat(wrongType).isEmpty();
    }
}
