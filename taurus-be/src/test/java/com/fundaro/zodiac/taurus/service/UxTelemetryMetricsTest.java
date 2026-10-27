package com.fundaro.zodiac.taurus.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fundaro.zodiac.taurus.service.dto.UxTelemetryDTO;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Che cosa entra nelle metriche da una segnalazione che arriva da un browser: l'elenco delle
 * funzioni e la forma dei nomi degli eventi sono gli unici due filtri che tengono sotto controllo
 * le etichette, quindi sono le prove che contano.
 */
class UxTelemetryMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final UxTelemetryMetrics metrics = new UxTelemetryMetrics(registry);

    @Test
    void recordsTheEventsOfAKnownFeature() {
        assertThat(metrics.record(report(Map.of("open", 1, "move", 3)))).isTrue();

        assertThat(events("open")).isEqualTo(1);
        assertThat(events("move")).isEqualTo(3);
        assertThat(registry.find("taurus.ux.session.duration").tag("feature", "track-parts-workspace").timer().count()).isEqualTo(1);
    }

    @Test
    void ignoresAFeatureThatIsNotInTheCatalog() {
        assertThat(metrics.record(new UxTelemetryDTO("qualcos-altro", 1_000, Map.of("open", 1)))).isFalse();

        assertThat(registry.find("taurus.ux.events").counters()).isEmpty();
    }

    /** Un nome di evento che non sembra un identificativo arriverebbe come etichetta: va scartato. */
    @Test
    void dropsAnEventNameThatIsNotAnIdentifier() {
        Map<String, Integer> events = new HashMap<>();
        events.put("Clarinetto 3.pdf", 1);
        events.put("open", 1);

        metrics.record(report(events));

        assertThat(registry.find("taurus.ux.events").counters()).hasSize(1);
        assertThat(events("open")).isEqualTo(1);
    }

    @Test
    void capsCountsAndDuration() {
        metrics.record(new UxTelemetryDTO("track-parts-workspace", Duration.ofDays(30).toMillis(), Map.of("move", 50_000)));

        assertThat(events("move")).isEqualTo(1_000);
        assertThat(registry.find("taurus.ux.session.duration").timer().totalTime(java.util.concurrent.TimeUnit.HOURS)).isEqualTo(8);
    }

    @Test
    void keepsAtMostTwentyEvents() {
        Map<String, Integer> events = new HashMap<>();
        for (int index = 0; index < 40; index++) events.put("evento-" + (char) ('a' + index / 26) + (char) ('a' + index % 26), 1);

        metrics.record(report(events));

        assertThat(registry.find("taurus.ux.events").counters()).hasSize(20);
    }

    @Test
    void acceptsAReportWithoutEvents() {
        assertThat(metrics.record(new UxTelemetryDTO("track-parts-workspace", 5_000, null))).isTrue();
        assertThat(registry.find("taurus.ux.events").counters()).isEmpty();
    }

    private static UxTelemetryDTO report(Map<String, Integer> events) {
        return new UxTelemetryDTO("track-parts-workspace", 12_000, events);
    }

    private double events(String event) {
        return registry.counter("taurus.ux.events", "feature", "track-parts-workspace", "event", event).count();
    }
}
