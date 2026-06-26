package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.Users;
import com.fundaro.zodiac.taurus.domain.criteria.UsersCriteria;
import com.fundaro.zodiac.taurus.domain.enumeration.RoleEnum;
import com.fundaro.zodiac.taurus.resolver.IndexResolver;
import com.fundaro.zodiac.taurus.security.SecurityUtils;
import com.fundaro.zodiac.taurus.service.OpenSearchService;
import com.fundaro.zodiac.taurus.service.TenantsService;
import com.fundaro.zodiac.taurus.service.UsersService;
import com.fundaro.zodiac.taurus.service.dto.TenantsDTO;
import com.fundaro.zodiac.taurus.service.dto.UsersDTO;
import com.fundaro.zodiac.taurus.service.mapper.UsersMapper;
import com.fundaro.zodiac.taurus.utils.Converter;
import com.fundaro.zodiac.taurus.utils.keycloak.domain.Role;
import com.fundaro.zodiac.taurus.utils.keycloak.domain.User;
import com.fundaro.zodiac.taurus.utils.keycloak.service.KeycloakService;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.jhipster.service.filter.StringFilter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Service Implementation for managing {@link Users}.
 */
@Service
@Transactional
public class UsersServiceImpl extends CommonOpenSearchServiceImpl<Users, UsersDTO, UsersCriteria, UsersMapper> implements UsersService {

    public final KeycloakService keycloakService;

    public final TenantsService tenantsService;

    public UsersServiceImpl(OpenSearchService openSearchService, IndexResolver indexResolver, UsersMapper mapper, KeycloakService keycloakService, TenantsService tenantsService) {
        super(openSearchService, indexResolver, mapper, UsersService.class, Users.class);
        this.keycloakService = keycloakService;
        this.tenantsService = tenantsService;
    }

    @Override
    public UsersDTO save(UsersDTO dto, AbstractAuthenticationToken abstractAuthenticationToken) {
        if (dto.getRoles().stream().anyMatch(roleEnum -> roleEnum == RoleEnum.ROLE_SUPER_ADMIN)) {
            throw new RequestAlertException(HttpStatus.FORBIDDEN, "Not allow", getEntityName(), "not.allow");
        }

        String tenantCode = SecurityUtils.getTenantIdFromAuthentication(abstractAuthenticationToken);
        TenantsDTO tenantsDTO = tenantsService.findByCode(tenantCode, abstractAuthenticationToken)
            .orElseThrow(() -> new RequestAlertException(HttpStatus.BAD_REQUEST, "Tenant not found", getEntityName(), "tenant.notFound"));
        long usersCount = super.count(new UsersCriteria(), abstractAuthenticationToken);

        if (usersCount >= tenantsDTO.getMaxUsers()) {
            throw new RequestAlertException(HttpStatus.BAD_REQUEST, String.format("Limit exceeded for this tenant (max users: %s)", tenantsDTO.getMaxUsers()), getEntityName(), "user.limit.exceeded");
        }

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
        try {
            return super.save(dto, abstractAuthenticationToken);
        } catch (Exception e) {
            keycloakService.deleteUser(userId);
            throw e;
        }
    }

    @Override
    public UsersDTO update(String id, UsersDTO dto, AbstractAuthenticationToken abstractAuthenticationToken) {
        if (dto.getRoles().stream().anyMatch(roleEnum -> roleEnum == RoleEnum.ROLE_SUPER_ADMIN)) {
            throw new RequestAlertException(HttpStatus.FORBIDDEN, "Not allow", getEntityName(), "not.allow");
        }

        UsersDTO usersDTO = findOne(id, abstractAuthenticationToken)
            .orElseThrow(() -> new RequestAlertException(HttpStatus.NOT_FOUND, "Entity not found", getEntityName(), "id.notFound"));
        updateUserOnKeycloak(dto, usersDTO, abstractAuthenticationToken);
        return super.update(id, dto, abstractAuthenticationToken);
    }

    @Override
    public UsersDTO partialUpdate(String id, UsersDTO dto, AbstractAuthenticationToken abstractAuthenticationToken) {
        if (dto.getRoles().stream().anyMatch(roleEnum -> roleEnum == RoleEnum.ROLE_SUPER_ADMIN)) {
            throw new RequestAlertException(HttpStatus.FORBIDDEN, "Not allow", getEntityName(), "not.allow");
        }

        UsersDTO usersDTO = findOne(id, abstractAuthenticationToken)
            .orElseThrow(() -> new RequestAlertException(HttpStatus.NOT_FOUND, "Entity not found", getEntityName(), "id.notFound"));
        updateUserOnKeycloak(dto, usersDTO, abstractAuthenticationToken);
        return super.partialUpdate(id, dto, abstractAuthenticationToken);
    }

    @Override
    public Optional<UsersDTO> findMe(AbstractAuthenticationToken abstractAuthenticationToken) {
        String userId = SecurityUtils.getUserIdFromAuthentication(abstractAuthenticationToken);
        UsersCriteria usersCriteria = new UsersCriteria();
        StringFilter keycloakFilter = new StringFilter();
        keycloakFilter.setEquals(userId);
        usersCriteria.setKeycloakId(keycloakFilter);

        Page<UsersDTO> page = findEntitiesByCriteria(usersCriteria, Pageable.ofSize(1), abstractAuthenticationToken);
        if (page.getContent().isEmpty()) {
            UsersDTO usersDTO = new UsersDTO();
            usersDTO.setKeycloakId(userId);
            return Optional.of(usersDTO);
        }

        return Optional.of(page.getContent().get(0));
    }

    @Override
    protected List<Query> getQueries(UsersCriteria criteria) {
        List<Query> queries = super.getQueries(criteria);
        queries.addAll(Converter.stringFilterToQuery("lastName.keyword", criteria.getLastName()));
        queries.addAll(Converter.dateFilterToQuery("birthDate.keyword", criteria.getBirthDate()));
        queries.addAll(Converter.stringFilterToQuery("email.keyword", criteria.getEmail()));
        queries.addAll(Converter.generalFilterToQuery("roles.keyword", criteria.getRoles()));
        queries.addAll(Converter.booleanFilterToQuery("active.keyword", criteria.getActive()));
        queries.addAll(Converter.stringFilterToQuery("instruments.index", criteria.getInstrumentId()));
        queries.addAll(Converter.stringFilterToQuery("keycloakId.keyword", criteria.getKeycloakId()));

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
