package com.fundaro.zodiac.taurus.service.signature;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import tech.jhipster.config.JHipsterProperties;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SignatureMailServiceTest {

    private static final SignatureMailService.CodeMessage MESSAGE = new SignatureMailService.CodeMessage(
        "mario.rossi@example.org",
        "Banda musicale di prova",
        "Presa visione dell'assegnazione di «Clarinetto»",
        "1a2b3c4d",
        "042137",
        ZonedDateTime.of(2026, 10, 18, 8, 40, 0, 0, ZoneId.of("UTC")),
        ZoneId.of("Europe/Rome")
    );

    @Test
    void sendsTheCodeWithTheTenantTheOperationTheReferenceAndTheExpiryButNoLinks() {
        JavaMailSender sender = mock(JavaMailSender.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        SignatureMailService service = service(sender, registry);

        service.sendCode(MESSAGE);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(captor.capture());
        SimpleMailMessage mail = captor.getValue();
        assertThat(mail.getTo()).containsExactly("mario.rossi@example.org");
        assertThat(mail.getFrom()).isEqualTo("taurus@example.org");
        assertThat(mail.getSubject()).isEqualTo("Codice di conferma – Banda musicale di prova");
        assertThat(mail.getText())
            .contains("Codice di conferma: 042137")
            .contains("Associazione: Banda musicale di prova")
            .contains("Operazione: Presa visione dell'assegnazione di «Clarinetto»")
            .contains("Riferimento del documento: 1a2b3c4d")
            .contains("scade alle 10:40 del 18/10/2026")
            .doesNotContain("http");
        assertThat(registry.counter("taurus.signature.mail", "outcome", "sent").count()).isEqualTo(1);
    }

    @Test
    void reportsAnSmtpFailure() {
        JavaMailSender sender = mock(JavaMailSender.class);
        doThrow(new MailSendException("refused")).when(sender).send(any(SimpleMailMessage.class));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        assertThatThrownBy(() -> service(sender, registry).sendCode(MESSAGE)).isInstanceOf(MailSendException.class);
        assertThat(registry.counter("taurus.signature.mail", "outcome", "failed").count()).isEqualTo(1);
    }

    @Test
    void isNotConfiguredWithoutAnSmtpServer() {
        SignatureMailService service = service(null, new SimpleMeterRegistry());

        assertThat(service.configured()).isFalse();
        assertThatThrownBy(() -> service.sendCode(MESSAGE)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void neverPrintsTheCodeOrTheAddress() {
        assertThat(MESSAGE.toString()).doesNotContain("042137").doesNotContain("mario.rossi");
    }

    @Test
    void sendsToTheParentTheLinkToSignWithTheMinorAndTheExpiry() {
        JavaMailSender sender = mock(JavaMailSender.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        service(sender, registry).sendLink(link(false));

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(captor.capture());
        SimpleMailMessage mail = captor.getValue();
        assertThat(mail.getTo()).containsExactly("genitore@example.org");
        assertThat(mail.getSubject()).isEqualTo("Firma richiesta – Banda musicale di prova");
        assertThat(mail.getText())
            .contains("Associazione: Banda musicale di prova")
            .contains("Atto: Emissione del foglio – foglio di affidamento n. 2026/007")
            .contains("Socio minorenne: Luca Rossi")
            .contains("https://app.example.org/firma/abc")
            .contains("vale fino alle 10:40 del 25/10/2026")
            .contains("codice di conferma");
        assertThat(registry.counter("taurus.signature.mail", "outcome", "sent").count()).isEqualTo(1);
    }

    @Test
    void sendsToTheParentTheLinkToTheSignedDocuments() {
        JavaMailSender sender = mock(JavaMailSender.class);

        service(sender, new SimpleMeterRegistry()).sendLink(link(true));

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(captor.capture());
        assertThat(captor.getValue().getSubject()).isEqualTo("Atto firmato – Banda musicale di prova");
        assertThat(captor.getValue().getText())
            .contains("Tutte le parti hanno firmato l'atto.")
            .contains("https://app.example.org/firma/abc")
            .contains("conserva una copia dei documenti");
        assertThat(link(true).toString()).doesNotContain("firma/abc").doesNotContain("genitore");
    }

    private static SignatureMailService.LinkMessage link(boolean signed) {
        return new SignatureMailService.LinkMessage(
            "genitore@example.org",
            "Banda musicale di prova",
            "Emissione del foglio",
            "2026/007",
            "Luca Rossi",
            "https://app.example.org/firma/abc",
            ZonedDateTime.of(2026, 10, 25, 9, 40, 0, 0, ZoneId.of("UTC")),
            ZoneId.of("Europe/Rome"),
            signed
        );
    }

    @SuppressWarnings("unchecked")
    private static SignatureMailService service(JavaMailSender sender, SimpleMeterRegistry registry) {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        JHipsterProperties properties = new JHipsterProperties();
        properties.getMail().setFrom("taurus@example.org");
        return new SignatureMailService(provider, properties, new SignatureMetrics(registry));
    }
}
