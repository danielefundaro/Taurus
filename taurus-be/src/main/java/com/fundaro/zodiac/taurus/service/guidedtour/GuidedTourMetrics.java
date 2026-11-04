package com.fundaro.zodiac.taurus.service.guidedtour;

import com.fundaro.zodiac.taurus.domain.enumeration.RoleEnum;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Aggregated counters of the guided tour: role and outcome only, never tenant, person or step.
 */
@Component
public class GuidedTourMetrics {

    private static final String PREFIX = "taurus.guided_tour.";

    public enum Decision {
        STARTED,
        COMPLETED,
        SKIPPED,
        RESTARTED,
        AUTO_START_DISABLED,
        AUTO_START_ENABLED
    }

    public enum InvitationOutcome {
        ACCEPTED,
        DECLINED
    }

    private final MeterRegistry registry;

    public GuidedTourMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordDecision(RoleEnum role, Decision decision) {
        registry.counter(PREFIX + "decisions", "role", role.name(), "outcome", tag(decision)).increment();
    }

    public void recordInvitation(InvitationOutcome outcome) {
        registry.counter(PREFIX + "invitations", "outcome", tag(outcome)).increment();
    }

    private static String tag(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }
}
