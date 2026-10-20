package com.fundaro.zodiac.taurus.service.signature;

import com.fundaro.zodiac.taurus.domain.enumeration.TenantFeature;
import com.fundaro.zodiac.taurus.domain.signature.SignatureLinkRegistry;
import com.fundaro.zodiac.taurus.multitenancy.TenantContext;
import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaRegistry;
import com.fundaro.zodiac.taurus.multitenancy.TenantTransactionExecutor;
import com.fundaro.zodiac.taurus.repository.signature.SignatureLinkRegistryRepository;
import com.fundaro.zodiac.taurus.service.TenantFeatureService;

import java.time.ZonedDateTime;
import java.util.function.Function;

import org.springframework.stereotype.Service;

/**
 * Resolves the token of a request to the public signature page: the public registry gives the tenant, which must be
 * active and use the inventory, and the link must be valid in the tenant. Every failure is the same
 * {@link SignatureLinkUnavailableException}. No authentication is created: the action receives the session only.
 */
@Service
public class SignatureLinkResolver {

    private final SignatureLinkTokens tokens;
    private final SignatureLinkRateLimiter rateLimiter;
    private final SignatureLinkRegistryRepository registryRepository;
    private final TenantSchemaRegistry tenantSchemaRegistry;
    private final TenantFeatureService tenantFeatureService;
    private final TenantTransactionExecutor tenantTransactionExecutor;
    private final SignatureLinkService linkService;

    public SignatureLinkResolver(
        SignatureLinkTokens tokens,
        SignatureLinkRateLimiter rateLimiter,
        SignatureLinkRegistryRepository registryRepository,
        TenantSchemaRegistry tenantSchemaRegistry,
        TenantFeatureService tenantFeatureService,
        TenantTransactionExecutor tenantTransactionExecutor,
        SignatureLinkService linkService
    ) {
        this.tokens = tokens;
        this.rateLimiter = rateLimiter;
        this.registryRepository = registryRepository;
        this.tenantSchemaRegistry = tenantSchemaRegistry;
        this.tenantFeatureService = tenantFeatureService;
        this.tenantTransactionExecutor = tenantTransactionExecutor;
        this.linkService = linkService;
    }

    /**
     * Runs the action for the link of the token, in the context of its tenant.
     */
    public <T> T within(String token, String remoteAddress, Function<SignatureLinkSession, T> action) {
        if (!rateLimiter.allowRequest(remoteAddress)) throw new SignatureLinkRateLimitException();
        String digest = tokens.digest(token);
        if (digest == null) throw new SignatureLinkUnavailableException();
        if (!rateLimiter.allowToken(digest)) throw new SignatureLinkRateLimitException();
        ZonedDateTime now = ZonedDateTime.now();
        SignatureLinkRegistry route = TenantContext.call(null, () -> registryRepository.resolveActive(digest, now))
            .orElseThrow(SignatureLinkUnavailableException::new);
        if (!tenantFeatureService.isEnabledForTenant(route.getTenantId(), TenantFeature.INVENTORY)) throw new SignatureLinkUnavailableException();
        String tenantCode = tenantSchemaRegistry.findActiveTenantCode(route.getTenantId()).orElseThrow(SignatureLinkUnavailableException::new);
        SignatureLinkSession session = tenantTransactionExecutor.execute(tenantCode, () -> linkService.open(route, digest, tenantCode, now));
        return TenantContext.call(tenantCode, () -> action.apply(session));
    }
}
