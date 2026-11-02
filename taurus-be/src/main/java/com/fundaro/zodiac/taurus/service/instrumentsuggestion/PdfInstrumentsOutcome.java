package com.fundaro.zodiac.taurus.service.instrumentsuggestion;

import com.fundaro.zodiac.taurus.domain.notification.NotificationPreferencePolicy;
import com.fundaro.zodiac.taurus.domain.notification.NotificationSeverity;
import com.fundaro.zodiac.taurus.domain.notification.NotificationSource;
import com.fundaro.zodiac.taurus.repository.QueueUploadFilesRepository;
import com.fundaro.zodiac.taurus.security.SecurityUtils;
import com.fundaro.zodiac.taurus.service.dto.SheetsMusicDTO;
import com.fundaro.zodiac.taurus.service.dto.TracksDTO;
import com.fundaro.zodiac.taurus.service.impl.NotificationOutboxPublisher;
import com.fundaro.zodiac.taurus.service.notification.NotificationAudience;
import com.fundaro.zodiac.taurus.service.notification.NotificationCommand;
import com.fundaro.zodiac.taurus.service.notification.NotificationEventKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * How the parts of a processed PDF were matched with the instruments: saved on the upload job for its summary, written
 * to the log without the text read, and notified to the user who uploaded the PDF when there are instruments to add.
 */
@Component
public class PdfInstrumentsOutcome {

    static final String AGGREGATE = "TRACK_PDF";
    static final String OPERATION = "STRUMENTI_DA_AGGIUNGERE";
    private static final String SYSTEM_ACTOR = "system";
    private static final Logger log = LoggerFactory.getLogger(PdfInstrumentsOutcome.class);

    private final QueueUploadFilesRepository uploadRepository;
    private final NotificationOutboxPublisher publisher;

    public PdfInstrumentsOutcome(QueueUploadFilesRepository uploadRepository, NotificationOutboxPublisher publisher) {
        this.uploadRepository = uploadRepository;
        this.publisher = publisher;
    }

    /**
     * The three groups of parts. A part with names to add counts among them even if it also received tenant instruments.
     */
    public record Summary(int withInstruments, int withSuggestions, int withoutInstruments) {
        static Summary of(List<SheetsMusicDTO> parts) {
            int withInstruments = 0;
            int withSuggestions = 0;
            int withoutInstruments = 0;
            for (SheetsMusicDTO part : parts) {
                if (part.getInstrumentSuggestions() != null && !part.getInstrumentSuggestions().isEmpty()) withSuggestions++;
                else if (part.getInstruments() != null && !part.getInstruments().isEmpty()) withInstruments++;
                else withoutInstruments++;
            }
            return new Summary(withInstruments, withSuggestions, withoutInstruments);
        }
    }

    @Transactional
    public Summary record(Long jobId, TracksDTO track, List<SheetsMusicDTO> parts, AbstractAuthenticationToken token) {
        Summary summary = Summary.of(parts);
        uploadRepository.findById(jobId).ifPresent(job -> {
            job.setPartsWithInstruments(summary.withInstruments());
            job.setPartsWithSuggestions(summary.withSuggestions());
            job.setPartsWithoutInstruments(summary.withoutInstruments());
            uploadRepository.save(job);
        });
        log.info(
            "Upload job {} matched the parts with the instruments: {} with instruments, {} with instruments to add, {} without instruments",
            jobId,
            summary.withInstruments(),
            summary.withSuggestions(),
            summary.withoutInstruments()
        );
        String uploader = SecurityUtils.getUserIdFromAuthentication(token);
        if (summary.withSuggestions() > 0 && uploader != null) {
            publisher.enqueue(new NotificationCommand(
                NotificationEventKey.deterministic("track-instrument-suggestions", jobId),
                NotificationSource.CONTENT,
                AGGREGATE,
                String.valueOf(jobId),
                OPERATION,
                "Traccia: strumenti da aggiungere",
                message(track.getName(), summary),
                NotificationSeverity.INFO,
                NotificationPreferencePolicy.CONFIGURABLE,
                "/tracks/" + track.getId() + "?tab=parts",
                SYSTEM_ACTOR,
                "Taurus",
                Set.of(NotificationAudience.user(uploader)),
                null
            ));
        }
        return summary;
    }

    static String message(String trackName, Summary summary) {
        String name = trackName == null ? "" : trackName.trim();
        if (name.length() > 80) name = name.substring(0, 79) + "…";
        String track = name.isEmpty() ? "della traccia" : "della traccia “" + name + "”";
        String text = "Il PDF " + track + " è stato elaborato: "
            + count(summary.withInstruments(), "parte", "parti") + " con strumenti assegnati, "
            + summary.withSuggestions() + " con strumenti da aggiungere, "
            + summary.withoutInstruments() + " senza strumenti. Scegli quali strumenti creare o associare nella scheda Parti.";
        return text.length() <= 255 ? text : text.substring(0, 254) + "…";
    }

    private static String count(int value, String singular, String plural) {
        return value + " " + (value == 1 ? singular : plural);
    }
}
