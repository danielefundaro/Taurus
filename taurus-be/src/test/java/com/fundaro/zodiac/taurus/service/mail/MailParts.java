package com.fundaro.zodiac.taurus.service.mail;

import jakarta.mail.BodyPart;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import java.util.Properties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import tech.jhipster.config.JHipsterProperties;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Reads the parts of an e-mail built by {@link TaurusMailSender}: the text, the HTML and the inline logo.
 */
public final class MailParts {

    private MailParts() {}

    /**
     * A mail sender that builds real MIME messages and sends nothing. Like JavaMailSenderImpl it saves the message
     * before sending, which sets the content types of the parts.
     */
    public static JavaMailSender sender() {
        JavaMailSender sender = mock(JavaMailSender.class);
        stub(sender);
        return sender;
    }

    /** Gives a mocked mail sender, such as a {@code @MockBean}, the behaviour of {@link #sender()}. */
    public static void stub(JavaMailSender sender) {
        when(sender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));
        doAnswer(invocation -> {
            invocation.<MimeMessage>getArgument(0).saveChanges();
            return null;
        })
            .when(sender)
            .send(any(MimeMessage.class));
    }

    /** The sender of the e-mails of Taurus on that mail sender, or without SMTP server when it is null. */
    @SuppressWarnings("unchecked")
    public static TaurusMailSender mailSender(JavaMailSender sender) {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        JHipsterProperties properties = new JHipsterProperties();
        properties.getMail().setFrom("taurus@example.org");
        return new TaurusMailSender(provider, properties);
    }

    public static String text(MimeMessage message) {
        return content(message, "text/plain");
    }

    public static String html(MimeMessage message) {
        return content(message, "text/html");
    }

    /** Whether the e-mail goes to that address. */
    public static boolean sentTo(MimeMessage message, String address) {
        try {
            return message.getAllRecipients() != null && List.of(message.getAllRecipients()).stream().anyMatch(to -> to.toString().equals(address));
        } catch (MessagingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static String subject(MimeMessage message) {
        try {
            return message.getSubject();
        } catch (MessagingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String content(MimeMessage message, String type) {
        try {
            return (String) find(message, type).getContent();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static Part find(Part part, String type) throws Exception {
        if (part.isMimeType(type)) return part;
        if (part.getContent() instanceof Multipart multipart) {
            for (int index = 0; index < multipart.getCount(); index++) {
                BodyPart child = multipart.getBodyPart(index);
                Part found = find(child, type);
                if (found != null) return found;
            }
        }
        return null;
    }
}
