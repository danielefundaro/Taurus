package com.fundaro.zodiac.taurus.web.rest;

import com.fundaro.zodiac.taurus.multitenancy.TenantContext;
import com.fundaro.zodiac.taurus.multitenancy.TenantProvisioningStatus;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Rejects requests that need the tenant schema while the tenant of the session has none, instead of letting them
 * fail on missing relations.
 */
@Component
public class TenantProvisioningInterceptor implements HandlerInterceptor {

    private final TenantProvisioningStatus provisioningStatus;

    public TenantProvisioningInterceptor(TenantProvisioningStatus provisioningStatus) {
        this.provisioningStatus = provisioningStatus;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) return true;
        String tenantCode = TenantContext.getTenantCode().orElse(null);
        if (tenantCode == null || schemaNotRequired(handlerMethod) || provisioningStatus.isProvisioned(tenantCode)) return true;
        throw new RequestAlertException(HttpStatus.CONFLICT, "Tenant is not provisioned", "Tenant", "tenant.notProvisioned");
    }

    private static boolean schemaNotRequired(HandlerMethod handlerMethod) {
        return AnnotatedElementUtils.hasAnnotation(handlerMethod.getMethod(), TenantSchemaNotRequired.class)
            || AnnotatedElementUtils.hasAnnotation(handlerMethod.getBeanType(), TenantSchemaNotRequired.class);
    }
}
