package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.Tracks;
import com.fundaro.zodiac.taurus.domain.criteria.AlbumsCriteria;
import com.fundaro.zodiac.taurus.domain.criteria.TracksCriteria;
import com.fundaro.zodiac.taurus.rabbitmq.Sender;
import com.fundaro.zodiac.taurus.rabbitmq.UploadFilesPackage;
import com.fundaro.zodiac.taurus.resolver.IndexResolver;
import com.fundaro.zodiac.taurus.security.SecurityUtils;
import com.fundaro.zodiac.taurus.service.AlbumsService;
import com.fundaro.zodiac.taurus.service.OpenSearchService;
import com.fundaro.zodiac.taurus.service.QueueUploadFilesService;
import com.fundaro.zodiac.taurus.service.TracksService;
import com.fundaro.zodiac.taurus.service.dto.ChildrenEntitiesDTO;
import com.fundaro.zodiac.taurus.service.dto.QueueUploadFilesDTO;
import com.fundaro.zodiac.taurus.service.dto.TracksDTO;
import com.fundaro.zodiac.taurus.service.mapper.TracksMapper;
import com.fundaro.zodiac.taurus.utils.Converter;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import org.apache.commons.io.FilenameUtils;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service Implementation for managing {@link Tracks}.
 */
@Service
@Transactional
public class TracksServiceImpl extends CommonOpenSearchServiceImpl<Tracks, TracksDTO, TracksCriteria, TracksMapper> implements TracksService {

    private final QueueUploadFilesService queueUploadFilesService;

    private final AlbumsService albumsService;

    private final Sender sender;

    public TracksServiceImpl(OpenSearchService openSearchService, IndexResolver indexResolver, TracksMapper tracksMapper, QueueUploadFilesService queueUploadFilesService, AlbumsService albumsService, Sender sender) {
        super(openSearchService, indexResolver, tracksMapper, TracksService.class, Tracks.class);
        this.queueUploadFilesService = queueUploadFilesService;
        this.albumsService = albumsService;
        this.sender = sender;
    }

    @Override
    public TracksDTO save(TracksDTO dto, AbstractAuthenticationToken abstractAuthenticationToken) {
        finalizeOrders(dto);
        return super.save(dto, abstractAuthenticationToken);
    }

    @Override
    public TracksDTO update(String id, TracksDTO dto, AbstractAuthenticationToken abstractAuthenticationToken) {
        finalizeOrders(dto);
        TracksDTO tracksDTO = super.update(id, dto, abstractAuthenticationToken);
        updateRelatedTracks(id, dto, tracksDTO, abstractAuthenticationToken);
        return tracksDTO;
    }

    @Override
    public TracksDTO partialUpdate(String id, TracksDTO dto, AbstractAuthenticationToken abstractAuthenticationToken) {
        finalizeOrders(dto);
        TracksDTO tracksDTO = super.partialUpdate(id, dto, abstractAuthenticationToken);
        updateRelatedTracks(id, dto, tracksDTO, abstractAuthenticationToken);
        return tracksDTO;
    }

    @Override
    public void uploadFile(String id, MultipartFile file, String annotations, AbstractAuthenticationToken abstractAuthenticationToken) {
        if (file == null || file.isEmpty()) {
            throw new RequestAlertException(HttpStatus.BAD_REQUEST, "File is empty", getEntityName(), "file.empty");
        }

        String userId = SecurityUtils.getUserIdFromAuthentication(abstractAuthenticationToken);
        QueueUploadFilesDTO queueUploadFilesDTO = new QueueUploadFilesDTO();
        queueUploadFilesDTO.setUserId(userId);
        queueUploadFilesDTO.setMultipartFile(file);
        queueUploadFilesDTO.setType(getEntityName());
        queueUploadFilesDTO.setDescription(annotations);

        if (id != null) {
            queueUploadFilesDTO.setTrackId(id);
            findOne(id, abstractAuthenticationToken);
            queueSaveEntity(queueUploadFilesDTO, abstractAuthenticationToken);
        } else {
            TracksDTO tracksDTO = new TracksDTO();
            tracksDTO.setName(FilenameUtils.removeExtension(file.getOriginalFilename()));
            TracksDTO saved = this.save(tracksDTO, abstractAuthenticationToken);
            queueUploadFilesDTO.setTrackId(saved.getId());
            queueSaveEntity(queueUploadFilesDTO, abstractAuthenticationToken);
        }
    }

