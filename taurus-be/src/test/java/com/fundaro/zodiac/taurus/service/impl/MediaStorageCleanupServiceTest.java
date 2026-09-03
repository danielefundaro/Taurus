package com.fundaro.zodiac.taurus.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.repository.MediaRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MediaStorageCleanupServiceTest {

    private static final String TENANT = "tenant-a";

    @TempDir
    Path basePath;

    @Mock
    MediaRepository mediaRepository;

    private TenantStorageService tenantStorageService;
    private MediaStorageCleanupService service;
    private Path tenantRoot;

    @BeforeEach
    void setUp() {
        ApplicationProperties properties = new ApplicationProperties();
        properties.setBasePath(basePath.toString());
        tenantStorageService = new TenantStorageService(properties);
        service = new MediaStorageCleanupService(mediaRepository, tenantStorageService, properties);
        tenantRoot = basePath.resolve(TENANT);
    }

    @Test
    void keepsFilesStillRegisteredAsMediaAssets() throws Exception {
        String key = "inventory/asset-1/digest.jpg";
        Path file = aged(write(key), 200);
        when(mediaRepository.findActiveStorageKeys()).thenReturn(List.of(key));

        service.cleanupCurrentTenant(TENANT);

        assertThat(file).exists();
    }

    @Test
    void removesFilesWithoutAMediaAssetRow() throws Exception {
        Path orphan = aged(write("inventory/asset-2/digest.jpg"), 200);
        when(mediaRepository.findActiveStorageKeys()).thenReturn(List.of());

        int removed = service.cleanupCurrentTenant(TENANT);

        assertThat(orphan).doesNotExist();
        assertThat(removed).isEqualTo(1);
    }

    @Test
    void keepsRecentFilesSoAnUploadInProgressIsNeverMistakenForAnOrphan() throws Exception {
        Path recent = write("inventory/asset-3/digest.jpg");
        when(mediaRepository.findActiveStorageKeys()).thenReturn(List.of());

        int removed = service.cleanupCurrentTenant(TENANT);

        assertThat(recent).exists();
        assertThat(removed).isZero();
    }

    @Test
    void removesStaleTemporaryLeftovers() throws Exception {
        Path work = tenantRoot.resolve(".tmp").resolve("pdf-processing").resolve("work-1");
        Files.createDirectories(work);
        Files.write(work.resolve("page.pdf"), new byte[] { 1 });
        aged(work, 30);
        Path partial = aged(write("inventory/.digest.jpg.1234.tmp"), 30);
        when(mediaRepository.findActiveStorageKeys()).thenReturn(List.of());

        service.cleanupCurrentTenant(TENANT);

        assertThat(work).doesNotExist();
        assertThat(partial).doesNotExist();
    }

    private Path write(String storageKey) throws Exception {
        tenantStorageService.writeAtomically(TENANT, storageKey, new byte[] { 1, 2, 3 });
        return tenantStorageService.resolveStorageKey(TENANT, storageKey);
    }

    private Path aged(Path path, int hours) throws Exception {
        Files.setLastModifiedTime(path, FileTime.from(Instant.now().minus(hours, ChronoUnit.HOURS)));
        return path;
    }
}
