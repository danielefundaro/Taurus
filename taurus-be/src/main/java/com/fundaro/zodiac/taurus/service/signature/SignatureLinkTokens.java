package com.fundaro.zodiac.taurus.service.signature;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Tokens of the signature links: 32 random bytes in base64url, of which only the SHA-256 digest and its first 8
 * characters are stored. A token carries neither tenant, act nor user.
 */
@Component
public class SignatureLinkTokens {

    private static final Pattern FORMAT = Pattern.compile("^[A-Za-z0-9_-]{43}$");
    static final int FINGERPRINT_LENGTH = 8;

    private final SecureRandom random = new SecureRandom();

    public Token generate() {
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        return new Token(Base64.getUrlEncoder().withoutPadding().encodeToString(raw), SignatureSupport.sha256(raw));
    }

    /**
     * The hexadecimal digest of a token, or {@code null} when the value cannot be a token.
     */
    public String digest(String token) {
        if (token == null || !FORMAT.matcher(token).matches()) return null;
        try {
            return SignatureSupport.sha256(Base64.getUrlDecoder().decode(token));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public static String fingerprint(String digest) {
        return digest.substring(0, FINGERPRINT_LENGTH);
    }

    /**
     * Compares the fingerprint stored with a link and the one of a digest in constant time.
     */
    public static boolean matches(String fingerprint, String digest) {
        return fingerprint != null && MessageDigest.isEqual(
            fingerprint.getBytes(StandardCharsets.US_ASCII),
            fingerprint(digest).getBytes(StandardCharsets.US_ASCII)
        );
    }

    public record Token(String value, String digest) {
        @Override
        public String toString() {
            return "Token[fingerprint=" + fingerprint(digest) + "]";
        }
    }
}
