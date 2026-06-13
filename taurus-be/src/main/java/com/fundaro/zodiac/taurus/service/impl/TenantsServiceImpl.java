package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.config.changelog.service.ChangelogService;
import com.fundaro.zodiac.taurus.domain.Tenants;
import com.fundaro.zodiac.taurus.domain.criteria.TenantsCriteria;
import com.fundaro.zodiac.taurus.domain.enumeration.RoleEnum;
import com.fundaro.zodiac.taurus.resolver.IndexResolver;
import com.fundaro.zodiac.taurus.service.OpenSearchService;
import com.fundaro.zodiac.taurus.service.TenantsService;
import com.fundaro.zodiac.taurus.service.dto.TenantsDTO;
import com.fundaro.zodiac.taurus.service.mapper.TenantsMapper;
import com.fundaro.zodiac.taurus.utils.Converter;
import com.fundaro.zodiac.taurus.utils.keycloak.domain.Group;
import com.fundaro.zodiac.taurus.utils.keycloak.domain.Role;
import com.fundaro.zodiac.taurus.utils.keycloak.domain.User;
import com.fundaro.zodiac.taurus.utils.keycloak.service.KeycloakService;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import tech.jhipster.service.filter.StringFilter;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;

import static com.fundaro.zodiac.taurus.config.Constants.TENANT_CHANGELOG_FILE_PATH;

/**
 * Service Implementation for managing {@link Tenants}.
 */
@Service
@Transactional
public class TenantsServiceImpl extends CommonOpenSearchServiceImpl<Tenants, TenantsDTO, TenantsCriteria, TenantsMapper> implements TenantsService {

    private final ChangelogService changelogService;

    private final KeycloakService keycloakService;

    public TenantsServiceImpl(OpenSearchService openSearchService, IndexResolver indexResolver, TenantsMapper mapper, ChangelogService changelogService, KeycloakService keycloakService) {
        super(openSearchService, indexResolver, mapper, TenantsService.class, Tenants.class);
        this.changelogService = changelogService;
        this.keycloakService = keycloakService;
    }

    @Override
    public Mono<TenantsDTO> save(TenantsDTO dto, AbstractAuthenticationToken abstractAuthenticationToken) {
        TenantsCriteria criteria = new TenantsCriteria();
        StringFilter filterCode = new StringFilter();
        filterCode.setEquals(dto.getCode());
        criteria.setCode(filterCode);
        Pageable pageable = PageRequest.of(0, 1);

        return super.findEntitiesByCriteria(criteria, pageable, abstractAuthenticationToken).flatMap(tenantsDTOS -> {
            if (tenantsDTOS.getContent().isEmpty()) {
                return super.save(dto, abstractAuthenticationToken).handle((tenantsDTO, sink) -> {
                    try {
                        // Save new tenant as keycloak group
                        keycloakService.saveGroup(new Group(tenantsDTO.getCode(), tenantsDTO.getName()));
                        String groupId = keycloakService.getGroupIdByName(tenantsDTO.getCode());

                        // Get access to the new tenant at all super admins
                        List<User> users = keycloakService.getUsersByClientRoles(RoleEnum.ROLE_SUPER_ADMIN);

                        for (User user : users) {
                            // Add super admin into the group
                            keycloakService.updateUserGroup(user.getId(), groupId);

                            // Update attributes for multi role logic
                            List<Role> roleList = keycloakService.getUserRoles(user.getId());
                            Map<String, List<String>> currentAttributes = user.getAttributes();
                            currentAttributes.put(String.format("%s_roles", tenantsDTO.getCode()), roleList.stream().map(Role::getName).toList());
                            user.setAttributes(currentAttributes);
                            keycloakService.updateUser(user);
                        }
                        changelogService.extractAllResources(TENANT_CHANGELOG_FILE_PATH, tenantsDTO.getCode());
                    } catch (IOException | NoSuchAlgorithmException e) {
                        getLogger().error(e.getMessage());
                        sink.error(new RequestAlertException(HttpStatus.INTERNAL_SERVER_ERROR, String.format("Something went wrong while creating tenant %s", tenantsDTO), Tenants.class.getSimpleName(), "save.tenant"));
                        return;
                    }
                    sink.next(tenantsDTO);
                });
            } else {
                return Mono.error(new RequestAlertException(HttpStatus.BAD_REQUEST, String.format("A new %s cannot have an existing CODE", getEntityName()), getEntityName(), "code.exists"));
            }
        });
    }

    @Override
    public Mono<TenantsDTO> findByCode(String code, AbstractAuthenticationToken abstractAuthenticationToken) {
        TenantsCriteria criteria = new TenantsCriteria();
        StringFilter filterCode = new StringFilter();
        filterCode.setEquals(code);
        criteria.setCode(filterCode);
        Pageable pageable = PageRequest.of(0, 1);

        return super.findEntitiesByCriteria(criteria, pageable, abstractAuthenticationToken).mapNotNull(tenantsDTOS -> {
            if (tenantsDTOS.isEmpty() || tenantsDTOS.getContent().isEmpty()) {
                return null;
            }

            return tenantsDTOS.getContent().get(0);
        });
    }

    @Override
    protected List<Query> getQueries(TenantsCriteria criteria) {
        List<Query> queries = super.getQueries(criteria);
        queries.addAll(Converter.stringFilterToQuery("code.keyword", criteria.getCode()));
        queries.addAll(Converter.stringFilterToQuery("email.keyword", criteria.getEmail()));
        queries.addAll(Converter.stringFilterToQuery("domain.keyword", criteria.getDomain()));
        queries.addAll(Converter.longFilterToQuery("maxUsers", criteria.getMaxUsers()));
        queries.addAll(Converter.dateFilterToQuery("expireDate", criteria.getExpireDate()));
        queries.addAll(Converter.booleanFilterToQuery("active", criteria.getActive()));

        return queries;
    }
}
