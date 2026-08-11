package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.Users;
import com.fundaro.zodiac.taurus.domain.criteria.CalendarEventsCriteria;
import com.fundaro.zodiac.taurus.domain.criteria.UserCalendarEventsCriteria;
import com.fundaro.zodiac.taurus.domain.criteria.UsersCriteria;
import com.fundaro.zodiac.taurus.domain.criteria.filter.DateFilter;
import com.fundaro.zodiac.taurus.domain.criteria.filter.StateFilter;
import com.fundaro.zodiac.taurus.domain.enumeration.RoleEnum;
import com.fundaro.zodiac.taurus.domain.enumeration.StateEnum;
import com.fundaro.zodiac.taurus.resolver.IndexResolver;
import com.fundaro.zodiac.taurus.security.SecurityUtils;
import com.fundaro.zodiac.taurus.service.CalendarEventsService;
import com.fundaro.zodiac.taurus.service.OpenSearchService;
import com.fundaro.zodiac.taurus.service.TenantsService;
import com.fundaro.zodiac.taurus.service.UsersService;
import com.fundaro.zodiac.taurus.service.dto.CalendarEventsDTO;
import com.fundaro.zodiac.taurus.service.dto.TenantsDTO;
import com.fundaro.zodiac.taurus.service.dto.UsersDTO;
import com.fundaro.zodiac.taurus.service.dto.UsersMeDTO;
import com.fundaro.zodiac.taurus.service.mapper.UsersMapper;
import com.fundaro.zodiac.taurus.utils.Converter;
import com.fundaro.zodiac.taurus.utils.keycloak.domain.Role;
import com.fundaro.zodiac.taurus.utils.keycloak.domain.Group;
import com.fundaro.zodiac.taurus.utils.keycloak.domain.User;
import com.fundaro.zodiac.taurus.utils.keycloak.service.KeycloakService;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import org.apache.logging.log4j.util.Strings;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.jhipster.service.filter.StringFilter;

import java.util.*;

/**
 * Service Implementation for managing {@link Users}.
 */
@Service
@Transactional
public class UsersServiceImpl extends CommonOpenSearchServiceImpl<Users, UsersDTO, UsersCriteria, UsersMapper> implements UsersService {

    public final KeycloakService keycloakService;

    public final TenantsService tenantsService;

    public final CalendarEventsService calendarEventsService;

    private final DataErasureService dataErasureService;

    public UsersServiceImpl(OpenSearchService openSearchService, IndexResolver indexResolver, UsersMapper mapper, KeycloakService keycloakService, TenantsService tenantsService, CalendarEventsService calendarEventsService, DataErasureService dataErasureService) {
        super(openSearchService, indexResolver, mapper, UsersService.class, Users.class);
        this.keycloakService = keycloakService;
        this.tenantsService = tenantsService;
        this.calendarEventsService = calendarEventsService;
        this.dataErasureService = dataErasureService;
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
        if (dto.getRoles() == null || dto.getRoles().stream().anyMatch(roleEnum -> roleEnum == RoleEnum.ROLE_SUPER_ADMIN)) {
            throw new RequestAlertException(HttpStatus.FORBIDDEN, "Not allow", getEntityName(), "not.allow");
        }

        UsersDTO usersDTO = findOne(id, abstractAuthenticationToken)
            .orElseThrow(() -> new RequestAlertException(HttpStatus.NOT_FOUND, "Entity not found", getEntityName(), "id.notFound"));
        updateUserOnKeycloak(dto, usersDTO, abstractAuthenticationToken);
        return super.update(id, dto, abstractAuthenticationToken);
    }

    @Override
    public UsersDTO partialUpdate(String id, UsersDTO dto, AbstractAuthenticationToken abstractAuthenticationToken) {
        if (dto.getRoles() != null && dto.getRoles().stream().anyMatch(roleEnum -> roleEnum == RoleEnum.ROLE_SUPER_ADMIN)) {
            throw new RequestAlertException(HttpStatus.FORBIDDEN, "Not allow", getEntityName(), "not.allow");
        }

        UsersDTO usersDTO = findOne(id, abstractAuthenticationToken)
            .orElseThrow(() -> new RequestAlertException(HttpStatus.NOT_FOUND, "Entity not found", getEntityName(), "id.notFound"));
        updateUserOnKeycloak(dto, usersDTO, abstractAuthenticationToken);
        return super.partialUpdate(id, dto, abstractAuthenticationToken);
    }

