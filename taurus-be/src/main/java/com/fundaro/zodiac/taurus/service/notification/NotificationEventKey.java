package com.fundaro.zodiac.taurus.service.notification;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

public final class NotificationEventKey {

    public static final int MAX_LENGTH = 160;

    private NotificationEventKey() {}

    public static String deterministic(Object... components) {
        String value = Arrays.stream(components)
            .filter(Objects::nonNull)
            .map(Object::toString)
            .map(String::trim)
            .filter(component -> !component.isEmpty())
            .map(component -> component.toLowerCase(Locale.ROOT))
            .collect(Collectors.joining(":"));
        if (value.isEmpty()) throw new IllegalArgumentException("At least one event-key component is required");
        return fit(value);
    }

    public static String random(Object... components) {
        Object[] values = Arrays.copyOf(components, components.length + 1);
        values[components.length] = UUID.randomUUID();
        return deterministic(values);
    }

    public static String fit(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) throw new IllegalArgumentException("eventKey is required");
        String value = rawValue.trim();
        return value.length() <= MAX_LENGTH ? value : "sha256:" + sha256(value);
    }

    public static String hashForLog(String eventKey) {
        return sha256(Objects.requireNonNullElse(eventKey, "")).substring(0, 16);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
