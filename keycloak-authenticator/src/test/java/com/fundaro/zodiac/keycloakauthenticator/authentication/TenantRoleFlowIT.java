package com.fundaro.zodiac.keycloakauthenticator.authentication;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Il flow di accesso e il mapper in un Keycloak reale, con il JAR del modulo, il tema e un realm
 * che usa {@code tenant-role-selector-authenticator} nel browser flow.
 *
 * <p>Fino a questa suite authenticator e mapper erano verificati soltanto con sessioni finte:
 * nessun test attraversava la pagina di scelta né leggeva i claim di un token emesso davvero.
 */
class TenantRoleFlowIT {

    private static final Path PROVIDER = Path.of("target", "keycloak-authenticator.jar");
    private static final String REALM = "tenant-role-test";
    private static final String CLIENT = "taurus-web";
    private static final String REDIRECT_URI = "http://localhost/callback";
    private static final Pattern FORM_ACTION = Pattern.compile("<form[^>]*action=\"([^\"]+)\"");
    private static final Pattern CODE = Pattern.compile("[?&]code=([^&]+)");
    private static final Pattern ACCESS_TOKEN = Pattern.compile("\"access_token\"\\s*:\\s*\"([^\"]+)\"");
    private static final String CLAIM_TEXT = "\"%s\"\\s*:\\s*\"([^\"]+)\"";

    private static GenericContainer<?> keycloak;
    private static String baseUrl;

    private HttpClient http;

    /**
     * I cookie della sessione di autenticazione, tenuti a mano: su `http` il gestore di serie di
     * Java non rimanda i cookie marcati `Secure`, e Keycloak li marca così.
     */
    private Map<String, String> cookies;

