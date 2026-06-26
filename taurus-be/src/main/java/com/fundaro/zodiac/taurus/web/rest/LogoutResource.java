package com.fundaro.zodiac.taurus.web.rest;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for managing global OIDC logout.
 */
@RestController
public class LogoutResource {

    private final ReactiveClientRegistrationRepository registrations;

    public LogoutResource(ReactiveClientRegistrationRepository registrations) {
        this.registrations = registrations;
    }

    /**
     * {@code POST  /api/logout} : logout the current user.
     *
     * @param idToken the ID token.
     * @param request a {@link HttpServletRequest} request.
     * @return status {@code 200 (OK)} and a body with a global logout URL.
     */
    @PostMapping("/api/logout")
    public Map<String, String> logout(
        @AuthenticationPrincipal(expression = "idToken") OidcIdToken idToken,
        HttpServletRequest request
    ) {
        ClientRegistration registration = registrations.findByRegistrationId("oidc").block();
        return prepareLogoutUri(request, registration, idToken);
    }

    private Map<String, String> prepareLogoutUri(HttpServletRequest request, ClientRegistration clientRegistration, OidcIdToken idToken) {
        StringBuilder logoutUrl = new StringBuilder();

        logoutUrl.append(clientRegistration.getProviderDetails().getConfigurationMetadata().get("end_session_endpoint").toString());

        String originUrl = request.getHeader("Origin");

        logoutUrl.append("?id_token_hint=").append(idToken.getTokenValue()).append("&post_logout_redirect_uri=").append(originUrl);

        return Map.of("logoutUrl", logoutUrl.toString());
    }
}
