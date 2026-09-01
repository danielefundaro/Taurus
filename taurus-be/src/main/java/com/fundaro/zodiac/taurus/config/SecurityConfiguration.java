package com.fundaro.zodiac.taurus.config;

import com.fundaro.zodiac.taurus.security.AuthoritiesConstants;
import com.fundaro.zodiac.taurus.security.SecurityUtils;
import com.fundaro.zodiac.taurus.security.oauth2.AudienceValidator;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestClient;
import tech.jhipster.config.JHipsterProperties;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static org.springframework.security.config.Customizer.withDefaults;
import static org.springframework.security.oauth2.core.oidc.StandardClaimNames.PREFERRED_USERNAME;

@Configuration
public class SecurityConfiguration {

    private final JHipsterProperties jHipsterProperties;

    @Value("${spring.security.oauth2.client.provider.oidc.issuer-uri}")
    private String issuerUri;

    private final ClientRegistrationRepository clientRegistrationRepository;

    // See https://github.com/jhipster/generator-jhipster/issues/18868
    // We don't use a distributed cache or the user selected cache implementation here on purpose
    private final Cache<String, Jwt> users = Caffeine.newBuilder()
        .maximumSize(10_000)
        .expireAfterWrite(Duration.ofHours(1))
        .recordStats()
        .build();

