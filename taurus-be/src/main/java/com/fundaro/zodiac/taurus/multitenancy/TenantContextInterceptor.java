package com.fundaro.zodiac.taurus.multitenancy;

import com.fundaro.zodiac.taurus.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Apre il {@link TenantContext} della richiesta e lo chiude quando finisce.
 *
 * <p>Mette anche il codice del tenant nel contesto di log: con i log in JSON ogni riga prodotta
 * durante la richiesta dice a quale associazione appartiene, che è la prima domanda davanti a un
 * guasto su un'installazione con più tenant. L'utente non finisce nei log: chi ha fatto cosa sta
 * nelle tabelle di audit, dove il dato è protetto e cancellabile.
 */
@Component
public class TenantContextInterceptor implements HandlerInterceptor {

    private static final String SCOPE_ATTRIBUTE = TenantContextInterceptor.class.getName() + ".scope";

    public static final String TENANT_KEY = "tenant";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String tenantCode = authentication instanceof JwtAuthenticationToken || authentication instanceof OAuth2AuthenticationToken
            ? SecurityUtils.getTenantIdFromAuthentication((AbstractAuthenticationToken) authentication)
            : null;
        request.setAttribute(SCOPE_ATTRIBUTE, TenantContext.use(tenantCode));
        if (tenantCode != null) {
            MDC.put(TENANT_KEY, tenantCode);
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception) {
        // Il thread torna al pool: il codice del tenant non deve restare addosso alla richiesta
        // successiva, che potrebbe appartenere a un'altra associazione.
        MDC.remove(TENANT_KEY);
        Object scope = request.getAttribute(SCOPE_ATTRIBUTE);
        if (scope instanceof TenantContext.Scope tenantScope) {
            tenantScope.close();
        }
    }
}
