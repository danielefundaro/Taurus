package com.fundaro.zodiac.taurus.service.signature;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakOtpClientTest {

    private HttpServer server;
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private final AtomicInteger tokens = new AtomicInteger();
    private volatile int status = 200;
    private volatile String body = "{\"configured\":true}";
    private volatile int forbiddenAnswers;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/realms/taurus/protocol/openid-connect/token", exchange -> {
            String form = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add("token " + form);
            respond(exchange, 200, "{\"access_token\":\"token-" + tokens.incrementAndGet() + "\",\"expires_in\":300,\"token_type\":\"Bearer\"}");
        });
        server.createContext("/realms/taurus/taurus-otp/users/", exchange -> {
            String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath() + " " + exchange.getRequestHeaders().getFirst("Authorization") + " " + request);
            if (forbiddenAnswers > 0) {
                forbiddenAnswers--;
                respond(exchange, 403, "{\"error\":\"forbidden\"}");
                return;
            }
            respond(exchange, status, status == 204 ? null : body);
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void offersNothingWithoutTheClientSecret() {
        KeycloakOtpClient client = client(baseUrl(), null);

        assertThat(client.configured()).isFalse();
        assertThat(client.status("user-1")).isEqualTo(KeycloakOtpClient.Status.UNAVAILABLE);
        assertThat(client.verify("user-1", "123456")).isEqualTo(KeycloakOtpClient.Verification.UNAVAILABLE);
        assertThat(requests).isEmpty();
    }

    @Test
    void readsTheStatusWithTheTokenOfTheConfidentialClient() {
        KeycloakOtpClient client = client(baseUrl(), "client-secret");

        assertThat(client.status("user-1")).isEqualTo(KeycloakOtpClient.Status.CONFIGURED);
        body = "{\"configured\":false}";
        assertThat(client.status("user-1")).isEqualTo(KeycloakOtpClient.Status.NOT_CONFIGURED);
        status = 404;
        body = "{\"error\":\"user_not_found\"}";
        assertThat(client.status("user-2")).isEqualTo(KeycloakOtpClient.Status.NOT_CONFIGURED);
        status = 500;
        assertThat(client.status("user-1")).isEqualTo(KeycloakOtpClient.Status.UNAVAILABLE);

        assertThat(requests.get(0)).isEqualTo("token grant_type=client_credentials&client_id=taurus-signature&client_secret=client-secret");
        assertThat(requests.get(1)).isEqualTo("GET /realms/taurus/taurus-otp/users/user-1/status Bearer token-1 ");
        assertThat(tokens).hasValue(1);
    }

    @Test
    void verifiesTheCodeOfTheUser() {
        KeycloakOtpClient client = client(baseUrl() + "/", "client-secret");

        status = 204;
        assertThat(client.verify("user-1", "123456")).isEqualTo(KeycloakOtpClient.Verification.VALID);
        status = 422;
        body = "{\"error\":\"invalid_code\"}";
        assertThat(client.verify("user-1", "123456")).isEqualTo(KeycloakOtpClient.Verification.INVALID);
        status = 404;
        assertThat(client.verify("user-1", "123456")).isEqualTo(KeycloakOtpClient.Verification.INVALID);
        status = 502;
        assertThat(client.verify("user-1", "123456")).isEqualTo(KeycloakOtpClient.Verification.UNAVAILABLE);

        assertThat(requests.get(1)).isEqualTo("POST /realms/taurus/taurus-otp/users/user-1/verify Bearer token-1 {\"code\":\"123456\"}");
    }

    @Test
    void renewsTheTokenOnceWhenKeycloakRefusesIt() {
        KeycloakOtpClient client = client(baseUrl(), "client-secret");
        assertThat(client.status("user-1")).isEqualTo(KeycloakOtpClient.Status.CONFIGURED);

        forbiddenAnswers = 1;
        assertThat(client.status("user-1")).isEqualTo(KeycloakOtpClient.Status.CONFIGURED);
        assertThat(tokens).hasValue(2);

        forbiddenAnswers = 2;
        assertThat(client.status("user-1")).isEqualTo(KeycloakOtpClient.Status.UNAVAILABLE);
        assertThat(tokens).hasValue(3);
    }

    @Test
    void reportsKeycloakUnavailableWhenItDoesNotAnswer() throws IOException {
        int port;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            port = socket.getLocalPort();
        }
        KeycloakOtpClient client = client("http://127.0.0.1:" + port + "/realms/taurus", "client-secret");

        assertThat(client.status("user-1")).isEqualTo(KeycloakOtpClient.Status.UNAVAILABLE);
        assertThat(client.verify("user-1", "123456")).isEqualTo(KeycloakOtpClient.Verification.UNAVAILABLE);
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/realms/taurus";
    }

    private static KeycloakOtpClient client(String realmUri, String secret) {
        ApplicationProperties properties = new ApplicationProperties();
        properties.getSignature().getKeycloak().setRealmUri(realmUri);
        properties.getSignature().getKeycloak().setClientSecret(secret);
        properties.getSignature().getKeycloak().setTimeout(Duration.ofSeconds(2));
        return new KeycloakOtpClient(properties);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        if (body == null) {
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
            return;
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
