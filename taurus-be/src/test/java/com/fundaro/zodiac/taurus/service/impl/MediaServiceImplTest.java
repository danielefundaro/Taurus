package com.fundaro.zodiac.taurus.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.Media;
import com.fundaro.zodiac.taurus.domain.enumeration.MediaAssetStatus;
import com.fundaro.zodiac.taurus.repository.MediaRepository;
import com.fundaro.zodiac.taurus.service.dto.MediaDTO;
import com.fundaro.zodiac.taurus.service.TenantFeatureService;
import com.fundaro.zodiac.taurus.service.mapper.MediaMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class MediaServiceImplTest {

    @TempDir Path tempDirectory;

    @Mock MediaRepository repository;
    @Mock MediaMapper mapper;
    @Mock TenantFeatureService tenantFeatureService;

    private MediaServiceImpl service;
    private final List<Media> savedMedia = new ArrayList<>();

    @BeforeEach
    void setUp() {
        ApplicationProperties properties = new ApplicationProperties();
        properties.setBasePath(tempDirectory.toString());
        service = new MediaServiceImpl(repository, mapper, new TenantStorageService(properties), tenantFeatureService);

        when(repository.save(any(Media.class))).thenAnswer(invocation -> {
            Media media = invocation.getArgument(0);
            media.setId((long) savedMedia.size() + 1);
            savedMedia.add(media);
            return media;
        });
        when(mapper.toDto(any(Media.class))).thenAnswer(invocation -> toDto(invocation.getArgument(0)));
    }

    @Test
    void storesIdenticalContentAsDistinctTenantScopedAssets() throws Exception {
        byte[] pdf = "%PDF-1.7\ncontent".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

        MediaDTO first = service.store(pdf, "report.pdf", "application/pdf", "inventory-reports", authentication("tenant-a"));
        MediaDTO second = service.store(pdf, "report.pdf", "application/pdf", "inventory-reports", authentication("tenant-a"));

        assertThat(first.getId()).isNotEqualTo(second.getId());
        assertThat(savedMedia).hasSize(2);
        assertThat(savedMedia.get(0).getStorageKey()).isNotEqualTo(savedMedia.get(1).getStorageKey());
        assertThat(savedMedia).allSatisfy(media -> {
            assertThat(media.getStorageKey()).matches("inventory-reports/[0-9a-f-]{36}/[0-9a-f]{64}\\.pdf");
            assertThat(media.getStatus()).isEqualTo(MediaAssetStatus.READY);
            assertThat(media.getSha256()).hasSize(64);
            assertThat(media.getFileSize()).isEqualTo(pdf.length);
            assertThat(tempDirectory.resolve("tenant-a").resolve(media.getStorageKey().replace('/', java.io.File.separatorChar)))
                .hasBinaryContent(pdf);
        });
    }

    @Test
    void storesFilesBelowDifferentTenantRoots() throws Exception {
        byte[] pdf = "%PDF-1.7\ncontent".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

        service.store(pdf, "report.pdf", "application/pdf", "inventory-reports", authentication("tenant-a"));
        service.store(pdf, "report.pdf", "application/pdf", "inventory-reports", authentication("tenant-b"));

        try (
            var tenantAFiles = Files.walk(tempDirectory.resolve("tenant-a"));
            var tenantBFiles = Files.walk(tempDirectory.resolve("tenant-b"))
        ) {
            assertThat(tenantAFiles.filter(Files::isRegularFile)).hasSize(1);
            assertThat(tenantBFiles.filter(Files::isRegularFile)).hasSize(1);
        }
    }

    private static MediaDTO toDto(Media media) {
        MediaDTO dto = new MediaDTO();
        dto.setId(media.getId());
        dto.setOriginalFilename(media.getOriginalFilename());
        dto.setMimeType(media.getMimeType());
        dto.setFileExtension(media.getFileExtension());
        dto.setFileSize(media.getFileSize());
        dto.setSha256(media.getSha256());
        dto.setStatus(media.getStatus());
        return dto;
    }

    private static JwtAuthenticationToken authentication(String tenant) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject("user-1").claim("tenant", tenant)
            .issuedAt(now).expiresAt(now.plusSeconds(300)).build();
        return new JwtAuthenticationToken(jwt);
    }
}