    @Override
    public UsersDTO partialUpdateOwn(UsersMeDTO dto, AbstractAuthenticationToken abstractAuthenticationToken) {
        UsersDTO currentUser = findMe(abstractAuthenticationToken)
            .orElseThrow(() -> new RequestAlertException(HttpStatus.NOT_FOUND, "User not found", getEntityName(), "id.notfound"));

        if (currentUser.getId() == null) {
            throw new RequestAlertException(HttpStatus.NOT_FOUND, "User not found", getEntityName(), "id.notfound");
        }

        // Fetch full Keycloak user to preserve existing attributes (e.g. tenant roles)
        User keycloakUser = keycloakService.getUser(currentUser.getKeycloakId());
        if (dto.getName() != null) keycloakUser.setFirstName(dto.getName());
        if (dto.getLastName() != null) keycloakUser.setLastName(dto.getLastName());
        if (dto.getEmail() != null) keycloakUser.setEmail(dto.getEmail());
        keycloakService.updateUser(keycloakUser);

        UsersDTO partialDto = new UsersDTO();
        partialDto.setId(currentUser.getId());
        partialDto.setName(dto.getName());
        partialDto.setLastName(dto.getLastName());
        partialDto.setEmail(dto.getEmail());
        return super.partialUpdate(currentUser.getId(), partialDto, abstractAuthenticationToken);
    }

    @Override
    public void deleteOwn(AbstractAuthenticationToken abstractAuthenticationToken) {
        UsersDTO currentUser = findMe(abstractAuthenticationToken)
            .orElseThrow(() -> new RequestAlertException(HttpStatus.NOT_FOUND, "User not found", getEntityName(), "id.notfound"));
        String keycloakId = Strings.isNotBlank(currentUser.getKeycloakId())
            ? currentUser.getKeycloakId()
            : SecurityUtils.getUserIdFromAuthentication(abstractAuthenticationToken);

        getTenantCodes(keycloakId, abstractAuthenticationToken)
            .forEach(tenantCode -> dataErasureService.softDeleteUserAccount(keycloakId, tenantCode));

        User keycloakUser = keycloakService.getUser(keycloakId);
        keycloakUser.setEnabled(false);
        keycloakService.updateUser(keycloakUser);
    }

    @Override
    public void deleteOwnForGdpr(AbstractAuthenticationToken abstractAuthenticationToken) {
        UsersDTO currentUser = findMe(abstractAuthenticationToken)
            .orElseThrow(() -> new RequestAlertException(HttpStatus.NOT_FOUND, "User not found", getEntityName(), "id.notfound"));
        String keycloakId = Strings.isNotBlank(currentUser.getKeycloakId())
            ? currentUser.getKeycloakId()
            : SecurityUtils.getUserIdFromAuthentication(abstractAuthenticationToken);

        getTenantCodes(keycloakId, abstractAuthenticationToken)
            .forEach(tenantCode -> dataErasureService.eraseUserData(keycloakId, tenantCode));
        keycloakService.deleteUser(keycloakId);
    }

    @Override
    public UsersDTO delete(String id, AbstractAuthenticationToken abstractAuthenticationToken) {
        UsersDTO user = findOne(id, abstractAuthenticationToken)
            .orElseThrow(() -> new RequestAlertException(HttpStatus.NOT_FOUND, "User not found", getEntityName(), "id.notfound"));
        String tenantCode = SecurityUtils.getTenantIdFromAuthentication(abstractAuthenticationToken);
        UsersDTO deleted = super.delete(id, abstractAuthenticationToken);
        removeUserFromTenant(user.getKeycloakId(), tenantCode);
        return deleted;
    }

