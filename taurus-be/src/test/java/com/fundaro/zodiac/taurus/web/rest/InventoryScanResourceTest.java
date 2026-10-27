package com.fundaro.zodiac.taurus.web.rest;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.enumeration.TenantFeature;
import com.fundaro.zodiac.taurus.service.TenantFeatureService;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryQrDtos.ScanResponse;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryQrDtos.ScanTarget;
import com.fundaro.zodiac.taurus.service.impl.InventoryQrMetrics;
import com.fundaro.zodiac.taurus.service.impl.InventoryScanRateLimiter;
import com.fundaro.zodiac.taurus.service.impl.InventoryScanService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InventoryScanResourceTest {
    private static final String CODE = "550e8400-e29b-41d4-a716-446655440000";
    private final InventoryScanService scanService = mock(InventoryScanService.class);
    private final TenantFeatureService tenantFeatures = mock(TenantFeatureService.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final JwtAuthenticationToken token = authentication();

    @Test
    void aDisabledTenantAnswersNotFoundWithoutBodyOrResolution() {
        InventoryScanResource resource = resource(60);

        ResponseEntity<?> response = resource.resolve(CODE, token, request("10.0.0.1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNull();
        verifyNoInteractions(scanService);
        assertThat(registry.get("taurus.inventory.qr.resolution").tags("outcome", "disabled", "role_group", "member").counter().count()).isEqualTo(1);
    }

    @Test
    void resolvesWithoutCachingOrReferrer() {
        when(tenantFeatures.isEnabled(TenantFeature.INVENTORY_QR)).thenReturn(true);
        ScanResponse body = new ScanResponse(ScanTarget.OWN_ASSIGNMENTS, null, null, null, null, null, null, null, null, null, null, null,
            null, null, null, List.of(), List.of());
        when(scanService.resolve(CODE, token)).thenReturn(body);

        ResponseEntity<?> response = resource(60).resolve(CODE, token, request("10.0.0.1"));

        assertThat(response.getBody()).isSameAs(body);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getHeaders().getFirst("Referrer-Policy")).isEqualTo("no-referrer");
    }

    /** Oltre il limite per minuto la risposta è un 429 controllato, contato senza codice, oggetto o utente nei tag. */
    @Test
    void answersTooManyRequestsBeyondTheLimit() {
        when(tenantFeatures.isEnabled(TenantFeature.INVENTORY_QR)).thenReturn(true);
        InventoryScanResource resource = resource(2);
        long minute = Instant.now().getEpochSecond() / 60;

        resource.resolve(CODE, token, request("10.0.0.1"));
        resource.resolve(CODE, token, request("10.0.0.1"));
        ResponseEntity<?> limited = resource.resolve(CODE, token, request("10.0.0.1"));

        assumeThat(Instant.now().getEpochSecond() / 60).as("le tre richieste cadono nello stesso minuto").isEqualTo(minute);
        assertThat(limited.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(limited.getHeaders().getFirst("Retry-After")).isEqualTo("60");
        verify(scanService, times(2)).resolve(any(), any());
        assertThat(registry.get("taurus.inventory.qr.resolution").tags("outcome", "rate_limited").counter().count()).isEqualTo(1);
        assertThat(registry.get("taurus.inventory.qr.resolution").counter().getId().getTags())
            .allSatisfy(tag -> assertThat(tag.getValue()).doesNotContain("550e8400", "member-1", "10.0.0.1"));
    }

    /** Anche cambiando utente, lo stesso indirizzo resta soggetto al proprio limite. */
    @Test
    void theAddressHasItsOwnLimit() {
        ApplicationProperties properties = new ApplicationProperties();
        properties.getInventory().getQr().setResolutionLimitPerMinute(1);
        InventoryScanRateLimiter limiter = new InventoryScanRateLimiter(properties);
        long minute = Instant.now().getEpochSecond() / 60;

        boolean first = limiter.tryAcquire("member-1", "10.0.0.1");
        boolean otherUserSameAddress = limiter.tryAcquire("member-2", "10.0.0.1");
        boolean otherUserOtherAddress = limiter.tryAcquire("member-3", "10.0.0.2");

        assumeThat(Instant.now().getEpochSecond() / 60).isEqualTo(minute);
        assertThat(first).isTrue();
        assertThat(otherUserSameAddress).isFalse();
        assertThat(otherUserOtherAddress).isTrue();
    }

    private InventoryScanResource resource(int limit) {
        ApplicationProperties properties = new ApplicationProperties();
        properties.getInventory().getQr().setResolutionLimitPerMinute(limit);
        return new InventoryScanResource(scanService, new InventoryScanRateLimiter(properties), tenantFeatures, new InventoryQrMetrics(registry));
    }

    private static MockHttpServletRequest request(String address) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(address);
        return request;
    }

    private static JwtAuthenticationToken authentication() {
        Instant now = Instant.now();
        Jwt jwt = new Jwt("token", now, now.plusSeconds(60), Map.of("alg", "none"), Map.of("sub", "member-1", "tenant", "tenant-a"));
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }
}
