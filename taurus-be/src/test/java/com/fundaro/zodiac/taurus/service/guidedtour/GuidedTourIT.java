package com.fundaro.zodiac.taurus.service.guidedtour;

import com.fundaro.zodiac.taurus.IntegrationTest;
import com.fundaro.zodiac.taurus.domain.UserIdentity;
import com.fundaro.zodiac.taurus.domain.Users;
import com.fundaro.zodiac.taurus.domain.enumeration.RoleEnum;
import com.fundaro.zodiac.taurus.domain.guidedtour.GuidedTourProfile;
import com.fundaro.zodiac.taurus.domain.guidedtour.GuidedTourStatus;
import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaProvisioningService;
import com.fundaro.zodiac.taurus.multitenancy.TenantTransactionExecutor;
import com.fundaro.zodiac.taurus.repository.UsersRepository;
import com.fundaro.zodiac.taurus.repository.guidedtour.GuidedTourProfileRepository;
import com.fundaro.zodiac.taurus.repository.guidedtour.GuidedTourProgressRepository;
import com.fundaro.zodiac.taurus.service.dto.guidedtour.GuidedTourDTO;
import com.fundaro.zodiac.taurus.service.dto.guidedtour.GuidedTourProgressRequest;
import com.fundaro.zodiac.taurus.service.dto.guidedtour.GuidedTourSettingsRequest;
import com.fundaro.zodiac.taurus.service.impl.DataErasureService;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Settings and progress of the guided tour on PostgreSQL: one profile per person in each tenant schema, one progress
 * per role, the super administrator without a tenant user and the erasure of the person.
 */
@IntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(
    properties = {
        "application.base-path=D:/data",
        "spring.liquibase.contexts=test",
        "spring.datasource.hikari.maximum-pool-size=4",
        "spring.datasource.hikari.minimum-idle=1",
        "spring.security.oauth2.client.registration.oidc.client-id=test",
        "spring.security.oauth2.client.registration.oidc.client-secret=test",
    }
)
class GuidedTourIT {

    private static final String SUBJECT = "guided-tour-user-1";
    private static final String SUPER_ADMIN = "guided-tour-super-admin";
    /** Erased in its own test: the anonymised user keeps its identity, so it cannot be created again. */
    private static final String ERASED = "guided-tour-erased-user";

    @MockitoBean
    ClientRegistrationRepository clientRegistrationRepository;
    @MockitoBean
    JwtDecoder jwtDecoder;
    @Autowired
    TenantSchemaProvisioningService provisioningService;
    @Autowired
    TenantTransactionExecutor transactionExecutor;
    @Autowired
    GuidedTourService service;
    @Autowired
    GuidedTourProfileRepository profileRepository;
    @Autowired
    GuidedTourProgressRepository progressRepository;
    @Autowired
    UsersRepository usersRepository;
    @Autowired
    DataErasureService dataErasureService;
    @PersistenceContext
    EntityManager entityManager;

    private final String tenantOne = "guided-tour-a-" + UUID.randomUUID();
    private final String tenantTwo = "guided-tour-b-" + UUID.randomUUID();

    @BeforeAll
    void provisionTenants() {
        provisioningService.provision(tenantOne);
        provisioningService.provision(tenantTwo);
    }

    @BeforeEach
    void prepare() {
        for (String tenant : List.of(tenantOne, tenantTwo)) {
            transactionExecutor.execute(tenant, () -> {
                if (usersRepository.findByKeycloakIdAndDeletedFalse(SUBJECT).isEmpty()) createUser(SUBJECT);
            });
        }
    }

    @AfterEach
    void cleanTenantData() {
        for (String tenant : List.of(tenantOne, tenantTwo)) {
            transactionExecutor.execute(tenant, () -> {
                progressRepository.deleteAllInBatch();
                profileRepository.deleteAllInBatch();
            });
        }
    }

    @AfterAll
    void dropTenants() {
        provisioningService.dropSchema(tenantOne);
        provisioningService.dropSchema(tenantTwo);
    }

    @Test
    void readsCompleteDefaultsWithoutWriting() {
        transactionExecutor.execute(tenantOne, () -> {
            GuidedTourDTO tour = service.get(authentication(SUBJECT, RoleEnum.ROLE_USER));

            assertThat(tour.enabled()).isTrue();
            assertThat(tour.settings().version()).isNull();
            assertThat(tour.settings().autoStartEnabled()).isTrue();
            assertThat(tour.progress()).isNull();
            assertThat(profileRepository.count()).isZero();
        });
    }

