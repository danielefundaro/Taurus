package com.fundaro.zodiac.taurus.service.calendarfeed;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

@Component
public class CalendarFeedIdempotencyCodec {
    private static final int NONCE_LENGTH = 12;
    private final SecureRandom random = new SecureRandom();

    public EncryptedToken encrypt(String token, UUID idempotencyKey, byte[] requestDigest) {
        byte[] nonce = new byte[NONCE_LENGTH];
        random.nextBytes(nonce);
        return new EncryptedToken(crypt(Cipher.ENCRYPT_MODE, token.getBytes(StandardCharsets.US_ASCII), idempotencyKey, requestDigest, nonce), nonce);
    }

    public String decrypt(byte[] ciphertext, byte[] nonce, UUID idempotencyKey, byte[] requestDigest) {
        return new String(crypt(Cipher.DECRYPT_MODE, ciphertext, idempotencyKey, requestDigest, nonce), StandardCharsets.US_ASCII);
    }

    public byte[] digest(String actor, String operation, UUID subscriptionId, UUID idempotencyKey) {
        return sha256((actor + "\u0000" + operation + "\u0000" + subscriptionId + "\u0000" + idempotencyKey).getBytes(StandardCharsets.UTF_8));
    }

    public byte[] fingerprint(String canonicalRequest) {
        return sha256(canonicalRequest.getBytes(StandardCharsets.UTF_8));
    }

    public long lockKey(byte[] requestDigest) {
        return ByteBuffer.wrap(requestDigest).getLong();
    }

    private static byte[] crypt(int mode, byte[] value, UUID key, byte[] aad, byte[] nonce) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, new SecretKeySpec(keyBytes(key), "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(aad);
            return cipher.doFinal(value);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Cannot protect calendar feed idempotency response", exception);
        }
    }

    private static byte[] keyBytes(UUID key) {
        ByteBuffer bytes = ByteBuffer.allocate(16);
        bytes.putLong(key.getMostSignificantBits());
        bytes.putLong(key.getLeastSignificantBits());
        return sha256(bytes.array());
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public record EncryptedToken(byte[] ciphertext, byte[] nonce) {
    }
}
