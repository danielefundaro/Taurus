package com.fundaro.zodiac.keycloakauthenticator.theme;

import freemarker.cache.ClassTemplateLoader;
import freemarker.cache.MultiTemplateLoader;
import freemarker.cache.StringTemplateLoader;
import freemarker.cache.TemplateLoader;
import freemarker.core.HTMLOutputFormat;
import freemarker.core.TemplateClassResolver;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateMethodModelEx;
import freemarker.template.TemplateNumberModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * I modelli di posta del tema viaggiano dentro il JAR e li compila Keycloak quando l'e-mail parte, non la
 * build: un errore di sintassi rende il messaggio «Failed to template email» e un'entita' HTML sfuggita
 * due volte si legge nel testo. Qui i modelli sono resi come fa DefaultFreeMarkerProvider, con l'output
 * HTML sfuggito e gli attributi che Keycloak passa. L'aspetto segue docs/email-layout-standard.md.
 */
class EmailTemplatesTest {

    private static final String THEME_PATH = "theme/taurus-theme/email";
    private static final String LINK = "http://localhost:9080/realms/taurus/login-actions/action-token?key=prova";
    private static final String RESOURCES_URL = "http://localhost:9080/resources/abc12/email/taurus-theme";
    private static final long EXPIRATION_MINUTES = 720L;
    /** Lo stesso valore di MailLogoTest nel backend: le e-mail dei due moduli usano lo stesso logo. */
    static final String LOGO_SHA256 = "54555ccf337f862d664d62c187b7916b8b5213453157ee3acb9bbc9c8f9abc15";

    // Un'entita' gia' sfuggita una volta: &egrave; uscito come &amp;egrave; si legge al posto della lettera.
    private static final Pattern DOUBLE_ESCAPED = Pattern.compile("&amp;[a-zA-Z]+;");
    // Un'interpolazione rimasta nel testo: la variabile non esiste e il modello non se ne accorge.
    private static final Pattern UNRESOLVED = Pattern.compile("[$#]\\{");
    // I colori tolti perche' sotto il contrasto minimo: #94a3b8 e #cbd5e1 per i testi.
    private static final Pattern LOW_CONTRAST = Pattern.compile("(?i)#94a3b8|#cbd5e1");

    @ParameterizedTest
    @ValueSource(strings = { "UPDATE_PASSWORD", "VERIFY_EMAIL", "CONFIGURE_TOTP", "delete_account" })
    void rendersTheRequiredActionsEmailForEveryBranch(String requiredAction) throws Exception {
        String html = html("executeActions.ftl", attributes(requiredAction));
        String text = text("executeActions.ftl", attributes(requiredAction));

        assertLayout(html);
        assertThat(html).contains("href=\"" + LINK + "\"").contains("<strong>12 ore</strong>");
        assertThat(text).contains(LINK).contains("Il link vale per 12 ore.").contains("Ciao Anna,");
        assertThat(UNRESOLVED.matcher(text).find()).isFalse();
    }

    @Test
    void namesTheActionInTitleButtonAndReason() throws Exception {
        String welcome = html("executeActions.ftl", attributes("UPDATE_PASSWORD"));
        String totp = html("executeActions.ftl", attributes("CONFIGURE_TOTP"));

        assertThat(welcome).contains("<title>Benvenuto in Taurus — Taurus</title>").contains(">Imposta la password</a>");
        assertThat(welcome).contains("è stato creato un account Taurus con questo indirizzo");
        // Solo il benvenuto parla di un account creato: le altre azioni riguardano un account esistente.
        assertThat(totp).contains("Configura l&#39;app di autenticazione").doesNotContain("è stato creato");
        assertThat(totp).contains("è stata chiesta un&#39;azione sul tuo account Taurus");
    }

    @Test
    void rendersThePasswordResetEmail() throws Exception {
        String html = html("password-reset.ftl", attributes(null));
        String text = text("password-reset.ftl", attributes(null));

        assertLayout(html);
        assertThat(html).contains(">Reimposta la password</a>").contains("<strong>12 ore</strong>");
        assertThat(text).startsWith("Reimposta la password").contains(LINK).contains("Il link vale per 12 ore.");
        assertThat(text).endsWith("è stato chiesto il recupero della password del tuo account.\n");
    }

    @Test
    void rendersTheSmtpTestEmail() throws Exception {
        String html = html("email-test.ftl", attributes(null));
        String text = text("email-test.ftl", attributes(null));

        assertLayout(html);
        assertThat(html).contains("Prova di invio").doesNotContain("<a ");
        assertThat(text).contains("server SMTP configurato nel realm");
    }

    @Test
    void escapesTheNameOfTheUser() throws Exception {
        Map<String, Object> attributes = attributes(null);
        attributes.put("user", Map.of("firstName", "<b>Anna</b>"));

        assertThat(html("password-reset.ftl", attributes)).contains("Ciao &lt;b&gt;Anna&lt;/b&gt;,").doesNotContain("<b>Anna");
    }

    @Test
    void greetsWithoutANameWhenTheUserHasNone() throws Exception {
        Map<String, Object> attributes = attributes(null);
        attributes.put("user", Map.of());

        assertThat(html("password-reset.ftl", attributes)).contains(">Ciao,</p>");
        assertThat(text("password-reset.ftl", attributes)).contains("\nCiao,\n");
    }