    @Test
    void storesTheSeenStepsAndBindsTheProfileToTheTenantUser() {
        transactionExecutor.execute(tenantOne, () -> service.saveProgress(
            progress("IN_PROGRESS", List.of("common.welcome", "calendar.availability"), "calendar.availability", null),
            authentication(SUBJECT, RoleEnum.ROLE_USER)
        ));

        transactionExecutor.execute(tenantOne, () -> {
            GuidedTourDTO tour = service.get(authentication(SUBJECT, RoleEnum.ROLE_USER));
            assertThat(tour.progress().seenSteps()).containsExactly("common.welcome", "calendar.availability");
            assertThat(tour.progress().lastStepId()).isEqualTo("calendar.availability");
            assertThat(tour.progress().version()).isZero();
            GuidedTourProfile profile = profileRepository.findByKeycloakSubjectAndDeletedFalse(SUBJECT).orElseThrow();
            assertThat(profile.getUser()).isNotNull();
            assertThat(profile.isLegacyInvitation()).isFalse();
        });
    }

    @Test
    void keepsTheProgressOfEachRoleApart() {
        transactionExecutor.execute(tenantOne, () -> {
            service.saveProgress(progress("COMPLETED", List.of("common.welcome"), "common.help", null), authentication(SUBJECT, RoleEnum.ROLE_USER));
            service.saveProgress(progress("SKIPPED", List.of(), null, null), authentication(SUBJECT, RoleEnum.ROLE_ARCHIVIST));
            service.saveProgress(progress("IN_PROGRESS", List.of("common.welcome"), "common.welcome", null), authentication(SUBJECT, RoleEnum.ROLE_BOARD));
        });

        transactionExecutor.execute(tenantOne, () -> {
            assertThat(service.get(authentication(SUBJECT, RoleEnum.ROLE_USER)).progress().status()).isEqualTo(GuidedTourStatus.COMPLETED);
            assertThat(service.get(authentication(SUBJECT, RoleEnum.ROLE_ARCHIVIST)).progress().status()).isEqualTo(GuidedTourStatus.SKIPPED);
            assertThat(service.get(authentication(SUBJECT, RoleEnum.ROLE_BOARD)).progress().status()).isEqualTo(GuidedTourStatus.IN_PROGRESS);
            assertThat(service.get(authentication(SUBJECT, RoleEnum.ROLE_ADMIN)).progress()).isNull();
            assertThat(profileRepository.count()).isEqualTo(1);
            assertThat(progressRepository.count()).isEqualTo(3);
        });
    }

    @Test
    void keepsSettingsAndProgressIsolatedBetweenTenants() {
        transactionExecutor.execute(tenantOne, () -> {
            service.saveSettings(new GuidedTourSettingsRequest(false, null), authentication(SUBJECT, RoleEnum.ROLE_USER));
            service.saveProgress(progress("SKIPPED", List.of(), null, null), authentication(SUBJECT, RoleEnum.ROLE_USER));
        });

        transactionExecutor.execute(tenantTwo, () -> {
            GuidedTourDTO other = service.get(authentication(SUBJECT, RoleEnum.ROLE_USER));
            assertThat(other.settings().autoStartEnabled()).isTrue();
            assertThat(other.settings().version()).isNull();
            assertThat(other.progress()).isNull();
        });
    }

    @Test
    void materialisesASuperAdminProfileWithoutATenantUser() {
        transactionExecutor.execute(tenantOne, () -> {
            assertThat(usersRepository.findByKeycloakIdAndDeletedFalse(SUPER_ADMIN)).isEmpty();

            service.saveProgress(progress("IN_PROGRESS", List.of("common.welcome"), "common.welcome", null), authentication(SUPER_ADMIN, RoleEnum.ROLE_SUPER_ADMIN));

            GuidedTourProfile profile = profileRepository.findByKeycloakSubjectAndDeletedFalse(SUPER_ADMIN).orElseThrow();
            assertThat(profile.getUser()).isNull();
        });
    }

    @Test
    void rejectsAStaleVersionOfTheSettingsAndOfTheProgress() {
        transactionExecutor.execute(tenantOne, () -> {
            service.saveSettings(new GuidedTourSettingsRequest(false, null), authentication(SUBJECT, RoleEnum.ROLE_USER));
            service.saveProgress(progress("IN_PROGRESS", List.of(), null, null), authentication(SUBJECT, RoleEnum.ROLE_USER));
        });
        transactionExecutor.execute(tenantOne, () ->
            service.saveProgress(progress("IN_PROGRESS", List.of("common.welcome"), null, 0L), authentication(SUBJECT, RoleEnum.ROLE_USER))
        );

        assertConflict(() -> transactionExecutor.execute(tenantOne, () ->
            service.saveProgress(progress("IN_PROGRESS", List.of("common.navigation"), null, 0L), authentication(SUBJECT, RoleEnum.ROLE_USER))
        ));
        assertConflict(() -> transactionExecutor.execute(tenantOne, () ->
            service.saveSettings(new GuidedTourSettingsRequest(true, null), authentication(SUBJECT, RoleEnum.ROLE_USER))
        ));
        transactionExecutor.execute(tenantOne, () ->
            assertThat(service.get(authentication(SUBJECT, RoleEnum.ROLE_USER)).progress().seenSteps()).containsExactly("common.welcome")
        );
    }

