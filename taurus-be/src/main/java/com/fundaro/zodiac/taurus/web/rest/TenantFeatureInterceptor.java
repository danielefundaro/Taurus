package com.fundaro.zodiac.taurus.web.rest;

import com.fundaro.zodiac.taurus.service.TenantFeatureService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class TenantFeatureInterceptor implements HandlerInterceptor {

    private final TenantFeatureService tenantFeatureService;

    public TenantFeatureInterceptor(TenantFeatureService tenantFeatureService) {
        this.tenantFeatureService = tenantFeatureService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) return true;
        RequiresTenantFeature requirement = AnnotatedElementUtils.findMergedAnnotation(
            handlerMethod.getMethod(),
            RequiresTenantFeature.class
        );
        if (requirement == null) {
            requirement = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), RequiresTenantFeature.class);
        }
        if (requirement != null) tenantFeatureService.requireEnabled(requirement.value());
        return true;
    }
}
