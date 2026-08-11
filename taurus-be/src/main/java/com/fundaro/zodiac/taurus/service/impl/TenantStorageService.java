package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

@Service
public class TenantStorageService {

    private final Path basePath;

    public TenantStorageService(ApplicationProperties applicationProperties) {
        this.basePath = Paths.get(applicationProperties.getBasePath()).toAbsolutePath().normalize();
    }

    public Path resolve(String tenantCode, String... parts) {
        Path tenantRoot = getTenantRoot(tenantCode);
        Path resolved = tenantRoot;
        for (String part : parts) {
            resolved = resolved.resolve(part);
        }
        resolved = resolved.toAbsolutePath().normalize();
        if (!resolved.startsWith(tenantRoot)) {
            throw new IllegalArgumentException("Invalid tenant storage path");
        }
        return resolved;
    }

    public boolean deleteFileIfManaged(String rawPath) throws IOException {
        if (rawPath == null || rawPath.isBlank()) {
            return false;
        }
        Path path = Paths.get(rawPath).toAbsolutePath().normalize();
        if (!path.startsWith(basePath) || path.equals(basePath)) {
            return false;
        }
        return Files.deleteIfExists(path);
    }

    public void deleteTenantDirectory(String tenantCode) throws IOException {
        Path tenantRoot = getTenantRoot(tenantCode);
        if (!Files.exists(tenantRoot)) {
            return;
        }
        try (var paths = Files.walk(tenantRoot)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private Path getTenantRoot(String tenantCode) {
        if (tenantCode == null || tenantCode.isBlank()) {
            throw new IllegalArgumentException("Tenant code is required");
        }
        String safeTenantCode = tenantCode.trim().toLowerCase().replaceAll("[^a-z0-9._-]", "_");
        Path tenantRoot = basePath.resolve(safeTenantCode).toAbsolutePath().normalize();
        if (tenantRoot.equals(basePath) || !tenantRoot.startsWith(basePath)) {
            throw new IllegalArgumentException("Invalid tenant code");
        }
        return tenantRoot;
    }
}
