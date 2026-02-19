package com.fundaro.zodiac.taurus.security;

import com.fundaro.zodiac.taurus.config.Constants;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;

/**
 * Utility class for Spring Security.
 */
public final class SecurityUtils {

    public static final String CLAIMS_NAMESPACE = "https://www.jhipster.tech/";

    private SecurityUtils() {}

    /**
     * Get the login of the current user.
     *
     * @return the login of the current user.
     */
    public static Mono<String> getCurrentUserLogin() {
        return ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .flatMap(authentication -> Mono.justOrEmpty(extractPrincipal(authentication)));
    }

    /**
     * Check if a user is authenticated.
     *
     * @return true if the user is authenticated, false otherwise.
     */
    public static Mono<Boolean> isAuthenticated() {
        return ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .map(Authentication::getAuthorities)
            .map(authorities -> authorities.stream().map(GrantedAuthority::getAuthority).noneMatch(AuthoritiesConstants.ANONYMOUS::equals));
    }

    /**
     * Checks if the current user has any of the authorities.
     *
     * @param authorities the authorities to check.
     * @return true if the current user has any of the authorities, false otherwise.
     */
    public static Mono<Boolean> hasCurrentUserAnyOfAuthorities(String... authorities) {
        return ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .map(Authentication::getAuthorities)
            .map(authorityList ->
                authorityList
                    .stream()
                    .map(GrantedAuthority::getAuthority)
                    .anyMatch(authority -> Arrays.asList(authorities).contains(authority))
            );
    }

    /**
     * Checks if the current user has none of the authorities.
     *
     * @param authorities the authorities to check.
     * @return true if the current user has none of the authorities, false otherwise.
     */
    public static Mono<Boolean> hasCurrentUserNoneOfAuthorities(String... authorities) {
        return hasCurrentUserAnyOfAuthorities(authorities).map(result -> !result);
    }

    /**
     * Checks if the current user has a specific authority.
     *
     * @param authority the authority to check.
     * @return true if the current user has the authority, false otherwise.
     */
    public static Mono<Boolean> hasCurrentUserThisAuthority(String authority) {
        return hasCurrentUserAnyOfAuthorities(authority);
    }

    public static List<GrantedAuthority> extractAuthorityFromClaims(Map<String, Object> claims) {
        return mapRolesToGrantedAuthorities(getRolesFromClaims(claims));
    }

    private static List<GrantedAuthority> mapRolesToGrantedAuthorities(Collection<String> roles) {
        return roles.stream().filter(role -> role.startsWith("ROLE_")).map(SimpleGrantedAuthority::new).collect(Collectors.toList());
    }

    public static Map<String, Object> extractDetailsFromTokenAttributes(Map<String, Object> attributes) {
        Map<String, Object> details = new HashMap<>();

        details.put("activated", Optional.ofNullable(attributes.get(StandardClaimNames.EMAIL_VERIFIED)).orElse(true));
        Optional.ofNullable(attributes.get("uid")).ifPresent(id -> details.put("id", id));
        Optional.ofNullable(attributes.get(StandardClaimNames.FAMILY_NAME)).ifPresent(lastName -> details.put("lastName", lastName));
        Optional.ofNullable(attributes.get(StandardClaimNames.PICTURE)).ifPresent(imageUrl -> details.put("imageUrl", imageUrl));

        Optional.ofNullable(attributes.get(StandardClaimNames.GIVEN_NAME)).ifPresentOrElse(
            firstName -> details.put("firstName", firstName),
            () -> Optional.ofNullable(attributes.get(StandardClaimNames.NAME)).ifPresent(firstName -> details.put("firstName", firstName))
        );

        if (attributes.get(StandardClaimNames.EMAIL) != null) {
            details.put("email", attributes.get(StandardClaimNames.EMAIL));
        } else {
            String sub = String.valueOf(attributes.get(StandardClaimNames.SUB));
            String preferredUsername = (String) attributes.get(StandardClaimNames.PREFERRED_USERNAME);
            if (sub.contains("|") && (preferredUsername != null && preferredUsername.contains("@"))) {
                // special handling for Auth0
                details.put("email", preferredUsername);
            } else {
                details.put("email", sub);
            }
        }

        if (attributes.get("langKey") != null) {
            details.put("langKey", attributes.get("langKey"));
        } else if (attributes.get(StandardClaimNames.LOCALE) != null) {
            // trim off country code if it exists
            String locale = (String) attributes.get(StandardClaimNames.LOCALE);
            if (locale.contains("_")) {
                locale = locale.substring(0, locale.indexOf('_'));
            } else if (locale.contains("-")) {
                locale = locale.substring(0, locale.indexOf('-'));
            }
            details.put("langKey", locale.toLowerCase());
        } else {
            // set langKey to default if not specified by IdP
            details.put("langKey", Constants.DEFAULT_LANGUAGE);
        }

        return details;
    }

    public static String getUserIdFromAuthentication(AbstractAuthenticationToken authToken) {
        return getAttributeFromAuthentication("sub", authToken);
    }

    public static String getTenantIdFromAuthentication(AbstractAuthenticationToken authToken) {
        return getAttributeFromAuthentication("tenant", authToken);
    }

    private static String extractPrincipal(Authentication authentication) {
        if (authentication == null) {
            return null;
        } else if (authentication.getPrincipal() instanceof UserDetails springSecurityUser) {
            return springSecurityUser.getUsername();
        } else if (authentication instanceof JwtAuthenticationToken) {
            return (String) ((JwtAuthenticationToken) authentication).getToken().getClaims().get("preferred_username");
        } else if (authentication.getPrincipal() instanceof DefaultOidcUser) {
            Map<String, Object> attributes = ((DefaultOidcUser) authentication.getPrincipal()).getAttributes();
            if (attributes.containsKey("preferred_username")) {
                return (String) attributes.get("preferred_username");
            }
        } else if (authentication.getPrincipal() instanceof String s) {
            return s;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Collection<String> getRolesFromClaims(Map<String, Object> claims) {
        return (Collection<String>) claims.getOrDefault(
            "groups",
            claims.getOrDefault("roles", claims.getOrDefault(CLAIMS_NAMESPACE + "roles", new ArrayList<>()))
        );
    }

    private static String getAttributeFromAuthentication(String attributeName, AbstractAuthenticationToken authToken) {
        Map<String, Object> attributes;
        if (authToken instanceof JwtAuthenticationToken) {
            attributes = ((JwtAuthenticationToken) authToken).getTokenAttributes();
        } else if (authToken instanceof OAuth2AuthenticationToken) {
            attributes = ((OAuth2AuthenticationToken) authToken).getPrincipal().getAttributes();
        } else {
            throw new IllegalArgumentException("AuthenticationToken is not OAuth2 or JWT!");
        }

        if (attributes.isEmpty() || !attributes.containsKey(attributeName)) {
            return null;
        }

        return attributes.get(attributeName).toString();
    }
}
