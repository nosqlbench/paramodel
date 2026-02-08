package io.nosqlbench.paramodel.util;

import java.io.InputStream;
import java.io.OutputStream;

///
/// # SerializationUtil
///
/// Utility for serializing and deserializing paramodel objects.
///
public interface SerializationUtil {

    static SerializationUtil create() {
        throw new UnsupportedOperationException(
            "SerializationUtil.create() requires a concrete implementation");
    }

    <T> void serialize(T object, OutputStream output);

    <T> T deserialize(InputStream input, Class<T> type);

    <T> String toJson(T object);

    <T> T fromJson(String json, Class<T> type);
}
