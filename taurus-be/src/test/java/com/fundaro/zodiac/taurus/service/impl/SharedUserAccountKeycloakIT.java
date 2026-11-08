package com.fundaro.zodiac.taurus.service.impl;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.TenantUserMembership;
import com.fundaro.zodiac.taurus.domain.UserIdentity;
import com.fundaro.zodiac.taurus.domain.Users;
import com.fundaro.zodiac.taurus.domain.enumeration.RoleEnum;
import com.fundaro.zodiac.taurus.repository.*;
import com.fundaro.zodiac.taurus.service.CalendarEventsService;
import com.fundaro.zodiac.taurus.service.TenantsService;
import com.fundaro.zodiac.taurus.service.dto.TenantsDTO;
import com.fundaro.zodiac.taurus.service.dto.UsersDTO;
import com.fundaro.zodiac.taurus.service.mapper.UsersMapper;
import com.fundaro.zodiac.taurus.test.DockerRequirement;
import com.fundaro.zodiac.taurus.utils.keycloak.domain.Group;
import com.fundaro.zodiac.taurus.utils.keycloak.domain.User;
import com.fundaro.zodiac.taurus.utils.keycloak.service.KeycloakService;
import com.fundaro.zodiac.taurus.utils.keycloak.service.impl.KeycloakServiceImpl;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * User management against a real Keycloak, with the account of a person who belongs to two tenants: the session is
 * in the second tenant, and the first one must never notice what happens there.
 */
class SharedUserAccountKeycloakIT {
    private static final String ADMIN = "shared-account-test-admin";
    private static final String PASSWORD = "shared-account-test-password";

    private static GenericContainer<?> keycloak;
    private static KeycloakService keycloakService;

    private UsersRepository usersRepository;
    private UsersMapper usersMapper;
    private UsersServiceImpl service;
    private String firstTenant;
    private String secondTenant;

    @BeforeAll
    static void startKeycloak() throws Exception {
        DockerRequirement.required();
        keycloak = new GenericContainer<>("quay.io/keycloak/keycloak:26.8.2")
            .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", ADMIN)
            .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", PASSWORD)
            .withCommand("start-dev")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/realms/master").forPort(8080).forStatusCode(200))
            .withStartupTimeout(Duration.ofMinutes(3));
        keycloak.start();
        String baseUrl = "http://" + keycloak.getHost() + ":" + keycloak.getMappedPort(8080);
        prepareRealm(baseUrl);
        ApplicationProperties properties = new ApplicationProperties();
        properties.setKeycloak(new ApplicationProperties.Keycloak());
        properties.getKeycloak().setMasterUri(baseUrl + "/realms/master");
        properties.getKeycloak().getAdmin().setIssuerUri(baseUrl + "/admin/realms/master");
        properties.getKeycloak().getAdmin().setUsername(ADMIN);
        properties.getKeycloak().getAdmin().setPassword(PASSWORD);
        KeycloakServiceImpl client = new KeycloakServiceImpl(properties);
        ReflectionTestUtils.setField(client, "clientId", "admin-cli");
        keycloakService = client;
    }

    @AfterAll
    static void stopKeycloak() {
        if (keycloak != null) keycloak.stop();
    }

    /** Keeps the tenant attributes, as the Taurus realm does, and gives the client the roles of the tests. */
    private static void prepareRealm(String baseUrl) throws Exception {
        HttpClient http = HttpClient.newHttpClient();
        ObjectMapper mapper = new ObjectMapper();
        String form = "client_id=admin-cli&username=" + URLEncoder.encode(ADMIN, StandardCharsets.UTF_8) +
            "&password=" + URLEncoder.encode(PASSWORD, StandardCharsets.UTF_8) + "&grant_type=password";
        HttpResponse<String> tokenResponse = http.send(
            HttpRequest.newBuilder(URI.create(baseUrl + "/realms/master/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );
        assertThat(tokenResponse.statusCode()).isEqualTo(200);
        String token = mapper.readTree(tokenResponse.body()).path("access_token").asString();

        HttpResponse<String> currentProfile = http.send(
            HttpRequest.newBuilder(URI.create(baseUrl + "/admin/realms/master/users/profile")).header("Authorization", "Bearer " + token).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        );
        ObjectNode profile = (ObjectNode) mapper.readTree(currentProfile.body());
        profile.put("unmanagedAttributePolicy", "ENABLED");
        HttpResponse<String> profileResponse = http.send(
            HttpRequest.newBuilder(URI.create(baseUrl + "/admin/realms/master/users/profile"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(profile)))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );
        assertThat(profileResponse.statusCode()).isIn(200, 204);

        HttpResponse<String> clients = http.send(
            HttpRequest.newBuilder(URI.create(baseUrl + "/admin/realms/master/clients?clientId=admin-cli")).header("Authorization", "Bearer " + token).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        );
        JsonNode client = mapper.readTree(clients.body()).get(0);
        for (RoleEnum role : List.of(RoleEnum.ROLE_USER, RoleEnum.ROLE_ARCHIVIST)) {
            HttpResponse<String> roleResponse = http.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/admin/realms/master/clients/" + client.path("id").asString() + "/roles"))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"" + role.name() + "\"}"))
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            );
            assertThat(roleResponse.statusCode()).isIn(201, 409);
        }
    }

