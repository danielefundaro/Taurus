package com.fundaro.zodiac.taurus.service.scoreplayback.omr;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.config.ApplicationProperties.OmrProperties;
import com.fundaro.zodiac.taurus.service.scoreplayback.omr.OmrDtos.PageImage;
import com.fundaro.zodiac.taurus.service.scoreplayback.omr.OmrDtos.RecognitionResponse;
import com.fundaro.zodiac.taurus.service.scoreplayback.omr.OmrDtos.TextRecognitionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

/**
 * Client of the internal optical music recognition service, used by score playback and by the text reading of uploaded
 * PDFs. The body is buffered, so every request carries its length as the service requires. Recognitions and text
 * readings have their own response timeout: a recognition can last minutes, a text reading a few seconds.
 */
@Component
public class OmrClient {

    public static final String API_KEY_HEADER = "X-Taurus-Omr-Key";

    private static final Logger log = LoggerFactory.getLogger(OmrClient.class);

    private final OmrProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient client;
    private final RestClient textClient;

    public OmrClient(ApplicationProperties applicationProperties, ObjectMapper objectMapper) {
        this.properties = applicationProperties.getOmr();
        this.objectMapper = objectMapper;
        // HTTP/1.1: the service is a plain HTTP server on the internal network, an h2c upgrade attempt is pointless.
        HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(properties.getConnectTimeout())
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
        this.client = client(httpClient, applicationProperties.getScorePlayback().getOmr().getTimeout());
        this.textClient = client(httpClient, applicationProperties.getPdfProcessing().getOcr().getTimeout());
        if (!isConfigured()) {
            log.info("OMR service not configured (application.omr.url): recognition, MIDI upload and text reading are not offered");
        }
    }

    private static RestClient client(HttpClient httpClient, Duration readTimeout) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder().requestFactory(new BufferingClientHttpRequestFactory(requestFactory)).build();
    }

    public boolean isConfigured() {
        return properties.isConfigured();
    }

    /** Reads the pages with both engines; returns when both have finished. */
    public RecognitionResponse recognize(List<PageImage> pages) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        for (PageImage page : pages) {
            body.add("pages", part(page.fileName(), page.mimeType(), page.bytes()));
        }
        return call(() -> client.post()
            .uri(endpoint("/v1/recognitions"))
            .header(API_KEY_HEADER, properties.getApiKey())
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(body)
            .retrieve()
            .onStatus(HttpStatusCode::isError, (request, response) -> {
                throw rejected(response);
            })
            .body(RecognitionResponse.class));
    }

    /**
     * Reads the text of the pages: every line with its confidence and its position as fractions of the page, in the
     * order of the pages. The service applies no threshold.
     */
    public TextRecognitionResponse readText(List<PageImage> pages) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        for (PageImage page : pages) {
            body.add("pages", part(page.fileName(), page.mimeType(), page.bytes()));
        }
        return call(() -> textClient.post()
            .uri(endpoint("/v1/text-recognitions"))
            .header(API_KEY_HEADER, properties.getApiKey())
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(body)
            .retrieve()
            .onStatus(HttpStatusCode::isError, (request, response) -> {
                throw rejected(response);
            })
            .body(TextRecognitionResponse.class));
    }

    /** Converts a MIDI file into MusicXML, one part per track. */
    public byte[] convertMidi(String fileName, byte[] midi) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", part(fileName, "audio/midi", midi));
        return call(() -> client.post()
            .uri(endpoint("/v1/midi-conversions"))
            .header(API_KEY_HEADER, properties.getApiKey())
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(body)
            .retrieve()
            .onStatus(HttpStatusCode::isError, (request, response) -> {
                throw rejected(response);
            })
            .body(byte[].class));
    }

    private <T> T call(Supplier<T> request) {
        if (!isConfigured()) throw OmrException.unavailable("the service is not configured");
        try {
            T result = request.get();
            if (result == null) throw OmrException.unavailable("empty response");
            return result;
        } catch (OmrException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            log.warn("OMR service not reachable: {}", exception.getMessage());
            throw OmrException.unavailable("service not reachable");
        } catch (RestClientException exception) {
            log.warn("OMR service answered unexpectedly: {}", exception.getMessage());
            throw OmrException.unavailable("unexpected response");
        }
    }

    private String endpoint(String path) {
        String base = properties.getUrl().endsWith("/") ? properties.getUrl().substring(0, properties.getUrl().length() - 1) : properties.getUrl();
        return base + path;
    }

    /** A refused request: the service code is kept when it describes the input, anything else means unavailable. */
    private OmrException rejected(ClientHttpResponse response) throws IOException {
        int status = response.getStatusCode().value();
        String code = null;
        try {
            JsonNode body = objectMapper.readTree(response.getBody());
            code = body.path("code").asString(null);
        } catch (IOException exception) {
            log.debug("OMR error without a readable body", exception);
        }
        if (status == 401) {
            log.error("OMR service refused the key: check application.omr.api-key");
            return OmrException.unavailable("key refused");
        }
        if (status >= 400 && status < 500 && code != null && OmrException.INPUT_CODES.contains(code)) {
            return OmrException.rejected(code);
        }
        log.warn("OMR service answered {} {}", status, code);
        return OmrException.unavailable("status " + status);
    }

    private static HttpEntity<ByteArrayResource> part(String fileName, String mimeType, byte[] bytes) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(mimeType == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : mimeType));
        ByteArrayResource resource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };
        return new HttpEntity<>(resource, headers);
    }
}