    @Override
    public void deleteForGdpr(String id, AbstractAuthenticationToken abstractAuthenticationToken) {
        UsersDTO user = findOneIncludingDeleted(id, abstractAuthenticationToken)
            .orElseThrow(() -> new RequestAlertException(HttpStatus.NOT_FOUND, "User not found", getEntityName(), "id.notfound"));
        String tenantCode = SecurityUtils.getTenantIdFromAuthentication(abstractAuthenticationToken);
        dataErasureService.eraseUserData(user.getKeycloakId(), tenantCode);
        removeUserFromTenant(user.getKeycloakId(), tenantCode);
    }

    private Set<String> getTenantCodes(String keycloakId, AbstractAuthenticationToken abstractAuthenticationToken) {
        Set<String> tenantCodes = keycloakService.getUserGroups(keycloakId).stream()
            .map(Group::getName)
            .filter(Objects::nonNull)
            .collect(java.util.stream.Collectors.toSet());
        tenantCodes.add(SecurityUtils.getTenantIdFromAuthentication(abstractAuthenticationToken));
        return tenantCodes.stream()
            .filter(tenantCode -> tenantCode != null && !tenantCode.isBlank())
            .collect(java.util.stream.Collectors.toSet());
    }

    private void removeUserFromTenant(String userId, String tenantCode) {
        User user = keycloakService.getUser(userId);
        Map<String, List<String>> attributes = user.getAttributes();
        if (attributes != null) {
            attributes.remove(getAttributeKey(tenantCode));
            user.setAttributes(attributes);
            keycloakService.updateUser(user);
        }
        keycloakService.deleteUserGroup(userId, keycloakService.getGroupIdByName(tenantCode));
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
    public void sendSetupEmail(String id, AbstractAuthenticationToken abstractAuthenticationToken) {
        UsersDTO usersDTO = findOne(id, abstractAuthenticationToken)
            .orElseThrow(() -> new RequestAlertException(HttpStatus.NOT_FOUND, "Entity not found", getEntityName(), "id.notFound"));
        keycloakService.sendExecuteActionsEmail(usersDTO.getKeycloakId(), List.of("UPDATE_PASSWORD", "VERIFY_EMAIL"));
    }

    @Override
    public Page<CalendarEventsDTO> getUserCalendarEvents(String id, UserCalendarEventsCriteria userCalendarEventsCriteria, Pageable pageable, AbstractAuthenticationToken abstractAuthenticationToken) {
        StringFilter userFilter = new StringFilter();
        StateFilter stateFilter = new StateFilter();
        DateFilter startDateFilter = new DateFilter(), endDateFilter = new DateFilter();
        CalendarEventsCriteria criteria = new CalendarEventsCriteria();

        userFilter.setEquals(id);
        stateFilter.setIn(List.of(StateEnum.COMPLETE, StateEnum.PUBLIC));
        criteria.setPresentUserId(userFilter).setState(stateFilter);

        if (userCalendarEventsCriteria.getStartDate() != null) {
            startDateFilter.setGreaterThanOrEqual(userCalendarEventsCriteria.getStartDate());
            criteria.setStartDate(startDateFilter);
        }

        if (userCalendarEventsCriteria.getEndDate() != null) {
            endDateFilter.setLessThanOrEqual(userCalendarEventsCriteria.getEndDate());
            criteria.setEndDate(endDateFilter);
        }

        return calendarEventsService.findEntitiesByCriteria(criteria, pageable, abstractAuthenticationToken);
    }

    @Override
    public Page<CalendarEventsDTO> getCurrentUserCalendarEvents(UserCalendarEventsCriteria userCalendarEventsCriteria, Pageable pageable, AbstractAuthenticationToken abstractAuthenticationToken) {
        UsersDTO currentUser = findMe(abstractAuthenticationToken).orElse(new UsersDTO());

        if (Strings.isBlank(currentUser.getId())) {
            return new PageImpl<>(new ArrayList<>(), pageable, 0L);
        }

        return this.getUserCalendarEvents(currentUser.getId(), userCalendarEventsCriteria, pageable, abstractAuthenticationToken);
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
