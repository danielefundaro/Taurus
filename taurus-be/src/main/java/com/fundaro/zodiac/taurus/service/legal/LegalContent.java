package com.fundaro.zodiac.taurus.service.legal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * Rules of the text and of the version of a legal document: the text is normalized before its fingerprint, so the
 * same words always give the same SHA-256, and the version is safe inside an address.
 */
public final class LegalContent {

    public static final int MAX_LENGTH = 100_000;

    private static final Pattern VERSION = Pattern.compile("[0-9A-Za-z][0-9A-Za-z._-]{0,31}");

    private LegalContent() {
    }

    /** Line breaks become {@code \n} and the blanks at the end of the text go away. */
    public static String normalize(String text) {
        if (text == null) return null;
        return text.replace("\r\n", "\n").replace('\r', '\n').stripTrailing();
    }

    /** Lowercase hexadecimal SHA-256 of the UTF-8 bytes of an already normalized text. */
    public static String sha256(String normalizedText) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(normalizedText.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    /** From 1 to 32 letters, digits, dots, hyphens and underscores, starting with a letter or a digit. */
    public static boolean isValidVersion(String version) {
        return version != null && VERSION.matcher(version).matches();
    }
}
