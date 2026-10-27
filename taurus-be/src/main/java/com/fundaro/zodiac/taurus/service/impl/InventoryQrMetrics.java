package com.fundaro.zodiac.taurus.service.impl;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

/**
 * Scansioni dei QR, stampa delle etichette e segnalazioni di guasto.
 *
 * <p>Le etichette sono chiuse — esito, gruppo di ruolo, layout, severità, stato — perché un codice, un oggetto, un
 * tenant o un utente come etichetta moltiplicherebbero le serie temporali e trasformerebbero le metriche in una
 * cronologia delle scansioni, che la specifica esclude.
 */
@Component
public class InventoryQrMetrics {

    private static final String PREFIX = "taurus.inventory.";

    private final MeterRegistry registry;

    public InventoryQrMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * Una risoluzione: {@code resolved}, {@code not_found}, {@code rate_limited} o {@code disabled}; il gruppo è
     * {@code admin} o {@code member}.
     */
    public void resolution(String outcome, String roleGroup, long durationMillis) {
        registry.counter(PREFIX + "qr.resolution", "outcome", tag(outcome), "role_group", tag(roleGroup)).increment();
        Timer.builder(PREFIX + "qr.resolution.duration").tag("outcome", tag(outcome)).register(registry)
            .record(durationMillis, TimeUnit.MILLISECONDS);
    }

    /** Una richiesta di etichette: {@code done}, {@code rejected}, {@code timeout}, {@code busy} o {@code failed}. */
    public void labels(String layout, String outcome, long durationMillis, int labels) {
        registry.counter(PREFIX + "label.generation", "layout", tag(layout), "outcome", tag(outcome)).increment();
        Timer.builder(PREFIX + "label.generation.duration").tag("layout", tag(layout)).register(registry)
            .record(durationMillis, TimeUnit.MILLISECONDS);
        if (labels > 0)
            DistributionSummary.builder(PREFIX + "label.count").tag("layout", tag(layout)).publishPercentileHistogram()
                .register(registry).record(labels);
    }

    public void issueCreated(String severity) {
        registry.counter(PREFIX + "issue.created", "severity", tag(severity)).increment();
    }

    public void issueTransition(String from, String to) {
        registry.counter(PREFIX + "issue.transition", "from", tag(from), "to", tag(to)).increment();
    }

    /** Fotografie di guasto rimosse perché ritraevano persone o altri dati personali. */
    public void issuePhotoErased() {
        registry.counter(PREFIX + "issue.photo.erased").increment();
    }

    private static String tag(String value) {
        return value == null || value.isBlank() ? "unknown" : value.toLowerCase(Locale.ROOT);
    }
}
