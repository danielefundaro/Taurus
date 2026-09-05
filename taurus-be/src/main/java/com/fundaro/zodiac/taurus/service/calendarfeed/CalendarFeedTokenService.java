package com.fundaro.zodiac.taurus.service.calendarfeed;

import java.security.*;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class CalendarFeedTokenService {
    private final SecureRandom random = new SecureRandom();
    public Token generate() {
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        return new Token(Base64.getUrlEncoder().withoutPadding().encodeToString(raw), digest(raw));
    }
    public byte[] decodeAndDigest(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) return null;
        try { return digest(Base64.getUrlDecoder().decode(token)); }
        catch (IllegalArgumentException ignored) { return null; }
    }
    public String fingerprint(byte[] digest) {
        return java.util.HexFormat.of().formatHex(digest).substring(0, 12);
    }
    private static byte[] digest(byte[] raw) {
        try { return MessageDigest.getInstance("SHA-256").digest(raw); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public record Token(String value, byte[] digest) {}
}
