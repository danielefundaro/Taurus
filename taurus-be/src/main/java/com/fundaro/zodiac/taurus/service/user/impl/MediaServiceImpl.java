package com.fundaro.zodiac.taurus.service.user.impl;

import com.fundaro.zodiac.taurus.domain.Media;
import com.fundaro.zodiac.taurus.domain.criteria.MediaCriteria;
import com.fundaro.zodiac.taurus.resolver.IndexResolver;
import com.fundaro.zodiac.taurus.security.SecurityUtils;
import com.fundaro.zodiac.taurus.service.OpenSearchService;
import com.fundaro.zodiac.taurus.service.dto.MediaDTO;
import com.fundaro.zodiac.taurus.service.mapper.MediaMapper;
import com.fundaro.zodiac.taurus.service.user.MediaService;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;

/**
 * Service Implementation for managing {@link Media}.
 */
@Service("LowPermissionsMediaService")
@Transactional
public class MediaServiceImpl extends CommonOpenSearchServiceImpl<Media, MediaDTO, MediaCriteria, MediaMapper> implements MediaService {

    public MediaServiceImpl(OpenSearchService openSearchService, IndexResolver indexResolver, MediaMapper mapper) {
        super(openSearchService, indexResolver, mapper, MediaService.class, Media.class);
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
}
