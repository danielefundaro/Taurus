package com.fundaro.zodiac.taurus.service.signature;

import com.fundaro.zodiac.taurus.security.SecurityUtils;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;

final class SignatureSupport {

    static final String ENTITY = "signature";

    private SignatureSupport() {
    }

    static String actor(AbstractAuthenticationToken token) {
        String value = SecurityUtils.getUserIdFromAuthentication(token);
        if (value == null || value.isBlank())
            throw error(HttpStatus.UNAUTHORIZED, "Identità utente non disponibile", "auth.identity.missing");
        return value;
    }

    static String tenant(AbstractAuthenticationToken token) {
        String value = SecurityUtils.getTenantIdFromAuthentication(token);
        if (value == null || value.isBlank())
            throw error(HttpStatus.FORBIDDEN, "Tenant non disponibile", "auth.tenant.missing");
        return value;
    }

    static RequestAlertException error(HttpStatus status, String message, String key) {
        return new RequestAlertException(status, message, ENTITY, key);
    }

    static String sha256(String value) {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    /**
     * Shows the first character of the local part and the domain: {@code m***@example.org}.
     */
    static String maskEmail(String email) {
        if (email == null || email.isBlank()) return null;
        String value = email.trim();
        int at = value.indexOf('@');
        if (at <= 0) return "***";
        return value.charAt(0) + "***" + value.substring(at).toLowerCase(Locale.ROOT);
    }

    static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