    @BeforeEach
    void setUp() {
        firstTenant = createTenantGroup();
        secondTenant = createTenantGroup();
        usersRepository = mock(UsersRepository.class);
        usersMapper = mock(UsersMapper.class);
        TenantsService tenantsService = mock(TenantsService.class);
        UserIdentityRepository identities = mock(UserIdentityRepository.class);
        TenantUserMembershipRepository memberships = mock(TenantUserMembershipRepository.class);
        service = new UsersServiceImpl(
            usersRepository,
            usersMapper,
            keycloakService,
            tenantsService,
            mock(CalendarEventsService.class),
            mock(DataErasureService.class),
            identities,
            mock(InstrumentsRepository.class),
            memberships,
            mock(TenantsRepository.class)
        );

        TenantsDTO tenant = new TenantsDTO();
        tenant.setId(1L);
        when(tenantsService.findByCode(any(), any())).thenReturn(Optional.of(tenant));
        when(usersRepository.count(org.mockito.ArgumentMatchers.<Specification<Users>>any())).thenReturn(0L);
        when(usersMapper.toEntity(any(UsersDTO.class))).thenAnswer(invocation -> new Users());
        when(usersMapper.toKeycloakUser(any(UsersDTO.class))).thenAnswer(invocation -> {
            UsersDTO dto = invocation.getArgument(0);
            User user = new User();
            user.setUsername(dto.getEmail());
            user.setEmail(dto.getEmail());
            user.setFirstName(dto.getName());
            user.setLastName(dto.getLastName());
            return user;
        });
        UserIdentity identity = new UserIdentity();
        identity.setId(3L);
        when(identities.findByKeycloakId(any())).thenReturn(Optional.of(identity));
        when(memberships.save(any(TenantUserMembership.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsTheUserInTheSecondTenantWithoutChangingTheAccount() {
        String userId = existingAccount(firstTenant);
        when(usersRepository.save(any(Users.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(usersMapper.toDto(any(Users.class))).thenReturn(new UsersDTO());

        service.save(newUser(userEmail(userId), true), session(secondTenant));

        User account = keycloakService.getUser(userId);
        assertThat(account.getFirstName()).isEqualTo("Profilo");
        assertThat(account.getLastName()).isEqualTo("Originale");
        assertThat(account.getEnabled()).isTrue();
        assertThat(account.getAttributes().get(firstTenant + "_roles")).containsExactly("ROLE_USER");
        assertThat(account.getAttributes().get(secondTenant + "_roles")).containsExactly("ROLE_ARCHIVIST");
        assertThat(groups(userId)).containsExactlyInAnyOrder(firstTenant, secondTenant);
    }

    @Test
    void createsAnInactiveUserInTheSecondTenantWithoutDisablingTheAccount() {
        String userId = existingAccount(firstTenant);
        when(usersRepository.save(any(Users.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(usersMapper.toDto(any(Users.class))).thenReturn(new UsersDTO());

        service.save(newUser(userEmail(userId), false), session(secondTenant));

        User account = keycloakService.getUser(userId);
        assertThat(account.getEnabled()).isTrue();
        assertThat(account.getAttributes()).doesNotContainKey(secondTenant + "_roles");
        assertThat(groups(userId)).containsExactlyInAnyOrder(firstTenant, secondTenant);
    }

    @Test
    void aFailedCreationOnlyUnlinksTheExistingAccountFromTheSecondTenant() {
        String userId = existingAccount(firstTenant);
        when(usersRepository.save(any(Users.class))).thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(() -> service.save(newUser(userEmail(userId), true), session(secondTenant)))
            .hasMessage("database unavailable");

        User account = keycloakService.getUser(userId);
        assertThat(account.getEnabled()).isTrue();
        assertThat(account.getFirstName()).isEqualTo("Profilo");
        assertThat(account.getAttributes().get(firstTenant + "_roles")).containsExactly("ROLE_USER");
        assertThat(account.getAttributes()).doesNotContainKey(secondTenant + "_roles");
        assertThat(groups(userId)).containsExactly(firstTenant);
    }

    @Test
    void aFailedCreationDeletesOnlyAnAccountItCreated() {
        String email = "new-" + UUID.randomUUID() + "@example.org";
        when(usersRepository.save(any(Users.class))).thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(() -> service.save(newUser(email, true), session(secondTenant)))
            .isInstanceOf(IllegalStateException.class);

        assertThat(keycloakService.getUsers()).noneMatch(user -> email.equalsIgnoreCase(user.getEmail()));
    }

    @Test
    void deactivatingTheUserInTheSecondTenantKeepsTheFirstTenantWorking() {
        String userId = existingAccount(firstTenant, secondTenant);
        stubStoredUser(userId, true);

        UsersDTO update = request(userId, true);
        update.setActive(false);
        service.update(2L, update, session(secondTenant));

        User account = keycloakService.getUser(userId);
        assertThat(account.getEnabled()).isTrue();
        assertThat(account.getAttributes().get(firstTenant + "_roles")).containsExactly("ROLE_USER");
        assertThat(account.getAttributes()).doesNotContainKey(secondTenant + "_roles");
        assertThat(groups(userId)).containsExactlyInAnyOrder(firstTenant, secondTenant);

        stubStoredUser(userId, false);
        service.update(2L, request(userId, true), session(secondTenant));

        assertThat(keycloakService.getUser(userId).getAttributes().get(secondTenant + "_roles")).containsExactly("ROLE_ARCHIVIST");
    }

    @Test
    void deactivatingTheUserOfASingleTenantDisablesTheAccount() {
        String userId = existingAccount(secondTenant);
        stubStoredUser(userId, true);

        UsersDTO update = request(userId, true);
        update.setActive(false);
        service.update(2L, update, session(secondTenant));

        assertThat(keycloakService.getUser(userId).getEnabled()).isFalse();
    }

    @Test
    void theProfileOfASharedAccountCannotBeChangedFromOneTenant() {
        String userId = existingAccount(firstTenant, secondTenant);
        stubStoredUser(userId, true);

        UsersDTO update = request(userId, true);
        update.setEmail("other-" + UUID.randomUUID() + "@example.org");

        assertThatThrownBy(() -> service.update(2L, update, session(secondTenant)))
            .isInstanceOfSatisfying(RequestAlertException.class, error -> assertThat(error.getErrorKey()).isEqualTo("user.profile.locked"));

        User account = keycloakService.getUser(userId);
        assertThat(account.getEmail()).isEqualTo(userEmail(userId));
        assertThat(account.getAttributes().get(secondTenant + "_roles")).containsExactly("ROLE_USER");
    }

    /** An account with the profile «Profilo Originale», in the groups of the tenants with the role ROLE_USER. */
    private static String existingAccount(String... tenants) {
        String email = "existing-" + UUID.randomUUID() + "@example.org";
        User user = new User();
        user.setUsername(email);
        user.setEmail(email);
        user.setFirstName("Profilo");
        user.setLastName("Originale");
        Map<String, List<String>> attributes = new HashMap<>();
        for (String tenant : tenants) attributes.put(tenant + "_roles", List.of("ROLE_USER"));
        user.setAttributes(attributes);
        keycloakService.saveUser(user);
        String userId = keycloakService.getUserIdByUsernameOrEmail(email, email);
        for (String tenant : tenants) keycloakService.updateUserGroup(userId, keycloakService.getGroupIdByName(tenant));
        return userId;
    }

    private static String userEmail(String userId) {
        return keycloakService.getUser(userId).getEmail();
    }

    private static List<String> groups(String userId) {
        return keycloakService.getUserGroups(userId).stream().map(Group::getName).toList();
    }

    private static String createTenantGroup() {
        String tenant = "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(Locale.ROOT);
        keycloakService.saveGroup(new Group(tenant, "Shared account integration test"));
        return tenant;
    }

    private static UsersDTO newUser(String email, boolean active) {
        UsersDTO dto = new UsersDTO();
        dto.setEmail(email);
        dto.setName("Nome");
        dto.setLastName("Digitato");
        dto.setRoles(Set.of(RoleEnum.ROLE_ARCHIVIST));
        dto.setActive(active);
        return dto;
    }

    /** The row of the user 2 in the second tenant, with the name typed by its administrator. */
    private void stubStoredUser(String userId, boolean active) {
        Users entity = new Users();
        UsersDTO stored = request(userId, active);
        stored.setId(2L);
        stored.setKeycloakId(userId);
        when(usersRepository.findByIdAndDeletedFalse(2L)).thenReturn(Optional.of(entity));
        when(usersRepository.save(entity)).thenReturn(entity);
        when(usersMapper.toDto(entity)).thenReturn(stored);
    }

    private static UsersDTO request(String userId, boolean active) {
        UsersDTO dto = new UsersDTO();
        dto.setId(2L);
        dto.setName("Nome");
        dto.setLastName("Digitato");
        dto.setEmail(userEmail(userId));
        dto.setRoles(Set.of(RoleEnum.ROLE_ARCHIVIST));
        dto.setActive(active);
        return dto;
    }

    private static JwtAuthenticationToken session(String tenant) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject("actor")
            .claim("tenant", tenant)
            .issuedAt(now)
            .expiresAt(now.plusSeconds(300))
            .build();
        return new JwtAuthenticationToken(jwt);
    }
}
