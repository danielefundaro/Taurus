package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.Users;
import com.fundaro.zodiac.taurus.security.AuthoritiesConstants;
import com.fundaro.zodiac.taurus.security.RoleGroups;
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
import org.springframework.beans.factory.annotation.Autowired;
import com.fundaro.zodiac.taurus.service.calendarfeed.CalendarFeedOwnerRevocationService;
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
    private CalendarFeedOwnerRevocationService calendarFeedRevocationService;

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

    @Autowired
    void setCalendarFeedRevocationService(CalendarFeedOwnerRevocationService value) {
        this.calendarFeedRevocationService = value;
    }

    @Override
    public UsersDTO save(UsersDTO dto, AbstractAuthenticationToken abstractAuthenticationToken) {
        if (dto.getRoles().stream().anyMatch(roleEnum -> roleEnum == RoleEnum.ROLE_SUPER_ADMIN)) {
            throw new RequestAlertException(HttpStatus.FORBIDDEN, "Not allow", getEntityName(), "user.role.forbidden");
        }
        requireNoAdministratorRole(dto.getRoles(), abstractAuthenticationToken);

        String tenantCode = SecurityUtils.getTenantIdFromAuthentication(abstractAuthenticationToken);
        TenantsDTO tenantsDTO = tenantsService.findByCode(tenantCode, abstractAuthenticationToken)
            .orElseThrow(() -> new RequestAlertException(HttpStatus.FORBIDDEN, "Tenant not found", getEntityName(), "auth.tenant.missing"));
        long usersCount = super.count(new UsersCriteria(), abstractAuthenticationToken);
        Long maxUsers = tenantsDTO.getMaxUsers();

        if (maxUsers != null && maxUsers >= 0 && usersCount >= maxUsers) {
            throw new RequestAlertException(HttpStatus.BAD_REQUEST, "Limit exceeded for this tenant (max users: %s)".formatted(maxUsers), getEntityName(), "user.limit.exceeded").with("maximum", maxUsers);
        }

        // The account is the person's, shared by all their tenants: an existing one is only linked to this tenant,
        // its profile and its enabled flag stay as they are.
        boolean active = !Boolean.FALSE.equals(dto.getActive());
        Optional<String> existingId = findKeycloakId(dto.getEmail());
        existingId.ifPresent(keycloakId -> requireNotAdministratorAccount(keycloakId, abstractAuthenticationToken));
        boolean created = existingId.isEmpty();
        String userId = existingId.orElse(null);
        TenantLink link = null;
        try {
            if (created) {
                User user = getMapper().toKeycloakUser(dto);
                user.setId(null);
                // Only this tenant knows a new account: its state is the state of the user in the tenant.
                user.setEnabled(active);
                keycloakService.saveUser(user);
                userId = keycloakService.getUserIdByUsernameOrEmail(dto.getEmail(), dto.getEmail());
            }
            User account = keycloakService.getUser(userId);
            link = readTenantLink(userId, account, tenantCode);
            if (!link.previouslyInGroup()) keycloakService.updateUserGroup(userId, link.groupId());
            setTenantRoles(account, tenantCode, active ? resolveRoleNames(dto.getRoles()) : List.of());
            keycloakService.updateUser(account);
        } catch (RuntimeException e) {
            undoSave(created, userId, link, tenantCode, e);
            throw e;
        }

        // Save keycloakId of the user
        String keycloakId = userId;
        dto.setKeycloakId(keycloakId);
        try {
            Users entity = getMapper().toEntity(dto);
            UserIdentity identity = userIdentityRepository.findByKeycloakId(keycloakId).orElseGet(() -> {
                UserIdentity newIdentity = new UserIdentity();
                newIdentity.setKeycloakId(keycloakId);
                return userIdentityRepository.save(newIdentity);
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
        } catch (RuntimeException e) {
            undoSave(created, keycloakId, link, tenantCode, e);
            throw e;
        }
    }

    // A board member manages the users of the tenant but not its administrators: only an administrator gives the
    // administrator role or acts on an administrator or a super administrator (docs/board-role-spec.md).

    private static boolean actsAsBoardMember(AbstractAuthenticationToken token) {
        return RoleGroups.hasAny(token, AuthoritiesConstants.BOARD) && !RoleGroups.isTenantAdmin(token);
    }

    private RequestAlertException administratorForbidden() {
        return new RequestAlertException(HttpStatus.FORBIDDEN, "Only an administrator can manage the administrators", getEntityName(), "user.admin.forbidden");
    }

    private void requireNoAdministratorRole(Collection<RoleEnum> roles, AbstractAuthenticationToken token) {
        if (roles != null && roles.contains(RoleEnum.ROLE_ADMIN) && actsAsBoardMember(token)) throw administratorForbidden();
    }

    /** The user is an administrator of the tenant, or the account is a super administrator. */
    private void requireNotAdministrator(UsersDTO user, AbstractAuthenticationToken token) {
        if (!actsAsBoardMember(token)) return;
        Set<RoleEnum> roles = user.getRoles() == null ? Set.of() : user.getRoles();
        if (roles.contains(RoleEnum.ROLE_ADMIN) || roles.contains(RoleEnum.ROLE_SUPER_ADMIN) || isSuperAdministrator(user.getKeycloakId())) {
            throw administratorForbidden();
        }
    }

    /** An existing account linked by a creation: a super administrator, or an administrator of any tenant. */
    private void requireNotAdministratorAccount(String keycloakId, AbstractAuthenticationToken token) {
        if (!actsAsBoardMember(token)) return;
        if (isSuperAdministrator(keycloakId) || administratorInAnyTenant(keycloakService.getUser(keycloakId))) throw administratorForbidden();
    }

    private boolean isSuperAdministrator(String keycloakId) {
        if (Strings.isBlank(keycloakId)) return false;
        return keycloakService.getUsersByClientRoles(RoleEnum.ROLE_SUPER_ADMIN).stream().anyMatch(user -> keycloakId.equals(user.getId()));
    }

    private static boolean administratorInAnyTenant(User account) {
        Map<String, List<String>> attributes = account == null ? null : account.getAttributes();
        if (attributes == null) return false;
        return attributes.entrySet().stream()
            .filter(entry -> entry.getKey().endsWith("_roles") && entry.getValue() != null)
            .flatMap(entry -> entry.getValue().stream())
            .filter(Objects::nonNull)
            .flatMap(value -> Arrays.stream(value.split(",")))
            .map(String::trim)
            .anyMatch(RoleEnum.ROLE_ADMIN.toString()::equals);
    }

    /**
     * The Keycloak id of the account with this e-mail, empty when there is none. Keycloak answers an unknown address
     * with an error, the same as before this lookup existed.
     */
    private Optional<String> findKeycloakId(String email) {
        try {
            return Optional.of(keycloakService.getUserIdByUsernameOrEmail(email, email));
        } catch (RequestAlertException e) {
            return Optional.empty();
        }
    }

    /** How the account was linked to the tenant before a creation, to put it back if the creation fails. */
    private record TenantLink(String groupId, boolean previouslyInGroup, List<String> previousRoles) {
    }

    private TenantLink readTenantLink(String userId, User account, String tenantCode) {
        String groupId = keycloakService.getGroupIdByName(tenantCode);
        boolean inGroup = keycloakService.getUserGroups(userId).stream().anyMatch(group -> tenantCode.equals(group.getName()));
        Map<String, List<String>> attributes = account.getAttributes();
        List<String> roles = attributes == null ? null : attributes.get(getAttributeKey(tenantCode));
        return new TenantLink(groupId, inGroup, roles == null ? null : List.copyOf(roles));
    }

    /**
     * Undoes only what the failed creation did: an account it created is deleted, an existing one gets back its roles
     * in the tenant and leaves the group only if the creation added it. A failure here is logged and the original
     * error is kept.
     */
    private void undoSave(boolean created, String userId, TenantLink link, String tenantCode, RuntimeException failure) {
        if (userId == null) return;
        try {
            if (created) {
                keycloakService.deleteUser(userId);
            } else if (link != null) {
                User account = keycloakService.getUser(userId);
                setTenantRoles(account, tenantCode, link.previousRoles());
                keycloakService.updateUser(account);
                if (!link.previouslyInGroup()) keycloakService.deleteUserGroup(userId, link.groupId());
            }
        } catch (RuntimeException undo) {
            getLogger().error("Could not undo the creation of user {} in tenant {} on Keycloak", userId, tenantCode, undo);
            failure.addSuppressed(undo);
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
            throw new RequestAlertException(HttpStatus.FORBIDDEN, "Not allow", getEntityName(), "user.role.forbidden");
        }
        requireNoAdministratorRole(dto.getRoles(), abstractAuthenticationToken);

        UsersDTO usersDTO = findOne(id, abstractAuthenticationToken)
            .orElseThrow(() -> new RequestAlertException(HttpStatus.NOT_FOUND, "Entity not found", getEntityName(), "id.notFound"));
        requireNotAdministrator(usersDTO, abstractAuthenticationToken);
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
        UsersDTO result = saveEntity(entity, abstractAuthenticationToken, false);
        if (calendarFeedRevocationService != null)
            calendarFeedRevocationService.revokeUnauthorized(entity, SecurityUtils.getUserIdFromAuthentication(abstractAuthenticationToken));
        return result;
    }

    @Override
    public UsersDTO partialUpdate(Long id, UsersDTO dto, AbstractAuthenticationToken abstractAuthenticationToken) {
        if (dto.getRoles() != null && dto.getRoles().stream().anyMatch(roleEnum -> roleEnum == RoleEnum.ROLE_SUPER_ADMIN)) {
            throw new RequestAlertException(HttpStatus.FORBIDDEN, "Not allow", getEntityName(), "user.role.forbidden");
        }

        UsersDTO usersDTO = findOne(id, abstractAuthenticationToken)
            .orElseThrow(() -> new RequestAlertException(HttpStatus.NOT_FOUND, "Entity not found", getEntityName(), "id.notFound"));
        requireNotAdministrator(usersDTO, abstractAuthenticationToken);
        // The roles left out keep their value; the update writes the account once.
        if (dto.getRoles() == null) dto.setRoles(usersDTO.getRoles() == null ? Set.of() : usersDTO.getRoles());
        return update(id, dto, abstractAuthenticationToken);
    }

    @Override
    public UsersDTO partialUpdateOwn(UsersMeDTO dto, AbstractAuthenticationToken abstractAuthenticationToken) {
        UsersDTO currentUser = findMe(abstractAuthenticationToken)
            .orElseThrow(() -> new RequestAlertException(HttpStatus.NOT_FOUND, "User not found", getEntityName(), "id.notFound"));

        if (currentUser.getId() == null) {
            throw new RequestAlertException(HttpStatus.NOT_FOUND, "User not found", getEntityName(), "id.notFound");
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
        // A date column: java.sql.Date keeps the calendar day whatever the time zone.
        if (dto.getBirthDate() != null) partialDto.setBirthDate(java.sql.Date.valueOf(dto.getBirthDate()));
        // Only the personal fields change, the null ones are ignored: the update of the administrators would refuse a
        // request without roles and would rewrite the roles in Keycloak.
        Users entity = getRepository().findByIdAndDeletedFalse(currentUser.getId())
            .orElseThrow(() -> new RequestAlertException(HttpStatus.NOT_FOUND, "User not found", getEntityName(), "id.notFound"));
        getMapper().partialUpdate(entity, partialDto);
        return saveEntity(entity, abstractAuthenticationToken, false);
    }

    @Override
    public void deleteOwn(AbstractAuthenticationToken abstractAuthenticationToken) {
        UsersDTO currentUser = findMe(abstractAuthenticationToken)
            .orElseThrow(() -> new RequestAlertException(HttpStatus.NOT_FOUND, "User not found", getEntityName(), "id.notFound"));
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
            .orElseThrow(() -> new RequestAlertException(HttpStatus.NOT_FOUND, "User not found", getEntityName(), "id.notFound"));
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
            .orElseThrow(() -> new RequestAlertException(HttpStatus.NOT_FOUND, "User not found", getEntityName(), "id.notFound"));
        requireNotAdministrator(user, abstractAuthenticationToken);
        String tenantCode = SecurityUtils.getTenantIdFromAuthentication(abstractAuthenticationToken);
        // Before the soft delete, while the row is still readable: the personal feeds of a user who
        // leaves the tenant are closed at once, and the history says why.
        if (calendarFeedRevocationService != null) {
            getRepository().findByIdAndDeletedFalse(id).ifPresent(entity ->
                calendarFeedRevocationService.revokeAll(entity, SecurityUtils.getUserIdFromAuthentication(abstractAuthenticationToken)));
        }
        UsersDTO deleted = super.delete(id, abstractAuthenticationToken);
        removeUserFromTenant(user.getKeycloakId(), tenantCode);
        return deleted;
    }

    @Override
    public void deleteForGdpr(Long id, AbstractAuthenticationToken abstractAuthenticationToken) {
        UsersDTO user = findOneIncludingDeleted(id)
            .orElseThrow(() -> new RequestAlertException(HttpStatus.NOT_FOUND, "User not found", getEntityName(), "id.notFound"));
        requireNotAdministrator(user, abstractAuthenticationToken);
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
        requireNotAdministrator(usersDTO, abstractAuthenticationToken);
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

    /** The names of the client roles matching the requested ones; empty when none is requested. */
    private List<String> resolveRoleNames(Set<RoleEnum> dtoRoles) {
        if (dtoRoles == null || dtoRoles.isEmpty()) return List.of();
        return keycloakService.getClientRoles().stream()
            .filter(role -> dtoRoles.stream().anyMatch(roleEnum -> role.getName().equalsIgnoreCase(roleEnum.toString())))
            .map(Role::getName)
            .toList();
    }

    /**
     * Sets the roles of the account in the tenant, the attribute the login reads to offer the tenant. Null or empty
     * removes it: the tenant no longer offers the access, the group and the other tenants stay as they are.
     */
    private void setTenantRoles(User account, String tenantCode, List<String> roleNames) {
        Map<String, List<String>> attributes = account.getAttributes() == null ? new HashMap<>() : new HashMap<>(account.getAttributes());
        if (roleNames == null || roleNames.isEmpty()) attributes.remove(getAttributeKey(tenantCode));
        else attributes.put(getAttributeKey(tenantCode), List.copyOf(roleNames));
        account.setAttributes(attributes);
    }

    /**
     * Applies an update of the user of this tenant to the account, which is shared by all the tenants of the person:
     * roles and state go to the attribute of this tenant; the account is disabled only when no tenant offers the
     * access any more, and enabled again only when the user goes back to active; name, last name and e-mail change
     * only when no other tenant shares the account.
     */
    private void updateUserOnKeycloak(UsersDTO dto, UsersDTO current, AbstractAuthenticationToken abstractAuthenticationToken) {
        String tenantCode = SecurityUtils.getTenantIdFromAuthentication(abstractAuthenticationToken);
        String userId = current.getKeycloakId();
        User account = keycloakService.getUser(userId);
        List<String> otherTenants = keycloakService.getUserGroups(userId).stream()
            .map(Group::getName)
            .filter(name -> name != null && !name.isBlank() && !name.equals(tenantCode))
            .toList();

        if (otherTenants.isEmpty()) {
            if (dto.getName() != null) account.setFirstName(dto.getName());
            if (dto.getLastName() != null) account.setLastName(dto.getLastName());
            if (dto.getEmail() != null) {
                account.setEmail(dto.getEmail());
                account.setUsername(dto.getEmail());
            }
        } else if (changesProfile(dto, current)) {
            throw new RequestAlertException(HttpStatus.CONFLICT, "Profile shared with other tenants", getEntityName(), "user.profile.locked");
        }

        boolean wasActive = !Boolean.FALSE.equals(current.getActive());
        boolean active = dto.getActive() == null ? wasActive : dto.getActive();
        setTenantRoles(account, tenantCode, active ? resolveRoleNames(dto.getRoles()) : List.of());

        boolean endSessions = false;
        if (!active) {
            boolean accessElsewhere = otherTenants.stream().anyMatch(tenant -> {
                List<String> roles = account.getAttributes().get(getAttributeKey(tenant));
                return roles != null && !roles.isEmpty();
            });
            if (!accessElsewhere) account.setEnabled(false);
            // Another tenant keeps the account enabled: the open sessions would go on in this tenant.
            else endSessions = wasActive;
        } else if (!wasActive && Boolean.FALSE.equals(account.getEnabled())) {
            account.setEnabled(true);
        }

        keycloakService.updateUser(account);
        if (endSessions) keycloakService.logoutUser(userId);
    }

    /** Whether the request changes name, last name or e-mail of the user of this tenant; null fields are left out. */
    private static boolean changesProfile(UsersDTO dto, UsersDTO current) {
        return changed(dto.getName(), current.getName(), false)
            || changed(dto.getLastName(), current.getLastName(), false)
            || changed(dto.getEmail(), current.getEmail(), true);
    }

    private static boolean changed(String requested, String stored, boolean ignoreCase) {
        if (requested == null) return false;
        String left = requested.trim();
        String right = stored == null ? "" : stored.trim();
        return ignoreCase ? !left.equalsIgnoreCase(right) : !left.equals(right);
    }

    private String getAttributeKey(String tenantId) {
        return "%s_roles".formatted(tenantId);
    }
}
