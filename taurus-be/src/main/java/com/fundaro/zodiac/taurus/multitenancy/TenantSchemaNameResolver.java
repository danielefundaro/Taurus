package com.fundaro.zodiac.taurus.multitenancy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Generates deterministic PostgreSQL identifiers without embedding user input.
 */
@Component
public class TenantSchemaNameResolver {

    public static final String DEFAULT_SCHEMA = "public";
    private static final String PREFIX = "tenant_";
    private static final Pattern SAFE_SCHEMA_NAME = Pattern.compile("tenant_[a-f0-9]{32}");

    public String resolve(String tenantCode) {
        if (tenantCode == null || tenantCode.isBlank()) {
            return DEFAULT_SCHEMA;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(tenantCode.getBytes(StandardCharsets.UTF_8));
            return PREFIX + HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    public void requireSafeSchemaName(String schemaName) {
        if (!SAFE_SCHEMA_NAME.matcher(schemaName).matches()) {
            throw new IllegalArgumentException("Invalid tenant schema name");
        }
    }
}
