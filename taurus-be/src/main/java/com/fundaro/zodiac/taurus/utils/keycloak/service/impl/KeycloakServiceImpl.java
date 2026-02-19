package com.fundaro.zodiac.taurus.utils.keycloak.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.utils.keycloak.domain.*;
import com.fundaro.zodiac.taurus.utils.keycloak.service.KeycloakService;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

@Service
public class KeycloakServiceImpl implements KeycloakService {

    private final ApplicationProperties applicationProperties;

    private final Logger log;

    private final RestClient restClient;

    private final ObjectMapper objectMapper;

    @Value("${spring.security.oauth2.client.registration.oidc.client-id}")
    private String clientId;

    public KeycloakServiceImpl(ApplicationProperties applicationProperties) {
        this.applicationProperties = applicationProperties;

        this.log = LoggerFactory.getLogger(KeycloakServiceImpl.class);
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMinutes(2));
        requestFactory.setReadTimeout(Duration.ofMinutes(2));
        restClient = RestClient.builder()
            .baseUrl("")
            .requestFactory(requestFactory)
            .build();
        this.objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public List<User> getUsers() {
        String url = String.format("%s/users", applicationProperties.getKeycloak().getAdmin().getIssuerUri());
        ParameterizedTypeReference<List<User>> typeRef = new ParameterizedTypeReference<>() {
        };
        ResponseEntity<List<User>> response = responseEntity(url, HttpMethod.GET, getAdminHttpHeaders(), null, typeRef);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            log.error("Error getting users from Keycloak");
            throw new RequestAlertException(HttpStatus.BAD_REQUEST, "Error getting users from Keycloak", User.class.getSimpleName(), "users.list");
        }

