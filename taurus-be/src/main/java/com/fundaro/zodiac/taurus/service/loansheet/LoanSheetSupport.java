package com.fundaro.zodiac.taurus.service.loansheet;

import com.fundaro.zodiac.taurus.security.SecurityUtils;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.HexFormat;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;

final class LoanSheetSupport {

    static final String ENTITY = "inventoryLoanSheet";

    private LoanSheetSupport() {
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

    static RequestAlertException notFound(String message, String key) {
        return error(HttpStatus.NOT_FOUND, message, key);
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    static LocalDate localDate(ZonedDateTime value, ZoneId zone) {
        return value == null ? null : value.withZoneSameInstant(zone).toLocalDate();
    }

    static LocalDate localDate(Date value, ZoneId zone) {
        if (value == null) return null;
        if (value instanceof java.sql.Date sqlDate) return sqlDate.toLocalDate();
        return value.toInstant().atZone(zone).toLocalDate();
    }

    static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
