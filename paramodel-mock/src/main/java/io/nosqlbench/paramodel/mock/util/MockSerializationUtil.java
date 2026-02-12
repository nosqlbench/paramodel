package io.nosqlbench.paramodel.mock.util;

import io.nosqlbench.paramodel.util.SerializationUtil;

import java.io.*;
import java.nio.charset.StandardCharsets;

///
/// Simple serialization utility for testing.
///
/// Uses `toString()` for JSON-like serialization and stores objects
/// as their string representation. Deserialization supports `String` type
/// by reading the serialized content directly.
///
/// @see SerializationUtil
/// @since 0.1.0
///
public class MockSerializationUtil implements SerializationUtil {

    /// Creates a new serialization utility.
    public MockSerializationUtil() {}

    @Override
    public <T> void serialize(T object, OutputStream output) {
        try {
            output.write(object.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException("Serialization failed", e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T deserialize(InputStream input, Class<T> type) {
        try {
            String content = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            if (type == String.class) {
                return (T) content;
            }
            throw new UnsupportedOperationException(
                "MockSerializationUtil only supports String deserialization, got: " + type);
        } catch (IOException e) {
            throw new RuntimeException("Deserialization failed", e);
        }
    }

    @Override
    public <T> String toJson(T object) {
        return object.toString();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T fromJson(String json, Class<T> type) {
        if (type == String.class) {
            return (T) json;
        }
        throw new UnsupportedOperationException(
            "MockSerializationUtil only supports String deserialization, got: " + type);
    }
}
