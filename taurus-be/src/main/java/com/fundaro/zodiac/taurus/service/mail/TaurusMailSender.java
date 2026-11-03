package com.fundaro.zodiac.taurus.service.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import tech.jhipster.config.JHipsterProperties;

/**
 * Sends the e-mails of Taurus with the layout shared with the Keycloak theme (docs/email-layout-standard.md): an HTML
 * part from {@code templates/mail/html/<name>.html}, a text part from {@code templates/mail/text/<name>.txt} and the
 * logo attached inline, so it shows even when the mail client blocks remote images.
 *
 * <p>The SMTP server is the one of the installation ({@code spring.mail.*}); without it nothing is sent.
 */
@Service
public class TaurusMailSender {

    public static final String LOGO_CONTENT_ID = "taurus-logo";
    public static final String SENDER_NAME = "Taurus";

    static final String LOGO = "templates/mail/logo.png";

    private final ObjectProvider<JavaMailSender> mailSender;
    private final JHipsterProperties jHipsterProperties;
    private final TemplateEngine htmlEngine = engine("templates/mail/html/", ".html", TemplateMode.HTML);
    private final TemplateEngine textEngine = engine("templates/mail/text/", ".txt", TemplateMode.TEXT);
    private final ByteArrayResource logo = new ByteArrayResource(read(LOGO));

    public TaurusMailSender(ObjectProvider<JavaMailSender> mailSender, JHipsterProperties jHipsterProperties) {
        this.mailSender = mailSender;
        this.jHipsterProperties = jHipsterProperties;
    }

    /**
     * Whether an SMTP server is configured: Spring Boot creates the mail sender only when {@code spring.mail.host} is set.
     */
    public boolean configured() {
        return mailSender.getIfAvailable() != null;
    }

    /**
     * Renders and sends an e-mail; throws {@link IllegalStateException} without an SMTP server and {@link MailException}
     * when the message cannot be built or the SMTP server refuses it.
     */
    public void send(Mail mail) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            throw new IllegalStateException("No SMTP server is configured");
        }
        MimeMessage message = sender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_RELATED, StandardCharsets.UTF_8.name());
            helper.setFrom(jHipsterProperties.getMail().getFrom(), SENDER_NAME);
            helper.setTo(mail.to());
            helper.setSubject(mail.subject());
            helper.setText(text(mail.template(), mail.variables()), html(mail.template(), mail.variables()));
            helper.addInline(LOGO_CONTENT_ID, logo, "image/png");
        } catch (MessagingException | UnsupportedEncodingException exception) {
            throw new MailPreparationException("Cannot build the e-mail " + mail.template(), exception);
        }
        sender.send(message);
    }

    String html(String template, Map<String, Object> variables) {
        return htmlEngine.process(template, context(variables));
    }

    String text(String template, Map<String, Object> variables) {
        return textEngine.process(template, context(variables));
    }

    private static Context context(Map<String, Object> variables) {
        return new Context(Locale.ITALIAN, variables);
    }

    private static TemplateEngine engine(String prefix, String suffix, TemplateMode mode) {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix(prefix);
        resolver.setSuffix(suffix);
        resolver.setTemplateMode(mode);
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resolver.setCacheable(true);
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    private static byte[] read(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return in.readAllBytes();
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot read " + path, exception);
        }
    }

    /**
     * An e-mail to send: the name of its templates, without folder and extension, and their variables.
     */
    public record Mail(String to, String subject, String template, Map<String, Object> variables) {
        @Override
        public String toString() {
            return "Mail[template=" + template + "]";
        }
    }
}
