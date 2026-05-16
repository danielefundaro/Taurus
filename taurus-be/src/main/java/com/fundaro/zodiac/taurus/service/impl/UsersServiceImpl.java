package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.Users;
import com.fundaro.zodiac.taurus.domain.criteria.UsersCriteria;
import com.fundaro.zodiac.taurus.domain.enumeration.RoleEnum;
import com.fundaro.zodiac.taurus.resolver.IndexResolver;
import com.fundaro.zodiac.taurus.security.SecurityUtils;
import com.fundaro.zodiac.taurus.service.OpenSearchService;
import com.fundaro.zodiac.taurus.service.UsersService;
import com.fundaro.zodiac.taurus.service.dto.UsersDTO;
import com.fundaro.zodiac.taurus.service.mapper.UsersMapper;
import com.fundaro.zodiac.taurus.utils.Converter;
import com.fundaro.zodiac.taurus.utils.keycloak.domain.Role;
import com.fundaro.zodiac.taurus.utils.keycloak.domain.User;
import com.fundaro.zodiac.taurus.utils.keycloak.service.KeycloakService;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import tech.jhipster.service.filter.StringFilter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service Implementation for managing {@link Users}.
 */
@Service
@Transactional
public class UsersServiceImpl extends CommonOpenSearchServiceImpl<Users, UsersDTO, UsersCriteria, UsersMapper> implements UsersService {

    public final KeycloakService keycloakService;

    public UsersServiceImpl(OpenSearchService openSearchService, IndexResolver indexResolver, UsersMapper mapper, KeycloakService keycloakService) {
        super(openSearchService, indexResolver, mapper, UsersService.class, Users.class);
        this.keycloakService = keycloakService;
    }

    @Override
    public Mono<UsersDTO> save(UsersDTO dto, AbstractAuthenticationToken abstractAuthenticationToken) {
        User user = getMapper().toKeycloakUser(dto);

        // Check if the user already exists into keycloak
        try {
            String keycloakId = keycloakService.getUserIdByUsernameOrEmail(dto.getEmail(), dto.getEmail());
            user.setId(keycloakId);
            keycloakService.updateUser(user);
        } catch (RequestAlertException e) {
            keycloakService.saveUser(user);
        }

        // Set user's roles on keycloak
        String userId = keycloakService.getUserIdByUsernameOrEmail(dto.getEmail(), dto.getEmail());
        user = keycloakService.getUser(userId);
        setUserRolesOnKeycloak(user, dto.getRoles(), userId, abstractAuthenticationToken);

        // Save keycloakId of the user
        dto.setKeycloakId(userId);
        return super.save(dto, abstractAuthenticationToken).onErrorContinue((a, b) -> keycloakService.deleteUser(userId));
    }

    @Override
    public Mono<UsersDTO> update(String id, UsersDTO dto, AbstractAuthenticationToken abstractAuthenticationToken) {
        return findOne(id, abstractAuthenticationToken).flatMap(usersDTO -> {
            updateUserOnKeycloak(dto, usersDTO, abstractAuthenticationToken);
            return super.update(id, dto, abstractAuthenticationToken);
        });
    }

    @Override
    public Mono<UsersDTO> partialUpdate(String id, UsersDTO dto, AbstractAuthenticationToken abstractAuthenticationToken) {
        return findOne(id, abstractAuthenticationToken).flatMap(usersDTO -> {
            updateUserOnKeycloak(dto, usersDTO, abstractAuthenticationToken);
            return super.partialUpdate(id, dto, abstractAuthenticationToken);
        });
    }

    @Override
    public Mono<UsersDTO> findMe(AbstractAuthenticationToken abstractAuthenticationToken) {
        String userId = SecurityUtils.getUserIdFromAuthentication(abstractAuthenticationToken);
        UsersCriteria usersCriteria = new UsersCriteria();
        StringFilter keycloakFilter = new StringFilter();
        keycloakFilter.setEquals(userId);
        usersCriteria.setKeycloakId(keycloakFilter);

        return findEntitiesByCriteria(usersCriteria, Pageable.ofSize(1), abstractAuthenticationToken).flatMap(page -> {
            if (page.getContent().isEmpty()) {
                UsersDTO usersDTO = new UsersDTO();
                usersDTO.setKeycloakId(userId);
                return Mono.just(usersDTO);
            }

            return Mono.just(page.getContent().get(0));
        });
    }

    @Override
    protected List<Query> getQueries(UsersCriteria criteria) {
        List<Query> queries = super.getQueries(criteria);
        queries.addAll(Converter.stringFilterToQuery("lastName", criteria.getLastName()));
        queries.addAll(Converter.dateFilterToQuery("birthDate", criteria.getBirthDate()));
        queries.addAll(Converter.stringFilterToQuery("email", criteria.getEmail()));
        queries.addAll(Converter.stringFilterToQuery("roles", criteria.getRoles()));
        queries.addAll(Converter.booleanFilterToQuery("active", criteria.getActive()));
        queries.addAll(Converter.stringFilterToQuery("instruments.index", criteria.getInstrumentId()));
        queries.addAll(Converter.stringFilterToQuery("keycloakId", criteria.getKeycloakId()));

        return queries;
    }

    private void setUserRolesOnKeycloak(User user, Set<RoleEnum> dtoRoles, String userId, AbstractAuthenticationToken abstractAuthenticationToken) {
        String tenantId = SecurityUtils.getTenantIdFromAuthentication(abstractAuthenticationToken);
        String groupId = keycloakService.getGroupIdByName(tenantId);
        keycloakService.updateUserGroup(userId, groupId);
        Map<String, List<String>> currentAttributes = user.getAttributes();

        if (dtoRoles != null && !dtoRoles.isEmpty()) {
            List<Role> roles = keycloakService.getClientRoles();
            roles = roles.stream().filter(role -> dtoRoles.stream().anyMatch(roleEnum -> role.getName().equalsIgnoreCase(roleEnum.toString()))).toList();

            if (!roles.isEmpty()) {
                if (currentAttributes == null) {
                    currentAttributes = new HashMap<>();
                }

                currentAttributes.put(getAttributeKey(tenantId), roles.stream().map(Role::getName).toList());
                user.setAttributes(currentAttributes);
            }
        }

        keycloakService.updateUser(user);
    }

    private void updateUserOnKeycloak(UsersDTO dto, UsersDTO usersDTO, AbstractAuthenticationToken abstractAuthenticationToken) {
        User user = getMapper().toKeycloakUser(dto);
        user.setId(usersDTO.getKeycloakId());
        keycloakService.updateUser(user);

        // Remove old roles, if any
        String tenantId = SecurityUtils.getTenantIdFromAuthentication(abstractAuthenticationToken);
        String userId = user.getId();
        user = keycloakService.getUser(userId);
        Map<String, List<String>> currentAttributes = user.getAttributes();

        if (currentAttributes != null && currentAttributes.containsKey(getAttributeKey(tenantId))) {
            currentAttributes.remove(getAttributeKey(tenantId));
            user.setAttributes(currentAttributes);
            keycloakService.updateUser(user);
        }

        // Set user's roles on keycloak
        setUserRolesOnKeycloak(user, dto.getRoles(), userId, abstractAuthenticationToken);
    }

    private String getAttributeKey(String tenantId) {
        return String.format("%s_roles", tenantId);
    }
}