        return response.getBody();
    }

    @Override
    public User getUser(String id) {
        String url = String.format("%s/user/%s", applicationProperties.getKeycloak().getAdmin().getIssuerUri(), id);
        ParameterizedTypeReference<User> typeRef = new ParameterizedTypeReference<>() {
        };
        ResponseEntity<User> response = responseEntity(url, HttpMethod.GET, getAdminHttpHeaders(), null, typeRef);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            log.error("Error getting user with id {} from Keycloak", id);
            throw new RequestAlertException(HttpStatus.BAD_REQUEST, "Error getting user from Keycloak", User.class.getSimpleName(), "get.user");
        }

        return response.getBody();
    }

    @Override
    public String getUserIdByUsernameOrEmail(String username, String email) {
        String url = String.format("%s/users?username=%s&email=%s&exact=true", applicationProperties.getKeycloak().getAdmin().getIssuerUri(), username, email);
        ParameterizedTypeReference<List<User>> typeRef = new ParameterizedTypeReference<>() {
        };
        ResponseEntity<List<User>> response = responseEntity(url, HttpMethod.GET, getAdminHttpHeaders(), null, typeRef);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null || response.getBody().isEmpty()) {
            log.error("Error getting user with username or email from Keycloak: {}, {}", username, email);
            throw new RequestAlertException(HttpStatus.BAD_REQUEST, String.format("Error getting user with username or email from Keycloak: %s, %s", username, email), User.class.getSimpleName(), "get.user");
        }

        return response.getBody().get(0).getId();
    }

    @Override
    public void saveUser(User user) {
        String url = String.format("%s/users", applicationProperties.getKeycloak().getAdmin().getIssuerUri());
        ParameterizedTypeReference<User> typeRef = new ParameterizedTypeReference<>() {
        };
        ResponseEntity<User> response = responseEntity(url, HttpMethod.POST, getAdminHttpHeaders(), user, typeRef);

        if (!response.getStatusCode().is2xxSuccessful()) {
            log.error("Error saving user on Keycloak: {}", user);
            throw new RequestAlertException(HttpStatus.BAD_REQUEST, "Error saving user on Keycloak", User.class.getSimpleName(), "save.user");
        }
    }

    @Override
    public void updateUser(User user) {
        String url = String.format("%s/users/%s", applicationProperties.getKeycloak().getAdmin().getIssuerUri(), user.getId());
        ParameterizedTypeReference<Void> typeRef = new ParameterizedTypeReference<>() {
        };
        ResponseEntity<Void> response = responseEntity(url, HttpMethod.PUT, getAdminHttpHeaders(), user, typeRef);

        if (!response.getStatusCode().is2xxSuccessful()) {
            log.error("Error updating user on Keycloak: {}", user);
            throw new RequestAlertException(HttpStatus.BAD_REQUEST, "Error updating user on Keycloak", User.class.getSimpleName(), "update.user");
        }
    }

    @Override
    public void deleteUser(String userId) {
        String url = String.format("%s/users/%s", applicationProperties.getKeycloak().getAdmin().getIssuerUri(), userId);
        ParameterizedTypeReference<Void> typeRef = new ParameterizedTypeReference<>() {
        };
        ResponseEntity<Void> response = responseEntity(url, HttpMethod.DELETE, getAdminHttpHeaders(), null, typeRef);

        if (!response.getStatusCode().is2xxSuccessful()) {
            log.error("Error deleting user on Keycloak: {}", userId);
            throw new RequestAlertException(HttpStatus.BAD_REQUEST, "Error deleting user on Keycloak", User.class.getSimpleName(), "delete.user");
        }
    }

    @Override
    public List<Role> getUserRoles(String userId) {
        String id = getIdByClientId();
        String url = String.format("%s/users/%s/role-mappings/clients/%s", applicationProperties.getKeycloak().getAdmin().getIssuerUri(), userId, id);
        ParameterizedTypeReference<List<Role>> typeRef = new ParameterizedTypeReference<>() {
        };
        ResponseEntity<List<Role>> response = responseEntity(url, HttpMethod.GET, getAdminHttpHeaders(), null, typeRef);

        if (!response.getStatusCode().is2xxSuccessful()) {
            log.error("Error getting roles for user on Keycloak: {}", userId);
            throw new RequestAlertException(HttpStatus.BAD_REQUEST, "Error getting roles for user on Keycloak", User.class.getSimpleName(), "get.user.roles");
        }

        return response.getBody();
    }

    @Override
    public void saveUserRoles(String userId, List<Role> roles) {
        handleUserRoles(userId, roles, HttpMethod.POST, "saving");
    }

    @Override
    public void deleteUserRoles(String userId, List<Role> roles) {
        handleUserRoles(userId, roles, HttpMethod.DELETE, "deleting");
    }

    @Override
    public void saveGroup(Group group) {
        String url = String.format("%s/groups", applicationProperties.getKeycloak().getAdmin().getIssuerUri());
        ParameterizedTypeReference<Group> typeRef = new ParameterizedTypeReference<>() {
        };
        ResponseEntity<Group> response = responseEntity(url, HttpMethod.POST, getAdminHttpHeaders(), group, typeRef);

        if (!response.getStatusCode().is2xxSuccessful()) {
            log.error("Error saving group on Keycloak: {}", group);
            throw new RequestAlertException(HttpStatus.BAD_REQUEST, "Error saving group on Keycloak", Group.class.getSimpleName(), "save.group");

        }
    }

    @Override
    public String getIdByClientId() {
        String url = String.format("%s/clients?clientId=%s", applicationProperties.getKeycloak().getAdmin().getIssuerUri(), clientId);
        ParameterizedTypeReference<List<Client>> typeRef = new ParameterizedTypeReference<>() {
        };
        ResponseEntity<List<Client>> response = responseEntity(url, HttpMethod.GET, getAdminHttpHeaders(), null, typeRef);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null || response.getBody().isEmpty()) {
            log.error("Error getting client with clientId from Keycloak: {}", clientId);
            throw new RequestAlertException(HttpStatus.BAD_REQUEST, String.format("Error getting client with clientId from Keycloak: %s", clientId), Client.class.getSimpleName(), "get.client");
        }

        return response.getBody().get(0).getId();
    }

    @Override
    public List<Role> getClientRoles() {
        String id = getIdByClientId();
        String url = String.format("%s/clients/%s/roles", applicationProperties.getKeycloak().getAdmin().getIssuerUri(), id);
        ParameterizedTypeReference<List<Role>> typeRef = new ParameterizedTypeReference<>() {
        };
        ResponseEntity<List<Role>> response = responseEntity(url, HttpMethod.GET, getAdminHttpHeaders(), null, typeRef);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null || response.getBody().isEmpty()) {
            log.error("Error getting roles of client with clientId from Keycloak: {}", clientId);
            throw new RequestAlertException(HttpStatus.BAD_REQUEST, String.format("Error getting roles of client with clientId from Keycloak: %s", clientId), Client.class.getSimpleName(), "get.client.roles");
        }

        return response.getBody();
    }

    private void handleUserRoles(String userId, List<Role> roles, HttpMethod httpMethod, String action) {
        String id = getIdByClientId();
        String url = String.format("%s/users/%s/role-mappings/clients/%s", applicationProperties.getKeycloak().getAdmin().getIssuerUri(), userId, id);
        ParameterizedTypeReference<Void> typeRef = new ParameterizedTypeReference<>() {
        };
        ResponseEntity<Void> response = responseEntity(url, httpMethod, getAdminHttpHeaders(), roles, typeRef);

        if (!response.getStatusCode().is2xxSuccessful()) {
            log.error("Error {} roles for user on Keycloak: {}", action, userId);
            throw new RequestAlertException(HttpStatus.BAD_REQUEST, String.format("Error %s roles for user on Keycloak", action), User.class.getSimpleName(), "save.user.roles");
        }
    }

    private HttpHeaders getAdminHttpHeaders() {
        ApplicationProperties.Keycloak keycloakProperties = applicationProperties.getKeycloak();
        String url = String.format("%s/protocol/openid-connect/token", keycloakProperties.getMasterUri());
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();

        form.add("client_id", "admin-cli");
        form.add("username", keycloakProperties.getAdmin().getUsername());
        form.add("password", keycloakProperties.getAdmin().getPassword());
        form.add("grant_type", "password");

        AccessToken adminToken = getAccessToken(url, form);
        log.info("Successfully logged in to Keycloak for the user {}", keycloakProperties.getAdmin().getUsername());

        HttpHeaders httpHeaders = new HttpHeaders();

        httpHeaders.setBearerAuth(adminToken.getToken());
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        return httpHeaders;
    }

    private AccessToken getAccessToken(String url, MultiValueMap<String, String> form) {
        ResponseEntity<String> response = postFormEntity(url, new HttpHeaders(), form);

        if (!response.getStatusCode().is2xxSuccessful() || !response.hasBody()) {
            throw new RequestAlertException(HttpStatus.BAD_REQUEST, "Token not acquired", AccessToken.class.getSimpleName(), "token.error");
        }

        try {
            return objectMapper.readValue(response.getBody(), AccessToken.class);
        } catch (JsonProcessingException e) {
            throw new RequestAlertException(HttpStatus.BAD_REQUEST, "Token not acquired", AccessToken.class.getSimpleName(), "token.error");
        }
    }

    private ResponseEntity<String> postFormEntity(String url, @NotNull HttpHeaders httpHeaders, MultiValueMap<String, String> form) {
        httpHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        try {
            ParameterizedTypeReference<String> typeRef = new ParameterizedTypeReference<>() {
            };
            return responseEntity(url, HttpMethod.POST, httpHeaders, form, typeRef);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().isSameCodeAs(HttpStatus.UNAUTHORIZED)) {
                throw new RequestAlertException(HttpStatus.UNAUTHORIZED, e.getMessage(), AccessToken.class.getSimpleName(), "credentials.error");
            }

            if (e.getStatusCode().isSameCodeAs(HttpStatus.FORBIDDEN)) {
                throw new RequestAlertException(HttpStatus.FORBIDDEN, e.getMessage(), AccessToken.class.getSimpleName(), "credentials.error");
            }

            throw new RequestAlertException(HttpStatus.BAD_REQUEST, e.getMessage(), AccessToken.class.getSimpleName(), "token.invalid");
        }
    }

    private <T> ResponseEntity<T> responseEntity(String url, HttpMethod httpMethod, HttpHeaders httpHeaders, Object body, ParameterizedTypeReference<T> responseType) throws HttpClientErrorException {
        RestClient.RequestBodySpec requestBodySpec = restClient
            .method(httpMethod)
            .uri(url);

        if (Objects.nonNull(httpHeaders)) {
            requestBodySpec.headers(h -> {
                h.setContentType(httpHeaders.getContentType());

                if (httpHeaders.containsKey("Authorization") && Objects.nonNull(httpHeaders.getFirst("Authorization"))) {
                    h.set("Authorization", Objects.requireNonNull(httpHeaders.getFirst("Authorization")));
                }
            });
        }

        if (Objects.nonNull(body)) {
            requestBodySpec.body(body);
        }

        try {
            return requestBodySpec
                .retrieve()
                .toEntity(responseType);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().isSameCodeAs(HttpStatus.CONFLICT)) {
                throw new RequestAlertException(HttpStatus.CONFLICT, e.getMessage(), AccessToken.class.getSimpleName(), "conflict.error");
            }

            throw new RequestAlertException(HttpStatus.valueOf(e.getStatusCode().value()), e.getMessage(), AccessToken.class.getSimpleName(), "generic.error");
        }
    }
}
