package com.fundaro.zodiac.taurus.service.user.impl;

import com.fundaro.zodiac.taurus.aop.tenant.TenantIndexAspect;
import com.fundaro.zodiac.taurus.domain.Media;
import com.fundaro.zodiac.taurus.domain.criteria.MediaCriteria;
import com.fundaro.zodiac.taurus.resolver.IndexResolver;
import com.fundaro.zodiac.taurus.service.OpenSearchService;
import com.fundaro.zodiac.taurus.service.dto.MediaDTO;
import com.fundaro.zodiac.taurus.service.mapper.MediaMapper;
import com.fundaro.zodiac.taurus.service.user.MediaService;
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
    public Mono<Flux<DataBuffer>> streamFile(String id, AbstractAuthenticationToken abstractAuthenticationToken) {
        // Return the stream of the media
        return Mono.deferContextual(ctx -> {
            String tenantId = ctx.getOrDefault(TenantIndexAspect.TENANT_CONTEXT_KEY, "");
            try {
                Media media = getById(id, tenantId);
                return Mono.just(DataBufferUtils.read(Paths.get(media.getPath()), new DefaultDataBufferFactory(), 1024));
            } catch (IOException e) {
                return Mono.error(new RequestAlertException(HttpStatus.NOT_FOUND, "Entity not found", getEntityName(), "id.notFound"));
            }
        });
    }
}
