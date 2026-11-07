package com.fundaro.zodiac.taurus.service.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.fundaro.zodiac.taurus.service.mail.MailParts;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.mail.internet.MimeMessage;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

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

    private static final String LINK_URL = "https://app.example.org/firma/abc";

    @Test
    void sendsTheCodeWithTheTenantTheOperationTheReferenceAndTheExpiry() throws Exception {
        JavaMailSender sender = MailParts.sender();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        service(sender, registry).sendCode(MESSAGE);

        MimeMessage mail = sent(sender);
        assertThat(mail.getAllRecipients()).extracting(Object::toString).containsExactly("mario.rossi@example.org");
        assertThat(mail.getSubject()).isEqualTo("Codice di conferma – Banda musicale di prova");
        assertThat(MailParts.text(mail))
            .startsWith("Codice di conferma\n")
            .contains("Usa questo codice per confermare la firma:\n042137\n")
            .contains("Associazione: Banda musicale di prova")
            .contains("Operazione: Presa visione dell'assegnazione di «Clarinetto»")
            .contains("Riferimento del documento: 1a2b3c4d")
            .contains("scade alle 10:40 del 18/10/2026")
            .endsWith("perché è stata chiesta una firma con questo indirizzo.\n");
        assertThat(MailParts.html(mail))
            .contains(">042137</div>")
            .contains(">Presa visione dell&#39;assegnazione di «Clarinetto»</td>")
            .contains(">Riferimento del documento</td>")
            .contains("scade alle 10:40 del 18/10/2026")
            .contains("perché è stata chiesta una firma con questo indirizzo.");
        assertThat(registry.counter("taurus.signature.mail", "outcome", "sent").count()).isEqualTo(1);
    }

    /**
     * Il messaggio del codice non ha link, e il codice compare una sola volta: non nell'oggetto, nel titolo o
     * nell'anteprima, che le notifiche del telefono mostrano anche a schermo bloccato.
     */
    @Test
    void keepsTheCodeOutOfLinksSubjectAndPreview() throws Exception {
        JavaMailSender sender = MailParts.sender();

        service(sender, new SimpleMeterRegistry()).sendCode(MESSAGE);

        MimeMessage mail = sent(sender);
        String html = MailParts.html(mail);
        assertThat(html).doesNotContain("<a ").doesNotContain("http");
        assertThat(MailParts.text(mail)).doesNotContain("http");
        assertThat(Pattern.compile("042137").matcher(html).results().count()).isEqualTo(1);
        assertThat(html).contains("Il codice scade alle 10:40.</div>");
        assertThat(mail.getSubject()).doesNotContain("042137");
    }

    @Test
    void reportsAnSmtpFailure() {
        JavaMailSender sender = MailParts.sender();
        doThrow(new MailSendException("refused")).when(sender).send(any(MimeMessage.class));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        assertThatThrownBy(() -> service(sender, registry).sendCode(MESSAGE)).isInstanceOf(MailSendException.class);
        assertThat(registry.counter("taurus.signature.mail", "outcome", "failed").count()).isEqualTo(1);
    }

    @Test
    void isNotConfiguredWithoutAnSmtpServer() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        SignatureMailService service = service(null, registry);

        assertThat(service.configured()).isFalse();
        assertThatThrownBy(() -> service.sendCode(MESSAGE)).isInstanceOf(IllegalStateException.class);
        assertThat(registry.counter("taurus.signature.mail", "outcome", "unconfigured").count()).isEqualTo(1);
    }

    @Test
    void neverPrintsTheCodeOrTheAddress() {
        assertThat(MESSAGE.toString()).doesNotContain("042137").doesNotContain("mario.rossi");
    }

    @Test
    void sendsToTheParentTheLinkToSignWithTheMinorAndTheExpiry() throws Exception {
        JavaMailSender sender = MailParts.sender();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        service(sender, registry).sendLink(link(false));

        MimeMessage mail = sent(sender);
        assertThat(mail.getAllRecipients()).extracting(Object::toString).containsExactly("genitore@example.org");
        assertThat(mail.getSubject()).isEqualTo("Firma richiesta – Banda musicale di prova");
        assertThat(MailParts.text(mail))
            .startsWith("Firma richiesta\n")
            .contains("Associazione: Banda musicale di prova")
            .contains("Atto: Emissione del foglio – foglio di affidamento n. 2026/007")
            .contains("Socio minorenne: Luca Rossi")
            .contains("apri questa pagina:\n" + LINK_URL + "\n")
            .contains("vale fino alle 10:40 del 25/10/2026")
            .contains("codice di conferma")
            .contains("Non inoltrare il messaggio")
            .endsWith("perché Banda musicale di prova ti ha indicato come genitore o tutore di un socio minorenne.\n");
        String html = MailParts.html(mail);
        // L'indirizzo sta nel pulsante e nel link di riserva.
        assertThat(html).contains("<a href=\"" + LINK_URL + "\" style=\"display:inline-block;background-color:#15803d;");
        assertThat(html).contains(">Leggi e firma</a>").contains(">" + LINK_URL + "</a>");
        assertThat(html).contains(">Emissione del foglio – foglio di affidamento n. 2026/007</td>").contains(">Luca Rossi</td>");
        assertThat(html).contains("Banda musicale di prova ti chiede di firmare un atto.</div>");
        // Testi con apostrofi, passati ai frammenti con th:with.
        assertThat(html)
            .contains(">L&#39;associazione ti chiede di firmare elettronicamente un atto del foglio di affidamento,")
            .contains("documenti dell&#39;atto. Se non ti aspettavi questa richiesta, ignorala e avvisa l&#39;associazione.</p>");
        assertThat(registry.counter("taurus.signature.mail", "outcome", "sent").count()).isEqualTo(1);
    }

    @Test
    void sendsToTheParentTheLinkToTheSignedDocuments() throws Exception {
        JavaMailSender sender = MailParts.sender();

        service(sender, new SimpleMeterRegistry()).sendLink(link(true));

        MimeMessage mail = sent(sender);
        assertThat(mail.getSubject()).isEqualTo("Atto firmato – Banda musicale di prova");
        assertThat(MailParts.text(mail))
            .startsWith("Atto firmato\n")
            .contains("Tutte le parti hanno firmato l'atto.")
            .contains(LINK_URL)
            .contains("conserva una copia dei documenti");
        assertThat(MailParts.html(mail))
            .contains(">Scarica i documenti</a>")
            .contains(">Tutte le parti hanno firmato l&#39;atto. I documenti firmati e l&#39;attestazione si scaricano")
            .contains("conserva una copia dei documenti. Dopo questa data puoi chiederli all&#39;associazione.</p>");
        assertThat(link(true).toString()).doesNotContain("firma/abc").doesNotContain("genitore");
    }

    private static MimeMessage sent(JavaMailSender sender) {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(captor.capture());
        return captor.getValue();
    }

    private static SignatureMailService.LinkMessage link(boolean signed) {
        return new SignatureMailService.LinkMessage(
            "genitore@example.org",
            "Banda musicale di prova",
            "Emissione del foglio",
            "2026/007",
            "Luca Rossi",
            LINK_URL,
            ZonedDateTime.of(2026, 10, 25, 9, 40, 0, 0, ZoneId.of("UTC")),
            ZoneId.of("Europe/Rome"),
            signed
        );
    }

    private static SignatureMailService service(JavaMailSender sender, SimpleMeterRegistry registry) {
        return new SignatureMailService(MailParts.mailSender(sender), new SignatureMetrics(registry));
    }
}
