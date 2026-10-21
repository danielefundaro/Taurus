package com.fundaro.zodiac.taurus.service.signature;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.config.ApplicationProperties.SignatureKeycloakProperties;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Client of the {@code taurus-otp} resource of Keycloak, which checks the codes of the authentication app. The backend
 * authenticates with the client credentials of the confidential signature client and always passes the subject of the
 * authenticated signer. Codes and tokens are never logged.
 */
@Component
public class KeycloakOtpClient {

    /**
     * Authentication app of a user, or {@code UNAVAILABLE} when the installation does not offer it or Keycloak does not
     * answer.
     */
    public enum Status {
        CONFIGURED,
        NOT_CONFIGURED,
        UNAVAILABLE
    }

    public enum Verification {
        VALID,
        INVALID,
        UNAVAILABLE
    }

    private static final Logger LOG = LoggerFactory.getLogger(KeycloakOtpClient.class);
    private static final Duration TOKEN_MARGIN = Duration.ofSeconds(30);
    private static final int FORBIDDEN = 403;

    private final SignatureKeycloakProperties properties;
    private final RestClient client;
    private final Object tokenLock = new Object();
    private String accessToken;
    private Instant tokenExpiresAt = Instant.EPOCH;

    public KeycloakOtpClient(ApplicationProperties applicationProperties) {
        this.properties = applicationProperties.getSignature().getKeycloak();
        HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(properties.getTimeout())
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getTimeout());
        this.client = RestClient.builder().requestFactory(requestFactory).build();
    }

    /**
     * Whether the installation offers the authentication app.
     */
    public boolean configured() {
        return properties.isConfigured();
    }

    public Status status(String userId) {
        if (!configured()) return Status.UNAVAILABLE;
        Response<StatusBody> response = call(token -> client.get()
            .uri(endpoint("status"), Objects.requireNonNull(userId, "userId"))
            .header("Authorization", "Bearer " + token)
            .accept(MediaType.APPLICATION_JSON)
            .exchange((request, result) -> {
                int status = result.getStatusCode().value();
                return new Response<>(status, status == 200 ? result.bodyTo(StatusBody.class) : null);
            }));
        if (response == null) return Status.UNAVAILABLE;
        if (response.status() == 200 && response.body() != null) return response.body().configured() ? Status.CONFIGURED : Status.NOT_CONFIGURED;
        if (response.status() == 404) return Status.NOT_CONFIGURED;
        LOG.error("signature_totp_unavailable operation=status status={}", response.status());
        return Status.UNAVAILABLE;
    }

    /**
     * Checks a code of the user's authentication app; Keycloak refuses a code already used.
     */
    public Verification verify(String userId, String code) {
        if (!configured()) return Verification.UNAVAILABLE;
        Response<Void> response = call(token -> client.post()
            .uri(endpoint("verify"), Objects.requireNonNull(userId, "userId"))
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("code", code))
            .exchange((request, result) -> new Response<Void>(result.getStatusCode().value(), null)));
        if (response == null) return Verification.UNAVAILABLE;
        return switch (response.status()) {
            case 204 -> Verification.VALID;
            case 404, 422 -> Verification.INVALID;
            default -> {
                LOG.error("signature_totp_unavailable operation=verify status={}", response.status());
                yield Verification.UNAVAILABLE;
            }
        };
    }

    /**
     * Calls Keycloak with the cached token, and once more with a new token when the resource refuses it: it may have
     * been revoked or Keycloak may have restarted. {@code null} when Keycloak does not answer.
     */
    private <T> Response<T> call(Call<T> call) {
        try {
            Response<T> response = call.with(token(false));
            if (response.status() != FORBIDDEN) return response;
            return call.with(token(true));
        } catch (RestClientException | IllegalStateException exception) {
            LOG.error("signature_totp_unavailable error={}", exception.getClass().getSimpleName());
            return null;
        }
    }

    private String token(boolean renew) {
        synchronized (tokenLock) {
            Instant now = Instant.now();
            if (!renew && accessToken != null && now.isBefore(tokenExpiresAt)) return accessToken;
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "client_credentials");
            form.add("client_id", properties.getClientId());
            form.add("client_secret", properties.getClientSecret());
            TokenBody body = client.post()
                .uri(stripSlash(properties.getRealmUri()) + "/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .body(form)
                .retrieve()
                .body(TokenBody.class);
            if (body == null || body.accessToken() == null) throw new IllegalStateException("Keycloak returned no access token");
            accessToken = body.accessToken();
            tokenExpiresAt = now.plusSeconds(Math.max(0, body.expiresIn())).minus(TOKEN_MARGIN);
            return accessToken;
        }
    }

    /**
     * URI template of an operation; the user id is expanded and encoded by the client.
     */
    private String endpoint(String operation) {
        return stripSlash(properties.getRealmUri()) + "/taurus-otp/users/{userId}/" + operation;
    }

    private static String stripSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    @FunctionalInterface
    private interface Call<T> {
        Response<T> with(String token);
    }

    private record Response<T>(int status, T body) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record StatusBody(boolean configured) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenBody(@JsonProperty("access_token") String accessToken, @JsonProperty("expires_in") long expiresIn) {
        @Override
        public String toString() {
            return "TokenBody[expiresIn=" + expiresIn + "]";
        }
    }
}
