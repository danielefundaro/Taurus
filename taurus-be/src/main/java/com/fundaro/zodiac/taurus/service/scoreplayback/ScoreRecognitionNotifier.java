package com.fundaro.zodiac.taurus.service.scoreplayback;

import com.fundaro.zodiac.taurus.domain.Instruments;
import com.fundaro.zodiac.taurus.domain.SheetsMusic;
import com.fundaro.zodiac.taurus.domain.Tracks;
import com.fundaro.zodiac.taurus.domain.notification.NotificationPreferencePolicy;
import com.fundaro.zodiac.taurus.domain.notification.NotificationSeverity;
import com.fundaro.zodiac.taurus.domain.notification.NotificationSource;
import com.fundaro.zodiac.taurus.domain.scoreplayback.ScoreRecognitionJob;
import com.fundaro.zodiac.taurus.domain.scoreplayback.ScoreRecognitionJobStatus;
import com.fundaro.zodiac.taurus.repository.TracksRepository;
import com.fundaro.zodiac.taurus.repository.scoreplayback.ScoreRecognitionJobRepository;
import com.fundaro.zodiac.taurus.service.impl.NotificationOutboxPublisher;
import com.fundaro.zodiac.taurus.service.notification.NotificationAudience;
import com.fundaro.zodiac.taurus.service.notification.NotificationCommand;
import com.fundaro.zodiac.taurus.service.notification.NotificationEventKey;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tells the user who started a recognition how it ended: one notification per job, or a single one when every job of a
 * "recognize all the parts" request has finished. Must run in a tenant transaction.
 */
@Component
public class ScoreRecognitionNotifier {

    static final String AGGREGATE = "SCORE_RECOGNITION";
    static final String COMPLETED = "RICONOSCIMENTO_COMPLETATO";
    static final String FAILED = "RICONOSCIMENTO_NON_RIUSCITO";
    static final String BATCH_COMPLETED = "RICONOSCIMENTO_PARTI_COMPLETATO";
    private static final String SYSTEM_ACTOR = "system";

    private static final Map<String, String> REASONS = Map.of(
        "engineTimeout", "i motori hanno superato il tempo massimo",
        "engineFailed", "i motori non sono riusciti a leggere le pagine",
        "tooManyPages", "la parte ha troppe pagine",
        "tooLarge", "le pagine sono troppo grandi",
        "unreadableImage", "una pagina non è un'immagine leggibile",
        "noPages", "la parte non ha pagine",
        "serviceUnavailable", "il servizio di riconoscimento non è raggiungibile",
        "interrupted", "il riconoscimento si è interrotto più volte"
    );

    private final NotificationOutboxPublisher publisher;
    private final ScoreRecognitionJobRepository jobRepository;
    private final TracksRepository tracksRepository;

    public ScoreRecognitionNotifier(NotificationOutboxPublisher publisher, ScoreRecognitionJobRepository jobRepository, TracksRepository tracksRepository) {
        this.publisher = publisher;
        this.jobRepository = jobRepository;
        this.tracksRepository = tracksRepository;
    }

    public void finished(ScoreRecognitionJob job) {
        Optional<Tracks> track = tracksRepository.findByIdAndDeletedFalse(job.getTrackId());
        String trackName = track.map(Tracks::getName).filter(name -> name != null && !name.isBlank()).orElse("Traccia senza nome");
        if (job.getBatchId() == null) {
            notifyJob(job, trackName, track.map(value -> partLabel(value, job.getSheetMusicId())).orElse("Parte"));
        } else {
            notifyBatchWhenComplete(job, trackName);
        }
    }

    private void notifyJob(ScoreRecognitionJob job, String trackName, String part) {
        boolean done = job.getStatus() == ScoreRecognitionJobStatus.DONE;
        String message = done
            ? part + " · " + trackName + ": la versione riconosciuta è pronta per l'ascolto e va verificata."
            : part + " · " + trackName + ": " + REASONS.getOrDefault(job.getErrorCode(), "errore imprevisto") + ".";
        publish(
            NotificationEventKey.deterministic("score-recognition", job.getId(), job.getAttempts(), job.getStatus()),
            job,
            done ? COMPLETED : FAILED,
            done ? "Riconoscimento completato" : "Riconoscimento non riuscito",
            message,
            done ? NotificationSeverity.SUCCESS : NotificationSeverity.WARNING
        );
    }

    private void notifyBatchWhenComplete(ScoreRecognitionJob job, String trackName) {
        List<ScoreRecognitionJob> batch = jobRepository.findAllByBatchIdAndDeletedFalse(job.getBatchId());
        if (batch.stream().anyMatch(item -> item.getStatus().isActive())) return;
        long done = batch.stream().filter(item -> item.getStatus() == ScoreRecognitionJobStatus.DONE).count();
        long failed = batch.size() - done;
        String message = trackName + ": " + done + (done == 1 ? " parte riconosciuta" : " parti riconosciute")
            + (failed == 0 ? "." : ", " + failed + (failed == 1 ? " non riuscita." : " non riuscite."))
            + (done > 0 ? " Le versioni riconosciute vanno verificate." : "");
        publish(
            NotificationEventKey.deterministic("score-recognition-batch", job.getBatchId()),
            job,
            BATCH_COMPLETED,
            "Riconoscimento delle parti completato",
            message,
            failed == 0 ? NotificationSeverity.SUCCESS : NotificationSeverity.WARNING
        );
    }

    private void publish(String eventKey, ScoreRecognitionJob job, String operation, String title, String message, NotificationSeverity severity) {
        publisher.enqueue(
            new NotificationCommand(
                eventKey,
                NotificationSource.CONTENT,
                AGGREGATE,
                String.valueOf(job.getBatchId() == null ? job.getId() : job.getBatchId()),
                operation,
                title,
                truncate(message),
                severity,
                NotificationPreferencePolicy.CONFIGURABLE,
                "/tracks/" + job.getTrackId() + "?tab=playback",
                SYSTEM_ACTOR,
                "Taurus",
                Set.of(NotificationAudience.user(job.getRequestedBy())),
                null
            )
        );
    }

    /** Description of the part, otherwise its instruments, otherwise its position. */
    static String partLabel(Tracks track, Long scoreId) {
        List<SheetsMusic> scores = track.getScores();
        for (int index = 0; index < scores.size(); index++) {
            SheetsMusic part = scores.get(index);
            if (!Objects.equals(part.getId(), scoreId)) continue;
            if (part.getDescription() != null && !part.getDescription().isBlank()) return part.getDescription().trim();
            String instruments = part.getInstruments().stream().map(Instruments::getName).filter(Objects::nonNull).collect(Collectors.joining(", "));
            return instruments.isBlank() ? "Parte " + (index + 1) : instruments;
        }
        return "Parte";
    }

    private static String truncate(String value) {
        return value.length() <= 255 ? value : value.substring(0, 254) + "…";
    }
}
