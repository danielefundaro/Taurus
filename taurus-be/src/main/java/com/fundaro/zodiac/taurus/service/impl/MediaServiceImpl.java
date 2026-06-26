package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.Media;
import com.fundaro.zodiac.taurus.domain.criteria.MediaCriteria;
import com.fundaro.zodiac.taurus.domain.criteria.TracksCriteria;
import com.fundaro.zodiac.taurus.resolver.IndexResolver;
import com.fundaro.zodiac.taurus.security.SecurityUtils;
import com.fundaro.zodiac.taurus.service.MediaService;
import com.fundaro.zodiac.taurus.service.OpenSearchService;
import com.fundaro.zodiac.taurus.service.TracksService;
import com.fundaro.zodiac.taurus.service.dto.ChildrenEntitiesDTO;
import com.fundaro.zodiac.taurus.service.dto.MediaDTO;
import com.fundaro.zodiac.taurus.service.dto.SheetsMusicDTO;
import com.fundaro.zodiac.taurus.service.mapper.MediaMapper;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.Objects;

/**
 * Service Implementation for managing {@link Media}.
 */
@Service
@Transactional
public class MediaServiceImpl extends CommonOpenSearchServiceImpl<Media, MediaDTO, MediaCriteria, MediaMapper> implements MediaService {

    private final TracksService tracksService;

    public MediaServiceImpl(OpenSearchService openSearchService, IndexResolver indexResolver, MediaMapper mediaMapper, TracksService tracksService) {
        super(openSearchService, indexResolver, mediaMapper, MediaService.class, Media.class);
        this.tracksService = tracksService;
    }

    @Override
    public MediaDTO update(String id, MediaDTO dto, AbstractAuthenticationToken abstractAuthenticationToken) {
        MediaDTO mediaDTO = super.update(id, dto, abstractAuthenticationToken);
        updateRelatedMedia(id, dto, mediaDTO, abstractAuthenticationToken);
        return mediaDTO;
    }

    @Override
    public MediaDTO partialUpdate(String id, MediaDTO dto, AbstractAuthenticationToken abstractAuthenticationToken) {
        MediaDTO mediaDTO = super.partialUpdate(id, dto, abstractAuthenticationToken);
        updateRelatedMedia(id, dto, mediaDTO, abstractAuthenticationToken);
        return mediaDTO;
    }

    @Override
    public Resource streamFile(String id, AbstractAuthenticationToken abstractAuthenticationToken) {
        String tenantId = SecurityUtils.getTenantIdFromAuthentication(abstractAuthenticationToken);
        try {
            Media media = getById(id, tenantId);
            return new FileSystemResource(media.getPath());
        } catch (IOException e) {
            throw new RequestAlertException(HttpStatus.NOT_FOUND, "Entity not found", getEntityName(), "id.notFound");
        }
    }

    @Override
    public MediaDTO delete(String id, AbstractAuthenticationToken abstractAuthenticationToken) {
        MediaDTO b = super.delete(id, abstractAuthenticationToken);
        if (b == null) {
            return null;
        }

        // Delete all related information
        tracksService.alignChildrenInformation(id, abstractAuthenticationToken, stringFilter -> new TracksCriteria().setMediaId(stringFilter), (tracksDTO, s) -> {
            boolean result = false;

            if (tracksDTO.getScores() != null) {
                for (SheetsMusicDTO sheetsMusicDTO : tracksDTO.getScores()) {
                    if (sheetsMusicDTO.getMedia() != null) {
                        result |= sheetsMusicDTO.getMedia().removeIf(childrenEntitiesDTO -> childrenEntitiesDTO.getIndex().equals(s));
                    }
                }

                result |= tracksDTO.getScores().removeIf(sheetsMusicDTO -> sheetsMusicDTO.getMedia().isEmpty());
            }

            return result;
        });

        return b;
    }

    private void updateRelatedMedia(String id, MediaDTO oldMediaDto, MediaDTO mediaDTO, AbstractAuthenticationToken abstractAuthenticationToken) {
        if (Objects.equals(oldMediaDto.getName(), mediaDTO.getName())) {
            tracksService.alignChildrenInformation(id, abstractAuthenticationToken, stringFilter -> new TracksCriteria().setMediaId(stringFilter), (tracksDTO, s) -> {
                boolean result = false;

                if (tracksDTO.getScores() != null) {
                    for (SheetsMusicDTO sheetsMusicDTO : tracksDTO.getScores()) {
                        if (sheetsMusicDTO.getMedia() != null) {
                            for (ChildrenEntitiesDTO childrenEntitiesDTO : sheetsMusicDTO.getMedia()) {
                                if (childrenEntitiesDTO.getIndex().equals(s)) {
                                    childrenEntitiesDTO.setName(mediaDTO.getName());
                                    result = true;
                                }
                            }
                        }
                    }
                }

                return result;
            });
        }
    }
}
