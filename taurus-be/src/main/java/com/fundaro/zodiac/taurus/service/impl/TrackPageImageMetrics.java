package com.fundaro.zodiac.taurus.service.impl;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

/**
 * Analisi e trasformazioni delle pagine: esiti, durate, dimensioni e operazioni applicate.
 *
 * <p>Le etichette sono chiuse — esito, operazione, tipo di conflitto — perché una metrica per
 * traccia o per media moltiplicherebbe le serie temporali senza aggiungere nulla alla diagnosi. Per
 * lo stesso motivo pixel e byte sono distribuzioni e non etichette: i bucket si leggono dai
 * percentili, non da un valore per richiesta. Non entra qui nessun nome di parte, di file o di
 * tenant.
 */
@Component
public class TrackPageImageMetrics {

    private static final String PREFIX = "taurus.image.";

    private final MeterRegistry registry;

    public TrackPageImageMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /** Un'analisi conclusa: {@code done} oppure la chiave dell'errore restituito al client. */
    public void analysis(String outcome, long durationMillis, long pixels) {
        registry.counter(PREFIX + "analysis", "outcome", tag(outcome)).increment();
        Timer.builder(PREFIX + "analysis.duration").tag("outcome", tag(outcome)).register(registry)
            .record(durationMillis, TimeUnit.MILLISECONDS);
        if (pixels > 0) summary("analysis.pixels").record(pixels);
    }

    /** Un salvataggio concluso: {@code done}, {@code replayed} oppure la chiave dell'errore. */
    public void edit(String outcome, long durationMillis, long inputBytes, long outputBytes, int results) {
        registry.counter(PREFIX + "edit", "outcome", tag(outcome)).increment();
        Timer.builder(PREFIX + "edit.duration").tag("outcome", tag(outcome)).register(registry)
            .record(durationMillis, TimeUnit.MILLISECONDS);
        if (inputBytes > 0) summary("edit.input.bytes").record(inputBytes);
        if (outputBytes > 0) summary("edit.output.bytes").record(outputBytes);
        if (results > 0) summary("edit.results").record(results);
    }

    /** I tipi di trasformazione della ricetta, non le coordinate. */
    public void operation(String operation) {
        registry.counter(PREFIX + "operations", "operation", tag(operation)).increment();
    }

    /** Un conflitto: {@code version}, {@code fingerprint} o {@code stale}. */
    public void conflict(String kind) {
        registry.counter(PREFIX + "conflicts", "kind", tag(kind)).increment();
    }

    /** Asset già scritti e poi eliminati dalla compensazione, perché la transazione è fallita. */
    public void storageCompensation(int assets) {
        if (assets > 0) registry.counter(PREFIX + "storage.compensations").increment(assets);
    }

    private DistributionSummary summary(String name) {
        return DistributionSummary.builder(PREFIX + name).publishPercentileHistogram().register(registry);
    }

    private static String tag(String value) {
        return value == null || value.isBlank() ? "unknown" : value.toLowerCase(Locale.ROOT);
    }
}
