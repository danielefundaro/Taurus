package com.fundaro.zodiac.taurus.service.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimePart;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;

class TaurusMailSenderTest {

    /** Lo stesso valore di EmailTemplatesTest nel modulo Keycloak: le e-mail dei due moduli usano lo stesso logo. */
    private static final String LOGO_SHA256 = "54555ccf337f862d664d62c187b7916b8b5213453157ee3acb9bbc9c8f9abc15";

    private static final Map<String, Object> VARIABLES = Map.of(
        "tenant",
        "Banda <script>alert(1)</script> & Co",
        "code",
        "042137",
        "expiresTime",
        "10:40",
        "expiresDate",
        "18/10/2026",
        "details",
        List.of(Map.of("label", "Associazione", "value", "Banda <script>alert(1)</script> & Co"))
    );

    @Test
    void sendsTextAndHtmlWithTheLogoInline() throws Exception {
        JavaMailSender sender = MailParts.sender();

        MimeMessage message = send(sender);

        // multipart/related: le alternative testo e HTML, poi il logo citato da cid:taurus-logo.
        assertThat(message.isMimeType("multipart/related")).isTrue();
        Multipart related = (Multipart) message.getContent();
        assertThat(related.getCount()).isEqualTo(2);
        assertThat(related.getBodyPart(0).isMimeType("multipart/alternative")).isTrue();
        Multipart alternative = (Multipart) related.getBodyPart(0).getContent();
        assertThat(alternative.getBodyPart(0).isMimeType("text/plain")).isTrue();
        assertThat(alternative.getBodyPart(1).isMimeType("text/html")).isTrue();
        MimePart logo = (MimePart) related.getBodyPart(1);
        assertThat(logo.isMimeType("image/png")).isTrue();
        assertThat(logo.getContentID()).isEqualTo("<taurus-logo>");
        assertThat(logo.getDisposition()).isEqualTo(Part.INLINE);
        assertThat(MailParts.html(message)).contains("<img src=\"cid:taurus-logo\" width=\"72\" height=\"72\" alt=\"Taurus\"");
    }

    @Test
    void sendsFromTaurus() throws Exception {
        MimeMessage message = send(MailParts.sender());

        InternetAddress from = (InternetAddress) message.getFrom()[0];
        assertThat(from.getAddress()).isEqualTo("taurus@example.org");
        assertThat(from.getPersonal()).isEqualTo("Taurus");
        assertThat(message.getAllRecipients()).extracting(Object::toString).containsExactly("mario.rossi@example.org");
        assertThat(message.getSubject()).isEqualTo("Codice di conferma – Banda");
    }

    @Test
    void escapesTheValuesInTheHtmlAndKeepsThemInTheText() throws Exception {
        MimeMessage message = send(MailParts.sender());

        assertThat(MailParts.html(message)).contains("Banda &lt;script&gt;alert(1)&lt;/script&gt; &amp; Co").doesNotContain("<script>");
        assertThat(MailParts.text(message)).contains("Associazione: Banda <script>alert(1)</script> & Co");
    }

    @Test
    void usesTheColoursOfTheStandard() throws Exception {
        String html = MailParts.html(send(MailParts.sender()));

        assertThat(html).contains("<html lang=\"it\">").contains("<meta name=\"color-scheme\" content=\"light\">");
        assertThat(html).contains("background-color:#22c55e;height:5px").doesNotContainIgnoringCase("#94a3b8").doesNotContainIgnoringCase("#cbd5e1");
        // Nessun attributo Thymeleaf rimasto nel messaggio.
        assertThat(html).doesNotContain("<svg").doesNotContain(" th:").doesNotContain("xmlns:th");
    }

    @Test
    void refusesToSendWithoutAnSmtpServer() {
        TaurusMailSender mailSender = mailSender(null);

        assertThat(mailSender.configured()).isFalse();
        assertThatThrownBy(() -> mailSender.send(mail())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shipsTheSharedLogo() throws Exception {
        byte[] logo;
        try (InputStream in = new ClassPathResource(TaurusMailSender.LOGO).getInputStream()) {
            logo = in.readAllBytes();
        }

        assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(logo))).isEqualTo(LOGO_SHA256);
    }

    @Test
    void neverPrintsTheAddressOrTheVariables() {
        assertThat(mail().toString()).doesNotContain("mario.rossi").doesNotContain("042137");
    }

    private static MimeMessage send(JavaMailSender sender) {
        mailSender(sender).send(mail());
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(captor.capture());
        return captor.getValue();
    }

    private static TaurusMailSender.Mail mail() {
        return new TaurusMailSender.Mail("mario.rossi@example.org", "Codice di conferma – Banda", "signature-code", VARIABLES);
    }

    private static TaurusMailSender mailSender(JavaMailSender sender) {
        return MailParts.mailSender(sender);
    }
}
