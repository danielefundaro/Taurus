package com.fundaro.zodiac.taurus.web.rest.errors;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parameters of an application error, sent to the client as {@code args}. Only domain data the caller can already see
 * is allowed, in a closed set of types: dates (as ISO {@code yyyy-MM-dd}), integers, strings, booleans, lists of those
 * and nested parameter maps. Never exception messages or technical details.
 */
public final class ErrorArgs {

    private final Map<String, Object> values = new LinkedHashMap<>();

    public static ErrorArgs of(String name, Object value) {
        return new ErrorArgs().with(name, value);
    }

    public ErrorArgs with(String name, Object value) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Error argument name is required");
        if (value != null) values.put(name, normalize(value));
        return this;
    }

    /**
     * Copies the parameters of another set, for example those of a rule violation.
     */
    public ErrorArgs withAll(ErrorArgs other) {
        if (other != null) values.putAll(other.values);
        return this;
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public Map<String, Object> values() {
        return Collections.unmodifiableMap(values);
    }

    private static Object normalize(Object value) {
        if (value instanceof LocalDate date) return date.toString();
        if (value instanceof String || value instanceof Boolean || value instanceof Integer || value instanceof Long || value instanceof Short) {
            return value;
        }
        if (value instanceof ErrorArgs args) return args.values();
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            map.forEach((name, element) -> {
                if (element != null) normalized.put(String.valueOf(name), normalize(element));
            });
            return Collections.unmodifiableMap(normalized);
        }
        if (value instanceof Collection<?> collection) {
            List<Object> list = new ArrayList<>(collection.size());
            collection.forEach(element -> list.add(normalize(element)));
            return Collections.unmodifiableList(list);
        }
        throw new IllegalArgumentException("Unsupported error argument type: " + value.getClass().getName());
    }
}
