package io.nosqlbench.paramodel.tck.util;

import io.nosqlbench.paramodel.tck.ImplementationProvider;
import io.nosqlbench.paramodel.util.SerializationUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

///
/// TCK tests for {@link SerializationUtil} implementations.
///
/// Validates serialize/deserialize round-trip and JSON
/// conversion operations.
///
/// @since 0.1.0
///
public abstract class SerializationUtilTCK {

    /// Returns the implementation provider under test.
    protected abstract ImplementationProvider getProvider();

    private SerializationUtil serializer;

    @BeforeEach
    void setUp() {
        serializer = getProvider().createSerializationUtil();
    }

    @Test
    void testSerializeAndDeserialize() {
        String original = "test-data-12345";
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        serializer.serialize(original, output);

        byte[] serialized = output.toByteArray();
        assertThat(serialized).isNotEmpty();

        String deserialized = serializer.deserialize(
            new ByteArrayInputStream(serialized), String.class);
        assertThat(deserialized).isEqualTo(original);
    }

    @Test
    void testToJsonAndFromJson() {
        String original = "json-test-value";
        String json = serializer.toJson(original);
        assertThat(json).isNotNull();
        assertThat(json).isNotEmpty();

        String restored = serializer.fromJson(json, String.class);
        assertThat(restored).isEqualTo(original);
    }

    @Test
    void testSerializeProducesNonEmptyOutput() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        serializer.serialize("hello world", output);
        assertThat(output.toByteArray()).isNotEmpty();
    }
}
