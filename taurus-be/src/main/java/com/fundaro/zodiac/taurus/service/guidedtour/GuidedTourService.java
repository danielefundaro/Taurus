package com.fundaro.zodiac.taurus.service.guidedtour;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.enumeration.RoleEnum;
import com.fundaro.zodiac.taurus.domain.guidedtour.GuidedTourProfile;
import com.fundaro.zodiac.taurus.domain.guidedtour.GuidedTourProgress;
import com.fundaro.zodiac.taurus.domain.guidedtour.GuidedTourStatus;
import com.fundaro.zodiac.taurus.repository.UsersRepository;
import com.fundaro.zodiac.taurus.repository.guidedtour.GuidedTourProfileRepository;
import com.fundaro.zodiac.taurus.repository.guidedtour.GuidedTourProgressRepository;
import com.fundaro.zodiac.taurus.security.SecurityUtils;
import com.fundaro.zodiac.taurus.service.dto.guidedtour.GuidedTourDTO;
import com.fundaro.zodiac.taurus.service.dto.guidedtour.GuidedTourProgressDTO;
import com.fundaro.zodiac.taurus.service.dto.guidedtour.GuidedTourProgressRequest;
import com.fundaro.zodiac.taurus.service.dto.guidedtour.GuidedTourSettingsDTO;
import com.fundaro.zodiac.taurus.service.dto.guidedtour.GuidedTourSettingsRequest;
import com.fundaro.zodiac.taurus.service.guidedtour.GuidedTourMetrics.Decision;
import com.fundaro.zodiac.taurus.service.guidedtour.GuidedTourMetrics.InvitationOutcome;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Settings and progress of the guided tour (docs/guided-tour-spec.md). Person and role always come from the token,
 * and the data live in the schema of the tenant of the session. The service does not know the catalog of the steps:
 * it checks their format and number, so a new step needs no backend release.
 */
@Service
@Transactional
public class GuidedTourService {

    static final int MAX_SEEN_STEPS = 100;
    static final int MAX_AUTO_PROPOSALS = 3;
    static final int MAX_STEP_ID_LENGTH = 64;
    private static final Pattern STEP_ID = Pattern.compile("^[a-z][a-zA-Z0-9]*(\\.[a-z][a-zA-Z0-9]*)+$");

    private final GuidedTourProfileRepository profileRepository;
    private final GuidedTourProgressRepository progressRepository;
    private final UsersRepository usersRepository;
    private final ApplicationProperties.GuidedTourProperties properties;
    private final GuidedTourMetrics metrics;

    public GuidedTourService(
        GuidedTourProfileRepository profileRepository,
        GuidedTourProgressRepository progressRepository,
        UsersRepository usersRepository,
        ApplicationProperties applicationProperties,
        GuidedTourMetrics metrics
    ) {
        this.profileRepository = profileRepository;
        this.progressRepository = progressRepository;
        this.usersRepository = usersRepository;
        this.properties = applicationProperties.getGuidedTour();
        this.metrics = metrics;
    }

    /**
     * The complete aggregate, with the defaults when nothing is stored: the read never writes.
     */
    @Transactional(readOnly = true)
    public GuidedTourDTO get(AbstractAuthenticationToken authentication) {
        String subject = subject(authentication);
        RoleEnum role = role(authentication);
        GuidedTourProfile profile = profileRepository.findByKeycloakSubjectAndDeletedFalse(subject).orElse(null);
        return toDto(role, profile, progress(profile, role));
    }

    public GuidedTourDTO saveSettings(GuidedTourSettingsRequest request, AbstractAuthenticationToken authentication) {
        requireEnabled();
        String subject = subject(authentication);
        RoleEnum role = role(authentication);
        if (request == null || request.autoStartEnabled() == null)
            throw error(HttpStatus.BAD_REQUEST, "The auto-start switch is required", "validation");

        GuidedTourProfile existing = profileRepository.findByKeycloakSubjectAndDeletedFalse(subject).orElse(null);
        if (existing == null) {
            if (request.version() != null) throw versionConflict();
        } else {
            if (request.version() == null || request.version() != existing.getEntityVersion()) throw versionConflict();
            existing.touchAudit(subject);
        }
        GuidedTourProfile profile = existing != null ? existing : newProfile(subject);
        boolean changed = profile.isAutoStartEnabled() != request.autoStartEnabled();
        profile.setAutoStartEnabled(request.autoStartEnabled());
        GuidedTourProfile saved = flush(() -> profileRepository.saveAndFlush(profile));
        if (changed) {
            metrics.recordDecision(role, request.autoStartEnabled() ? Decision.AUTO_START_ENABLED : Decision.AUTO_START_DISABLED);
        }
        return toDto(role, saved, progress(saved, role));
    }

