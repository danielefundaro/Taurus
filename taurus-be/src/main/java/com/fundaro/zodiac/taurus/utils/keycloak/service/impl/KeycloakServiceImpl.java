package com.fundaro.zodiac.taurus.utils.keycloak.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.enumeration.RoleEnum;
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
            throw new RequestAlertException(HttpStatus.BAD_GATEWAY, "Error getting users from Keycloak", User.class.getSimpleName(), "keycloak.users.readFailed");
        }

        return response.getBody();
    }

    @Override
    public User getUser(String id) {
        String url = String.format("%s/users/%s", applicationProperties.getKeycloak().getAdmin().getIssuerUri(), id);
        ParameterizedTypeReference<User> typeRef = new ParameterizedTypeReference<>() {
        };
        ResponseEntity<User> response = responseEntity(url, HttpMethod.GET, getAdminHttpHeaders(), null, typeRef);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            log.error("Error getting user with id {} from Keycloak", id);
            throw new RequestAlertException(HttpStatus.BAD_GATEWAY, "Error getting user from Keycloak", User.class.getSimpleName(), "keycloak.user.readFailed");
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
            throw new RequestAlertException(HttpStatus.BAD_GATEWAY, String.format("Error getting user with username or email from Keycloak: %s, %s", username, email), User.class.getSimpleName(), "keycloak.user.readFailed");
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
            throw new RequestAlertException(HttpStatus.BAD_GATEWAY, "Error saving user on Keycloak", User.class.getSimpleName(), "keycloak.user.createFailed");
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
            throw new RequestAlertException(HttpStatus.BAD_GATEWAY, "Error updating user on Keycloak", User.class.getSimpleName(), "keycloak.user.updateFailed");
        }
    }

    @Override
    public void updateUserGroup(String userId, String groupId) {
        String url = String.format("%s/users/%s/groups/%s", applicationProperties.getKeycloak().getAdmin().getIssuerUri(), userId, groupId);
        ParameterizedTypeReference<Void> typeRef = new ParameterizedTypeReference<>() {
        };
        ResponseEntity<Void> response = responseEntity(url, HttpMethod.PUT, getAdminHttpHeaders(), null, typeRef);

        if (!response.getStatusCode().is2xxSuccessful()) {
            log.error("Error updating user group on Keycloak: {}, {}", userId, groupId);
            throw new RequestAlertException(HttpStatus.BAD_GATEWAY, "Error updating user group on Keycloak", User.class.getSimpleName(), "keycloak.userGroup.updateFailed");
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
            throw new RequestAlertException(HttpStatus.BAD_GATEWAY, "Error deleting user on Keycloak", User.class.getSimpleName(), "keycloak.user.deleteFailed");
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
            throw new RequestAlertException(HttpStatus.BAD_GATEWAY, "Error getting roles for user on Keycloak", User.class.getSimpleName(), "keycloak.userRoles.readFailed");
        }

        return response.getBody();
    }

    @Override
    public void saveUserRoles(String userId, List<Role> roles) {
        if (!updateUserRoles(userId, roles, HttpMethod.POST)) {
            throw new RequestAlertException(HttpStatus.BAD_GATEWAY, "Error saving roles for user on Keycloak", User.class.getSimpleName(), "keycloak.userRoles.assignFailed");
        }
    }

    @Override
    public void deleteUserRoles(String userId, List<Role> roles) {
        if (!updateUserRoles(userId, roles, HttpMethod.DELETE)) {
            throw new RequestAlertException(HttpStatus.BAD_GATEWAY, "Error deleting roles for user on Keycloak", User.class.getSimpleName(), "keycloak.userRoles.removeFailed");
        }
    }

    @Override
    public void sendResetPasswordEmail(String userId) {
        String url = String.format("%s/users/%s/reset-password-email", applicationProperties.getKeycloak().getAdmin().getIssuerUri(), userId);
        ParameterizedTypeReference<Void> typeRef = new ParameterizedTypeReference<>() {
        };
        ResponseEntity<Void> response = responseEntity(url, HttpMethod.PUT, getAdminHttpHeaders(), null, typeRef);

        if (!response.getStatusCode().is2xxSuccessful()) {
            log.error("Error sending reset password for user on Keycloak: {}", userId);
            throw new RequestAlertException(HttpStatus.BAD_GATEWAY, "Error sending reset password for user on Keycloak", User.class.getSimpleName(), "keycloak.email.resetPasswordFailed");
        }
    }

    @Override
    public void sendVerifyEmail(String userId) {
        String url = String.format("%s/users/%s/send-verify-email", applicationProperties.getKeycloak().getAdmin().getIssuerUri(), userId);
        ParameterizedTypeReference<Void> typeRef = new ParameterizedTypeReference<>() {
        };
        ResponseEntity<Void> response = responseEntity(url, HttpMethod.PUT, getAdminHttpHeaders(), null, typeRef);

        if (!response.getStatusCode().is2xxSuccessful()) {
            log.error("Error sending verify password for user on Keycloak: {}", userId);
            throw new RequestAlertException(HttpStatus.BAD_GATEWAY, "Error sending verify password for user on Keycloak", User.class.getSimpleName(), "keycloak.email.verifyAddressFailed");
        }
    }

    @Override
    public void sendExecuteActionsEmail(String userId, List<String> actions) {
        String url = String.format("%s/users/%s/execute-actions-email", applicationProperties.getKeycloak().getAdmin().getIssuerUri(), userId);
        ParameterizedTypeReference<Void> typeRef = new ParameterizedTypeReference<>() {
        };
        ResponseEntity<Void> response = responseEntity(url, HttpMethod.PUT, getAdminHttpHeaders(), actions, typeRef);

        if (!response.getStatusCode().is2xxSuccessful()) {
            log.error("Error sending execute actions email for user on Keycloak: {}", userId);
            throw new RequestAlertException(HttpStatus.BAD_GATEWAY, "Error sending execute actions email for user on Keycloak", User.class.getSimpleName(), "keycloak.email.setupFailed");
        }
    }

    @Override
    public void saveGroup(Group group) {
        String url = String.format("%s/groups", applicationProperties.getKeycloak().getAdmin().getIssuerUri());
        ParameterizedTypeReference<Group> typeRef = new ParameterizedTypeReference<>() {
        };
        ResponseEntity<Group> response = responseEntity(url, HttpMethod.POST, getAdminHttpHeaders(), group, typeRef);

        if (!response.getStatusCode().is2xxSuccessful()) {
            log.error("Error saving group on Keycloak: {}", group);
            throw new RequestAlertException(HttpStatus.BAD_GATEWAY, "Error saving group on Keycloak", Group.class.getSimpleName(), "keycloak.group.createFailed");

        }
    }

    @Override
    public void deleteUserGroup(String userId, String groupId) {
        String url = String.format("%s/users/%s/groups/%s", applicationProperties.getKeycloak().getAdmin().getIssuerUri(), userId, groupId);
        ParameterizedTypeReference<Void> typeRef = new ParameterizedTypeReference<>() {
        };
        ResponseEntity<Void> response = responseEntity(url, HttpMethod.DELETE, getAdminHttpHeaders(), null, typeRef);

        if (!response.getStatusCode().is2xxSuccessful()) {
            log.error("Error removing user {} from Keycloak group {}", userId, groupId);
            throw new RequestAlertException(HttpStatus.BAD_GATEWAY, "Error removing user from Keycloak group", Group.class.getSimpleName(), "keycloak.userGroup.deleteFailed");
        }
    }

    @Override
    public List<Group> getUserGroups(String userId) {
        String url = String.format("%s/users/%s/groups", applicationProperties.getKeycloak().getAdmin().getIssuerUri(), userId);
        ParameterizedTypeReference<List<Group>> typeRef = new ParameterizedTypeReference<>() {
        };
        ResponseEntity<List<Group>> response = responseEntity(url, HttpMethod.GET, getAdminHttpHeaders(), null, typeRef);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new RequestAlertException(HttpStatus.BAD_GATEWAY, "Error getting user groups from Keycloak", Group.class.getSimpleName(), "keycloak.userGroups.readFailed");
        }
        return response.getBody();
    }

    @Override
    public void deleteGroup(String groupId) {
        String url = String.format("%s/groups/%s", applicationProperties.getKeycloak().getAdmin().getIssuerUri(), groupId);
        ParameterizedTypeReference<Void> typeRef = new ParameterizedTypeReference<>() {
        };
        ResponseEntity<Void> response = responseEntity(url, HttpMethod.DELETE, getAdminHttpHeaders(), null, typeRef);

        if (!response.getStatusCode().is2xxSuccessful()) {
            log.error("Error deleting group from Keycloak: {}", groupId);
            throw new RequestAlertException(HttpStatus.BAD_GATEWAY, "Error deleting group from Keycloak", Group.class.getSimpleName(), "keycloak.group.deleteFailed");
        }
    }

    @Override
    public String getGroupIdByName(String name) {
        String url = String.format("%s/groups", applicationProperties.getKeycloak().getAdmin().getIssuerUri());
        ParameterizedTypeReference<List<Group>> typeRef = new ParameterizedTypeReference<>() {
        };
        ResponseEntity<List<Group>> response = responseEntity(url, HttpMethod.GET, getAdminHttpHeaders(), null, typeRef);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null || response.getBody().isEmpty()) {
            log.error("Error getting group with name from Keycloak: {}", name);
            throw new RequestAlertException(HttpStatus.BAD_GATEWAY, String.format("Error getting group with name from Keycloak: %s", name), Group.class.getSimpleName(), "keycloak.group.readFailed");
        }

        Group group = response.getBody().stream().filter(g -> g.getName().equals(name)).findFirst().orElse(null);

        if (group == null) {
            throw new RequestAlertException(HttpStatus.BAD_GATEWAY, String.format("Error getting group with name from Keycloak: %s", name), Group.class.getSimpleName(), "keycloak.group.readFailed");
        }

        return group.getId();

    }

    @Override
    public String getIdByClientId() {
        String url = String.format("%s/clients?clientId=%s", applicationProperties.getKeycloak().getAdmin().getIssuerUri(), clientId);
        ParameterizedTypeReference<List<Client>> typeRef = new ParameterizedTypeReference<>() {
        };
        ResponseEntity<List<Client>> response = responseEntity(url, HttpMethod.GET, getAdminHttpHeaders(), null, typeRef);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null || response.getBody().isEmpty()) {
            log.error("Error getting client with clientId from Keycloak: {}", clientId);
            throw new RequestAlertException(HttpStatus.BAD_GATEWAY, String.format("Error getting client with clientId from Keycloak: %s", clientId), Client.class.getSimpleName(), "keycloak.configuration.incomplete");
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
            throw new RequestAlertException(HttpStatus.BAD_GATEWAY, String.format("Error getting roles of client with clientId from Keycloak: %s", clientId), Client.class.getSimpleName(), "keycloak.configuration.incomplete");
        }

        return response.getBody();
    }

    @Override
    public void ensureClientRole(String roleName, String description) {
        if (getClientRoles().stream().anyMatch(role -> roleName.equals(role.getName()))) return;

        String id = getIdByClientId();
        String url = String.format("%s/clients/%s/roles", applicationProperties.getKeycloak().getAdmin().getIssuerUri(), id);
        Role role = new Role();
        role.setName(roleName);
        role.setDescription(description);
        role.setComposite(false);
        role.setClientRole(true);
        ParameterizedTypeReference<Void> typeRef = new ParameterizedTypeReference<>() {
        };
        ResponseEntity<Void> response = responseEntity(url, HttpMethod.POST, getAdminHttpHeaders(), role, typeRef);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RequestAlertException(HttpStatus.BAD_GATEWAY, "Error provisioning Keycloak client role", Role.class.getSimpleName(), "keycloak.configuration.incomplete");
        }
        log.info("Provisioned Keycloak client role {}", roleName);
    }

    @Override
    public List<User> getUsersByClientRoles(RoleEnum roleEnum) {
        String id = getIdByClientId();
        String url = String.format("%s/clients/%s/roles/%s/users", applicationProperties.getKeycloak().getAdmin().getIssuerUri(), id, roleEnum.toString());
        ParameterizedTypeReference<List<User>> typeRef = new ParameterizedTypeReference<>() {
        };
        ResponseEntity<List<User>> response = responseEntity(url, HttpMethod.GET, getAdminHttpHeaders(), null, typeRef);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            log.error("Error getting users by client role from Keycloak: {}", roleEnum);
            throw new RequestAlertException(HttpStatus.BAD_GATEWAY, String.format("Error getting users by client role from Keycloak: %s", roleEnum), Client.class.getSimpleName(), "keycloak.roleUsers.readFailed");
        }

        return response.getBody();
    }

    /**
     * Applies a change of the client roles of a user; false when Keycloak does not confirm it.
     */
    private boolean updateUserRoles(String userId, List<Role> roles, HttpMethod httpMethod) {
        String id = getIdByClientId();
        String url = String.format("%s/users/%s/role-mappings/clients/%s", applicationProperties.getKeycloak().getAdmin().getIssuerUri(), userId, id);
        ParameterizedTypeReference<Void> typeRef = new ParameterizedTypeReference<>() {
        };
        ResponseEntity<Void> response = responseEntity(url, httpMethod, getAdminHttpHeaders(), roles, typeRef);

        if (!response.getStatusCode().is2xxSuccessful()) {
            log.error("Error {} roles for user on Keycloak: {}", httpMethod, userId);
            return false;
        }
        return true;
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
            throw new RequestAlertException(HttpStatus.BAD_GATEWAY, "Token not acquired", AccessToken.class.getSimpleName(), "keycloak.authorizationFailed");
        }

        try {
            return objectMapper.readValue(response.getBody(), AccessToken.class);
        } catch (JsonProcessingException e) {
            throw new RequestAlertException(HttpStatus.BAD_GATEWAY, "Token not acquired", AccessToken.class.getSimpleName(), "keycloak.authorizationFailed");
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
                throw new RequestAlertException(HttpStatus.BAD_GATEWAY, e.getMessage(), AccessToken.class.getSimpleName(), "keycloak.authorizationFailed");
            }

            if (e.getStatusCode().isSameCodeAs(HttpStatus.FORBIDDEN)) {
                throw new RequestAlertException(HttpStatus.BAD_GATEWAY, e.getMessage(), AccessToken.class.getSimpleName(), "keycloak.authorizationFailed");
            }

            throw new RequestAlertException(HttpStatus.BAD_GATEWAY, e.getMessage(), AccessToken.class.getSimpleName(), "keycloak.authorizationFailed");
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
                throw new RequestAlertException(HttpStatus.CONFLICT, e.getMessage(), AccessToken.class.getSimpleName(), "keycloak.conflict");
            }

            throw new RequestAlertException(HttpStatus.BAD_GATEWAY, e.getStatusCode() + " " + e.getMessage(), AccessToken.class.getSimpleName(), "keycloak.requestFailed");
        }
    }
}