    @BeforeAll
    static void startKeycloak() {
        assertThat(PROVIDER).as("package the provider before the integration tests").exists();
        keycloak = new GenericContainer<>("quay.io/keycloak/keycloak:26.6.4")
            .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "tenant-role-admin")
            .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "tenant-role-password")
            .withCopyFileToContainer(MountableFile.forHostPath(PROVIDER), "/opt/keycloak/providers/keycloak-authenticator.jar")
            .withCopyFileToContainer(MountableFile.forClasspathResource("tenant-role-test-realm.json"), "/opt/keycloak/data/import/tenant-role-test-realm.json")
            // Nessuna cartella di temi montata: taurus-theme arriva dal provider, che lo dichiara in
            // META-INF/keycloak-themes.json. Se la dichiarazione manca, il realm cade sul tema di serie.
            .withCommand("start-dev", "--import-realm")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/realms/" + REALM).forPort(8080).forStatusCode(200))
            .withStartupTimeout(Duration.ofMinutes(4));
        keycloak.start();
        baseUrl = "http://" + keycloak.getHost() + ":" + keycloak.getMappedPort(8080) + "/realms/" + REALM;
    }

    @AfterAll
    static void stopKeycloak() throws Exception {
        if (keycloak == null) return;
        // Tenuto per la diagnosi: il log del server di questa esecuzione.
        Files.writeString(Path.of("target", "keycloak-tenant-role-it.log"), keycloak.getLogs());
        keycloak.stop();
    }

    /** Ogni prova parte da un browser pulito: i cookie di sessione non devono passare fra le prove. */
    @BeforeEach
    void freshBrowser() {
        http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        cookies = new LinkedHashMap<>();
    }

    /** Invia la richiesta con i cookie raccolti e raccoglie quelli della risposta. */
    private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        if (!cookies.isEmpty()) {
            request.header("Cookie", cookies.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((first, second) -> first + "; " + second)
                .orElse(""));
        }
        HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        response.headers().allValues("set-cookie").forEach(header -> {
            String pair = header.split(";", 2)[0];
            int separator = pair.indexOf('=');
            if (separator > 0) cookies.put(pair.substring(0, separator).trim(), pair.substring(separator + 1).trim());
        });

        return response;
    }

    /**
     * Il tema del realm arriva dal provider. Senza la dichiarazione in META-INF/keycloak-themes.json
     * Keycloak non lo trova nel JAR e serve il tema di serie: la pagina non avrebbe il markup di Taurus.
     */
    @Test
    void servesTheLoginPageWithTheThemeDeclaredInTheProvider() throws Exception {
        HttpResponse<String> login = send(HttpRequest.newBuilder(URI.create(authorizeUrl())).GET());

        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(login.body()).contains("taurus-card").contains("taurus-form-body");
        assertThat(login.body()).contains("/login/taurus-theme/css/login.css");
    }

    @Test
    void choosesTenantAndRoleByItselfWhenTheUserHasOnlyOne() throws Exception {
        HttpResponse<String> afterPassword = signIn("anna", "anna-test-password");

        // Nessuna pagina di scelta: il flow conclude subito con il codice di autorizzazione.
        assertThat(afterPassword.statusCode()).isEqualTo(302);
        Map<String, Object> claims = claimsOf(exchange(codeOf(afterPassword)));
        assertThat(claims).containsEntry("tenant", "TENANT-A").containsEntry("tenant_role", "ROLE_ADMIN");
        assertThat(claims).doesNotContainKey("role_choice");
    }

    @Test
    void asksForTheRoleWhenTheTenantOffersMoreThanOne() throws Exception {
        HttpResponse<String> form = signIn("bruno", "bruno-test-password");

        assertThat(form.statusCode()).isEqualTo(200);
        assertThat(form.body()).contains("kc-tenant-role-select-form").contains("ROLE_USER");

        HttpResponse<String> chosen = submit(form, Map.of("tenant", "TENANT-A", "role", "ROLE_USER"));
        assertThat(chosen.statusCode()).isEqualTo(302);

        Map<String, Object> claims = claimsOf(exchange(codeOf(chosen)));
        assertThat(claims).containsEntry("tenant", "TENANT-A").containsEntry("tenant_role", "ROLE_USER");
        // Il ruolo è stato scelto fra più ruoli del tenant: la topbar lo mostra.
        assertThat(claims).containsEntry("role_choice", true);
    }

    @Test
    void refusesARoleThatDoesNotBelongToTheTenant() throws Exception {
        HttpResponse<String> form = signIn("bruno", "bruno-test-password");

        HttpResponse<String> refused = submit(form, Map.of("tenant", "TENANT-A", "role", "ROLE_TREASURER"));

        // La pagina torna con l'errore invece di emettere un token con un ruolo non posseduto.
        assertThat(refused.statusCode()).isEqualTo(200);
        assertThat(refused.body()).contains("kc-tenant-role-select-form");
    }

    @Test
    void choosesTheRoleOfTheSelectedTenantWhenThereIsOnlyOne() throws Exception {
        HttpResponse<String> form = signIn("carla", "carla-test-password");

        assertThat(form.statusCode()).isEqualTo(200);
        assertThat(form.body()).contains("TENANT-A").contains("TENANT-B");

        HttpResponse<String> chosen = submit(form, Map.of("tenant", "TENANT-B"));
        assertThat(chosen.statusCode()).isEqualTo(302);

        Map<String, Object> claims = claimsOf(exchange(codeOf(chosen)));
        assertThat(claims).containsEntry("tenant", "TENANT-B").containsEntry("tenant_role", "ROLE_ARCHIVIST");
        assertThat(claims).doesNotContainKey("role_choice");
    }

    @Test
    void refusesAUserWithoutAnyTenant() throws Exception {
        HttpResponse<String> afterPassword = signIn("dario", "dario-test-password");

        assertThat(afterPassword.statusCode()).isNotEqualTo(302);
        assertThat(afterPassword.body()).doesNotContain("kc-tenant-role-select-form");
    }

    /** Apre la pagina di accesso e invia le credenziali, restituendo la risposta del passo successivo. */
    /** L'indirizzo con cui il client avvia l'accesso, uguale per ogni prova. */
    private static String authorizeUrl() {
        return baseUrl + "/protocol/openid-connect/auth?client_id=" + CLIENT
            + "&response_type=code&scope=openid&state=test&redirect_uri=" + encode(REDIRECT_URI);
    }

    private HttpResponse<String> signIn(String username, String password) throws Exception {
        HttpResponse<String> loginPage = send(HttpRequest.newBuilder(URI.create(authorizeUrl())).GET());
        assertThat(loginPage.statusCode()).isEqualTo(200);

        return submit(loginPage, Map.of("username", username, "password", password, "credentialId", ""));
    }

    /** Invia il form della pagina ricevuta, con i campi indicati. */
    private HttpResponse<String> submit(HttpResponse<String> page, Map<String, String> fields) throws Exception {
        Matcher action = FORM_ACTION.matcher(page.body());
        assertThat(action.find()).as("the page carries a form").isTrue();
        String target = action.group(1).replace("&amp;", "&");

        Map<String, String> ordered = new LinkedHashMap<>(fields);
        String body = ordered.entrySet().stream()
            .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
            .reduce((first, second) -> first + "&" + second)
            .orElse("");

        return send(
            HttpRequest.newBuilder(URI.create(target))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
        );
    }

    private String codeOf(HttpResponse<String> redirect) {
        String location = redirect.headers().firstValue("Location").orElse("");
        Matcher code = CODE.matcher(location);
        assertThat(code.find()).as("the redirect carries the authorization code: %s", location).isTrue();

        return code.group(1);
    }

    /** Scambia il codice con il token del client pubblico. */
    private String exchange(String code) throws Exception {
        HttpResponse<String> response = send(
            HttpRequest.newBuilder(URI.create(baseUrl + "/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(
                    "grant_type=authorization_code&client_id=" + CLIENT + "&code=" + encode(code) + "&redirect_uri=" + encode(REDIRECT_URI)
                ))
        );
        assertThat(response.statusCode()).as("token response: %s", response.body()).isEqualTo(200);
        Matcher accessToken = ACCESS_TOKEN.matcher(response.body());
        assertThat(accessToken.find()).isTrue();

        return accessToken.group(1);
    }

    /** I soli claim del mapper, letti dal payload del token. */
    private Map<String, Object> claimsOf(String accessToken) {
        String payload = new String(Base64.getUrlDecoder().decode(accessToken.split("\\.")[1]), StandardCharsets.UTF_8);
        Map<String, Object> claims = new LinkedHashMap<>();
        text(payload, "tenant").ifPresent(value -> claims.put("tenant", value));
        text(payload, "tenant_role").ifPresent(value -> claims.put("tenant_role", value));
        if (payload.matches("(?s).*\"role_choice\"\\s*:\\s*true.*")) claims.put("role_choice", true);

        return claims;
    }

    private static java.util.Optional<String> text(String payload, String claim) {
        Matcher matcher = Pattern.compile(String.format(CLAIM_TEXT, claim)).matcher(payload);

        return matcher.find() ? java.util.Optional.of(matcher.group(1)) : java.util.Optional.empty();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
