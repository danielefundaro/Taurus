package com.fundaro.zodiac.taurus.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TenantStorageServiceTest {

    @TempDir
    Path tempDirectory;

    private TenantStorageService service;

    @BeforeEach
    void setUp() {
        ApplicationProperties properties = new ApplicationProperties();
        properties.setBasePath(tempDirectory.toString());
        service = new TenantStorageService(properties);
    }

    @Test
    void resolvesFilesInsideTheTenantDirectory() {
        Path path = service.resolve("Tenant A", "to_process", "document.pdf");

        assertThat(path.startsWith(tempDirectory.resolve("tenant_a").toAbsolutePath().normalize())).isTrue();
        assertThat(path.getFileName()).hasToString("document.pdf");
    }

    @Test
    void rejectsPathsEscapingTheTenantDirectory() {
        assertThatThrownBy(() -> service.resolve("tenant", "..", "other-tenant", "document.pdf"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resolvesRelativeStorageKeysInsideTheSelectedTenantOnly() throws Exception {
        String key = "inventory/550e8400-e29b-41d4-a716-446655440000/digest.jpg";

        service.writeAtomically("Tenant A", key, new byte[] { 1, 2, 3 });

        Path tenantAFile = tempDirectory.resolve("tenant_a").resolve(key.replace('/', java.io.File.separatorChar));
        Path tenantBFile = tempDirectory.resolve("tenant_b").resolve(key.replace('/', java.io.File.separatorChar));
        assertThat(tenantAFile).hasBinaryContent(new byte[] { 1, 2, 3 });
        assertThat(tenantBFile).doesNotExist();
    }

    @Test
    void rejectsAbsoluteAndTraversingStorageKeys() {
        assertThatThrownBy(() -> service.resolveStorageKey("tenant", "../other-tenant/document.pdf"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.resolveStorageKey("tenant", tempDirectory.resolve("document.pdf").toString()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void physicallyDeletesTheTenantDirectory() throws Exception {
        Path file = service.resolve("tenant", "media", "document.pdf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "test");

        service.deleteTenantDirectory("tenant");

        assertThat(tempDirectory.resolve("tenant")).doesNotExist();
    }

    @Test
    void doesNotDeleteFilesOutsideManagedStorage() throws Exception {
        Path outsideFile = Files.createTempFile("taurus-outside", ".txt");
        try {
            assertThat(service.deleteFileIfManaged(outsideFile.toString())).isFalse();
            assertThat(outsideFile).exists();
        } finally {
            Files.deleteIfExists(outsideFile);
        }
    }
}
