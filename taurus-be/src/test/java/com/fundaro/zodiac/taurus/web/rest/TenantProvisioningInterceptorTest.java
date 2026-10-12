package com.fundaro.zodiac.taurus.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fundaro.zodiac.taurus.multitenancy.TenantContext;
import com.fundaro.zodiac.taurus.multitenancy.TenantProvisioningStatus;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

class TenantProvisioningInterceptorTest {

    private final TenantProvisioningStatus provisioningStatus = mock(TenantProvisioningStatus.class);
    private final TenantProvisioningInterceptor interceptor = new TenantProvisioningInterceptor(provisioningStatus);
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private TenantContext.Scope tenantScope;

    @AfterEach
    void closeTenantScope() {
        if (tenantScope != null) tenantScope.close();
    }

    @Test
    void letsRequestsWithoutTenantThrough() {
        assertThat(interceptor.preHandle(request, response, handler(new TenantDataController(), "read"))).isTrue();
        verifyNoInteractions(provisioningStatus);
    }

    @Test
    void letsRequestsOfProvisionedTenantsThrough() {
        tenantScope = TenantContext.use("acme");
        when(provisioningStatus.isProvisioned("acme")).thenReturn(true);

        assertThat(interceptor.preHandle(request, response, handler(new TenantDataController(), "read"))).isTrue();
    }

    @Test
    void rejectsSchemaRequestsOfUnprovisionedTenants() {
        tenantScope = TenantContext.use("temp");

        assertThatThrownBy(() -> interceptor.preHandle(request, response, handler(new TenantDataController(), "read")))
            .isInstanceOfSatisfying(RequestAlertException.class, exception -> {
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(exception.getErrorKey()).isEqualTo("tenant.notProvisioned");
            });
    }

    @Test
    void letsAnnotatedMethodsThroughForUnprovisionedTenants() {
        tenantScope = TenantContext.use("temp");

        assertThat(interceptor.preHandle(request, response, handler(new TenantDataController(), "capabilities"))).isTrue();
        verifyNoInteractions(provisioningStatus);
    }

    @Test
    void letsAnnotatedControllersThroughForUnprovisionedTenants() {
        tenantScope = TenantContext.use("temp");

        assertThat(interceptor.preHandle(request, response, handler(new PublicSchemaController(), "list"))).isTrue();
        verifyNoInteractions(provisioningStatus);
    }

    @Test
    void ignoresHandlersThatAreNotControllerMethods() {
        tenantScope = TenantContext.use("temp");

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    }

    private static HandlerMethod handler(Object controller, String method) {
        try {
            return new HandlerMethod(controller, controller.getClass().getMethod(method));
        } catch (NoSuchMethodException exception) {
            throw new IllegalArgumentException(exception);
        }
    }

    static class TenantDataController {

        public void read() {
            // empty method
        }

        @TenantSchemaNotRequired
        public void capabilities() {
            // empty method
        }
    }

    @TenantSchemaNotRequired
    static class PublicSchemaController {

        public void list() {
            // empty method
        }
    }
}
