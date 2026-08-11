package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.QueueUploadFiles;
import com.fundaro.zodiac.taurus.domain.criteria.QueueUploadFilesCriteria;
import com.fundaro.zodiac.taurus.domain.enumeration.UploadFileStatusEnum;
import com.fundaro.zodiac.taurus.resolver.IndexResolver;
import com.fundaro.zodiac.taurus.security.SecurityUtils;
import com.fundaro.zodiac.taurus.service.OpenSearchService;
import com.fundaro.zodiac.taurus.service.QueueUploadFilesService;
import com.fundaro.zodiac.taurus.service.dto.QueueUploadFilesDTO;
import com.fundaro.zodiac.taurus.service.mapper.QueueUploadFilesMapper;
import com.fundaro.zodiac.taurus.utils.Converter;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    private final TenantStorageService tenantStorageService;

    public QueueUploadFilesServiceImpl(OpenSearchService openSearchService, IndexResolver indexResolver, QueueUploadFilesMapper queueUploadFilesMapper, TenantStorageService tenantStorageService) {
        super(openSearchService, indexResolver, queueUploadFilesMapper, QueueUploadFilesService.class, QueueUploadFiles.class);
        this.tenantStorageService = tenantStorageService;
    }

    @Override
    public QueueUploadFilesDTO saveStream(QueueUploadFilesDTO dto, AbstractAuthenticationToken abstractAuthenticationToken) {
        try {
            MultipartFile multipartFile = dto.getMultipartFile();
            String fileName = multipartFile.getOriginalFilename().replaceAll(" ", "_");
            byte[] bytes = multipartFile.getBytes();
            String tenantCode = SecurityUtils.getTenantIdFromAuthentication(abstractAuthenticationToken);
            Path path = tenantStorageService.resolve(
                tenantCode,
                UploadFileStatusEnum.TO_PROCESS.toString().toLowerCase(),
                new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()),
                fileName
            );
            if (!path.toFile().exists()) {
                Files.createDirectories(path.getParent());
            }
            Files.write(path, bytes, StandardOpenOption.CREATE);
            dto.setPath(path.toString());
            dto.setStatus(UploadFileStatusEnum.TO_PROCESS);
            dto.setName(fileName);
            return super.save(dto, abstractAuthenticationToken);
        } catch (IOException e) {
            throw new RequestAlertException(HttpStatus.BAD_REQUEST, "Error occurred while uploading the file", getEntityName(), "file.upload");
        }
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
