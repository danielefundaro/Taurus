package com.fundaro.zodiac.taurus.service.upload;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

/**
 * Metriche dei job di caricamento dei PDF.
 *
 * <p>I tag sono soltanto enum a cardinalità nota: nessun tenant, nessun identificativo, nessun
 * nome di file. Il job di un'associazione non si deve poter ricostruire da una serie temporale.
 *
 * <p>I contatori raccontano che cosa è successo; le misure di stato — job fermi, anzianità del più
 * vecchio, messaggi in coda — sono scritte dal giro di recupero, che è l'unico punto che guarda
 * tutti i tenant insieme.
 */
@Component
public class UploadJobMetrics {

    private static final String PREFIX = "taurus.upload.";

    /** Che cosa è successo a un job. */
    public enum Operation {
        /** Accettato e accodato. */
        QUEUED,
        /** Preso in carico dal worker. */
        STARTED,
        /** Parti e media creati. */
        COMPLETED,
        /** Chiuso in errore dal worker. */
        FAILED,
        /** Riaccodato dall'utente. */
        RETRIED,
        /** Chiuso in errore dal recupero perché fermo oltre la soglia. */
        CLOSED_STALE,
        /** Messaggio scartato perché il job non era più in coda. */
        IGNORED,
        /** Elaborazione finita su un job che qualcun altro aveva già chiuso. */
        LATE
    }

    /** Perché un job non è arrivato a {@code DONE}. */
    public enum FailureReason {
        /** Pubblicazione del messaggio non riuscita dopo il commit. */
        PUBLISH_FAILED,
        /** Eccezione durante la conversione del PDF o la creazione delle parti. */
        PROCESSING_FAILED,
        /** Worker fermo: il job era in elaborazione oltre la soglia. */
        STALE_WORKER,
        /** Coda ferma: il job era accodato oltre la soglia. */
        STALE_QUEUE,
        /** Il messaggio cita un job che non esiste nel tenant. */
        MISSING_JOB,
        /** Il messaggio non porta un tenant. */
        MISSING_TENANT,
        /** Nuovo tentativo chiesto su un job che non è in errore. */
        NOT_RETRYABLE,
        /** Lo stato del job è cambiato mentre lo si stava riaccodando. */
        STATE_CHANGED
    }

    private final MeterRegistry registry;
    private final AtomicLong queuedJobs = new AtomicLong();
    private final AtomicLong runningJobs = new AtomicLong();
    private final AtomicLong oldestPendingSeconds = new AtomicLong();
    private final AtomicLong queueMessages = new AtomicLong(-1);

    public UploadJobMetrics(MeterRegistry registry) {
        this.registry = registry;
        Gauge.builder(PREFIX + "jobs.pending", queuedJobs, AtomicLong::doubleValue)
            .description("Job di caricamento accodati")
            .tag("status", "TO_PROCESS")
            .register(registry);
        Gauge.builder(PREFIX + "jobs.pending", runningJobs, AtomicLong::doubleValue)
            .description("Job di caricamento in elaborazione")
            .tag("status", "IN_PROGRESS")
            .register(registry);
        Gauge.builder(PREFIX + "jobs.oldest.age", oldestPendingSeconds, AtomicLong::doubleValue)
            .description("Anzianità del job non terminale più vecchio")
            .baseUnit("seconds")
            .register(registry);
        Gauge.builder(PREFIX + "queue.messages", queueMessages, AtomicLong::doubleValue)
            .description("Messaggi nella coda upload.files, -1 quando il broker non risponde")
            .register(registry);
    }

    public void record(Operation operation) {
        registry.counter(PREFIX + "jobs", "operation", operation.name()).increment();
    }

    public void recordFailure(FailureReason reason) {
        registry.counter(PREFIX + "failures", "reason", reason.name()).increment();
    }

    /**
     * Quanto è durata l'elaborazione nel worker, dalla presa in carico all'esito.
     *
     * <p>Misura il lavoro, non l'attesa: il tempo passato in coda si legge su
     * {@code taurus.upload.jobs.oldest.age} e {@code taurus.upload.jobs.pending}, perché il
     * messaggio non porta la data di accettazione del job.
     */
    public void recordDuration(long elapsedNanos, Operation outcome) {
        Timer.builder(PREFIX + "job.duration")
            .description("Durata dell'elaborazione nel worker")
            .tag("outcome", outcome.name())
            .register(registry)
            .record(elapsedNanos, TimeUnit.NANOSECONDS);
    }

    /** Lo stato dei job non terminali di tutti i tenant, misurato dal giro di recupero. */
    public void recordPending(long queued, long running, long oldestAgeSeconds) {
        queuedJobs.set(queued);
        runningJobs.set(running);
        oldestPendingSeconds.set(Math.max(oldestAgeSeconds, 0));
    }

    /** I messaggi ancora in coda, o -1 quando il broker non risponde. */
    public void recordQueueDepth(long messages) {
        queueMessages.set(messages);
    }
}