    @Override
    public TracksDTO delete(String id, AbstractAuthenticationToken abstractAuthenticationToken) {
        TracksDTO b = super.delete(id, abstractAuthenticationToken);
        if (b == null) {
            return null;
        }

        // Delete all related information
        albumsService.alignChildrenInformation(id, abstractAuthenticationToken, stringFilter -> new AlbumsCriteria().setTrackId(stringFilter), (albumsDTO, s) -> albumsDTO.getTracks().removeIf(childrenEntitiesDTO -> childrenEntitiesDTO.getIndex().equals(s)));

        return b;
    }

    @Override
    protected List<Query> getQueries(TracksCriteria criteria) {
        List<Query> queries = super.getQueries(criteria);
        queries.addAll(Converter.stringFilterToQuery("composer.keyword", criteria.getComposer()));
        queries.addAll(Converter.stringFilterToQuery("arranger.keyword", criteria.getArranger()));
        queries.addAll(Converter.stringFilterToQuery("tempo.keyword", criteria.getTempo()));
        queries.addAll(Converter.stringFilterToQuery("tone.keyword", criteria.getTone()));
        queries.addAll(Converter.generalFilterToQuery("state.keyword", criteria.getState()));
        queries.addAll(Converter.stringFilterToQuery("type.keyword", criteria.getType()));
        queries.addAll(Converter.stringFilterToQuery("scores.media.index.keyword", criteria.getMediaId()));
        queries.addAll(Converter.stringFilterToQuery("scores.instruments.index.keyword", criteria.getInstrumentId()));

        return queries;
    }

    private void finalizeOrders(TracksDTO dto) {
        if (dto.getScores() != null && !dto.getScores().isEmpty()) {
            AtomicLong i = new AtomicLong(0L);
            dto.getScores().stream()
                .sorted((a, b) -> Objects.compare(a.getOrder(), b.getOrder(), Comparator.naturalOrder()))
                .forEach(score -> score.setOrder(i.incrementAndGet()));

            dto.getScores().forEach(score -> {
                if (score.getMedia() != null && !score.getMedia().isEmpty()) {
                    AtomicLong j = new AtomicLong(0L);
                    score.getMedia().stream()
                        .sorted((a, b) -> Objects.compare(a.getOrder(), b.getOrder(), Comparator.naturalOrder()))
                        .forEach(media -> media.setOrder(j.incrementAndGet()));
                }

                if (score.getInstruments() != null && !score.getInstruments().isEmpty()) {
                    AtomicLong j = new AtomicLong(0L);
                    score.getInstruments().stream()
                        .sorted((a, b) -> Objects.compare(a.getOrder(), b.getOrder(), Comparator.naturalOrder()))
                        .forEach(instrument -> instrument.setOrder(j.incrementAndGet()));
                }
            });
        }
    }

    private void updateRelatedTracks(String id, TracksDTO oldTracksDto, TracksDTO tracksDTO, AbstractAuthenticationToken abstractAuthenticationToken) {
        if (Objects.equals(oldTracksDto.getName(), tracksDTO.getName())) {
            albumsService.alignChildrenInformation(id, abstractAuthenticationToken, stringFilter -> new AlbumsCriteria().setTrackId(stringFilter), (albumsDTO, s) -> {
                boolean result = false;

                if (albumsDTO.getTracks() != null) {
                    for (ChildrenEntitiesDTO childrenEntitiesDTO : albumsDTO.getTracks()) {
                        if (childrenEntitiesDTO.getIndex().equals(s)) {
                            childrenEntitiesDTO.setName(tracksDTO.getName());
                            result = true;
                        }
                    }
                }

                return result;
            });
        }
    }

    private void queueSaveEntity(QueueUploadFilesDTO queueUploadFilesDTO, AbstractAuthenticationToken abstractAuthenticationToken) {
        QueueUploadFilesDTO q = queueUploadFilesService.saveStream(queueUploadFilesDTO, abstractAuthenticationToken);
        try {
            sender.send(Converter.objectToBytes(new UploadFilesPackage(q.getId(), abstractAuthenticationToken)));
        } catch (IOException e) {
            throw new RequestAlertException(HttpStatus.BAD_REQUEST, "Error occurred while sending message", getEntityName(), "send.message");
        }
    }
}
