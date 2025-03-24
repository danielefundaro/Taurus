package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.Tracks;
import com.fundaro.zodiac.taurus.domain.criteria.TracksCriteria;
import com.fundaro.zodiac.taurus.rabbitmq.UploadFilesPackage;
import com.fundaro.zodiac.taurus.rabbitmq.Sender;
import com.fundaro.zodiac.taurus.security.SecurityUtils;
import com.fundaro.zodiac.taurus.service.OpenSearchService;
import com.fundaro.zodiac.taurus.service.QueueUploadFilesService;
import com.fundaro.zodiac.taurus.service.TracksService;
import com.fundaro.zodiac.taurus.service.dto.QueueUploadFilesDTO;
import com.fundaro.zodiac.taurus.service.dto.TracksDTO;
import com.fundaro.zodiac.taurus.service.mapper.TracksMapper;
import com.fundaro.zodiac.taurus.utils.Converter;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.List;

/**
 * Service Implementation for managing {@link Tracks}.
 */
@Service
@Transactional
public class TracksServiceImpl extends CommonOpenSearchServiceImpl<Tracks, TracksDTO, TracksCriteria, TracksMapper> implements TracksService {

    private final QueueUploadFilesService queueUploadFilesService;

    private final Sender sender;

    public TracksServiceImpl(OpenSearchService openSearchService, TracksMapper tracksMapper, QueueUploadFilesService queueUploadFilesService, Sender sender) {
        super(openSearchService, tracksMapper, TracksService.class, Tracks.class, "Tracks");
        this.queueUploadFilesService = queueUploadFilesService;
        this.sender = sender;
    }

    @Override
    public Mono<Void> uploadFile(String id, FilePart filePart, AbstractAuthenticationToken abstractAuthenticationToken) {
        return findOne(id, abstractAuthenticationToken).flatMap(tracksDTO -> DataBufferUtils.join(filePart.content()).flatMap(dataBuffer -> {
            if (dataBuffer.capacity() == 0) {
                return Mono.error(new RequestAlertException(HttpStatus.BAD_REQUEST, "File is empty", getEntityName(), "file.empty"));
            }

            String userId = SecurityUtils.getUserIdFromAuthentication(abstractAuthenticationToken);
            QueueUploadFilesDTO queueUploadFilesDTO = new QueueUploadFilesDTO();
            queueUploadFilesDTO.setUserId(userId);
            queueUploadFilesDTO.setTrackId(id);
            queueUploadFilesDTO.setFilePart(filePart);
            queueUploadFilesDTO.setType(getEntityName());

            return queueUploadFilesService.saveStream(queueUploadFilesDTO, abstractAuthenticationToken).flatMap(mono -> mono.map(q -> {
                try {
                    sender.send(Converter.objectToBytes(new UploadFilesPackage(q.getId(), abstractAuthenticationToken)));
                } catch (IOException e) {
                    return Mono.error(new RequestAlertException(HttpStatus.BAD_REQUEST, "Error occurred while sending message", getEntityName(), "send.message"));
                }

                return q;
            }));
        })).then();
    }

    @Override
    protected List<Query> getQueries(TracksCriteria criteria) {
        List<Query> queries = super.getQueries(criteria);
        queries.addAll(Converter.stringFilterToQuery("composer", criteria.getComposer()));
        queries.addAll(Converter.stringFilterToQuery("arranger", criteria.getArranger()));
        queries.addAll(Converter.stringFilterToQuery("type", criteria.getType()));

        return queries;
    }
}
