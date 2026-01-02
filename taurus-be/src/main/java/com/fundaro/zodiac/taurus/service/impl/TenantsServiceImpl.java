package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.config.changelog.service.ChangelogService;
import com.fundaro.zodiac.taurus.domain.Tenants;
import com.fundaro.zodiac.taurus.domain.criteria.TenantsCriteria;
import com.fundaro.zodiac.taurus.service.OpenSearchService;
import com.fundaro.zodiac.taurus.service.TenantsService;
import com.fundaro.zodiac.taurus.service.dto.TenantsDTO;
import com.fundaro.zodiac.taurus.service.mapper.TenantsMapper;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

/**
 * Service Implementation for managing {@link Tenants}.
 */
@Service
@Transactional
public class TenantsServiceImpl extends CommonOpenSearchServiceImpl<Tenants, TenantsDTO, TenantsCriteria, TenantsMapper> implements TenantsService {

    private final ChangelogService changelogService;

    public TenantsServiceImpl(OpenSearchService openSearchService, TenantsMapper mapper, ChangelogService changelogService) {
        super(openSearchService, mapper, TenantsService.class, Tenants.class);
        this.changelogService = changelogService;
    }

    @Override
    public Mono<TenantsDTO> save(TenantsDTO dto, AbstractAuthenticationToken abstractAuthenticationToken) {
        return super.save(dto, abstractAuthenticationToken).handle((tenantsDTO, sink) -> {
            try {
                changelogService.extractAllResources("config/opensearch/tenants.json", tenantsDTO.getCode());
            } catch (IOException | NoSuchAlgorithmException e) {
                getLogger().error(e.getMessage());
                sink.error(new RequestAlertException(HttpStatus.INTERNAL_SERVER_ERROR, String.format("Something went wrong while creating tenant %s", tenantsDTO), Tenants.class.getSimpleName(), "save.tenant"));
                return;
            }
            sink.next(tenantsDTO);
        });
    }
}
