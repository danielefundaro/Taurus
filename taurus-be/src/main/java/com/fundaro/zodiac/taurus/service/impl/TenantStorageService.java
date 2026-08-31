package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.UUID;

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

    public Path resolveStorageKey(String tenantCode, String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("Storage key is required");
        }
        Path relative = Paths.get(storageKey.replace('/', java.io.File.separatorChar)).normalize();
        if (relative.isAbsolute() || relative.startsWith("..")) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        Path resolved = getTenantRoot(tenantCode).resolve(relative).toAbsolutePath().normalize();
        if (!resolved.startsWith(getTenantRoot(tenantCode))) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return resolved;
    }

    public Path createTemporaryDirectory(String tenantCode, String purpose) throws IOException {
        String safePurpose = requireSafePart(purpose);
        Path temporaryRoot = resolve(tenantCode, ".tmp", safePurpose);
        Files.createDirectories(temporaryRoot);
        return Files.createTempDirectory(temporaryRoot, "work-");
    }

    public void writeAtomically(String tenantCode, String storageKey, byte[] content) throws IOException {
        Path target = resolveStorageKey(tenantCode, storageKey);
        Files.createDirectories(target.getParent());
        Path temporary = target.resolveSibling("." + target.getFileName() + "." + UUID.randomUUID() + ".tmp");
        try {
            Files.write(temporary, content);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public byte[] read(String tenantCode, String storageKey) throws IOException {
        return Files.readAllBytes(resolveStorageKey(tenantCode, storageKey));
    }

    public boolean delete(String tenantCode, String storageKey) throws IOException {
        return Files.deleteIfExists(resolveStorageKey(tenantCode, storageKey));
    }

    public void deleteDirectoryIfManaged(String tenantCode, Path directory) throws IOException {
        if (directory == null) return;
        Path tenantRoot = getTenantRoot(tenantCode);
        Path target = directory.toAbsolutePath().normalize();
        if (target.equals(tenantRoot) || !target.startsWith(tenantRoot) || !Files.exists(target)) {
            return;
        }
        try (var paths = Files.walk(target)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
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

    private static String requireSafePart(String value) {
        if (value == null || !value.matches("[a-z0-9][a-z0-9-]{0,63}")) {
            throw new IllegalArgumentException("Invalid storage category");
        }
        return value;
    }
}
