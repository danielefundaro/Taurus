package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.Users;
import com.fundaro.zodiac.taurus.domain.UserIdentity;
import com.fundaro.zodiac.taurus.domain.TenantUserMembership;
import com.fundaro.zodiac.taurus.domain.TenantUserMembershipId;
import com.fundaro.zodiac.taurus.domain.criteria.CalendarEventsCriteria;
import com.fundaro.zodiac.taurus.domain.criteria.UserCalendarEventsCriteria;
import com.fundaro.zodiac.taurus.domain.criteria.UsersCriteria;
import com.fundaro.zodiac.taurus.domain.criteria.filter.DateFilter;
import com.fundaro.zodiac.taurus.domain.criteria.filter.StateFilter;
import com.fundaro.zodiac.taurus.domain.enumeration.RoleEnum;
import com.fundaro.zodiac.taurus.domain.enumeration.StateEnum;
import com.fundaro.zodiac.taurus.repository.InstrumentsRepository;
import com.fundaro.zodiac.taurus.repository.UserIdentityRepository;
import com.fundaro.zodiac.taurus.repository.UsersRepository;
import com.fundaro.zodiac.taurus.repository.TenantUserMembershipRepository;
import com.fundaro.zodiac.taurus.repository.TenantsRepository;
import com.fundaro.zodiac.taurus.security.SecurityUtils;
import com.fundaro.zodiac.taurus.service.CalendarEventsService;
import com.fundaro.zodiac.taurus.service.TenantsService;
import com.fundaro.zodiac.taurus.service.UsersService;
import com.fundaro.zodiac.taurus.service.dto.CalendarEventsDTO;
import com.fundaro.zodiac.taurus.service.dto.TenantsDTO;
import com.fundaro.zodiac.taurus.service.dto.UsersDTO;
import com.fundaro.zodiac.taurus.service.dto.UsersMeDTO;
import com.fundaro.zodiac.taurus.service.mapper.UsersMapper;
import com.fundaro.zodiac.taurus.utils.keycloak.domain.Role;
import com.fundaro.zodiac.taurus.utils.keycloak.domain.Group;
import com.fundaro.zodiac.taurus.utils.keycloak.domain.User;
import com.fundaro.zodiac.taurus.utils.keycloak.service.KeycloakService;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import org.apache.logging.log4j.util.Strings;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.jhipster.service.filter.StringFilter;
import tech.jhipster.service.filter.LongFilter;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service Implementation for managing {@link Users}.
 */
@Service
@Transactional
public class UsersServiceImpl extends CommonOpenSearchServiceImpl<Users, UsersDTO, UsersCriteria, UsersMapper, UsersRepository> implements UsersService {

    public final KeycloakService keycloakService;

    public final TenantsService tenantsService;

    public final CalendarEventsService calendarEventsService;

    private final DataErasureService dataErasureService;
    private final UserIdentityRepository userIdentityRepository;
    private final InstrumentsRepository instrumentsRepository;
    private final TenantUserMembershipRepository membershipRepository;
    private final TenantsRepository tenantsRepository;

