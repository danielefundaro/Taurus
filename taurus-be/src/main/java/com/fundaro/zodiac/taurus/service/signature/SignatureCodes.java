package com.fundaro.zodiac.taurus.service.signature;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

/**
 * Six-digit confirmation codes. Only their HMAC-SHA256 is stored, keyed with {@code application.signature.otp-secret}
 * and bound to the challenge, the signer, the purpose and the hash of the confirmed items, so a code is valid for that
 * confirmation only.
 */
@Component
public class SignatureCodes {

    private static final String HMAC = "HmacSHA256";
    private final SecureRandom random = new SecureRandom();
    private final ApplicationProperties.SignatureProperties properties;

    public SignatureCodes(ApplicationProperties applicationProperties) {
        this.properties = applicationProperties.getSignature();
    }

    public boolean configured() {
        String secret = properties.getOtpSecret();
        return secret != null && !secret.isBlank();
    }

    public String generate() {
        return String.format("%06d", random.nextInt(1_000_000));
    }

    public String digest(Binding binding, String code) {
        if (!configured()) throw new IllegalStateException("application.signature.otp-secret is not configured");
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(properties.getOtpSecret().getBytes(StandardCharsets.UTF_8), HMAC));
            String value = binding.challengeId() + "|" + binding.signer() + "|" + binding.purpose() + "|" + binding.itemsHash() + "|" + code;
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 is not available", exception);
        }
    }

    /**
     * Compares in constant time the code entered by the signer with the stored digest.
     */
    public boolean matches(Binding binding, String code, String storedDigest) {
        if (code == null || storedDigest == null || !code.matches("\\d{6}")) return false;
        return MessageDigest.isEqual(
            digest(binding, code).getBytes(StandardCharsets.US_ASCII),
            storedDigest.getBytes(StandardCharsets.US_ASCII)
        );
    }

    public record Binding(long challengeId, String signer, String purpose, String itemsHash) {
    }
}