    /**
     * Creates or updates the progress of the role of the token, writing the profile first when it does not exist.
     * Any write ends the invitation for that role, because the progress row then exists.
     */
    public GuidedTourDTO saveProgress(GuidedTourProgressRequest request, AbstractAuthenticationToken authentication) {
        requireEnabled();
        String subject = subject(authentication);
        RoleEnum role = role(authentication);
        GuidedTourStatus status = validate(request);

        GuidedTourProfile existingProfile = profileRepository.findByKeycloakSubjectAndDeletedFalse(subject).orElse(null);
        GuidedTourProgress progress = progress(existingProfile, role);
        if (progress == null) {
            if (request.version() != null) throw versionConflict();
        } else if (request.version() == null || request.version() != progress.getEntityVersion()) {
            throw versionConflict();
        }
        GuidedTourProfile profile = existingProfile != null
            ? existingProfile
            : flush(() -> profileRepository.saveAndFlush(newProfile(subject)));

        GuidedTourStatus previous = progress == null ? null : progress.getStatus();
        if (progress == null) {
            progress = new GuidedTourProgress();
            progress.initializeAudit(subject);
            progress.setProfile(profile);
            progress.setRole(role);
        } else {
            progress.touchAudit(subject);
        }
        apply(progress, request, status, previous, ZonedDateTime.now());
        GuidedTourProgress target = progress;
        GuidedTourProgress saved = flush(() -> progressRepository.saveAndFlush(target));

        recordTransition(role, previous, status);
        if (previous == null && profile.isLegacyInvitation()) {
            metrics.recordInvitation(status == GuidedTourStatus.SKIPPED ? InvitationOutcome.DECLINED : InvitationOutcome.ACCEPTED);
        }
        return toDto(role, profile, saved);
    }

    /**
     * «Ricomincia la guida»: removes the progress of the role of the token only.
     */
    public GuidedTourDTO deleteProgress(AbstractAuthenticationToken authentication) {
        requireEnabled();
        String subject = subject(authentication);
        RoleEnum role = role(authentication);
        GuidedTourProfile profile = profileRepository.findByKeycloakSubjectAndDeletedFalse(subject).orElse(null);
        GuidedTourProgress progress = progress(profile, role);
        if (progress != null) {
            flush(() -> {
                progressRepository.delete(progress);
                progressRepository.flush();
                return null;
            });
            metrics.recordDecision(role, Decision.RESTARTED);
        }
        return toDto(role, profile, null);
    }

    private static void apply(
        GuidedTourProgress progress,
        GuidedTourProgressRequest request,
        GuidedTourStatus status,
        GuidedTourStatus previous,
        ZonedDateTime now
    ) {
        progress.setStatus(status);
        progress.setSeenSteps(new ArrayList<>(new LinkedHashSet<>(request.seenSteps())));
        progress.setLastStepId(request.lastStepId());
        progress.setAutoProposals(request.autoProposals());
        progress.setCatalogRevision(request.catalogRevision());
        // A tour reopened after its end starts again; a completion always has a start.
        if (status == GuidedTourStatus.IN_PROGRESS && previous != GuidedTourStatus.IN_PROGRESS) progress.setStartedAt(now);
        if (status == GuidedTourStatus.COMPLETED && progress.getStartedAt() == null) progress.setStartedAt(now);
        if (status != GuidedTourStatus.COMPLETED) progress.setCompletedAt(null);
        else if (previous != GuidedTourStatus.COMPLETED) progress.setCompletedAt(now);
    }

    private void recordTransition(RoleEnum role, GuidedTourStatus previous, GuidedTourStatus status) {
        if (status == previous) return;
        metrics.recordDecision(role, switch (status) {
            case IN_PROGRESS -> Decision.STARTED;
            case COMPLETED -> Decision.COMPLETED;
            case SKIPPED -> Decision.SKIPPED;
        });
    }

