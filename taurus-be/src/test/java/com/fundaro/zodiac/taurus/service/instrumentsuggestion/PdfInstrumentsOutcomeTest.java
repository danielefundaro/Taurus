package com.fundaro.zodiac.taurus.service.instrumentsuggestion;

import com.fundaro.zodiac.taurus.domain.QueueUploadFiles;
import com.fundaro.zodiac.taurus.domain.instrumentsuggestion.InstrumentSuggestionSource;
import com.fundaro.zodiac.taurus.domain.notification.NotificationSeverity;
import com.fundaro.zodiac.taurus.domain.notification.NotificationSource;
import com.fundaro.zodiac.taurus.repository.QueueUploadFilesRepository;
import com.fundaro.zodiac.taurus.service.dto.ChildrenEntitiesDTO;
import com.fundaro.zodiac.taurus.service.dto.SheetsMusicDTO;
import com.fundaro.zodiac.taurus.service.dto.TracksDTO;
import com.fundaro.zodiac.taurus.service.impl.NotificationOutboxPublisher;
import com.fundaro.zodiac.taurus.service.instrumentsuggestion.PdfInstrumentsOutcome.Summary;
import com.fundaro.zodiac.taurus.service.notification.NotificationAudience;
import com.fundaro.zodiac.taurus.service.notification.NotificationCommand;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PdfInstrumentsOutcomeTest {

    private final QueueUploadFilesRepository uploadRepository = mock(QueueUploadFilesRepository.class);
    private final NotificationOutboxPublisher publisher = mock(NotificationOutboxPublisher.class);
    private final PdfInstrumentsOutcome outcome = new PdfInstrumentsOutcome(uploadRepository, publisher);

    @Test
    void countsAPartWithNamesToAddAmongThemEvenWithInstruments() {
        Summary summary = Summary.of(List.of(withInstrument(), withInstrument(), withSuggestion(true), withSuggestion(false), new SheetsMusicDTO()));

        assertThat(summary).isEqualTo(new Summary(2, 2, 1));
    }

    @Test
    void savesTheSummaryOnTheJobAndNotifiesTheUploader() {
        QueueUploadFiles job = new QueueUploadFiles();
        when(uploadRepository.findById(3L)).thenReturn(Optional.of(job));

        outcome.record(3L, track("Marcia"), List.of(withInstrument(), withSuggestion(false), new SheetsMusicDTO()), uploader("user-1"));

        assertThat(job.getPartsWithInstruments()).isEqualTo(1);
        assertThat(job.getPartsWithSuggestions()).isEqualTo(1);
        assertThat(job.getPartsWithoutInstruments()).isEqualTo(1);
        verify(uploadRepository).save(job);
        ArgumentCaptor<NotificationCommand> command = ArgumentCaptor.forClass(NotificationCommand.class);
        verify(publisher).enqueue(command.capture());
        assertThat(command.getValue().source()).isEqualTo(NotificationSource.CONTENT);
        assertThat(command.getValue().severity()).isEqualTo(NotificationSeverity.INFO);
        assertThat(command.getValue().title()).isEqualTo("Traccia: strumenti da aggiungere");
        assertThat(command.getValue().message()).isEqualTo(
            "Il PDF della traccia “Marcia” è stato elaborato: 1 parte con strumenti assegnati, 1 con strumenti da aggiungere, "
                + "1 senza strumenti. Scegli quali strumenti creare o associare nella scheda Parti."
        );
        assertThat(command.getValue().targetPath()).isEqualTo("/tracks/8?tab=parts");
        assertThat(command.getValue().audiences()).isEqualTo(Set.of(NotificationAudience.user("user-1")));
        assertThat(command.getValue().eventKey()).isNotBlank();
    }

    @Test
    void notifiesNothingWithoutNamesToAdd() {
        outcome.record(3L, track("Marcia"), List.of(withInstrument(), new SheetsMusicDTO()), uploader("user-1"));

        verify(publisher, never()).enqueue(any());
    }

    @Test
    void notifiesNothingWithoutAnUploader() {
        Instant now = Instant.now();
        Jwt withoutSubject = Jwt.withTokenValue("token").header("alg", "none").claim("tenant", "BMCDG").issuedAt(now).expiresAt(now.plusSeconds(300)).build();

        outcome.record(3L, track("Marcia"), List.of(withSuggestion(false)), new JwtAuthenticationToken(withoutSubject));

        verify(publisher, never()).enqueue(any());
    }

    @Test
    void shortensALongTrackNameAndWritesTheMessageWithoutAName() {
        String message = PdfInstrumentsOutcome.message("Marcia ".repeat(30), new Summary(0, 3, 2));

        assertThat(message).contains("…”").contains("0 parti con strumenti assegnati").hasSizeLessThanOrEqualTo(255);
        assertThat(PdfInstrumentsOutcome.message(" ", new Summary(1, 1, 0))).startsWith("Il PDF della traccia è stato elaborato: 1 parte");
    }

    private static SheetsMusicDTO withInstrument() {
        SheetsMusicDTO part = new SheetsMusicDTO();
        ChildrenEntitiesDTO instrument = new ChildrenEntitiesDTO();
        instrument.setIndex(1L);
        part.setInstruments(Set.of(instrument));
        return part;
    }

    private static SheetsMusicDTO withSuggestion(boolean alsoWithInstrument) {
        SheetsMusicDTO part = alsoWithInstrument ? withInstrument() : new SheetsMusicDTO();
        part.setInstrumentSuggestions(List.of(new InstrumentSuggestionDraft("Arpa", "Arpa", "arpa", "arpa", InstrumentSuggestionSource.OCR)));
        return part;
    }

    private static TracksDTO track(String name) {
        TracksDTO track = new TracksDTO();
        track.setId(8L);
        track.setName(name);
        return track;
    }

    private static JwtAuthenticationToken uploader(String subject) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject(subject).issuedAt(now).expiresAt(now.plusSeconds(300)).build();
        return new JwtAuthenticationToken(jwt);
    }
}
