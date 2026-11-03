package com.fundaro.zodiac.keycloakauthenticator.theme;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Un'e-mail inviata davvero da Keycloak con il tema del provider, raccolta da Mailpit. I modelli resi a
 * parte non dimostrano due cose: che Keycloak passi {@code url.resourcesUrl} ai modelli di posta e che
 * serva il logo del tema email a quell'indirizzo. Qui si verificano entrambe, con oggetto e parte testo.
 */
class EmailDeliveryIT {

    private static final Path PROVIDER = Path.of("target", "keycloak-authenticator.jar");
    private static final String REALM = "email-test";
    private static final String ADMIN = "email-admin";
    private static final String ADMIN_PASSWORD = "email-admin-password";
    private static final Pattern LOGO = Pattern.compile("<img src=\"([^\"]+/img/logo\\.png)\"");

    private static Network network;
    private static GenericContainer<?> mailpit;
    private static GenericContainer<?> keycloak;
    private static String keycloakUrl;
    private static String mailpitUrl;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeAll
    static void start() {
        assertThat(PROVIDER).as("package the provider before the integration tests").exists();
        network = Network.newNetwork();
        mailpit = new GenericContainer<>("axllent/mailpit:v1.31.3")
            .withNetwork(network)
            .withNetworkAliases("mailpit")
            .withExposedPorts(8025)
            .waitingFor(Wait.forHttp("/readyz").forPort(8025).forStatusCode(200));
        mailpit.start();
        keycloak = new GenericContainer<>("quay.io/keycloak/keycloak:26.6.4")
            .withNetwork(network)
            .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", ADMIN)
            .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", ADMIN_PASSWORD)
            .withCopyFileToContainer(MountableFile.forHostPath(PROVIDER), "/opt/keycloak/providers/keycloak-authenticator.jar")
            .withCopyFileToContainer(MountableFile.forClasspathResource("email-test-realm.json"), "/opt/keycloak/data/import/email-test-realm.json")
            .withCommand("start-dev", "--import-realm")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/realms/" + REALM).forPort(8080).forStatusCode(200))
            .withStartupTimeout(Duration.ofMinutes(4));
        keycloak.start();
        keycloakUrl = "http://" + keycloak.getHost() + ":" + keycloak.getMappedPort(8080);
        mailpitUrl = "http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(8025);
    }

    @AfterAll
    static void stop() throws Exception {
        if (keycloak != null) {
            Files.writeString(Path.of("target", "keycloak-email-it.log"), keycloak.getLogs());
            keycloak.stop();
        }
        if (mailpit != null) mailpit.stop();
        if (network != null) network.close();
    }

    @Test
    void sendsTheAccountSetupEmailWithTheTaurusLayout() throws Exception {
        String token = adminToken();
        String userId = JsonPath.read(get(keycloakUrl + "/admin/realms/" + REALM + "/users?username=anna&exact=true", token), "$[0].id");

        HttpResponse<String> sent = http.send(HttpRequest.newBuilder(URI.create(keycloakUrl + "/admin/realms/" + REALM + "/users/" + userId + "/execute-actions-email"))
            .header("Authorization", "Bearer " + token)
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString("[\"UPDATE_PASSWORD\"]"))
            .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(sent.statusCode()).as(sent.body()).isEqualTo(204);

        String message = latestMessage();
        // L'oggetto viene da messages_it del tema: il realm ha l'italiano come lingua.
        assertThat(JsonPath.<String>read(message, "$.Subject")).isEqualTo("Completa il tuo account Taurus");
        assertThat(JsonPath.<String>read(message, "$.From.Name")).isEqualTo("Taurus");

        String html = JsonPath.read(message, "$.HTML");
        String text = JsonPath.read(message, "$.Text");
        assertThat(html).contains("Benvenuto in Taurus").contains("Ciao Anna,").contains("12 ore").doesNotContain("<svg");
        assertThat(text).startsWith("Benvenuto in Taurus").contains("Il link vale per 12 ore.").contains("/login-actions/action-token?key=");

        Matcher logo = LOGO.matcher(html);
        assertThat(logo.find()).as("the e-mail cites the logo of the theme").isTrue();
        assertThat(logo.group(1)).contains("/email/taurus-theme/img/logo.png");
        HttpResponse<byte[]> image = http.send(HttpRequest.newBuilder(URI.create(logo.group(1))).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(image.statusCode()).isEqualTo(200);
        assertThat(image.headers().firstValue("Content-Type")).hasValueSatisfying(type -> assertThat(type).startsWith("image/png"));
    }

    private String adminToken() throws Exception {
        String form = "grant_type=password&client_id=admin-cli&username=" + ADMIN + "&password=" + URLEncoder.encode(ADMIN_PASSWORD, StandardCharsets.UTF_8);
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(keycloakUrl + "/realms/master/protocol/openid-connect/token"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return JsonPath.read(response.body(), "$.access_token");
    }

    private String get(String url, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).GET();
        if (token != null) request.header("Authorization", "Bearer " + token);
        HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(url).isEqualTo(200);
        return response.body();
    }

    /** Mailpit riceve il messaggio poco dopo la risposta di Keycloak: lo si aspetta fino a 10 secondi. */
    private String latestMessage() throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            List<String> ids = JsonPath.read(get(mailpitUrl + "/api/v1/messages", null), "$.messages[*].ID");
            if (!ids.isEmpty()) return get(mailpitUrl + "/api/v1/message/" + ids.get(0), null);
            Thread.sleep(200);
        }
        throw new AssertionError("Mailpit received no e-mail");
    }
}