    /**
     * I modelli del tema base, come email-verification.ftl, chiamano la macro senza parametri: devono
     * ricevere lo stesso aspetto, senza titolo ne' motivo.
     */
    @Test
    void wrapsTheTemplatesOfTheBaseThemeInTheSameLayout() throws Exception {
        String base = "<#import \"template.ftl\" as layout>\n<@layout.emailLayout>\n<p>Testo del tema base</p>\n</@layout.emailLayout>\n";

        String html = render("html", Map.of("base.ftl", base), "base.ftl", attributes(null));

        assertLayout(html);
        assertThat(html).contains("<p>Testo del tema base</p>").contains("<title>Taurus</title>").doesNotContain("<h1");
    }

    @Test
    void leavesOutTheImageWhenKeycloakPassesNoResourcesUrl() throws Exception {
        Map<String, Object> attributes = attributes(null);
        attributes.remove("url");

        String html = html("password-reset.ftl", attributes);

        // Resta la scritta: l'intestazione si riconosce anche senza logo.
        assertThat(html).doesNotContain("<img").contains(">Taurus</div>");
    }

    @Test
    void definesTheSubjectsInItalian() throws Exception {
        Properties messages = properties(THEME_PATH + "/messages/messages_it.properties");

        assertThat(messages)
            .containsEntry("passwordResetSubject", "Reimposta la password")
            .containsEntry("executeActionsSubject", "Completa il tuo account Taurus")
            .containsEntry("emailTestSubject", "Prova di invio da Taurus");
    }

    /** Con l'italiano come lingua del realm il login legge messages_it: deve avere tutte le chiavi dell'inglese. */
    @Test
    void keepsTheLoginMessagesAligned() throws Exception {
        Properties english = properties("theme/taurus-theme/login/messages/messages_en.properties");
        Properties italian = properties("theme/taurus-theme/login/messages/messages_it.properties");

        assertThat(italian.stringPropertyNames()).isEqualTo(english.stringPropertyNames());
    }

    @Test
    void shipsTheSharedLogo() throws Exception {
        byte[] logo;
        try (InputStream in = resource(THEME_PATH + "/resources/img/logo.png")) {
            logo = in.readAllBytes();
        }

        assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(logo))).isEqualTo(LOGO_SHA256);
    }

    private static void assertLayout(String html) {
        assertThat(html).contains("<html lang=\"it\">").contains("<meta name=\"color-scheme\" content=\"light\">");
        assertThat(html).contains("<img src=\"" + RESOURCES_URL + "/img/logo.png\" width=\"72\" height=\"72\" alt=\"Taurus\"");
        assertThat(html).doesNotContain("<svg");
        assertThat(LOW_CONTRAST.matcher(html).find()).isFalse();
        if (html.contains("<a href=\"" + LINK)) {
            assertThat(html).contains("background-color:#15803d;color:#ffffff;");
        }
        assertThat(DOUBLE_ESCAPED.matcher(html).find()).isFalse();
        assertThat(UNRESOLVED.matcher(html).find()).isFalse();
    }

    private Map<String, Object> attributes(String requiredAction) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("link", LINK);
        attributes.put("linkExpiration", EXPIRATION_MINUTES);
        attributes.put("linkExpirationFormatter", new ExpirationFormatter());
        attributes.put("realmName", "Taurus");
        attributes.put("user", Map.of("firstName", "Anna"));
        attributes.put("url", Map.of("resourcesUrl", RESOURCES_URL));
        if (requiredAction != null) {
            attributes.put("requiredActions", List.of(requiredAction));
        }
        return attributes;
    }

    private String html(String template, Map<String, Object> attributes) throws Exception {
        return render("html", Map.of(), template, attributes);
    }

    private String text(String template, Map<String, Object> attributes) throws Exception {
        return render("text", Map.of(), template, attributes);
    }

    @SuppressWarnings("deprecation")
    private String render(String folder, Map<String, String> extra, String template, Map<String, Object> attributes) throws Exception {
        // La stessa configurazione di org.keycloak.theme.freemarker.DefaultFreeMarkerProvider.
        Configuration configuration = new Configuration();
        configuration.setOutputFormat(HTMLOutputFormat.INSTANCE);
        configuration.setNewBuiltinClassResolver(TemplateClassResolver.ALLOWS_NOTHING_RESOLVER);
        StringTemplateLoader strings = new StringTemplateLoader();
        extra.forEach(strings::putTemplate);
        configuration.setTemplateLoader(new MultiTemplateLoader(new TemplateLoader[] {
            strings,
            new ClassTemplateLoader(getClass().getClassLoader(), THEME_PATH + "/" + folder),
        }));

        Template compiled = configuration.getTemplate(template, "UTF-8");
        StringWriter rendered = new StringWriter();
        compiled.process(attributes, rendered);
        return rendered.toString();
    }

    private Properties properties(String path) throws Exception {
        Properties properties = new Properties();
        try (InputStream in = resource(path)) {
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return properties;
    }

    private InputStream resource(String path) {
        InputStream in = getClass().getClassLoader().getResourceAsStream(path);
        assertThat(in).as(path).isNotNull();
        return in;
    }

    /** Come LinkExpirationFormatterMethod di Keycloak con i messaggi italiani: minuti, ore o giorni interi. */
    private static final class ExpirationFormatter implements TemplateMethodModelEx {

        @Override
        public Object exec(@SuppressWarnings("rawtypes") List arguments) throws freemarker.template.TemplateModelException {
            long minutes = ((TemplateNumberModel) arguments.get(0)).getAsNumber().longValue();
            if (minutes % (60 * 24) == 0) return (minutes / (60 * 24)) + (minutes == 60 * 24 ? " giorno" : " giorni");
            if (minutes % 60 == 0) return (minutes / 60) + (minutes == 60 ? " ora" : " ore");
            return minutes + (minutes == 1 ? " minuto" : " minuti");
        }
    }
}
