package com.fundaro.zodiac.taurus.web.rest;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.Tenants;
import com.fundaro.zodiac.taurus.multitenancy.TenantContext;
import com.fundaro.zodiac.taurus.repository.TenantsRepository;
import com.fundaro.zodiac.taurus.service.TenantFeatureService;
import com.fundaro.zodiac.taurus.service.finance.PayoutService;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Every endpoint of the shares answers 403 when the feature is not effective for the tenant of the request, for the
 * switch off or for a dependency off, and lets the request through for a tenant that has it.
 */
class PayoutResourceFeatureTest {

    private final TenantsRepository repository = mock(TenantsRepository.class);
    private final TenantFeatureInterceptor interceptor = new TenantFeatureInterceptor(
        new TenantFeatureService(repository, new ApplicationProperties())
    );
    private final PayoutResource resource = new PayoutResource(mock(PayoutService.class));

    @Test
    void refusesEveryEndpointOfATenantWithoutTheFeatureAndAdmitsTheOther() {
        Tenants switchedOff = tenant("A", true, true, false);
        Tenants switchedOn = tenant("B", true, true, true);
        Tenants withoutFinance = tenant("C", false, true, true);
        Tenants withoutCalendar = tenant("D", true, false, true);
        List.of(switchedOff, switchedOn, withoutFinance, withoutCalendar)
            .forEach(tenant -> when(repository.findByCodeAndDeletedFalse(tenant.getCode())).thenReturn(Optional.of(tenant)));
        List<Method> endpoints = Arrays.stream(PayoutResource.class.getDeclaredMethods())
            .filter(method -> AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class))
            .toList();
        assertThat(endpoints).hasSize(11);

        for (Method endpoint : endpoints) {
            HandlerMethod handler = new HandlerMethod(resource, endpoint);
            for (String refused : List.of("A", "C", "D")) {
                TenantContext.run(refused, () -> assertThatThrownBy(() -> preHandle(handler))
                    .as(endpoint.getName() + " for " + refused)
                    .isInstanceOfSatisfying(RequestAlertException.class, error -> {
                        assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                        assertThat(error.getErrorKey()).isEqualTo("tenantFeature.eventPayouts.disabled");
                    }));
            }
            TenantContext.run("B", () -> assertThat(preHandle(handler)).as(endpoint.getName()).isTrue());
        }
    }

    private boolean preHandle(HandlerMethod handler) {
        return interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), handler);
    }

    private static Tenants tenant(String code, boolean finance, boolean calendar, boolean payouts) {
        Tenants tenant = new Tenants();
        tenant.setCode(code);
        tenant.setActive(true);
        tenant.setDeleted(false);
        tenant.setFinanceEnabled(finance);
        tenant.setCalendarEnabled(calendar);
        tenant.setEventPayoutsEnabled(payouts);
        return tenant;
    }
}
