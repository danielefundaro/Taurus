package com.fundaro.zodiac.taurus.service;

import com.fundaro.zodiac.taurus.domain.Media;
import com.fundaro.zodiac.taurus.domain.criteria.MediaCriteria;
import com.fundaro.zodiac.taurus.service.dto.MediaDTO;
import org.springframework.core.io.Resource;
import org.springframework.security.authentication.AbstractAuthenticationToken;

/**
 * Service Interface for managing {@link Media}.
 */
public interface MediaService extends CommonOpenSearchService<Media, MediaDTO, MediaCriteria> {
    Resource streamFile(Long id, AbstractAuthenticationToken abstractAuthenticationToken);

    MediaContent getContent(Long id, AbstractAuthenticationToken abstractAuthenticationToken);

    MediaContent getContent(Long id, String tenantCode);

    MediaDTO store(
        byte[] content,
        String originalFilename,
        String declaredMimeType,
        String category,
        AbstractAuthenticationToken abstractAuthenticationToken
    );

    /**
     * Stores a file for a background task of the current tenant, without a user request; {@code actor} is written to
     * the audit fields.
     */
    MediaDTO store(byte[] content, String originalFilename, String declaredMimeType, String category, String tenantCode, String actor);

    void deleteIfUnreferenced(Long id, AbstractAuthenticationToken abstractAuthenticationToken);

    void deleteIfUnreferenced(Long id, String tenantCode, String actor);

    record MediaContent(String fileName, String mimeType, long fileSize, byte[] bytes) {
    }
}