    public SecurityConfiguration(ClientRegistrationRepository clientRegistrationRepository, JHipsterProperties jHipsterProperties) {
        this.clientRegistrationRepository = clientRegistrationRepository;
        this.jHipsterProperties = jHipsterProperties;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(withDefaults())
            .csrf(csrf -> csrf.disable())
            .headers(headers ->
                headers
                    .contentSecurityPolicy(csp -> csp.policyDirectives(jHipsterProperties.getSecurity().getContentSecurityPolicy()))
                    .frameOptions(frameOptions -> frameOptions.deny())
                    .referrerPolicy(referrer ->
                        referrer.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
                    )
                    .permissionsPolicy(permissions ->
                        permissions.policy(
                            "camera=(), fullscreen=(self), geolocation=(), gyroscope=(), magnetometer=(), microphone=(), midi=(), payment=(), sync-xhr=()"
                        )
                    )
            )
            .authorizeHttpRequests(authz ->
                // prettier-ignore
                authz
                    // Public authentication metadata.
                    .requestMatchers(HttpMethod.GET, "/api/authenticate", "/api/auth-info").permitAll()

                    // Endpoints available to every authenticated user.
                    .requestMatchers(HttpMethod.GET, "/api/account").authenticated()
                    .requestMatchers(HttpMethod.POST, "/api/logout").authenticated()
                    .requestMatchers(HttpMethod.GET, "/api/legal/status").authenticated()
                    .requestMatchers(HttpMethod.POST, "/api/legal/acceptances").authenticated()
                    .requestMatchers(HttpMethod.GET, "/api/users/me", "/api/users/me/calendar-events").authenticated()
                    .requestMatchers(HttpMethod.PATCH, "/api/users/me").authenticated()
                    .requestMatchers(HttpMethod.DELETE, "/api/users/me").authenticated()
                    .requestMatchers(HttpMethod.DELETE, "/api/users/me/gdpr").authenticated()
                    .requestMatchers("/api/preferences/**", "/api/last-researches/**", "/api/notices/**", "/api/push-subscriptions/**")
                    .authenticated()
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/user/inventory/assignments/**",
                        "/api/user/inventory/photos/**",
                        "/api/user/inventory/return-photos/**",
                        "/api/user/inventory/summary",
                        "/api/user/inventory/report"
                    )
                    .authenticated()
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/user/inventory/assignments/{id}/decision",
                        "/api/user/inventory/assignments/{id}/returns",
                        "/api/user/inventory/returns/{id}/photos"
                    )
                    .authenticated()
                    .requestMatchers("/api/user/inventory/**").denyAll()

                    // Tenant inventory administration. Personal inventory endpoints are matched above.
                    .requestMatchers("/api/inventory/**")
                    .hasAnyAuthority(AuthoritiesConstants.SUPER_ADMIN, AuthoritiesConstants.ADMIN)

                    // All users can get instruments information
                    .requestMatchers(HttpMethod.GET, "/api/instruments/**").authenticated()

                    // Legal document administration and tenant administration.
                    .requestMatchers(HttpMethod.DELETE, "/api/tenants/{id}/gdpr")
                    .hasAuthority(AuthoritiesConstants.SUPER_ADMIN)
                    .requestMatchers("/api/legal/documents/**", "/api/tenants/**")
                    .hasAuthority(AuthoritiesConstants.SUPER_ADMIN)
                    .requestMatchers("/api/legal/**").denyAll()

                    // User administration. Self-service endpoints are matched above.
                    .requestMatchers(HttpMethod.DELETE, "/api/users/{id}/gdpr")
                    .hasAnyAuthority(AuthoritiesConstants.SUPER_ADMIN, AuthoritiesConstants.ADMIN)
                    .requestMatchers("/api/users/**")
                    .hasAnyAuthority(AuthoritiesConstants.SUPER_ADMIN, AuthoritiesConstants.ADMIN)

                    // Catalogue administration.
                    .requestMatchers("/api/albums/**", "/api/tracks/**", "/api/instruments/**")
                    .hasAnyAuthority(AuthoritiesConstants.SUPER_ADMIN, AuthoritiesConstants.ADMIN, AuthoritiesConstants.ARCHIVIST)

                    // Read-only catalogue APIs for standard and external users.
                    .requestMatchers(HttpMethod.GET, "/api/user/albums/**", "/api/user/tracks/**", "/api/user/media/**")
                    .hasAuthority(AuthoritiesConstants.USER)
                    .requestMatchers("/api/user/albums/**", "/api/user/tracks/**", "/api/user/media/**").denyAll()
                    .requestMatchers(HttpMethod.GET, "/api/external/albums/**", "/api/external/tracks/**")
                    .hasAuthority(AuthoritiesConstants.USER_EXTERNAL)
                    .requestMatchers("/api/external/albums/**", "/api/external/tracks/**").denyAll()

                    // Calendar event administration and personal availability.
                    .requestMatchers(HttpMethod.PATCH, "/api/calendar-events/{id}/availability")
                    .hasAnyAuthority(AuthoritiesConstants.SUPER_ADMIN, AuthoritiesConstants.ADMIN, AuthoritiesConstants.ARCHIVIST)
                    .requestMatchers(HttpMethod.DELETE, "/api/calendar-events/{id}/availability")
                    .hasAnyAuthority(AuthoritiesConstants.SUPER_ADMIN, AuthoritiesConstants.ADMIN, AuthoritiesConstants.ARCHIVIST)
                    .requestMatchers(HttpMethod.PATCH, "/api/calendar-events/series/{seriesId}/availability")
                    .hasAnyAuthority(AuthoritiesConstants.SUPER_ADMIN, AuthoritiesConstants.ADMIN, AuthoritiesConstants.ARCHIVIST)
                    .requestMatchers(HttpMethod.DELETE, "/api/calendar-events/series/{seriesId}/availability")
                    .hasAnyAuthority(AuthoritiesConstants.SUPER_ADMIN, AuthoritiesConstants.ADMIN, AuthoritiesConstants.ARCHIVIST)
                    .requestMatchers(HttpMethod.GET, "/api/calendar-events/**")
                    .hasAnyAuthority(AuthoritiesConstants.SUPER_ADMIN, AuthoritiesConstants.ADMIN, AuthoritiesConstants.ARCHIVIST)
                    .requestMatchers(HttpMethod.POST, "/api/calendar-events")
                    .hasAnyAuthority(AuthoritiesConstants.SUPER_ADMIN, AuthoritiesConstants.ADMIN)
                    .requestMatchers(HttpMethod.PUT, "/api/calendar-events/**")
                    .hasAnyAuthority(AuthoritiesConstants.SUPER_ADMIN, AuthoritiesConstants.ADMIN)
                    .requestMatchers(HttpMethod.PATCH, "/api/calendar-events/**")
                    .hasAnyAuthority(AuthoritiesConstants.SUPER_ADMIN, AuthoritiesConstants.ADMIN)
                    .requestMatchers(HttpMethod.DELETE, "/api/calendar-events/**")
                    .hasAnyAuthority(AuthoritiesConstants.SUPER_ADMIN, AuthoritiesConstants.ADMIN)
                    .requestMatchers("/api/calendar-events/**").denyAll()

                    .requestMatchers("/api/calendar-event-series/**")
                    .hasAnyAuthority(AuthoritiesConstants.SUPER_ADMIN, AuthoritiesConstants.ADMIN)

                    .requestMatchers(HttpMethod.GET, "/api/user/calendar-events/**")
                    .hasAuthority(AuthoritiesConstants.USER)
                    .requestMatchers(HttpMethod.PATCH, "/api/user/calendar-events/{id}/availability")
                    .hasAuthority(AuthoritiesConstants.USER)
                    .requestMatchers(HttpMethod.DELETE, "/api/user/calendar-events/{id}/availability")
                    .hasAuthority(AuthoritiesConstants.USER)
                    .requestMatchers(HttpMethod.PATCH, "/api/user/calendar-events/series/{seriesId}/availability")
                    .hasAuthority(AuthoritiesConstants.USER)
                    .requestMatchers(HttpMethod.DELETE, "/api/user/calendar-events/series/{seriesId}/availability")
                    .hasAuthority(AuthoritiesConstants.USER)
                    .requestMatchers("/api/user/calendar-events/**").denyAll()

                    .requestMatchers(HttpMethod.GET, "/api/external/calendar-events/**")
                    .hasAuthority(AuthoritiesConstants.USER_EXTERNAL)
                    .requestMatchers(HttpMethod.PATCH, "/api/external/calendar-events/{id}/availability")
                    .hasAuthority(AuthoritiesConstants.USER_EXTERNAL)
                    .requestMatchers(HttpMethod.DELETE, "/api/external/calendar-events/{id}/availability")
                    .hasAuthority(AuthoritiesConstants.USER_EXTERNAL)
                    .requestMatchers(HttpMethod.PATCH, "/api/external/calendar-events/series/{seriesId}/availability")
                    .hasAuthority(AuthoritiesConstants.USER_EXTERNAL)
                    .requestMatchers(HttpMethod.DELETE, "/api/external/calendar-events/series/{seriesId}/availability")
                    .hasAuthority(AuthoritiesConstants.USER_EXTERNAL)
                    .requestMatchers("/api/external/calendar-events/**").denyAll()

                    // Media reads are used by every authenticated FE role; writes remain administrative.
                    .requestMatchers(HttpMethod.GET, "/api/media/**").authenticated()
                    .requestMatchers(HttpMethod.POST, "/api/media/**")
                    .hasAnyAuthority(AuthoritiesConstants.SUPER_ADMIN, AuthoritiesConstants.ADMIN, AuthoritiesConstants.ARCHIVIST)
                    .requestMatchers(HttpMethod.PUT, "/api/media/**")
                    .hasAnyAuthority(AuthoritiesConstants.SUPER_ADMIN, AuthoritiesConstants.ADMIN, AuthoritiesConstants.ARCHIVIST)
                    .requestMatchers(HttpMethod.PATCH, "/api/media/**")
                    .hasAnyAuthority(AuthoritiesConstants.SUPER_ADMIN, AuthoritiesConstants.ADMIN, AuthoritiesConstants.ARCHIVIST)
                    .requestMatchers(HttpMethod.DELETE, "/api/media/**")
                    .hasAnyAuthority(AuthoritiesConstants.SUPER_ADMIN, AuthoritiesConstants.ADMIN, AuthoritiesConstants.ARCHIVIST)
                    .requestMatchers("/api/media/**").denyAll()

                    .requestMatchers("/api/admin/**")
                    .hasAnyAuthority(AuthoritiesConstants.SUPER_ADMIN, AuthoritiesConstants.ADMIN)
                    .requestMatchers("/api/**").authenticated()
                    .requestMatchers("/services/**").authenticated()
                    .requestMatchers("/v3/api-docs/**")
                    .hasAnyAuthority(AuthoritiesConstants.SUPER_ADMIN, AuthoritiesConstants.ADMIN)
                    .requestMatchers("/management/health").permitAll()
                    .requestMatchers("/management/health/**").permitAll()
                    .requestMatchers("/management/info").permitAll()
                    .requestMatchers("/management/prometheus")
                    .hasAnyAuthority(AuthoritiesConstants.SUPER_ADMIN, AuthoritiesConstants.ADMIN)
                    .requestMatchers("/management/**")
                    .hasAnyAuthority(AuthoritiesConstants.SUPER_ADMIN, AuthoritiesConstants.ADMIN)

                    // Angular static resources and client-side route fallback. Must remain last.
                    .requestMatchers("/**").permitAll()
            )
            .oauth2Login(oauth2 -> oauth2.authorizationEndpoint(endpoint ->
                endpoint.authorizationRequestResolver(authorizationRequestResolver(this.clientRegistrationRepository))
            ))
            .oauth2Client(withDefaults())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));

        return http.build();
    }

    private OAuth2AuthorizationRequestResolver authorizationRequestResolver(
        ClientRegistrationRepository clientRegistrationRepository
    ) {
        DefaultOAuth2AuthorizationRequestResolver authorizationRequestResolver = new DefaultOAuth2AuthorizationRequestResolver(
            clientRegistrationRepository,
            "/oauth2/authorization"
        );
        if (this.issuerUri.contains("auth0.com")) {
            authorizationRequestResolver.setAuthorizationRequestCustomizer(authorizationRequestCustomizer());
        }
        return authorizationRequestResolver;
    }

    private Consumer<OAuth2AuthorizationRequest.Builder> authorizationRequestCustomizer() {
        return customizer ->
            customizer.authorizationRequestUri(uriBuilder ->
                uriBuilder.queryParam("audience", jHipsterProperties.getSecurity().getOauth2().getAudience()).build()
            );
    }

    Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(
            jwt -> SecurityUtils.extractAuthorityFromClaims(jwt.getClaims())
        );
        jwtAuthenticationConverter.setPrincipalClaimName(PREFERRED_USERNAME);
        return jwtAuthenticationConverter;
    }

    /**
     * Map authorities from "groups" or "roles" claim in ID Token.
     *
     * @return a {@link OAuth2UserService} that has the groups from the IdP.
     */
    @Bean
    public OAuth2UserService<OidcUserRequest, OidcUser> oidcUserService() {
        final OidcUserService delegate = new OidcUserService();

        return userRequest -> {
            OidcUser user = delegate.loadUser(userRequest);
            Set<GrantedAuthority> mappedAuthorities = new HashSet<>();

            user
                .getAuthorities()
                .forEach(authority -> {
                    if (authority instanceof OidcUserAuthority) {
                        OidcUserAuthority oidcUserAuthority = (OidcUserAuthority) authority;
                        mappedAuthorities.addAll(
                            SecurityUtils.extractAuthorityFromClaims(oidcUserAuthority.getUserInfo().getClaims())
                        );
                    }
                });

            return new DefaultOidcUser(mappedAuthorities, user.getIdToken(), user.getUserInfo(), PREFERRED_USERNAME);
        };
    }

    @Bean
    public JwtDecoder jwtDecoder(ClientRegistrationRepository clientRegistrationRepository) {
        ClientRegistration clientRegistration = clientRegistrationRepository.findByRegistrationId("oidc");
        return createJwtDecoder(
            clientRegistration.getProviderDetails().getIssuerUri(),
            clientRegistration.getProviderDetails().getJwkSetUri(),
            clientRegistration.getProviderDetails().getUserInfoEndpoint().getUri()
        );
    }

    private JwtDecoder createJwtDecoder(String issuerUri, String jwkSetUri, String userInfoUri) {
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        OAuth2TokenValidator<Jwt> audienceValidator = new AudienceValidator(jHipsterProperties.getSecurity().getOauth2().getAudience());
        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuerUri);
        OAuth2TokenValidator<Jwt> withAudience = new DelegatingOAuth2TokenValidator<>(withIssuer, audienceValidator);

        jwtDecoder.setJwtValidator(withAudience);

        return token -> {
            Jwt jwt = jwtDecoder.decode(token);
            return enrich(token, jwt, userInfoUri);
        };
    }

    private Jwt enrich(String token, Jwt jwt, String userInfoUri) {
        if (jwt.hasClaim("given_name") && jwt.hasClaim("family_name")) {
            return jwt;
        }
        Jwt cached = users.getIfPresent(jwt.getSubject());
        if (cached != null) {
            return cached;
        }
        Map<String, Object> userInfo = RestClient.create()
            .get()
            .uri(userInfoUri)
            .header("Authorization", "Bearer " + token)
            .retrieve()
            .body(new ParameterizedTypeReference<Map<String, Object>>() {
            });

        String username = userInfo.get("preferred_username").toString();
        if (userInfo.get("sub").toString().contains("|") && username.contains("@")) {
            userInfo.put("email", username);
        }
        if (userInfo.get("name") != null) {
            String[] name = userInfo.get("name").toString().split("\\s+");
            if (name.length > 0) {
                userInfo.put("given_name", name[0]);
                userInfo.put("family_name", String.join(" ", Arrays.copyOfRange(name, 1, name.length)));
            }
        }

        Jwt enriched = Jwt.withTokenValue(jwt.getTokenValue())
            .subject(jwt.getSubject())
            .audience(jwt.getAudience())
            .headers(headers -> headers.putAll(jwt.getHeaders()))
            .claims(claims -> claims.putAll(userInfo))
            .claims(claims -> claims.putAll(jwt.getClaims()))
            .build();

        users.put(jwt.getSubject(), enriched);
        return enriched;
    }
}
