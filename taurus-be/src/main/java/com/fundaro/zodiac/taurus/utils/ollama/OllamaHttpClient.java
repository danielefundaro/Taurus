package com.fundaro.zodiac.taurus.utils.ollama;

import com.fundaro.zodiac.taurus.utils.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.lang.NonNull;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

public class OllamaHttpClient {

    private final Logger log;

    private final String protocol;

    private final String host;

    private final Integer port;

    private final String modelName;

    private final RestClient restClient;

    public OllamaHttpClient(@NonNull String protocol, @NonNull String host, @NonNull Integer port, @NonNull String modelName) {
        this.protocol = protocol;
        this.host = host;
        this.port = port;
        this.modelName = modelName;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofHours(1));
        requestFactory.setReadTimeout(Duration.ofHours(1));
        restClient = RestClient.builder()
            .baseUrl(getBaseUrl())
            .requestFactory(requestFactory)
            .build();

        this.log = LoggerFactory.getLogger(OllamaHttpClient.class);
    }

    public OllamaHttpClient(@NonNull String protocol, @NonNull String host, @NonNull Integer port) {
        this(protocol, host, port, "gemma3:12b");
    }

    public OllamaHttpClient(@NonNull String host, @NonNull String modelName) {
        this("http", host, 80, modelName);
    }

    public OllamaHttpClient(@NonNull String host) {
        this(host, "gemma3:12b");
    }

    public OllamaHttpClient() {
        this("localhost");
    }

    public ResponseEntity<Map<String, Object>> post(String prompt, Iterable<String> filesPath) throws IOException {
        log.debug("Rest POST request to analyze images");
        Payload payload = new Payload(modelName, prompt);

        for (String filePath : filesPath) {
            payload.getImages().add(Converter.imageEncodeBase64(filePath));
        }

        ParameterizedTypeReference<Map<String, Object>> parameterizedTypeReference = new ParameterizedTypeReference<>() {
        };
        ResponseEntity<Map<String, Object>> response = restClient.post()
            .uri("api/generate")
            .body(payload)
            .retrieve()
            .toEntity(parameterizedTypeReference);
        log.debug(response.getStatusCode().toString());
        log.debug(Objects.requireNonNull(response.getBody()).toString());

        return response;
    }

    private String getBaseUrl() {
        String s = "";

        if (protocol.equalsIgnoreCase("http") && port != 80 || protocol.equalsIgnoreCase("https") && port != 443) {
            s = String.format(":%s", port);
        }

        return String.format("%s://%s%s", protocol, host, s);
    }
}
