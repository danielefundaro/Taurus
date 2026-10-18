package com.fundaro.zodiac.taurus.service.scoreplayback.omr;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.service.scoreplayback.omr.OmrDtos.PageImage;
import com.fundaro.zodiac.taurus.service.scoreplayback.omr.OmrDtos.RecognitionResponse;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OmrClientTest {

    private HttpServer server;
    private final AtomicReference<String> response = new AtomicReference<>();
    private final AtomicReference<Integer> status = new AtomicReference<>(200);
    private final AtomicReference<String> key = new AtomicReference<>();
    private final AtomicReference<String> length = new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            key.set(exchange.getRequestHeaders().getFirst(OmrClient.API_KEY_HEADER));
            length.set(exchange.getRequestHeaders().getFirst("Content-Length"));
            byte[] body = response.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status.get(), body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void sendsThePagesWithTheKeyAndTheirLengthAndReadsTheResults() {
        response.set("""
            {"results":[{"engine":"AUDIVERIS","version":"5.11.0","status":"DONE","errorCode":null,"durationMillis":1500,
            "musicXml":"UEsD","measureCount":34,"layout":[{"measure":1,"page":1,"x":0.1,"y":0.2,"width":0.3,"height":0.05,"approximate":false}],
            "unknown":"ignored"}]}
            """);

        RecognitionResponse result = client("http://127.0.0.1:" + server.getAddress().getPort() + "/").recognize(
            List.of(new PageImage("page-1.png", "image/png", new byte[] { 1, 2, 3 }))
        );

        assertThat(key.get()).isEqualTo("service-key-0123456789");
        assertThat(Long.parseLong(length.get())).isPositive();
        assertThat(result.results()).singleElement().satisfies(engine -> {
            assertThat(engine.succeeded()).isTrue();
            assertThat(engine.measureCount()).isEqualTo(34);
            assertThat(engine.layout()).singleElement().satisfies(box -> assertThat(box.width()).isEqualTo(0.3));
        });
    }

    @Test
    void keepsTheCodesThatDescribeTheInput() {
        status.set(422);
        response.set("{\"code\":\"tooManyPages\",\"message\":\"at most 30 pages\"}");

        assertThatThrownBy(() -> client(url()).recognize(List.of(new PageImage("p.png", "image/png", new byte[] { 1 }))))
            .isInstanceOfSatisfying(OmrException.class, exception -> {
                assertThat(exception.getCode()).isEqualTo("tooManyPages");
                assertThat(exception.isUnavailable()).isFalse();
            });
    }

    @Test
    void reportsEverythingElseAsUnavailable() throws IOException {
        status.set(401);
        response.set("{\"code\":\"unauthorized\"}");
        assertThatThrownBy(() -> client(url()).convertMidi("brano.mid", new byte[] { 1 }))
            .isInstanceOfSatisfying(OmrException.class, exception -> assertThat(exception.isUnavailable()).isTrue());

        status.set(503);
        response.set("{\"code\":\"busy\"}");
        assertThatThrownBy(() -> client(url()).convertMidi("brano.mid", new byte[] { 1 }))
            .isInstanceOfSatisfying(OmrException.class, exception -> assertThat(exception.isUnavailable()).isTrue());

        int closedPort;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            closedPort = socket.getLocalPort();
        }
        assertThatThrownBy(() -> client("http://127.0.0.1:" + closedPort).convertMidi("brano.mid", new byte[] { 1 }))
            .isInstanceOfSatisfying(OmrException.class, exception -> assertThat(exception.getCode()).isEqualTo(OmrException.UNAVAILABLE));

        OmrClient unconfigured = client("");
        assertThat(unconfigured.isConfigured()).isFalse();
        assertThatThrownBy(() -> unconfigured.convertMidi("brano.mid", new byte[] { 1 })).isInstanceOf(OmrException.class);
    }

    private String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static OmrClient client(String url) {
        ApplicationProperties properties = new ApplicationProperties();
        properties.getScorePlayback().getOmr().setUrl(url);
        properties.getScorePlayback().getOmr().setApiKey("service-key-0123456789");
        return new OmrClient(properties, new ObjectMapper());
    }
}
