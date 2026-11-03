package com.fundaro.zodiac.taurus.service.signature;

import com.fundaro.zodiac.taurus.service.mail.TaurusMailSender;
import com.fundaro.zodiac.taurus.service.mail.TaurusMailSender.Mail;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;

/**
 * Sends the confirmation codes and the links of the parents without an account through the SMTP server of the
 * installation ({@code spring.mail.*}), shared by all the tenants; every message names the tenant. Messages with a code
 * contain no links, and neither codes, links nor addresses are logged. The layout is the one of all the e-mails of
 * Taurus (docs/email-layout-standard.md): templates {@code signature-code}, {@code signature-link} and
 * {@code signature-signed} under {@code templates/mail}.
 */
@Service
public class SignatureMailService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final TaurusMailSender mailSender;
    private final SignatureMetrics metrics;

    public SignatureMailService(TaurusMailSender mailSender, SignatureMetrics metrics) {
        this.mailSender = mailSender;
        this.metrics = metrics;
    }

    /**
     * Whether an SMTP server is configured: Spring Boot creates the mail sender only when {@code spring.mail.host} is set.
     */
    public boolean configured() {
        return mailSender.configured();
    }

    /**
     * Sends a confirmation code; throws {@link MailException} when the SMTP server refuses or cannot be reached.
     */
    public void sendCode(CodeMessage message) {
        send(new Mail(message.destination(), "Codice di conferma – " + message.tenantName(), "signature-code", variables(message)));
    }

    /**
     * Sends the link to the public signature page to a parent or guardian; throws {@link MailException} when the SMTP
     * server refuses or cannot be reached.
     */
    public void sendLink(LinkMessage message) {
        String subject = (message.signed() ? "Atto firmato – " : "Firma richiesta – ") + message.tenantName();
        send(new Mail(message.destination(), subject, message.signed() ? "signature-signed" : "signature-link", variables(message)));
    }

    private void send(Mail mail) {
        if (!mailSender.configured()) {
            metrics.mail("unconfigured");
            throw new IllegalStateException("No SMTP server is configured");
        }
        try {
            mailSender.send(mail);
            metrics.mail("sent");
        } catch (MailException exception) {
            metrics.mail("failed");
            throw exception;
        }
    }

    static Map<String, Object> variables(CodeMessage message) {
        Map<String, Object> variables = expiry(message.expiresAt(), message.zone());
        variables.put("tenant", message.tenantName());
        variables.put("code", message.code());
        variables.put(
            "details",
            List.of(
                row("Associazione", message.tenantName()),
                row("Operazione", message.operation()),
                row("Riferimento del documento", message.reference())
            )
        );
        return variables;
    }

    static Map<String, Object> variables(LinkMessage message) {
        Map<String, Object> variables = expiry(message.expiresAt(), message.zone());
        variables.put("tenant", message.tenantName());
        variables.put("url", message.url());
        variables.put(
            "details",
            List.of(
                row("Associazione", message.tenantName()),
                row("Atto", message.operation() + " – foglio di affidamento n. " + message.sheetNumber()),
                row("Socio minorenne", message.minorName())
            )
        );
        return variables;
    }

    private static Map<String, Object> expiry(ZonedDateTime expiresAt, ZoneId zone) {
        ZonedDateTime local = expiresAt.withZoneSameInstant(zone);
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("expiresTime", TIME.format(local));
        variables.put("expiresDate", DATE.format(local));
        return variables;
    }

    private static Map<String, String> row(String label, String value) {
        return Map.of("label", label, "value", value);
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
