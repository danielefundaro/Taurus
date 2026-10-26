package com.fundaro.zodiac.taurus.service.finance;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

/**
 * Metriche del modulo finanza.
 *
 * <p>I tag sono soltanto enum a cardinalità nota: nessun tenant, nessun identificativo, nessun
 * IBAN, nessuna descrizione. Una metrica serve a vedere l'andamento del modulo, non a ricostruire
 * la contabilità di un'associazione.
 */
@Component
public class FinanceMetrics {

    private static final String PREFIX = "taurus.finance.";

    /** Su che cosa si è agito. */
    public enum Entity {
        ACCOUNT,
        CATEGORY,
        MOVEMENT,
        TRANSFER,
        ATTACHMENT,
        YEAR
    }

    /** Che cosa si è fatto. */
    public enum Operation {
        CREATED,
        UPDATED,
        DELETED,
        ARCHIVED,
        RESTORED,
        RECONCILED,
        REORDERED,
        REPLAYED,
        ROLLED_OVER,
        RECALCULATED,
        DOWNLOADED
    }

    /** Perché un'operazione non è andata a buon fine. */
    public enum FailureReason {
        VERSION_CONFLICT,
        CONTENT_MISMATCH,
        UNSUPPORTED_TYPE,
        ATTACHMENT_TOO_LARGE,
        TOO_MANY_ATTACHMENTS,
        UPLOAD_FAILED,
        CLOSED_YEAR,
        SAME_ACCOUNT_TRANSFER,
        CURRENCY_MISMATCH,
        ACCESS_DENIED,
        RECALCULATION_FAILED
    }

    private final MeterRegistry registry;

    public FinanceMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void record(Entity entity, Operation operation) {
        registry.counter(PREFIX + "operations", "entity", entity.name(), "operation", operation.name()).increment();
    }

    public void recordFailure(FailureReason reason) {
        registry.counter(PREFIX + "failures", "reason", reason.name()).increment();
    }

    /** Quanto spazio sta occupando negli allegati del tenant il documento appena caricato. */
    public void recordAttachmentSize(long bytes) {
        if (bytes > 0) DistributionSummary.builder(PREFIX + "attachment.bytes").baseUnit("bytes").register(registry).record(bytes);
    }

    /** Quante segnalazioni ha prodotto il controllo di un esercizio, per codice. */
    public void recordAnomaly(String code, int count) {
        if (count > 0) registry.counter(PREFIX + "year.anomalies", "code", code).increment(count);
    }

    /** Durata della generazione di un rendiconto: è l'operazione più lenta del modulo. */
    public void recordReport(String report, String format, long elapsedNanos) {
        Timer.builder(PREFIX + "report.duration")
            .tag("report", report)
            .tag("format", format)
            .register(registry)
            .record(elapsedNanos, TimeUnit.NANOSECONDS);
    }
}