    @Test
    void restartsOnlyTheRoleOfTheToken() {
        transactionExecutor.execute(tenantOne, () -> {
            service.saveProgress(progress("COMPLETED", List.of("common.welcome"), null, null), authentication(SUBJECT, RoleEnum.ROLE_USER));
            service.saveProgress(progress("COMPLETED", List.of("common.welcome"), null, null), authentication(SUBJECT, RoleEnum.ROLE_ARCHIVIST));
        });

        transactionExecutor.execute(tenantOne, () -> service.deleteProgress(authentication(SUBJECT, RoleEnum.ROLE_USER)));

        transactionExecutor.execute(tenantOne, () -> {
            assertThat(service.get(authentication(SUBJECT, RoleEnum.ROLE_USER)).progress()).isNull();
            assertThat(service.get(authentication(SUBJECT, RoleEnum.ROLE_ARCHIVIST)).progress().status()).isEqualTo(GuidedTourStatus.COMPLETED);
        });
    }

    @Test
    void keepsTheInvitationOfAnExistingMemberUntilTheRoleIsWritten() {
        transactionExecutor.execute(tenantOne, () -> {
            GuidedTourProfile profile = new GuidedTourProfile();
            profile.initializeAudit("system");
            profile.setKeycloakSubject(SUBJECT);
            profile.setUser(usersRepository.findByKeycloakIdAndDeletedFalse(SUBJECT).orElseThrow());
            profile.setLegacyInvitation(true);
            profileRepository.saveAndFlush(profile);
        });

        transactionExecutor.execute(tenantOne, () -> {
            GuidedTourDTO tour = service.saveProgress(progress("SKIPPED", List.of(), null, null), authentication(SUBJECT, RoleEnum.ROLE_USER));
            assertThat(tour.settings().legacyInvitation()).isTrue();
            assertThat(tour.progress().status()).isEqualTo(GuidedTourStatus.SKIPPED);
            assertThat(service.get(authentication(SUBJECT, RoleEnum.ROLE_ARCHIVIST)).progress()).isNull();
            assertThat(profileRepository.count()).isEqualTo(1);
        });
    }

    @Test
    void erasesProfileAndProgressWithThePersonOnlyInItsTenant() {
        for (String tenant : List.of(tenantOne, tenantTwo)) {
            transactionExecutor.execute(tenant, () -> {
                createUser(ERASED);
                service.saveSettings(new GuidedTourSettingsRequest(false, null), authentication(ERASED, RoleEnum.ROLE_USER));
                service.saveProgress(progress("IN_PROGRESS", List.of("common.welcome"), null, null), authentication(ERASED, RoleEnum.ROLE_USER));
                service.saveProgress(progress("SKIPPED", List.of(), null, null), authentication(ERASED, RoleEnum.ROLE_ARCHIVIST));
                service.saveProgress(progress("SKIPPED", List.of(), null, null), authentication(SUBJECT, RoleEnum.ROLE_USER));
            });
        }

        dataErasureService.eraseUserData(ERASED, tenantOne);

        transactionExecutor.execute(tenantOne, () -> {
            assertThat(profileRepository.findByKeycloakSubjectAndDeletedFalse(ERASED)).isEmpty();
            assertThat(profileRepository.count()).isEqualTo(1);
            assertThat(progressRepository.count()).isEqualTo(1);
        });
        transactionExecutor.execute(tenantTwo, () -> {
            assertThat(profileRepository.findByKeycloakSubjectAndDeletedFalse(ERASED)).isPresent();
            assertThat(progressRepository.count()).isEqualTo(3);
        });
    }

    private static void assertConflict(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
            .isInstanceOf(RequestAlertException.class)
            .extracting(error -> ((RequestAlertException) error).getErrorKey())
            .isEqualTo("guidedTour.versionConflict");
    }

    private static GuidedTourProgressRequest progress(String status, List<String> seenSteps, String lastStepId, Long version) {
        return new GuidedTourProgressRequest(status, seenSteps, lastStepId, 0, 1, version);
    }

    private static JwtAuthenticationToken authentication(String subject, RoleEnum role) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject(subject).claim("role", role.name()).build();
        return new JwtAuthenticationToken(jwt);
    }

    private void createUser(String subject) {
        // user_identity lives in the public schema: it is shared between the tenants and reused.
        UserIdentity identity = entityManager
            .createQuery("select identity from UserIdentity identity where identity.keycloakId = :subject", UserIdentity.class)
            .setParameter("subject", subject)
            .getResultStream()
            .findFirst()
            .orElse(null);
        if (identity == null) {
            identity = new UserIdentity();
            identity.setKeycloakId(subject);
            identity.initializeAudit(subject);
            entityManager.persist(identity);
        }
        Users user = new Users();
        user.setKeycloakId(subject);
        user.setActive(true);
        user.setName("Maria");
        user.setLastName("Bianchi");
        user.setEmail(subject + "@example.test");
        user.setUserIdentity(identity);
        user.setDeleted(false);
        user.setInsertBy(subject);
        user.setInsertDate(new java.util.Date());
        user.setEditBy(subject);
        user.setEditDate(new java.util.Date());
        usersRepository.save(user);
    }
}
