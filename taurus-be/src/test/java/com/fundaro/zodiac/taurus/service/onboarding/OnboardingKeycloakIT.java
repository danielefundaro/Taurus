package com.fundaro.zodiac.taurus.service.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.onboarding.OnboardingIdentityOperation;
import com.fundaro.zodiac.taurus.domain.onboarding.OnboardingImportJob;
import com.fundaro.zodiac.taurus.domain.onboarding.OnboardingImportRow;
import com.fundaro.zodiac.taurus.domain.onboarding.OnboardingRowAction;
import com.fundaro.zodiac.taurus.repository.onboarding.OnboardingIdentityOperationRepository;
import com.fundaro.zodiac.taurus.utils.keycloak.domain.Group;
import com.fundaro.zodiac.taurus.utils.keycloak.domain.User;
import com.fundaro.zodiac.taurus.utils.keycloak.service.KeycloakService;
import com.fundaro.zodiac.taurus.utils.keycloak.service.impl.KeycloakServiceImpl;
import java.time.Duration;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class OnboardingKeycloakIT {
    private static final String ADMIN = "onboarding-test-admin";
    private static final String PASSWORD = "onboarding-test-password";

    @Container
    private static final GenericContainer<?> keycloak = new GenericContainer<>("quay.io/keycloak/keycloak:26.5.2")
        .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", ADMIN)
        .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", PASSWORD)
        .withCommand("start-dev")
        .withExposedPorts(8080)
        .waitingFor(Wait.forHttp("/realms/master").forPort(8080).forStatusCode(200))
        .withStartupTimeout(Duration.ofMinutes(3));

    private static KeycloakService keycloakService;

    @BeforeAll
    static void configureClient() throws Exception {
        String baseUrl = "http://" + keycloak.getHost() + ":" + keycloak.getMappedPort(8080);
        enableUnmanagedAttributes(baseUrl);
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

    private static void enableUnmanagedAttributes(String baseUrl) throws Exception {
        HttpClient http = HttpClient.newHttpClient();
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
        ObjectMapper mapper = new ObjectMapper();
        String token = mapper.readTree(tokenResponse.body()).path("access_token").asText();
        HttpResponse<String> currentProfile = http.send(
            HttpRequest.newBuilder(URI.create(baseUrl + "/admin/realms/master/users/profile"))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );
        assertThat(currentProfile.statusCode()).isEqualTo(200);
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
    }

    @Test
    void createsLinksAndCompensatesANewIdentityAgainstKeycloak() {
        String tenant = createTenantGroup();
        String email = "new-" + UUID.randomUUID() + "@example.org";
        Journal journal = new Journal();
        OnboardingIdentitySagaService saga = new OnboardingIdentitySagaService(keycloakService, journal.repository());
        OnboardingImportRow row = userRow(1L, email, "Nuovo", "Utente", "ADMIN|USER");

        String userId = saga.prepare(new OnboardingImportJob(), List.of(row), tenant).get(1L);

        User created = keycloakService.getUser(userId);
        assertThat(created.getFirstName()).isEqualTo("Nuovo");
        assertThat(created.getAttributes().get(tenant + "_roles")).containsExactly("ROLE_ADMIN", "ROLE_USER");
        assertThat(keycloakService.getUserGroups(userId)).extracting(Group::getName).contains(tenant);

        assertThat(saga.compensate(10L, tenant)).isTrue();
        assertThat(keycloakService.getUsers()).noneMatch(user -> email.equalsIgnoreCase(user.getEmail()));
    }

    @Test
    void linksAnExistingIdentityWithoutChangingItsProfileAndRestoresIt() {
        String tenant = createTenantGroup();
        String email = "existing-" + UUID.randomUUID() + "@example.org";
        User original = new User();
        original.setUsername(email);
        original.setEmail(email);
        original.setFirstName("Profilo");
        original.setLastName("Originale");
        original.setAttributes(new HashMap<>(Map.of(tenant + "_roles", List.of("ROLE_USER"))));
        keycloakService.saveUser(original);
        String userId = keycloakService.getUserIdByUsernameOrEmail(email, email);
        Journal journal = new Journal();
        OnboardingIdentitySagaService saga = new OnboardingIdentitySagaService(keycloakService, journal.repository());
        OnboardingImportRow row = userRow(2L, email, "Nome", "Importato", "TREASURER");

        saga.prepare(new OnboardingImportJob(), List.of(row), tenant);

        User linked = keycloakService.getUser(userId);
        assertThat(linked.getFirstName()).isEqualTo("Profilo");
        assertThat(linked.getLastName()).isEqualTo("Originale");
        assertThat(linked.getAttributes().get(tenant + "_roles")).containsExactly("ROLE_TREASURER");
        assertThat(saga.compensate(10L, tenant)).isTrue();

        User restored = keycloakService.getUser(userId);
        assertThat(restored.getAttributes().get(tenant + "_roles")).containsExactly("ROLE_USER");
        assertThat(keycloakService.getUserGroups(userId)).extracting(Group::getName).doesNotContain(tenant);
    }

    private static String createTenantGroup() {
        String tenant = "tenant-" + UUID.randomUUID();
        keycloakService.saveGroup(new Group(tenant, "Onboarding integration test"));
        return tenant;
    }

    private static OnboardingImportRow userRow(Long id, String email, String firstName, String lastName, String roles) {
        OnboardingImportRow row = new OnboardingImportRow();
        ReflectionTestUtils.setField(row, "id", id);
        row.setRowNumber(Math.toIntExact(id));
        row.setAction(OnboardingRowAction.CREATE);
        row.setNormalizedPayload(
            Map.of("email", email, "nome", firstName, "cognome", lastName, "ruoli", roles, "attivo", "SI")
        );
        return row;
    }

    private static final class Journal {
        private final Map<Long, OnboardingIdentityOperation> byRow = new HashMap<>();
        private final OnboardingIdentityOperationRepository repository = mock(OnboardingIdentityOperationRepository.class);

        private Journal() {
            when(repository.findByRow_Id(any())).thenAnswer(invocation -> Optional.ofNullable(byRow.get(invocation.getArgument(0))));
            when(repository.save(any())).thenAnswer(invocation -> {
                OnboardingIdentityOperation operation = invocation.getArgument(0);
                byRow.put(operation.getRow().getId(), operation);
                return operation;
            });
            when(repository.findAllByJob_IdOrderByRow_RowNumberDesc(any())).thenAnswer(invocation -> {
                List<OnboardingIdentityOperation> operations = new ArrayList<>(byRow.values());
                operations.sort((left, right) -> Integer.compare(right.getRow().getRowNumber(), left.getRow().getRowNumber()));
                return operations;
            });
        }

        private OnboardingIdentityOperationRepository repository() {
            return repository;
        }
    }
}
