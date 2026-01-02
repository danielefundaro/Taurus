package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.Media;
import com.fundaro.zodiac.taurus.domain.criteria.MediaCriteria;
import com.fundaro.zodiac.taurus.domain.criteria.TracksCriteria;
import com.fundaro.zodiac.taurus.service.MediaService;
import com.fundaro.zodiac.taurus.service.OpenSearchService;
import com.fundaro.zodiac.taurus.service.TracksService;
import com.fundaro.zodiac.taurus.service.dto.ChildrenEntitiesDTO;
import com.fundaro.zodiac.taurus.service.dto.MediaDTO;
import com.fundaro.zodiac.taurus.service.dto.SheetsMusicDTO;
import com.fundaro.zodiac.taurus.service.mapper.MediaMapper;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Objects;

/**
 * Service Implementation for managing {@link Media}.
 */
@Service
@Transactional
public class MediaServiceImpl extends CommonOpenSearchServiceImpl<Media, MediaDTO, MediaCriteria, MediaMapper> implements MediaService {

    private final TracksService tracksService;

    public MediaServiceImpl(OpenSearchService openSearchService, MediaMapper mediaMapper, TracksService tracksService) {
        super(openSearchService, mediaMapper, MediaService.class, Media.class);
        this.tracksService = tracksService;
    }

    @Override
    public Mono<MediaDTO> update(String id, MediaDTO dto, AbstractAuthenticationToken abstractAuthenticationToken) {
        return super.update(id, dto, abstractAuthenticationToken).map(mediaDTO -> {
            updateRelatedMedia(id, dto, mediaDTO, abstractAuthenticationToken);
            return mediaDTO;
        });
    }

    @Override
    public Mono<MediaDTO> partialUpdate(String id, MediaDTO dto, AbstractAuthenticationToken abstractAuthenticationToken) {
        return super.partialUpdate(id, dto, abstractAuthenticationToken).map(mediaDTO -> {
            updateRelatedMedia(id, dto, mediaDTO, abstractAuthenticationToken);
            return mediaDTO;
        });
    }

    @Override
    public Mono<Flux<DataBuffer>> streamFile(String id, AbstractAuthenticationToken abstractAuthenticationToken) {
        // Return the stream of the media
        try {
            Media media = getById(id);
            return Mono.just(DataBufferUtils.read(Paths.get(media.getPath()), new DefaultDataBufferFactory(), 1024));
        } catch (IOException e) {
            return Mono.error(new RequestAlertException(HttpStatus.NOT_FOUND, "Entity not found", getEntityName(), "id.notFound"));
        }
    }

    @Override
    public Mono<Boolean> delete(String id, AbstractAuthenticationToken abstractAuthenticationToken) {
        return super.delete(id, abstractAuthenticationToken).map(b -> {
            if (b) {
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
            }

            return b;
        });
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
