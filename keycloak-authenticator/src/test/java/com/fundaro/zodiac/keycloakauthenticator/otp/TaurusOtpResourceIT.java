package com.fundaro.zodiac.keycloakauthenticator.otp;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The provider in a real Keycloak, with the packaged JAR and a realm that refuses reused codes.
 */
class TaurusOtpResourceIT {

    private static final Path PROVIDER = Path.of("target", "keycloak-authenticator.jar");
    private static final String REALM = "otp-test";
    private static final String MARIO = "6f1d3c0e-8b9a-4a51-9a51-0b2c6d7e8f01";
    private static final String LUIGI = "6f1d3c0e-8b9a-4a51-9a51-0b2c6d7e8f02";
    private static final byte[] SECRET = "taurus-otp-test-secret-0001".getBytes(StandardCharsets.UTF_8);
    private static final Pattern ACCESS_TOKEN = Pattern.compile("\"access_token\"\\s*:\\s*\"([^\"]+)\"");

    private static GenericContainer<?> keycloak;
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static String baseUrl;

    @BeforeAll
    static void startKeycloak() {
        assertThat(PROVIDER).as("package the provider before the integration tests").exists();
        keycloak = new GenericContainer<>("quay.io/keycloak/keycloak:26.6.4")
            .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "otp-test-admin")
            .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "otp-test-password")
            .withCopyFileToContainer(MountableFile.forHostPath(PROVIDER), "/opt/keycloak/providers/keycloak-authenticator.jar")
            .withCopyFileToContainer(MountableFile.forClasspathResource("otp-test-realm.json"), "/opt/keycloak/data/import/otp-test-realm.json")
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
        // Kept for diagnosis: the server log of the run.
        Files.writeString(Path.of("target", "keycloak-otp-it.log"), keycloak.getLogs());
        keycloak.stop();
    }

    @Test
    void reportsTheAppOfEachUser() throws Exception {
        String token = token("taurus-signature", "signature-test-secret");

        HttpResponse<String> mario = get("/taurus-otp/users/" + MARIO + "/status", token);
        assertThat(mario.statusCode()).isEqualTo(200);
        assertThat(mario.body()).isEqualTo("{\"configured\":true}");
        assertThat(get("/taurus-otp/users/" + LUIGI + "/status", token).body()).isEqualTo("{\"configured\":false}");
        assertThat(get("/taurus-otp/users/missing/status", token).statusCode()).isEqualTo(404);
    }

    @Test
    void acceptsAValidCodeOnlyOnce() throws Exception {
        String token = token("taurus-signature", "signature-test-secret");
        String code = totp(System.currentTimeMillis() / 30_000);

        assertThat(verify(MARIO, code, token).statusCode()).isEqualTo(204);
        assertThat(verify(MARIO, code, token).statusCode()).isEqualTo(422);
        assertThat(verify(MARIO, wrongCode(), token).statusCode()).isEqualTo(422);
        assertThat(verify(LUIGI, code, token).statusCode()).isEqualTo(422);
    }

    @Test
    void refusesOtherCallers() throws Exception {
        String other = token("other-service", "other-test-secret");

        assertThat(get("/taurus-otp/users/" + MARIO + "/status", other).statusCode()).isEqualTo(403);
        assertThat(verify(MARIO, totp(System.currentTimeMillis() / 30_000), other).statusCode()).isEqualTo(403);
        assertThat(get("/taurus-otp/users/" + MARIO + "/status", null).statusCode()).isEqualTo(403);
    }

    private static String token(String clientId, String secret) throws Exception {
        HttpResponse<String> response = HTTP.send(
            HttpRequest.newBuilder(URI.create(baseUrl + "/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials&client_id=" + clientId + "&client_secret=" + secret))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );
        assertThat(response.statusCode()).isEqualTo(200);
        Matcher matcher = ACCESS_TOKEN.matcher(response.body());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private static HttpResponse<String> get(String path, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path)).GET();
        if (token != null) request.header("Authorization", "Bearer " + token);
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> verify(String userId, String code, String token) throws Exception {
        return HTTP.send(
            HttpRequest.newBuilder(URI.create(baseUrl + "/taurus-otp/users/" + userId + "/verify"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"code\":\"" + code + "\"}"))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );
    }

    /**
     * A code outside the window accepted by the realm policy.
     */
    private static String wrongCode() throws Exception {
        long step = System.currentTimeMillis() / 30_000;
        Set<String> valid = Set.of(totp(step - 2), totp(step - 1), totp(step), totp(step + 1), totp(step + 2));
        int candidate = 0;
        while (valid.contains(String.format("%06d", candidate))) candidate++;
        return String.format("%06d", candidate);
    }

    /**
     * RFC 6238 code of the test secret for a time step.
     */
    private static String totp(long step) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(SECRET, "HmacSHA1"));
        byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
        int offset = hash[hash.length - 1] & 0x0f;
        int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16) | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
        return String.format("%06d", binary % 1_000_000);
    }
}
