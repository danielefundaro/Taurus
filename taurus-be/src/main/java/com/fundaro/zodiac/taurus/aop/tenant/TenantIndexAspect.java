package com.fundaro.zodiac.taurus.aop.tenant;

import com.fundaro.zodiac.taurus.security.SecurityUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Optional;

/**
 * AOP Aspect that intercepts all public methods of
 * {@link com.fundaro.zodiac.taurus.service.impl.CommonOpenSearchServiceImpl} and its subclasses.
 *
 * <h3>Perché ThreadLocal non funziona in WebFlux</h3>
 * <p>Un ThreadLocal viene impostato sul thread corrente, ma Reactor può eseguire gli operatori
 * della pipeline su thread diversi (es. {@code boundedElastic-4} → {@code boundedElastic-5}).
 * Quando avviene uno switch di thread il ThreadLocal del thread precedente non è più visibile,
 * e {@code IndexResolver} trova un valore nullo.
 *
 * <h3>Soluzione: Reactor Context</h3>
 * <p>Il Reactor Context è immutabile, immesso tramite {@code contextWrite} e propagato
 * <em>automaticamente</em> a tutta la pipeline — inclusi {@code flatMap} annidati, chiamate
 * interne come {@code this.save()} o {@code super.save()}, e qualsiasi switch di scheduler.
 * I singoli operatori lo leggono con {@code Mono.deferContextual}.
 *
 * <h3>Flusso</h3>
 * <pre>
 * Aspect intercetta metodo pubblico
 *   → estrae tenantId dal token
 *   → proceed() → ottiene il Mono restituito
 *   → wrappa il Mono con contextWrite(TENANT_KEY → tenantId)
 *
 * Quando il Mono viene subscribed (anche su thread diverso):
 *   → Reactor Context contiene tenantId
 *   → Mono.deferContextual nei metodi privati legge il tenantId ✓
 *   → Thread switch successivi non perdono il Context ✓
 *   → Chiamate this.save() / super.save() trovano il Context ✓
 * </pre>
 */
@Aspect
@Component
public class TenantIndexAspect {

    /**
     * Chiave usata per memorizzare il tenant ID nel Reactor Context.
     * Usata da {@link com.fundaro.zodiac.taurus.service.impl.CommonOpenSearchServiceImpl}
     * tramite {@code Mono.deferContextual(ctx -> ctx.getOrDefault(TENANT_CONTEXT_KEY, ""))}.
     */
    public static final String TENANT_CONTEXT_KEY = "tenantId";

    private static final Logger log = LoggerFactory.getLogger(TenantIndexAspect.class);

    @Around("execution(public * com.fundaro.zodiac.taurus.service.impl.CommonOpenSearchServiceImpl+.*(..))")
    public Object resolveTenant(ProceedingJoinPoint joinPoint) throws Throwable {
        AbstractAuthenticationToken tokenOpt = Arrays.stream(joinPoint.getArgs())
            .filter(arg -> arg instanceof AbstractAuthenticationToken)
            .map(arg -> (AbstractAuthenticationToken) arg)
            .findFirst().orElse(null);

        // Nessun token (es. getQueries, getMapper) — nessuna risoluzione necessaria
        if (tokenOpt == null) {
            return joinPoint.proceed();
        }

        String tenantId = SecurityUtils.getTenantIdFromAuthentication(tokenOpt);
        final String resolvedTenant = tenantId != null ? tenantId : "";
        log.debug("Resolved tenantId='{}' for method '{}'", resolvedTenant, joinPoint.getSignature().toShortString());

        Object result = joinPoint.proceed();

        // Inietta il tenant nel Reactor Context: si propaga lungo tutta la pipeline
        // indipendentemente dai thread switch e dalle chiamate interne
        if (result instanceof Mono<?> mono) {
            return mono.contextWrite(ctx -> ctx.put(TENANT_CONTEXT_KEY, resolvedTenant));
        }

        return result;
    }
}