    private static GuidedTourStatus validate(GuidedTourProgressRequest request) {
        if (request == null || request.status() == null || request.seenSteps() == null
            || request.autoProposals() == null || request.catalogRevision() == null) {
            throw invalid("Status, seen steps, auto proposals and catalog revision are required");
        }
        GuidedTourStatus status;
        try {
            status = GuidedTourStatus.valueOf(request.status());
        } catch (IllegalArgumentException exception) {
            throw invalid("Unknown guided tour status");
        }
        if (request.autoProposals() < 0 || request.autoProposals() > MAX_AUTO_PROPOSALS)
            throw invalid("Auto proposals must be between 0 and " + MAX_AUTO_PROPOSALS);
        if (request.catalogRevision() < 0) throw invalid("The catalog revision cannot be negative");
        if (request.seenSteps().size() > MAX_SEEN_STEPS) {
            throw error(HttpStatus.BAD_REQUEST, "Too many seen steps", "guidedTour.step.limit");
        }
        request.seenSteps().forEach(step -> {
            if (!isStepId(step)) throw invalid("Malformed step identifier");
        });
        if (request.lastStepId() != null && !isStepId(request.lastStepId())) throw invalid("Malformed step identifier");
        return status;
    }

    static boolean isStepId(String value) {
        return value != null && value.length() <= MAX_STEP_ID_LENGTH && STEP_ID.matcher(value).matches();
    }

    private GuidedTourProgress progress(GuidedTourProfile profile, RoleEnum role) {
        if (profile == null) return null;
        return progressRepository.findByProfileAndRoleAndDeletedFalse(profile, role).orElse(null);
    }

    private GuidedTourProfile newProfile(String subject) {
        GuidedTourProfile profile = new GuidedTourProfile();
        profile.initializeAudit(subject);
        profile.setKeycloakSubject(subject);
        // A super administrator may have no user in the tenant: the profile is then bound to the subject only.
        profile.setUser(usersRepository.findByKeycloakIdAndDeletedFalse(subject).orElse(null));
        return profile;
    }

    /**
     * A concurrent write, for example from a second browser tab, is reported as a version conflict: the client reads
     * again and merges the seen steps.
     */
    private static <T> T flush(Supplier<T> write) {
        try {
            return write.get();
        } catch (DataIntegrityViolationException | OptimisticLockingFailureException exception) {
            throw versionConflict();
        }
    }

    private GuidedTourDTO toDto(RoleEnum role, GuidedTourProfile profile, GuidedTourProgress progress) {
        GuidedTourSettingsDTO settings = profile == null
            ? new GuidedTourSettingsDTO(true, false, null)
            : new GuidedTourSettingsDTO(profile.isAutoStartEnabled(), profile.isLegacyInvitation(), profile.getEntityVersion());
        return new GuidedTourDTO(properties.isEnabled(), settings, role, progress == null ? null : toDto(progress));
    }

    private static GuidedTourProgressDTO toDto(GuidedTourProgress progress) {
        return new GuidedTourProgressDTO(
            progress.getStatus(),
            List.copyOf(progress.getSeenSteps()),
            progress.getLastStepId(),
            progress.getAutoProposals(),
            progress.getCatalogRevision(),
            progress.getStartedAt(),
            progress.getCompletedAt(),
            progress.getEntityVersion()
        );
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw error(HttpStatus.NOT_FOUND, "The guided tour is not enabled", "guidedTour.disabled");
        }
    }

    private static String subject(AbstractAuthenticationToken authentication) {
        String subject = SecurityUtils.getUserIdFromAuthentication(authentication);
        if (subject == null || subject.isBlank())
            throw error(HttpStatus.UNAUTHORIZED, "Authentication subject is required", "auth.identity.missing");
        return subject;
    }

    /**
     * The role of the session, from the {@code role} claim: a token without a known role cannot have a tour.
     */
    private static RoleEnum role(AbstractAuthenticationToken authentication) {
        String role = SecurityUtils.getRoleFromAuthentication(authentication);
        try {
            if (role != null) return RoleEnum.valueOf(role);
        } catch (IllegalArgumentException exception) {
            // Reported below as a missing role.
        }
        throw error(HttpStatus.FORBIDDEN, "The token has no known role", "auth.forbidden");
    }

    private static RequestAlertException versionConflict() {
        return error(HttpStatus.CONFLICT, "The guided tour was changed by another request", "guidedTour.versionConflict");
    }

    private static RequestAlertException invalid(String message) {
        return error(HttpStatus.BAD_REQUEST, message, "validation");
    }

    private static RequestAlertException error(HttpStatus status, String message, String code) {
        return new RequestAlertException(status, message, "guidedTour", code);
    }
}
