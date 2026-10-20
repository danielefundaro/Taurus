package com.fundaro.zodiac.taurus.service.signature;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import tech.jhipster.config.JHipsterProperties;

/**
 * Sends the confirmation codes and the links of the parents without an account through the SMTP server of the
 * installation ({@code spring.mail.*}), shared by all the tenants; every message names the tenant. Messages with a code
 * contain no links, and neither codes, links nor addresses are logged.
 */
@Service
public class SignatureMailService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ObjectProvider<JavaMailSender> mailSender;
    private final JHipsterProperties jHipsterProperties;
    private final SignatureMetrics metrics;

    public SignatureMailService(ObjectProvider<JavaMailSender> mailSender, JHipsterProperties jHipsterProperties, SignatureMetrics metrics) {
        this.mailSender = mailSender;
        this.jHipsterProperties = jHipsterProperties;
        this.metrics = metrics;
    }

    /**
     * Whether an SMTP server is configured: Spring Boot creates the mail sender only when {@code spring.mail.host} is set.
     */
    public boolean configured() {
        return mailSender.getIfAvailable() != null;
    }

    /**
     * Sends a confirmation code; throws {@link MailException} when the SMTP server refuses or cannot be reached.
     */
    public void sendCode(CodeMessage message) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            metrics.mail("unconfigured");
            throw new IllegalStateException("No SMTP server is configured");
        }
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(jHipsterProperties.getMail().getFrom());
        mail.setTo(message.destination());
        mail.setSubject("Codice di conferma – " + message.tenantName());
        mail.setText(text(message));
        try {
            sender.send(mail);
            metrics.mail("sent");
        } catch (MailException exception) {
            metrics.mail("failed");
            throw exception;
        }
    }

    /**
     * Sends the link to the public signature page to a parent or guardian; throws {@link MailException} when the SMTP
     * server refuses or cannot be reached.
     */
    public void sendLink(LinkMessage message) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            metrics.mail("unconfigured");
            throw new IllegalStateException("No SMTP server is configured");
        }
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(jHipsterProperties.getMail().getFrom());
        mail.setTo(message.destination());
        mail.setSubject((message.signed() ? "Atto firmato – " : "Firma richiesta – ") + message.tenantName());
        mail.setText(text(message));
        try {
            sender.send(mail);
            metrics.mail("sent");
        } catch (MailException exception) {
            metrics.mail("failed");
            throw exception;
        }
    }

    static String text(LinkMessage message) {
        ZonedDateTime expiresAt = message.expiresAt().withZoneSameInstant(message.zone());
        if (message.signed()) {
            return """
                Tutte le parti hanno firmato l'atto.

                Associazione: %s
                Atto: %s – foglio di affidamento n. %s
                Socio minorenne: %s

                I documenti firmati e l'attestazione si scaricano da questa pagina:
                %s

                Il collegamento vale fino alle %s del %s: conserva una copia dei documenti. Dopo questa data puoi chiederli all'associazione.
                """.formatted(
                message.tenantName(),
                message.operation(),
                message.sheetNumber(),
                message.minorName(),
                message.url(),
                TIME.format(expiresAt),
                DATE.format(expiresAt)
            );
        }
        return """
            L'associazione ti chiede di firmare elettronicamente un atto del foglio di affidamento, come genitore o tutore del socio minorenne.

            Associazione: %s
            Atto: %s – foglio di affidamento n. %s
            Socio minorenne: %s

            Per leggere i documenti e firmarli apri questa pagina:
            %s

            Il collegamento vale fino alle %s del %s e solo per questo atto. Per firmare riceverai a questo indirizzo un codice di conferma.
            Non inoltrare il messaggio: chi ha il collegamento può leggere i documenti dell'atto. Se non ti aspettavi questa richiesta, ignorala e avvisa l'associazione.
            """.formatted(
            message.tenantName(),
            message.operation(),
            message.sheetNumber(),
            message.minorName(),
            message.url(),
            TIME.format(expiresAt),
            DATE.format(expiresAt)
        );
    }

    static String text(CodeMessage message) {
        ZonedDateTime expiresAt = message.expiresAt().withZoneSameInstant(message.zone());
        return """
            Codice di conferma: %s

            Associazione: %s
            Operazione: %s
            Riferimento del documento: %s
            Il codice scade alle %s del %s e vale solo per questa operazione.

            Se non hai chiesto tu questa conferma, ignora il messaggio: senza il codice non viene registrata alcuna firma.
            """.formatted(
            message.code(),
            message.tenantName(),
            message.operation(),
            message.reference(),
            TIME.format(expiresAt),
            DATE.format(expiresAt)
        );
    }

    /**
     * A link to the public signature page: to sign the act or, once {@code signed}, to download its documents.
     */
    public record LinkMessage(
        String destination,
        String tenantName,
        String operation,
        String sheetNumber,
        String minorName,
        String url,
        ZonedDateTime expiresAt,
        ZoneId zone,
        boolean signed
    ) {
        @Override
        public String toString() {
            return "LinkMessage[operation=" + operation + ", signed=" + signed + "]";
        }
    }

    public record CodeMessage(
        String destination,
        String tenantName,
        String operation,
        String reference,
        String code,
        ZonedDateTime expiresAt,
        ZoneId zone
    ) {
        @Override
        public String toString() {
            return "CodeMessage[operation=" + operation + ", reference=" + reference + "]";
        }
    }
}
