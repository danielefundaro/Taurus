package com.fundaro.zodiac.keycloakauthenticator.theme;

import freemarker.cache.ClassTemplateLoader;
import freemarker.core.HTMLOutputFormat;
import freemarker.core.TemplateClassResolver;
import freemarker.template.Configuration;
import freemarker.template.Template;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * I modelli di posta del tema viaggiano dentro il JAR e li compila Keycloak quando l'e-mail parte, non la
 * build: un errore di sintassi rende il messaggio «Failed to template email» e un'entita' HTML sfuggita
 * due volte si legge nel testo. Qui i modelli sono resi come fa DefaultFreeMarkerProvider, con l'output
 * HTML sfuggito e i soli attributi che Keycloak passa.
 */
class EmailTemplatesTest {

    private static final String THEME_PATH = "theme/taurus-theme/email/html";
    private static final String LINK = "http://localhost:9080/realms/taurus/login-actions/action-token?key=prova";
    private static final long EXPIRATION_MINUTES = 720L;

    // Un'entita' gia' sfuggita una volta: &egrave; uscito come &amp;egrave; si legge al posto della lettera.
    private static final Pattern DOUBLE_ESCAPED = Pattern.compile("&amp;[a-zA-Z]+;");
    // Un'interpolazione rimasta nel testo: la variabile non esiste e il modello non se ne accorge.
    private static final Pattern UNRESOLVED = Pattern.compile("[$#]\\{");

    @ParameterizedTest
    @ValueSource(strings = { "UPDATE_PASSWORD", "VERIFY_EMAIL", "CONFIGURE_TOTP", "delete_account" })
    void rendersTheRequiredActionsEmailForEveryBranch(String requiredAction) throws Exception {
        String html = render("executeActions.ftl", attributes(requiredAction));

        assertThat(html).contains(LINK).contains(String.valueOf(EXPIRATION_MINUTES));
        // Il logo e' un assign a blocco: se finisse sfuggito, l'SVG si leggerebbe come testo.
        assertThat(html).contains("<svg");
        assertThat(DOUBLE_ESCAPED.matcher(html).find()).isFalse();
        assertThat(UNRESOLVED.matcher(html).find()).isFalse();
    }

    @Test
    void rendersThePasswordResetEmail() throws Exception {
        String html = render("password-reset.ftl", attributes(null));

        assertThat(html).contains(LINK).contains(String.valueOf(EXPIRATION_MINUTES));
        assertThat(DOUBLE_ESCAPED.matcher(html).find()).isFalse();
        assertThat(UNRESOLVED.matcher(html).find()).isFalse();
    }

    @Test
    void keepsTheEntitiesOfTheWelcomeMessage() throws Exception {
        String html = render("executeActions.ftl", attributes("UPDATE_PASSWORD"));

        // La frase sta in un assign a blocco: in uno di stringa l'entita' uscirebbe come &amp;egrave;.
        assertThat(html).contains("Benvenuto in Taurus").contains("Il tuo account &egrave; stato creato");
    }

    private Map<String, Object> attributes(String requiredAction) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("link", LINK);
        attributes.put("linkExpiration", EXPIRATION_MINUTES);
        attributes.put("realmName", "taurus");
        attributes.put("user", Map.of("firstName", "Daniele"));
        if (requiredAction != null) {
            attributes.put("requiredActions", List.of(requiredAction));
        }
        return attributes;
    }

    @SuppressWarnings("deprecation")
    private String render(String template, Map<String, Object> attributes) throws Exception {
        // La stessa configurazione di org.keycloak.theme.freemarker.DefaultFreeMarkerProvider.
        Configuration configuration = new Configuration();
        configuration.setOutputFormat(HTMLOutputFormat.INSTANCE);
        configuration.setNewBuiltinClassResolver(TemplateClassResolver.ALLOWS_NOTHING_RESOLVER);
        configuration.setTemplateLoader(new ClassTemplateLoader(getClass().getClassLoader(), THEME_PATH));

        Template compiled = configuration.getTemplate(template, "UTF-8");
        StringWriter rendered = new StringWriter();
        compiled.process(attributes, rendered);
        return rendered.toString();
    }
}
