package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.Instruments;
import com.fundaro.zodiac.taurus.domain.TenantUserMembership;
import com.fundaro.zodiac.taurus.domain.UserIdentity;
import com.fundaro.zodiac.taurus.domain.Users;
import com.fundaro.zodiac.taurus.domain.enumeration.RoleEnum;
import com.fundaro.zodiac.taurus.repository.*;
import com.fundaro.zodiac.taurus.service.CalendarEventsService;
import com.fundaro.zodiac.taurus.service.TenantsService;
import com.fundaro.zodiac.taurus.service.dto.ChildrenEntitiesDTO;
import com.fundaro.zodiac.taurus.service.dto.TenantsDTO;
import com.fundaro.zodiac.taurus.service.dto.UsersDTO;
import com.fundaro.zodiac.taurus.service.dto.UsersMeDTO;
import com.fundaro.zodiac.taurus.service.mapper.UsersMapper;
import com.fundaro.zodiac.taurus.utils.keycloak.domain.Group;
import com.fundaro.zodiac.taurus.utils.keycloak.domain.Role;
import com.fundaro.zodiac.taurus.utils.keycloak.domain.User;
import com.fundaro.zodiac.taurus.utils.keycloak.service.KeycloakService;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsersServiceImplTest {

    @Mock
    UsersRepository usersRepository;
    @Mock
    UsersMapper usersMapper;
    @Mock
    KeycloakService keycloakService;
    @Mock
    TenantsService tenantsService;
    @Mock
    CalendarEventsService calendarEventsService;
    @Mock
    DataErasureService dataErasureService;
    @Mock
    UserIdentityRepository userIdentityRepository;
    @Mock
    InstrumentsRepository instrumentsRepository;
    @Mock
    TenantUserMembershipRepository membershipRepository;
    @Mock
    TenantsRepository tenantsRepository;

    private UsersServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UsersServiceImpl(
            usersRepository,
            usersMapper,
            keycloakService,
            tenantsService,
            calendarEventsService,
            dataErasureService,
            userIdentityRepository,
            instrumentsRepository,
            membershipRepository,
            tenantsRepository
        );
    }

    @Test
    void shouldKeepManagedInstrumentCollectionMutableWhenUpdatingUser() {
        Users entity = new Users();
        entity.setId(2L);
        List<Instruments> managedInstruments = new ArrayList<>();
        managedInstruments.add(new Instruments());
        entity.setInstruments(managedInstruments);

        UsersDTO storedDto = new UsersDTO();
        storedDto.setId(2L);
        storedDto.setKeycloakId("keycloak-user-2");

        ChildrenEntitiesDTO instrumentReference = new ChildrenEntitiesDTO();
        instrumentReference.setIndex(6L);
        instrumentReference.setOrder(1L);
        UsersDTO update = new UsersDTO();
        update.setId(2L);
        update.setRoles(Set.of(RoleEnum.ROLE_ARCHIVIST));
        update.setInstruments(new LinkedHashSet<>(List.of(instrumentReference)));

        User storedKeycloakUser = new User();
        Role archivist = new Role();
        archivist.setName(RoleEnum.ROLE_ARCHIVIST.name());
        Instruments resolvedInstrument = new Instruments();
        resolvedInstrument.setId(6L);

        when(usersRepository.findByIdAndDeletedFalse(2L)).thenReturn(Optional.of(entity));
        when(usersMapper.toDto(entity)).thenReturn(storedDto);
        when(keycloakService.getUser("keycloak-user-2")).thenReturn(storedKeycloakUser);
        when(keycloakService.getClientRoles()).thenReturn(List.of(archivist));
        when(instrumentsRepository.getReferenceById(6L)).thenReturn(resolvedInstrument);
        when(usersRepository.save(entity)).thenReturn(entity);

        service.update(2L, update, authentication());

        assertThat(entity.getInstruments()).isSameAs(managedInstruments).containsExactly(resolvedInstrument);
        entity.getInstruments().clear();
        assertThat(entity.getInstruments()).isEmpty();
    }

    @Test
    void shouldUpdateTheOwnProfileWithTheDateOfBirthWithoutTouchingTheRoles() {
        Users entity = new Users();
        entity.setId(2L);
        UsersDTO current = new UsersDTO();
        current.setId(2L);
        current.setKeycloakId("actor");
        User keycloakUser = new User();
        UsersDTO saved = new UsersDTO();
        JwtAuthenticationToken authentication = authentication();

        when(usersRepository.findAll(org.mockito.ArgumentMatchers.<Specification<Users>>any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(entity)));
        when(usersMapper.toDto(entity)).thenReturn(current, saved);
        when(keycloakService.getUser("actor")).thenReturn(keycloakUser);
        when(usersRepository.findByIdAndDeletedFalse(2L)).thenReturn(Optional.of(entity));
        when(usersRepository.save(entity)).thenReturn(entity);

        UsersMeDTO request = new UsersMeDTO();
        request.setName("Mario");
        request.setLastName("Rossi");
        request.setEmail("mario.rossi@example.org");
        request.setBirthDate(LocalDate.of(1990, 3, 4));

        assertThat(service.partialUpdateOwn(request, authentication)).isSameAs(saved);

        ArgumentCaptor<UsersDTO> changes = ArgumentCaptor.forClass(UsersDTO.class);
        verify(usersMapper).partialUpdate(org.mockito.ArgumentMatchers.eq(entity), changes.capture());
        assertThat(changes.getValue().getRoles()).isNull();
        assertThat(changes.getValue().getBirthDate()).isEqualTo(java.sql.Date.valueOf(LocalDate.of(1990, 3, 4)));
        assertThat(keycloakUser.getFirstName()).isEqualTo("Mario");
        assertThat(entity.getEditBy()).isEqualTo("actor");
        verify(keycloakService).updateUser(keycloakUser);
        verify(keycloakService, never()).getClientRoles();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = -1L)
    void shouldAllowSavingWhenTenantHasNoUserLimit(Long maxUsers) {
        UsersDTO input = new UsersDTO();
        input.setEmail("new-user@example.com");
        input.setRoles(Set.of());

        TenantsDTO tenant = new TenantsDTO();
        tenant.setId(1L);
        tenant.setMaxUsers(maxUsers);

        User keycloakUser = new User();
        Users entity = new Users();
        UsersDTO saved = new UsersDTO();
        saved.setId(2L);
        UserIdentity identity = new UserIdentity();
        identity.setId(3L);
        JwtAuthenticationToken authentication = authentication();

        when(tenantsService.findByCode("BMCDG", authentication)).thenReturn(Optional.of(tenant));
        when(usersRepository.count(org.mockito.ArgumentMatchers.<Specification<Users>>any())).thenReturn(100L);
        when(keycloakService.getUserIdByUsernameOrEmail(input.getEmail(), input.getEmail())).thenReturn("keycloak-user-2");
        when(keycloakService.getUser("keycloak-user-2")).thenReturn(keycloakUser);
        when(usersMapper.toEntity(input)).thenReturn(entity);
        when(userIdentityRepository.findByKeycloakId("keycloak-user-2")).thenReturn(Optional.of(identity));
        when(usersRepository.save(entity)).thenReturn(entity);
        when(usersMapper.toDto(entity)).thenReturn(saved);
        when(membershipRepository.save(any(TenantUserMembership.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.save(input, authentication)).isSameAs(saved);
    }

    // An account shared by two tenants: the session is in BMCDG, the person is also in OTHER.

    @Test
    void shouldLinkAnExistingAccountWithoutWritingItsProfile() {
        User account = account(true, Map.of("OTHER_roles", List.of("ROLE_USER")));
        JwtAuthenticationToken authentication = authentication();
        UsersDTO input = newUser(true);
        UsersDTO saved = stubSave(input, account, authentication);

        assertThat(service.save(input, authentication)).isSameAs(saved);

        assertThat(account.getFirstName()).isEqualTo("Profilo");
        assertThat(account.getLastName()).isEqualTo("Originale");
        assertThat(account.getEmail()).isEqualTo("mario@example.org");
        assertThat(account.getEnabled()).isTrue();
        assertThat(account.getAttributes())
            .containsEntry("OTHER_roles", List.of("ROLE_USER"))
            .containsEntry("BMCDG_roles", List.of("ROLE_ARCHIVIST"));
        verify(keycloakService).updateUserGroup("kc-1", "group-bmcdg");
        verify(keycloakService, never()).saveUser(any());
        verify(usersMapper, never()).toKeycloakUser(any());
    }

    @Test
    void shouldLinkAnInactiveUserToTheGroupWithoutRolesAndKeepTheAccountEnabled() {
        User account = account(true, Map.of("OTHER_roles", List.of("ROLE_USER")));
        JwtAuthenticationToken authentication = authentication();
        UsersDTO input = newUser(false);
        stubSave(input, account, authentication);

        service.save(input, authentication);

        assertThat(account.getEnabled()).isTrue();
        assertThat(account.getAttributes()).containsOnlyKeys("OTHER_roles");
        verify(keycloakService).updateUserGroup("kc-1", "group-bmcdg");
        verify(keycloakService, never()).getClientRoles();
    }

    @Test
    void shouldOnlyUnlinkAnExistingAccountWhenTheCreationFails() {
        User account = account(true, Map.of("OTHER_roles", List.of("ROLE_USER")));
        JwtAuthenticationToken authentication = authentication();
        UsersDTO input = newUser(true);
        stubSave(input, account, authentication);
        when(usersRepository.save(any(Users.class))).thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(() -> service.save(input, authentication)).hasMessage("database unavailable");

        verify(keycloakService, never()).deleteUser(any());
        verify(keycloakService).deleteUserGroup("kc-1", "group-bmcdg");
        assertThat(account.getAttributes()).containsOnly(Map.entry("OTHER_roles", List.of("ROLE_USER")));
    }

    @Test
    void shouldRestoreThePreviousRolesOfAnAccountAlreadyInTheGroupWhenTheCreationFails() {
        User account = account(true, Map.of("BMCDG_roles", List.of("ROLE_USER")));
        JwtAuthenticationToken authentication = authentication();
        UsersDTO input = newUser(true);
        stubSave(input, account, authentication);
        when(keycloakService.getUserGroups("kc-1")).thenReturn(List.of(group("BMCDG")));
        when(usersRepository.save(any(Users.class))).thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(() -> service.save(input, authentication)).isInstanceOf(IllegalStateException.class);

        assertThat(account.getAttributes()).containsOnly(Map.entry("BMCDG_roles", List.of("ROLE_USER")));
        verify(keycloakService, never()).updateUserGroup(any(), any());
        verify(keycloakService, never()).deleteUserGroup(any(), any());
        verify(keycloakService, never()).deleteUser(any());
    }

    @Test
    void shouldKeepTheOriginalErrorWhenTheUndoFails() {
        User account = account(true, Map.of("OTHER_roles", List.of("ROLE_USER")));
        JwtAuthenticationToken authentication = authentication();
        UsersDTO input = newUser(true);
        stubSave(input, account, authentication);
        when(usersRepository.save(any(Users.class))).thenThrow(new IllegalStateException("database unavailable"));
        org.mockito.Mockito.doThrow(new RequestAlertException(HttpStatus.BAD_GATEWAY, "Keycloak down", "Group", "keycloak.userGroup.deleteFailed"))
            .when(keycloakService).deleteUserGroup("kc-1", "group-bmcdg");

        assertThatThrownBy(() -> service.save(input, authentication))
            .hasMessage("database unavailable")
            .satisfies(error -> assertThat(error.getSuppressed()).hasSize(1));
    }

    @Test
    void shouldDeleteAnAccountCreatedByTheFailedCreation() {
        User account = account(true, null);
        JwtAuthenticationToken authentication = authentication();
        UsersDTO input = newUser(true);
        User created = new User();
        stubSave(input, account, authentication);
        when(keycloakService.getUserIdByUsernameOrEmail(input.getEmail(), input.getEmail()))
            .thenThrow(new RequestAlertException(HttpStatus.BAD_GATEWAY, "Not found", "User", "keycloak.user.readFailed"))
            .thenReturn("kc-1");
        when(keycloakService.getUserGroups("kc-1")).thenReturn(List.of());
        when(usersMapper.toKeycloakUser(input)).thenReturn(created);
        when(usersRepository.save(any(Users.class))).thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(() -> service.save(input, authentication)).isInstanceOf(IllegalStateException.class);

        verify(keycloakService).saveUser(created);
        verify(keycloakService).deleteUser("kc-1");
        verify(keycloakService, never()).deleteUserGroup(any(), any());
    }

    @Test
    void shouldCreateAnInactiveNewAccountDisabled() {
        User account = account(false, null);
        JwtAuthenticationToken authentication = authentication();
        UsersDTO input = newUser(false);
        User created = new User();
        stubSave(input, account, authentication);
        when(keycloakService.getUserIdByUsernameOrEmail(input.getEmail(), input.getEmail()))
            .thenThrow(new RequestAlertException(HttpStatus.BAD_GATEWAY, "Not found", "User", "keycloak.user.readFailed"))
            .thenReturn("kc-1");
        when(keycloakService.getUserGroups("kc-1")).thenReturn(List.of());
        when(usersMapper.toKeycloakUser(input)).thenReturn(created);

        service.save(input, authentication);

        assertThat(created.getEnabled()).isFalse();
        assertThat(account.getAttributes()).doesNotContainKey("BMCDG_roles");
    }

    @Test
    void shouldKeepTheAccountEnabledWhenAnotherTenantStillOffersTheAccess() {
        User account = account(true, Map.of("BMCDG_roles", List.of("ROLE_ARCHIVIST"), "OTHER_roles", List.of("ROLE_USER")));
        UsersDTO update = stubUpdate(stored(true), account, List.of(group("BMCDG"), group("OTHER")));
        update.setActive(false);

        service.update(2L, update, authentication());

        assertThat(account.getEnabled()).isTrue();
        assertThat(account.getAttributes()).containsOnly(Map.entry("OTHER_roles", List.of("ROLE_USER")));
        verify(keycloakService).logoutUser("kc-1");
    }

    @Test
    void shouldDisableTheAccountWhenNoTenantOffersTheAccess() {
        User account = account(true, Map.of("BMCDG_roles", List.of("ROLE_ARCHIVIST")));
        // Still in the group of OTHER, but not active there.
        UsersDTO update = stubUpdate(stored(true), account, List.of(group("BMCDG"), group("OTHER")));
        update.setActive(false);

        service.update(2L, update, authentication());

        assertThat(account.getEnabled()).isFalse();
        assertThat(account.getAttributes()).isEmpty();
        verify(keycloakService, never()).logoutUser(any());
    }

    @Test
    void shouldEnableTheAccountAgainWhenTheUserGoesBackToActive() {
        User account = account(false, Map.of());
        UsersDTO update = stubUpdate(stored(false), account, List.of(group("BMCDG")));
        update.setActive(true);
        stubArchivistRole();

        service.update(2L, update, authentication());

        assertThat(account.getEnabled()).isTrue();
        assertThat(account.getAttributes()).containsEntry("BMCDG_roles", List.of("ROLE_ARCHIVIST"));
    }

    @Test
    void shouldNotEnableADisabledAccountWhenTheStateDoesNotChange() {
        User account = account(false, Map.of("BMCDG_roles", List.of("ROLE_ARCHIVIST")));
        UsersDTO update = stubUpdate(stored(true), account, List.of(group("BMCDG")));
        update.setActive(true);
        stubArchivistRole();

        service.update(2L, update, authentication());

        assertThat(account.getEnabled()).isFalse();
    }

    @Test
    void shouldRefuseToChangeTheProfileOfASharedAccount() {
        User account = account(true, Map.of("BMCDG_roles", List.of("ROLE_ARCHIVIST"), "OTHER_roles", List.of("ROLE_USER")));
        Users entity = new Users();
        UsersDTO current = stored(true);
        when(usersRepository.findByIdAndDeletedFalse(2L)).thenReturn(Optional.of(entity));
        when(usersMapper.toDto(entity)).thenReturn(current);
        when(keycloakService.getUser("kc-1")).thenReturn(account);
        when(keycloakService.getUserGroups("kc-1")).thenReturn(List.of(group("BMCDG"), group("OTHER")));
        UsersDTO update = request(current);
        update.setEmail("altro@example.org");

        assertThatThrownBy(() -> service.update(2L, update, authentication()))
            .isInstanceOfSatisfying(RequestAlertException.class, error -> {
                assertThat(error.getErrorKey()).isEqualTo("user.profile.locked");
                assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            });

        verify(keycloakService, never()).updateUser(any());
        verify(usersRepository, never()).save(any());
    }

    @Test
    void shouldKeepTheProfileOfASharedAccountWhenTheRequestDoesNotChangeIt() {
        User account = account(true, Map.of("BMCDG_roles", List.of("ROLE_USER"), "OTHER_roles", List.of("ROLE_USER")));
        // The row of the tenant differs from the account: an unchanged request must not overwrite the account.
        UsersDTO update = stubUpdate(stored(true), account, List.of(group("BMCDG"), group("OTHER")));
        update.setEmail("  MARIO@example.org ");
        stubArchivistRole();

        service.update(2L, update, authentication());

        assertThat(account.getFirstName()).isEqualTo("Profilo");
        assertThat(account.getEmail()).isEqualTo("mario@example.org");
        assertThat(account.getAttributes()).containsEntry("BMCDG_roles", List.of("ROLE_ARCHIVIST"));
    }

    @Test
    void shouldChangeTheProfileOfAnAccountOfASingleTenant() {
        User account = account(true, Map.of("BMCDG_roles", List.of("ROLE_ARCHIVIST")));
        UsersDTO update = stubUpdate(stored(true), account, List.of(group("BMCDG")));
        update.setName("Maria");
        update.setEmail("maria@example.org");
        stubArchivistRole();

        service.update(2L, update, authentication());

        assertThat(account.getFirstName()).isEqualTo("Maria");
        assertThat(account.getLastName()).isEqualTo("Importato");
        assertThat(account.getEmail()).isEqualTo("maria@example.org");
        assertThat(account.getUsername()).isEqualTo("maria@example.org");
    }

    @Test
    void shouldKeepTheRolesLeftOutOfAPartialUpdateAndWriteTheAccountOnce() {
        User account = account(true, Map.of("BMCDG_roles", List.of("ROLE_ARCHIVIST")));
        UsersDTO update = stubUpdate(stored(true), account, List.of(group("BMCDG")));
        update.setRoles(null);
        stubArchivistRole();

        service.partialUpdate(2L, update, authentication());

        verify(keycloakService, times(1)).updateUser(account);
        assertThat(account.getAttributes()).containsEntry("BMCDG_roles", List.of("ROLE_ARCHIVIST"));
    }

    private static User account(boolean enabled, Map<String, List<String>> attributes) {
        User account = new User();
        account.setId("kc-1");
        account.setUsername("mario@example.org");
        account.setEmail("mario@example.org");
        account.setFirstName("Profilo");
        account.setLastName("Originale");
        account.setEnabled(enabled);
        account.setAttributes(attributes == null ? null : new HashMap<>(attributes));
        return account;
    }

    private static Group group(String name) {
        Group group = new Group(name, name);
        group.setId("group-" + name.toLowerCase(Locale.ROOT));
        return group;
    }

    private static UsersDTO newUser(boolean active) {
        UsersDTO dto = new UsersDTO();
        dto.setEmail("mario@example.org");
        dto.setName("Nome");
        dto.setLastName("Importato");
        dto.setRoles(Set.of(RoleEnum.ROLE_ARCHIVIST));
        dto.setActive(active);
        return dto;
    }

    /** The stubs of a creation in BMCDG for an account that exists and belongs to OTHER; tests override some. */
    private UsersDTO stubSave(UsersDTO input, User account, JwtAuthenticationToken authentication) {
        TenantsDTO tenant = new TenantsDTO();
        tenant.setId(1L);
        Users entity = new Users();
        UsersDTO saved = new UsersDTO();
        UserIdentity identity = new UserIdentity();
        identity.setId(3L);
        lenient().when(tenantsService.findByCode("BMCDG", authentication)).thenReturn(Optional.of(tenant));
        lenient().when(usersRepository.count(org.mockito.ArgumentMatchers.<Specification<Users>>any())).thenReturn(0L);
        lenient().when(keycloakService.getUserIdByUsernameOrEmail(input.getEmail(), input.getEmail())).thenReturn("kc-1");
        lenient().when(keycloakService.getUser("kc-1")).thenReturn(account);
        lenient().when(keycloakService.getGroupIdByName("BMCDG")).thenReturn("group-bmcdg");
        lenient().when(keycloakService.getUserGroups("kc-1")).thenReturn(List.of(group("OTHER")));
        if (Boolean.TRUE.equals(input.getActive())) stubArchivistRole();
        lenient().when(usersMapper.toEntity(input)).thenReturn(entity);
        lenient().when(userIdentityRepository.findByKeycloakId("kc-1")).thenReturn(Optional.of(identity));
        lenient().when(usersRepository.save(entity)).thenReturn(entity);
        lenient().when(usersMapper.toDto(entity)).thenReturn(saved);
        lenient().when(membershipRepository.save(any(TenantUserMembership.class))).thenAnswer(invocation -> invocation.getArgument(0));
        return saved;
    }

    /** The row of the user in BMCDG: its name differs from the profile of the account. */
    private static UsersDTO stored(boolean active) {
        UsersDTO dto = new UsersDTO();
        dto.setId(2L);
        dto.setKeycloakId("kc-1");
        dto.setName("Nome");
        dto.setLastName("Importato");
        dto.setEmail("mario@example.org");
        dto.setRoles(Set.of(RoleEnum.ROLE_ARCHIVIST));
        dto.setActive(active);
        return dto;
    }

    /** A request that changes nothing of the stored row. */
    private static UsersDTO request(UsersDTO current) {
        UsersDTO dto = new UsersDTO();
        dto.setId(current.getId());
        dto.setName(current.getName());
        dto.setLastName(current.getLastName());
        dto.setEmail(current.getEmail());
        dto.setRoles(Set.of(RoleEnum.ROLE_ARCHIVIST));
        dto.setActive(current.getActive());
        return dto;
    }

    /** The stubs of an update of the user 2 in BMCDG; returns a request that changes nothing. */
    private UsersDTO stubUpdate(UsersDTO current, User account, List<Group> groups) {
        Users entity = new Users();
        when(usersRepository.findByIdAndDeletedFalse(2L)).thenReturn(Optional.of(entity));
        when(usersMapper.toDto(entity)).thenReturn(current);
        when(keycloakService.getUser("kc-1")).thenReturn(account);
        when(keycloakService.getUserGroups("kc-1")).thenReturn(groups);
        when(usersRepository.save(entity)).thenReturn(entity);
        return request(current);
    }

    private void stubArchivistRole() {
        Role archivist = new Role();
        archivist.setName(RoleEnum.ROLE_ARCHIVIST.name());
        lenient().when(keycloakService.getClientRoles()).thenReturn(List.of(archivist));
    }

    private JwtAuthenticationToken authentication() {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject("actor")
            .claim("tenant", "BMCDG")
            .issuedAt(now)
            .expiresAt(now.plusSeconds(300))
            .build();
        return new JwtAuthenticationToken(jwt);
    }
}
