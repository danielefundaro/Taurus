package com.fundaro.zodiac.taurus.service.onboarding;

import com.fundaro.zodiac.taurus.domain.enumeration.RoleEnum;
import com.fundaro.zodiac.taurus.domain.notification.NotificationPreferencePolicy;
import com.fundaro.zodiac.taurus.domain.notification.NotificationSeverity;
import com.fundaro.zodiac.taurus.domain.notification.NotificationSource;
import com.fundaro.zodiac.taurus.domain.onboarding.OnboardingImportSection;
import com.fundaro.zodiac.taurus.repository.onboarding.OnboardingImportSectionRepository;
import com.fundaro.zodiac.taurus.service.impl.NotificationOutboxPublisher;
import com.fundaro.zodiac.taurus.service.notification.NotificationAudience;
import com.fundaro.zodiac.taurus.service.notification.NotificationCommand;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * One notification for a whole import, not one per record created: a package of five hundred rows would otherwise
 * bury every other notice of the instance. The message carries the counts of the sections, never a value read from
 * the file, and the administrators reach the outcome from it.
 */
@Service
public class OnboardingNotifier {
    private static final Logger LOG = LoggerFactory.getLogger(OnboardingNotifier.class);
    private static final String AGGREGATE = "ONBOARDING_IMPORT";

    private final OnboardingImportSectionRepository sections;
    private final NotificationOutboxPublisher outbox;

    public OnboardingNotifier(OnboardingImportSectionRepository sections, NotificationOutboxPublisher outbox) {
        this.sections = sections;
        this.outbox = outbox;
    }

    /** The summary of an import that ended. A failure here never fails the import, which is already applied. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void summary(Long jobId, String actor, int emailFailures) {
        try {
            List<OnboardingImportSection> summaries = sections.findAllByJob_IdOrderBySectionAsc(jobId);
            int applied = summaries.stream().mapToInt(OnboardingImportSection::getApplied).sum();
            String detail = summaries.stream()
                .filter(section -> section.getApplied() > 0 || section.getReuseCount() > 0 || section.getSkipCount() > 0)
                .map(section -> label(section) + ": " + section.getApplied() + " creati, " + section.getReuseCount()
                    + " riutilizzati, " + section.getSkipCount() + " saltati")
                .collect(Collectors.joining(" · "));
            String message = (detail.isBlank() ? "Nessun dato applicato" : detail)
                + (emailFailures > 0 ? " · " + emailFailures + " e-mail di primo accesso da ritentare" : "");
            Set<NotificationAudience> audiences = new LinkedHashSet<>(Set.of(
                NotificationAudience.role(RoleEnum.ROLE_ADMIN),
                NotificationAudience.role(RoleEnum.ROLE_SUPER_ADMIN)
            ));
            if (actor != null && !actor.isBlank()) audiences.add(NotificationAudience.excludedUser(actor));
            outbox.enqueue(new NotificationCommand(
                "onboarding-import:" + jobId + ":completed",
                NotificationSource.TENANT,
                AGGREGATE,
                String.valueOf(jobId),
                "IMPORT_COMPLETED",
                "Importazione iniziale completata",
                "Righe applicate: " + applied + ". " + message,
                emailFailures > 0 ? NotificationSeverity.WARNING : NotificationSeverity.INFO,
                NotificationPreferencePolicy.CONFIGURABLE,
                "/onboarding/imports/" + jobId,
                actor == null || actor.isBlank() ? OnboardingCleanupScheduler.SYSTEM_ACTOR : actor,
                "Taurus",
                audiences,
                null
            ));
        } catch (RuntimeException exception) {
            LOG.warn("Unable to record the summary notification of the onboarding import {}", jobId, exception);
        }
    }

    private static String label(OnboardingImportSection section) {
        return switch (section.getSection()) {
            case INSTRUMENTS -> "Strumenti";
            case USERS -> "Utenti";
            case INVENTORY -> "Inventario";
            case CATEGORIES -> "Categorie";
            case ACCOUNTS -> "Conti";
            case OPENING_BALANCES -> "Saldi iniziali";
        };
    }
}
