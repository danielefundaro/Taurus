package com.fundaro.zodiac.taurus.multitenancy;

import com.fundaro.zodiac.taurus.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class TenantContextInterceptor implements HandlerInterceptor {

    private static final String SCOPE_ATTRIBUTE = TenantContextInterceptor.class.getName() + ".scope";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String tenantCode = authentication instanceof JwtAuthenticationToken || authentication instanceof OAuth2AuthenticationToken
            ? SecurityUtils.getTenantIdFromAuthentication((AbstractAuthenticationToken) authentication)
            : null;
        request.setAttribute(SCOPE_ATTRIBUTE, TenantContext.use(tenantCode));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception) {
        Object scope = request.getAttribute(SCOPE_ATTRIBUTE);
        if (scope instanceof TenantContext.Scope tenantScope) {
            tenantScope.close();
        }
    }
}
