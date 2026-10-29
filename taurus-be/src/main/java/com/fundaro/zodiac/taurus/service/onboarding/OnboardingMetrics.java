package com.fundaro.zodiac.taurus.service.onboarding;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

/**
 * Validations, applications, identities and retention of the imports: outcomes, durations and sizes.
 *
 * <p>The labels are closed — the outcome, the phase, the kind of identity operation — because one series per tenant
 * or per job would multiply the time series without helping the diagnosis. For the same reason the rows and the
 * problems are distributions and not labels: the buckets are read from the percentiles. Nothing read from a file
 * enters here: no address, no name, no file name.
 */
@Component
public class OnboardingMetrics {

    private static final String PREFIX = "taurus.onboarding.";

    private final MeterRegistry registry;

    public OnboardingMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /** A validation that ended: {@code ready}, {@code invalid}, or the code that rejected the file. */
    public void validation(String outcome, long durationMillis, int rows, int issues) {
        registry.counter(PREFIX + "validation", "outcome", tag(outcome)).increment();
        Timer.builder(PREFIX + "validation.duration").tag("outcome", tag(outcome)).register(registry)
            .record(durationMillis, TimeUnit.MILLISECONDS);
        if (rows > 0) summary("validation.rows").record(rows);
        if (issues > 0) summary("validation.issues").record(issues);
    }

    /** An application that ended: {@code completed}, {@code failed} or {@code compensation_required}. */
    public void application(String outcome, long durationMillis) {
        registry.counter(PREFIX + "apply", "outcome", tag(outcome)).increment();
        Timer.builder(PREFIX + "apply.duration").tag("outcome", tag(outcome)).register(registry)
            .record(durationMillis, TimeUnit.MILLISECONDS);
    }

    /** One identity of the saga: {@code create} or {@code link}, applied or compensated, and whether it worked. */
    public void identity(String operation, boolean succeeded) {
        registry.counter(PREFIX + "identities", "operation", tag(operation), "outcome", succeeded ? "done" : "failed").increment();
    }

    /** One setup e-mail asked of Keycloak. */
    public void setupEmail(boolean succeeded) {
        registry.counter(PREFIX + "setup.emails", "outcome", succeeded ? "sent" : "failed").increment();
    }

    /** The imports the nightly retention touched, by phase: {@code abandoned}, {@code sources} or {@code records}. */
    public void retention(String phase, int jobs) {
        if (jobs > 0) registry.counter(PREFIX + "retention", "phase", tag(phase)).increment(jobs);
    }

    private DistributionSummary summary(String name) {
        return DistributionSummary.builder(PREFIX + name).publishPercentileHistogram().register(registry);
    }

    private static String tag(String value) {
        if (value == null || value.isBlank()) return "unknown";
        return value.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }
}
