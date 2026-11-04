package com.fundaro.zodiac.taurus.service.guidedtour;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.enumeration.RoleEnum;
import com.fundaro.zodiac.taurus.domain.guidedtour.GuidedTourProfile;
import com.fundaro.zodiac.taurus.domain.guidedtour.GuidedTourProgress;
import com.fundaro.zodiac.taurus.domain.guidedtour.GuidedTourStatus;
import com.fundaro.zodiac.taurus.repository.UsersRepository;
import com.fundaro.zodiac.taurus.repository.guidedtour.GuidedTourProfileRepository;
import com.fundaro.zodiac.taurus.repository.guidedtour.GuidedTourProgressRepository;
import com.fundaro.zodiac.taurus.service.dto.guidedtour.GuidedTourDTO;
import com.fundaro.zodiac.taurus.service.dto.guidedtour.GuidedTourProgressRequest;
import com.fundaro.zodiac.taurus.service.dto.guidedtour.GuidedTourSettingsRequest;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class GuidedTourServiceTest {

    private static final String SUBJECT = "user-1";

    private final GuidedTourProfileRepository profileRepository = mock(GuidedTourProfileRepository.class);
    private final GuidedTourProgressRepository progressRepository = mock(GuidedTourProgressRepository.class);
    private final UsersRepository usersRepository = mock(UsersRepository.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final ApplicationProperties properties = new ApplicationProperties();
    private GuidedTourService service;

    @BeforeEach
    void setUp() {
        service = new GuidedTourService(profileRepository, progressRepository, usersRepository, properties, new GuidedTourMetrics(registry));
        when(profileRepository.findByKeycloakSubjectAndDeletedFalse(SUBJECT)).thenReturn(Optional.empty());
        when(usersRepository.findByKeycloakIdAndDeletedFalse(SUBJECT)).thenReturn(Optional.empty());
        when(profileRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            GuidedTourProfile profile = invocation.getArgument(0);
            if (profile.getId() == null) profile.setId(10L);
            return profile;
        });
        when(progressRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            GuidedTourProgress progress = invocation.getArgument(0);
            if (progress.getId() == null) progress.setId(20L);
            return progress;
        });
    }

    @Test
    void readsCompleteDefaultsWithoutWriting() {
        GuidedTourDTO tour = service.get(authentication("ROLE_USER"));

        assertThat(tour.enabled()).isTrue();
        assertThat(tour.role()).isEqualTo(RoleEnum.ROLE_USER);
        assertThat(tour.settings().autoStartEnabled()).isTrue();
        assertThat(tour.settings().legacyInvitation()).isFalse();
        assertThat(tour.settings().version()).isNull();
        assertThat(tour.progress()).isNull();
        verify(profileRepository, never()).saveAndFlush(any());
        verify(progressRepository, never()).saveAndFlush(any());
    }

    @Test
    void readsTheProgressOfTheRoleOfTheToken() {
        GuidedTourProfile profile = profile(false);
        GuidedTourProgress archivist = progress(profile, RoleEnum.ROLE_ARCHIVIST, GuidedTourStatus.COMPLETED);
        when(profileRepository.findByKeycloakSubjectAndDeletedFalse(SUBJECT)).thenReturn(Optional.of(profile));
        when(progressRepository.findByProfileAndRoleAndDeletedFalse(profile, RoleEnum.ROLE_ARCHIVIST)).thenReturn(Optional.of(archivist));
        when(progressRepository.findByProfileAndRoleAndDeletedFalse(profile, RoleEnum.ROLE_USER)).thenReturn(Optional.empty());

        assertThat(service.get(authentication("ROLE_ARCHIVIST")).progress().status()).isEqualTo(GuidedTourStatus.COMPLETED);
        assertThat(service.get(authentication("ROLE_USER")).progress()).isNull();
    }

    @Test
    void treatsTheBoardAsAnyOtherRole() {
        GuidedTourDTO tour = service.saveProgress(request("IN_PROGRESS", List.of("common.welcome"), "common.welcome"), authentication("ROLE_BOARD"));
        GuidedTourProfile profile = profile(false);
        when(profileRepository.findByKeycloakSubjectAndDeletedFalse(SUBJECT)).thenReturn(Optional.of(profile));
        when(progressRepository.findByProfileAndRoleAndDeletedFalse(profile, RoleEnum.ROLE_BOARD))
            .thenReturn(Optional.of(progress(profile, RoleEnum.ROLE_BOARD, GuidedTourStatus.IN_PROGRESS)));
        service.saveProgress(request("COMPLETED", List.of("common.welcome", "common.help"), "common.help", 0L), authentication("ROLE_BOARD"));

        assertThat(tour.role()).isEqualTo(RoleEnum.ROLE_BOARD);
        assertThat(decisions(RoleEnum.ROLE_BOARD, "started")).isEqualTo(1.0);
        assertThat(decisions(RoleEnum.ROLE_BOARD, "completed")).isEqualTo(1.0);
    }

    @Test
    void rejectsATokenWithoutAKnownRole() {
        assertErrorKey(() -> service.get(authentication(null)), "auth.forbidden");
        assertErrorKey(() -> service.get(authentication("ROLE_UNKNOWN")), "auth.forbidden");
    }

    @Test
    void rejectsATokenWithoutSubject() {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").claim("role", "ROLE_USER").build();

        assertErrorKey(() -> service.get(new JwtAuthenticationToken(jwt)), "auth.identity.missing");
    }

    @Test
    void reportsTheApplicationSwitchAndRefusesTheWritesWhenItIsOff() {
        properties.getGuidedTour().setEnabled(false);

        assertThat(service.get(authentication("ROLE_USER")).enabled()).isFalse();
        assertErrorKey(() -> service.saveSettings(new GuidedTourSettingsRequest(false, null), authentication("ROLE_USER")), "guidedTour.disabled");
        assertErrorKey(() -> service.saveProgress(request("IN_PROGRESS", List.of(), null), authentication("ROLE_USER")), "guidedTour.disabled");
        assertErrorKey(() -> service.deleteProgress(authentication("ROLE_USER")), "guidedTour.disabled");
    }

    @Test
    void materialisesTheProfileOnTheFirstSettingsWrite() {
        GuidedTourDTO tour = service.saveSettings(new GuidedTourSettingsRequest(false, null), authentication("ROLE_USER"));

        assertThat(tour.settings().autoStartEnabled()).isFalse();
        assertThat(tour.settings().version()).isNotNull();
        assertThat(decisions(RoleEnum.ROLE_USER, "auto_start_disabled")).isEqualTo(1.0);
    }

    @Test
    void keepsTheInvitationWhenTheSwitchChanges() {
        GuidedTourProfile profile = profile(true);
        when(profileRepository.findByKeycloakSubjectAndDeletedFalse(SUBJECT)).thenReturn(Optional.of(profile));

        GuidedTourDTO tour = service.saveSettings(new GuidedTourSettingsRequest(false, 0L), authentication("ROLE_USER"));
        tour = service.saveSettings(new GuidedTourSettingsRequest(true, 0L), authentication("ROLE_USER"));

        assertThat(tour.settings().legacyInvitation()).isTrue();
        assertThat(decisions(RoleEnum.ROLE_USER, "auto_start_disabled")).isEqualTo(1.0);
        assertThat(decisions(RoleEnum.ROLE_USER, "auto_start_enabled")).isEqualTo(1.0);
    }

    @Test
    void rejectsAStaleSettingsVersion() {
        assertErrorKey(() -> service.saveSettings(new GuidedTourSettingsRequest(false, 3L), authentication("ROLE_USER")), "guidedTour.versionConflict");

        when(profileRepository.findByKeycloakSubjectAndDeletedFalse(SUBJECT)).thenReturn(Optional.of(profile(false)));
        assertErrorKey(() -> service.saveSettings(new GuidedTourSettingsRequest(false, null), authentication("ROLE_USER")), "guidedTour.versionConflict");
        assertErrorKey(() -> service.saveSettings(new GuidedTourSettingsRequest(false, 7L), authentication("ROLE_USER")), "guidedTour.versionConflict");
    }

    @Test
    void rejectsASettingsRequestWithoutTheSwitch() {
        assertErrorKey(() -> service.saveSettings(new GuidedTourSettingsRequest(null, null), authentication("ROLE_USER")), "validation");
    }

    @Test
    void createsTheProgressOfTheRoleAndTheProfileWhenMissing() {
        GuidedTourDTO tour = service.saveProgress(
            request("IN_PROGRESS", List.of("common.welcome", "common.navigation", "common.welcome"), "common.navigation"),
            authentication("ROLE_USER")
        );

        assertThat(tour.role()).isEqualTo(RoleEnum.ROLE_USER);
        assertThat(tour.settings().version()).isNotNull();
        assertThat(tour.progress().status()).isEqualTo(GuidedTourStatus.IN_PROGRESS);
        assertThat(tour.progress().seenSteps()).containsExactly("common.welcome", "common.navigation");
        assertThat(tour.progress().lastStepId()).isEqualTo("common.navigation");
        assertThat(tour.progress().startedAt()).isNotNull();
        assertThat(tour.progress().completedAt()).isNull();
        verify(profileRepository).saveAndFlush(any());
        assertThat(decisions(RoleEnum.ROLE_USER, "started")).isEqualTo(1.0);
    }

    @Test
    void recordsTheCompletionOnceAndClearsItWhenTheTourIsReopened() {
        GuidedTourProfile profile = profile(false);
        GuidedTourProgress progress = progress(profile, RoleEnum.ROLE_USER, GuidedTourStatus.IN_PROGRESS);
        ZonedDateTime started = progress.getStartedAt();
        when(profileRepository.findByKeycloakSubjectAndDeletedFalse(SUBJECT)).thenReturn(Optional.of(profile));
        when(progressRepository.findByProfileAndRoleAndDeletedFalse(profile, RoleEnum.ROLE_USER)).thenReturn(Optional.of(progress));

        GuidedTourDTO completed = service.saveProgress(request("COMPLETED", List.of("common.welcome"), "common.help", 0L), authentication("ROLE_USER"));
        assertThat(completed.progress().completedAt()).isNotNull();
        assertThat(completed.progress().startedAt()).isEqualTo(started);
        ZonedDateTime completedAt = completed.progress().completedAt();

        assertThat(service.saveProgress(request("COMPLETED", List.of("common.welcome"), "common.help", 0L), authentication("ROLE_USER"))
            .progress().completedAt()).isEqualTo(completedAt);
        assertThat(decisions(RoleEnum.ROLE_USER, "completed")).isEqualTo(1.0);

        GuidedTourDTO reopened = service.saveProgress(request("IN_PROGRESS", List.of("common.welcome"), "common.welcome", 0L), authentication("ROLE_USER"));
        assertThat(reopened.progress().completedAt()).isNull();
        assertThat(reopened.progress().startedAt()).isAfterOrEqualTo(completedAt);
        assertThat(decisions(RoleEnum.ROLE_USER, "started")).isEqualTo(1.0);
    }

    @Test
    void recordsTheSkipWithoutAStart() {
        GuidedTourDTO tour = service.saveProgress(request("SKIPPED", List.of(), null), authentication("ROLE_TREASURER"));

        assertThat(tour.progress().startedAt()).isNull();
        assertThat(decisions(RoleEnum.ROLE_TREASURER, "skipped")).isEqualTo(1.0);
    }

    @Test
    void recordsTheInvitationOutcomeOnTheFirstWriteOfTheRole() {
        GuidedTourProfile profile = profile(true);
        when(profileRepository.findByKeycloakSubjectAndDeletedFalse(SUBJECT)).thenReturn(Optional.of(profile));

        service.saveProgress(request("SKIPPED", List.of(), null), authentication("ROLE_USER"));
        service.saveProgress(request("IN_PROGRESS", List.of("common.welcome"), "common.welcome"), authentication("ROLE_ARCHIVIST"));

        assertThat(registry.counter("taurus.guided_tour.invitations", "outcome", "declined").count()).isEqualTo(1.0);
        assertThat(registry.counter("taurus.guided_tour.invitations", "outcome", "accepted").count()).isEqualTo(1.0);
    }

    @Test
    void rejectsAStaleProgressVersion() {
        assertErrorKey(() -> service.saveProgress(request("IN_PROGRESS", List.of(), null, 2L), authentication("ROLE_USER")), "guidedTour.versionConflict");

        GuidedTourProfile profile = profile(false);
        when(profileRepository.findByKeycloakSubjectAndDeletedFalse(SUBJECT)).thenReturn(Optional.of(profile));
        when(progressRepository.findByProfileAndRoleAndDeletedFalse(profile, RoleEnum.ROLE_USER))
            .thenReturn(Optional.of(progress(profile, RoleEnum.ROLE_USER, GuidedTourStatus.IN_PROGRESS)));
        assertErrorKey(() -> service.saveProgress(request("IN_PROGRESS", List.of(), null), authentication("ROLE_USER")), "guidedTour.versionConflict");
        assertErrorKey(() -> service.saveProgress(request("IN_PROGRESS", List.of(), null, 5L), authentication("ROLE_USER")), "guidedTour.versionConflict");
        verify(progressRepository, never()).saveAndFlush(any());
    }

    @Test
    void reportsAConcurrentWriteAsAVersionConflict() {
        doThrow(new DataIntegrityViolationException("duplicate")).when(progressRepository).saveAndFlush(any());
        assertErrorKey(() -> service.saveProgress(request("IN_PROGRESS", List.of(), null), authentication("ROLE_USER")), "guidedTour.versionConflict");

        doThrow(new ObjectOptimisticLockingFailureException(GuidedTourProfile.class, 10L)).when(profileRepository).saveAndFlush(any());
        assertErrorKey(() -> service.saveSettings(new GuidedTourSettingsRequest(false, null), authentication("ROLE_USER")), "guidedTour.versionConflict");
    }

    @Test
    void validatesStatusStepsAndCounters() {
        JwtAuthenticationToken user = authentication("ROLE_USER");
        assertErrorKey(() -> service.saveProgress(request("FINISHED", List.of(), null), user), "validation");
        assertErrorKey(() -> service.saveProgress(request(null, List.of(), null), user), "validation");
        assertErrorKey(() -> service.saveProgress(request("IN_PROGRESS", null, null), user), "validation");
        assertErrorKey(() -> service.saveProgress(request("IN_PROGRESS", List.of("welcome"), null), user), "validation");
        assertErrorKey(() -> service.saveProgress(request("IN_PROGRESS", List.of("Common.welcome"), null), user), "validation");
        assertErrorKey(() -> service.saveProgress(request("IN_PROGRESS", List.of("common.<b>"), null), user), "validation");
        assertErrorKey(() -> service.saveProgress(request("IN_PROGRESS", List.of("common." + "a".repeat(64)), null), user), "validation");
        assertErrorKey(() -> service.saveProgress(request("IN_PROGRESS", List.of(), "../etc"), user), "validation");
        assertErrorKey(() -> service.saveProgress(new GuidedTourProgressRequest("IN_PROGRESS", List.of(), null, 4, 1, null), user), "validation");
        assertErrorKey(() -> service.saveProgress(new GuidedTourProgressRequest("IN_PROGRESS", List.of(), null, -1, 1, null), user), "validation");
        assertErrorKey(() -> service.saveProgress(new GuidedTourProgressRequest("IN_PROGRESS", List.of(), null, 0, -1, null), user), "validation");
        assertErrorKey(() -> service.saveProgress(new GuidedTourProgressRequest("IN_PROGRESS", List.of(), null, null, 1, null), user), "validation");
        assertErrorKey(() -> service.saveProgress(null, user), "validation");
        verify(progressRepository, never()).saveAndFlush(any());
    }

    @Test
    void acceptsAHundredSeenStepsAndRejectsMore() {
        List<String> hundred = IntStream.range(0, 100).mapToObj(index -> "area.step" + index).toList();
        List<String> tooMany = IntStream.range(0, 101).mapToObj(index -> "area.step" + index).toList();

        assertThat(service.saveProgress(request("IN_PROGRESS", hundred, null), authentication("ROLE_USER")).progress().seenSteps()).hasSize(100);
        assertErrorKey(() -> service.saveProgress(request("IN_PROGRESS", tooMany, null), authentication("ROLE_ADMIN")), "guidedTour.step.limit");
    }

    @Test
    void recognisesTheStepIdentifierFormat() {
        assertThat(GuidedTourService.isStepId("common.welcome")).isTrue();
        assertThat(GuidedTourService.isStepId("superAdmin.legalDocuments")).isTrue();
        assertThat(GuidedTourService.isStepId("phase2.calendar.detail")).isTrue();
        assertThat(GuidedTourService.isStepId("common")).isFalse();
        assertThat(GuidedTourService.isStepId("common.")).isFalse();
        assertThat(GuidedTourService.isStepId("common.1step")).isFalse();
        assertThat(GuidedTourService.isStepId("common.wel-come")).isFalse();
        assertThat(GuidedTourService.isStepId(null)).isFalse();
    }

    @Test
    void restartsOnlyTheRoleOfTheToken() {
        GuidedTourProfile profile = profile(false);
        GuidedTourProgress user = progress(profile, RoleEnum.ROLE_USER, GuidedTourStatus.COMPLETED);
        when(profileRepository.findByKeycloakSubjectAndDeletedFalse(SUBJECT)).thenReturn(Optional.of(profile));
        when(progressRepository.findByProfileAndRoleAndDeletedFalse(profile, RoleEnum.ROLE_USER)).thenReturn(Optional.of(user));

        GuidedTourDTO tour = service.deleteProgress(authentication("ROLE_USER"));

        assertThat(tour.progress()).isNull();
        verify(progressRepository).delete(user);
        assertThat(decisions(RoleEnum.ROLE_USER, "restarted")).isEqualTo(1.0);
    }

    @Test
    void restartingARoleWithoutProgressChangesNothing() {
        service.deleteProgress(authentication("ROLE_USER"));

        verify(progressRepository, never()).delete(any());
        assertThat(decisions(RoleEnum.ROLE_USER, "restarted")).isZero();
    }

    private double decisions(RoleEnum role, String outcome) {
        return registry.counter("taurus.guided_tour.decisions", "role", role.name(), "outcome", outcome).count();
    }

    private static GuidedTourProfile profile(boolean legacyInvitation) {
        GuidedTourProfile profile = new GuidedTourProfile();
        profile.setId(1L);
        profile.initializeAudit("system");
        profile.setKeycloakSubject(SUBJECT);
        profile.setLegacyInvitation(legacyInvitation);
        return profile;
    }

    private static GuidedTourProgress progress(GuidedTourProfile profile, RoleEnum role, GuidedTourStatus status) {
        GuidedTourProgress progress = new GuidedTourProgress();
        progress.setId(2L);
        progress.initializeAudit(SUBJECT);
        progress.setProfile(profile);
        progress.setRole(role);
        progress.setStatus(status);
        progress.setStartedAt(ZonedDateTime.now().minusDays(1));
        if (status == GuidedTourStatus.COMPLETED) progress.setCompletedAt(ZonedDateTime.now().minusHours(1));
        return progress;
    }

    private static GuidedTourProgressRequest request(String status, List<String> seenSteps, String lastStepId) {
        return request(status, seenSteps, lastStepId, null);
    }

    private static GuidedTourProgressRequest request(String status, List<String> seenSteps, String lastStepId, Long version) {
        return new GuidedTourProgressRequest(status, seenSteps, lastStepId, 0, 1, version);
    }

    private static JwtAuthenticationToken authentication(String role) {
        Jwt.Builder jwt = Jwt.withTokenValue("token").header("alg", "none").subject(SUBJECT).claim("tenant", "tenant-a");
        if (role != null) jwt.claim("role", role);
        return new JwtAuthenticationToken(jwt.build());
    }

    private static void assertErrorKey(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String key) {
        assertThatThrownBy(call)
            .isInstanceOf(RequestAlertException.class)
            .extracting(error -> ((RequestAlertException) error).getErrorKey())
            .isEqualTo(key);
    }
}