    public UsersServiceImpl(UsersRepository repository, UsersMapper mapper, KeycloakService keycloakService, TenantsService tenantsService, CalendarEventsService calendarEventsService, DataErasureService dataErasureService, UserIdentityRepository userIdentityRepository, InstrumentsRepository instrumentsRepository, TenantUserMembershipRepository membershipRepository, TenantsRepository tenantsRepository) {
        super(repository, mapper, UsersService.class, Users.class);
        this.keycloakService = keycloakService;
        this.tenantsService = tenantsService;
        this.calendarEventsService = calendarEventsService;
        this.dataErasureService = dataErasureService;
        this.userIdentityRepository = userIdentityRepository;
        this.instrumentsRepository = instrumentsRepository;
        this.membershipRepository = membershipRepository;
        this.tenantsRepository = tenantsRepository;
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
            Users entity = getMapper().toEntity(dto);
            UserIdentity identity = userIdentityRepository.findByKeycloakId(userId).orElseGet(() -> {
                UserIdentity created = new UserIdentity();
                created.setKeycloakId(userId);
                return userIdentityRepository.save(created);
            });
            entity.setUserIdentity(identity);
            entity.setInstruments(resolveInstruments(dto));
            UsersDTO saved = saveEntity(entity, abstractAuthenticationToken, true);
            TenantUserMembership membership = new TenantUserMembership();
            membership.setId(new TenantUserMembershipId(tenantsDTO.getId(), identity.getId()));
            membership.setActive(true);
            membership.setJoinedAt(java.time.ZonedDateTime.now());
            membershipRepository.save(membership);
            return saved;
        } catch (Exception e) {
            keycloakService.deleteUser(userId);
            throw e;
        }
    }

    @Override
    protected Specification<Users> buildSpecification(UsersCriteria criteria) {
        return super.buildSpecification(criteria).and((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (criteria == null) return cb.conjunction();
            addStringFilter(predicates, cb, root.get("lastName"), criteria.getLastName());
            addRangeFilter(predicates, cb, root.get("birthDate"), criteria.getBirthDate());
            addStringFilter(predicates, cb, root.get("email"), criteria.getEmail());
            addFilter(predicates, cb, root.get("active"), criteria.getActive());
            addStringFilter(predicates, cb, root.get("keycloakId"), criteria.getKeycloakId());
            if (criteria.getRoles() != null) {
                query.distinct(true);
                addFilter(predicates, cb, root.join("roles"), criteria.getRoles());
            }
            if (criteria.getInstrumentId() != null) {
                query.distinct(true);
                addFilter(predicates, cb, root.join("instruments").get("id"), criteria.getInstrumentId());
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        });
    }

    @Override
    public UsersDTO update(Long id, UsersDTO dto, AbstractAuthenticationToken abstractAuthenticationToken) {
        if (dto.getRoles() == null || dto.getRoles().stream().anyMatch(roleEnum -> roleEnum == RoleEnum.ROLE_SUPER_ADMIN)) {
            throw new RequestAlertException(HttpStatus.FORBIDDEN, "Not allow", getEntityName(), "not.allow");
        }

        UsersDTO usersDTO = findOne(id, abstractAuthenticationToken)
            .orElseThrow(() -> new RequestAlertException(HttpStatus.NOT_FOUND, "Entity not found", getEntityName(), "id.notFound"));
        updateUserOnKeycloak(dto, usersDTO, abstractAuthenticationToken);
        Users entity = getRepository().findByIdAndDeletedFalse(id).orElseThrow();
        getMapper().partialUpdate(entity, dto);
        if (dto.getInstruments() != null) {
            List<com.fundaro.zodiac.taurus.domain.Instruments> instruments = resolveInstruments(dto);
            if (entity.getInstruments() == null) {
                entity.setInstruments(instruments);
            } else {
                entity.getInstruments().clear();
                entity.getInstruments().addAll(instruments);
            }
        }
        return saveEntity(entity, abstractAuthenticationToken, false);
    }

    @Override
    public UsersDTO partialUpdate(Long id, UsersDTO dto, AbstractAuthenticationToken abstractAuthenticationToken) {
        if (dto.getRoles() != null && dto.getRoles().stream().anyMatch(roleEnum -> roleEnum == RoleEnum.ROLE_SUPER_ADMIN)) {
            throw new RequestAlertException(HttpStatus.FORBIDDEN, "Not allow", getEntityName(), "not.allow");
        }

        UsersDTO usersDTO = findOne(id, abstractAuthenticationToken)
            .orElseThrow(() -> new RequestAlertException(HttpStatus.NOT_FOUND, "Entity not found", getEntityName(), "id.notFound"));
        updateUserOnKeycloak(dto, usersDTO, abstractAuthenticationToken);
        return update(id, dto, abstractAuthenticationToken);
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

        String requestedBy = SecurityUtils.getUserIdFromAuthentication(abstractAuthenticationToken);
        boolean deferred = false;
        for (String tenantCode : getTenantCodes(keycloakId, abstractAuthenticationToken)) {
            deferred |= dataErasureService.requestInventoryAwareErasure(
                keycloakId,
                currentUser.getId(),
                tenantCode,
                String.join(" ", Objects.toString(currentUser.getName(), ""), Objects.toString(currentUser.getLastName(), "")).trim(),
                currentUser.getEmail(),
                requestedBy
            );
        }
        if (deferred) {
            User keycloakUser = keycloakService.getUser(keycloakId);
            keycloakUser.setEnabled(false);
            keycloakService.updateUser(keycloakUser);
        } else {
            keycloakService.deleteUser(keycloakId);
        }
    }

    @Override
    public UsersDTO delete(Long id, AbstractAuthenticationToken abstractAuthenticationToken) {
        UsersDTO user = findOne(id, abstractAuthenticationToken)
            .orElseThrow(() -> new RequestAlertException(HttpStatus.NOT_FOUND, "User not found", getEntityName(), "id.notfound"));
        String tenantCode = SecurityUtils.getTenantIdFromAuthentication(abstractAuthenticationToken);
        UsersDTO deleted = super.delete(id, abstractAuthenticationToken);
        removeUserFromTenant(user.getKeycloakId(), tenantCode);
        return deleted;
    }

    @Override
    public void deleteForGdpr(Long id, AbstractAuthenticationToken abstractAuthenticationToken) {
        UsersDTO user = findOneIncludingDeleted(id)
            .orElseThrow(() -> new RequestAlertException(HttpStatus.NOT_FOUND, "User not found", getEntityName(), "id.notfound"));
        String tenantCode = SecurityUtils.getTenantIdFromAuthentication(abstractAuthenticationToken);
        boolean deferred = dataErasureService.requestInventoryAwareErasure(
            user.getKeycloakId(),
            user.getId(),
            tenantCode,
            String.join(" ", Objects.toString(user.getName(), ""), Objects.toString(user.getLastName(), "")).trim(),
            user.getEmail(),
            SecurityUtils.getUserIdFromAuthentication(abstractAuthenticationToken)
        );
        if (deferred) {
            User keycloakUser = keycloakService.getUser(user.getKeycloakId());
            keycloakUser.setEnabled(false);
            keycloakService.updateUser(keycloakUser);
        } else {
            removeUserFromTenant(user.getKeycloakId(), tenantCode);
        }
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
        userIdentityRepository.findByKeycloakId(userId).ifPresent(identity ->
            tenantsRepository.findByCodeAndDeletedFalse(tenantCode).ifPresent(tenant ->
                membershipRepository.findById(new TenantUserMembershipId(tenant.getId(), identity.getId())).ifPresent(membership -> {
                    membership.setActive(false);
                    membership.setLeftAt(java.time.ZonedDateTime.now());
                    membershipRepository.save(membership);
                })
            )
        );
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
    public void sendSetupEmail(Long id, AbstractAuthenticationToken abstractAuthenticationToken) {
        UsersDTO usersDTO = findOne(id, abstractAuthenticationToken)
            .orElseThrow(() -> new RequestAlertException(HttpStatus.NOT_FOUND, "Entity not found", getEntityName(), "id.notFound"));
        keycloakService.sendExecuteActionsEmail(usersDTO.getKeycloakId(), List.of("UPDATE_PASSWORD", "VERIFY_EMAIL"));
    }

    @Override
    public Page<CalendarEventsDTO> getUserCalendarEvents(Long id, UserCalendarEventsCriteria userCalendarEventsCriteria, Pageable pageable, AbstractAuthenticationToken abstractAuthenticationToken) {
        LongFilter userFilter = new LongFilter();
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

        if (currentUser.getId() == null) {
            return new PageImpl<>(new ArrayList<>(), pageable, 0L);
        }

        return this.getUserCalendarEvents(currentUser.getId(), userCalendarEventsCriteria, pageable, abstractAuthenticationToken);
    }

    private List<com.fundaro.zodiac.taurus.domain.Instruments> resolveInstruments(UsersDTO dto) {
        if (dto.getInstruments() == null) return new ArrayList<>();
        return dto.getInstruments().stream()
            .sorted(Comparator.comparing(ref -> ref.getOrder() == null ? Long.MAX_VALUE : ref.getOrder()))
            .map(ref -> instrumentsRepository.getReferenceById(ref.getIndex()))
            .collect(Collectors.toCollection(ArrayList::new));
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
