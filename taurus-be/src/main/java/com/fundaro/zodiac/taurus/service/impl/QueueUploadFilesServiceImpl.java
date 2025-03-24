package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.QueueUploadFiles;
import com.fundaro.zodiac.taurus.domain.criteria.QueueUploadFilesCriteria;
import com.fundaro.zodiac.taurus.domain.enumeration.UploadFileStatus;
import com.fundaro.zodiac.taurus.service.OpenSearchService;
import com.fundaro.zodiac.taurus.service.QueueUploadFilesService;
import com.fundaro.zodiac.taurus.service.dto.QueueUploadFilesDTO;
import com.fundaro.zodiac.taurus.service.mapper.QueueUploadFilesMapper;
import com.fundaro.zodiac.taurus.utils.Converter;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Service Implementation for managing {@link QueueUploadFiles}.
 */
@Service
@Transactional
public class QueueUploadFilesServiceImpl extends CommonOpenSearchServiceImpl<QueueUploadFiles, QueueUploadFilesDTO, QueueUploadFilesCriteria, QueueUploadFilesMapper> implements QueueUploadFilesService {

    private final String basePath;

    public QueueUploadFilesServiceImpl(OpenSearchService openSearchService, QueueUploadFilesMapper queueUploadFilesMapper, ApplicationProperties applicationProperties) {
        super(openSearchService, queueUploadFilesMapper, QueueUploadFilesService.class, QueueUploadFiles.class, "QueueUploadFiles");
        basePath = applicationProperties.getBasePath();
    }

    @Override
    public Mono<Mono<QueueUploadFilesDTO>> saveStream(QueueUploadFilesDTO dto, AbstractAuthenticationToken abstractAuthenticationToken) {
        return DataBufferUtils.join(dto.getFilePart().content()).map(dataBuffer -> {
            String fileName = dto.getFilePart().filename().replaceAll(" ", "_");
            byte[] bytes = new byte[dataBuffer.readableByteCount()];
            dataBuffer.read(bytes);
            DataBufferUtils.release(dataBuffer);

            Path path = Paths.get(basePath, UploadFileStatus.TO_PROCESS.toString().toLowerCase(), new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()), fileName);
            try {
                if (!path.toFile().exists()) {
                    Files.createDirectories(path.getParent());
                }
                Files.write(path, bytes, StandardOpenOption.CREATE);
            } catch (IOException e) {
                return Mono.error(new RequestAlertException(HttpStatus.BAD_REQUEST, "Error occurred while uploading the file", getEntityName(), "file.upload"));
            }

            dto.setPath(path.toString());
            dto.setStatus(UploadFileStatus.TO_PROCESS);
            dto.setName(fileName);

            return super.save(dto, abstractAuthenticationToken);
        });
    }

    @Override
    protected List<Query> getQueries(QueueUploadFilesCriteria criteria) {
        List<Query> queries = super.getQueries(criteria);
        queries.addAll(Converter.stringFilterToQuery("userId", criteria.getUserId()));
        queries.addAll(Converter.stringFilterToQuery("trackId", criteria.getTrackId()));
        queries.addAll(Converter.generalFilterToQuery("status", criteria.getStatus()));
        queries.addAll(Converter.stringFilterToQuery("type", criteria.getType()));

        return queries;
    }
}
