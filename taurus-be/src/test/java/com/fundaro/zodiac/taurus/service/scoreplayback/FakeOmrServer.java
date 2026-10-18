package com.fundaro.zodiac.taurus.service.scoreplayback;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** A local stand-in for the OMR service: canned answers per path and a record of the requests received. */
final class FakeOmrServer implements AutoCloseable {

    record Answer(int status, String contentType, byte[] body) {}

    record Received(String path, String key, String contentLength, int bodySize) {}

    private final HttpServer server;
    private final Map<String, Answer> answers = new ConcurrentHashMap<>();
    private final List<Received> received = new CopyOnWriteArrayList<>();

    FakeOmrServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    void answer(String path, int status, String contentType, String body) {
        answers.put(path, new Answer(status, contentType, body.getBytes(StandardCharsets.UTF_8)));
    }

    List<Received> received() {
        return received;
    }

    void reset() {
        answers.clear();
        received.clear();
    }

    /** An engine result as returned by the service, with the score compressed and encoded in Base64. */
    static String engine(String engine, String status, String errorCode, String musicXml, String layout) {
        String encoded = musicXml == null ? "null" : '"' + Base64.getEncoder().encodeToString(compressed(musicXml)) + '"';
        return """
            {"engine":"%s","version":"1","status":"%s","errorCode":%s,"durationMillis":1200,"musicXml":%s,"measureCount":2,"layout":%s}
            """.formatted(engine, status, errorCode == null ? "null" : '"' + errorCode + '"', encoded, layout == null ? "null" : layout);
    }

    static String results(String... engines) {
        return "{\"results\":[" + String.join(",", engines) + "]}";
    }

    static byte[] compressed(String musicXml) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("META-INF/container.xml"));
            zip.write("<container><rootfiles><rootfile full-path=\"score.musicxml\"/></rootfiles></container>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("score.musicxml"));
            zip.write(musicXml.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.finish();
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        byte[] body = exchange.getRequestBody().readAllBytes();
        received.add(new Received(
            exchange.getRequestURI().getPath(),
            exchange.getRequestHeaders().getFirst("X-Taurus-Omr-Key"),
            exchange.getRequestHeaders().getFirst("Content-Length"),
            body.length
        ));
        Answer answer = answers.getOrDefault(exchange.getRequestURI().getPath(), new Answer(404, "application/json", "{\"code\":\"notFound\"}".getBytes(StandardCharsets.UTF_8)));
        exchange.getResponseHeaders().add("Content-Type", answer.contentType());
        exchange.sendResponseHeaders(answer.status(), answer.body().length);
        exchange.getResponseBody().write(answer.body());
        exchange.close();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
