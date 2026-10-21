package com.fundaro.zodiac.taurus.service.signature;

import com.fundaro.zodiac.taurus.domain.signature.SignatureActType;
import com.fundaro.zodiac.taurus.domain.signature.SignatureMethod;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.Locale;

import org.springframework.stereotype.Component;

/**
 * Counters of the electronic signature, without tenant, user or act identifiers.
 */
@Component
public class SignatureMetrics {

    private static final String PREFIX = "taurus.signature.";
    private final MeterRegistry registry;

    public SignatureMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void challenge(SignatureMethod method, String outcome) {
        registry.counter(PREFIX + "challenges", "method", method.name().toLowerCase(Locale.ROOT), "outcome", outcome).increment();
    }

    public void act(SignatureActType type, String outcome) {
        registry.counter(PREFIX + "acts", "type", type.name().toLowerCase(Locale.ROOT), "outcome", outcome).increment();
    }

    public void mail(String outcome) {
        registry.counter(PREFIX + "mail", "outcome", outcome).increment();
    }

    public void link(String outcome) {
        registry.counter(PREFIX + "links", "outcome", outcome).increment();
    }

    public void cieSign(String outcome) {
        registry.counter(PREFIX + "ciesign", "outcome", outcome).increment();
    }

    public void purge(String reason, String outcome) {
        registry.counter(PREFIX + "purge", "reason", reason, "outcome", outcome).increment();
    }
}
