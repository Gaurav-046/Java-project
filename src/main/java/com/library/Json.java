package com.library;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Standard request-body reading for the JSON API: the request InputStream is always consumed
 * fully and decoded as UTF-8, and an empty or null-mapping payload is rejected with the
 * {@link JsonSyntaxException} that every handler already translates into a 400 response.
 * Handlers must never invoke a DAO with a half-parsed body.
 */
final class Json {

    private static final Gson GSON = new Gson();

    private Json() {
    }

    /** Reads the request body fully and decodes it as UTF-8. */
    static String readBody(HttpExchange ex) throws IOException {
        return new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    /**
     * Reads and parses the JSON body into {@code type}. Missing (empty) or unparseable
     * payloads raise JsonSyntaxException so callers never invoke a DAO with bad input.
     */
    static <T> T read(HttpExchange ex, Class<T> type) throws IOException {
        String body = readBody(ex);
        if (body.isBlank()) {
            throw new JsonSyntaxException("Empty request body");
        }
        T value = GSON.fromJson(body, type);
        if (value == null) {
            throw new JsonSyntaxException("Null request body");
        }
        return value;
    }
}
