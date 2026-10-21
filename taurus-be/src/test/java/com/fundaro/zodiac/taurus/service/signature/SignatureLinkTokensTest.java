package com.fundaro.zodiac.taurus.service.signature;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class SignatureLinkTokensTest {

    private final SignatureLinkTokens tokens = new SignatureLinkTokens();

    @Test
    void generatesRandomTokensWhoseDigestOnlyIsKept() {
        SignatureLinkTokens.Token first = tokens.generate();
        SignatureLinkTokens.Token second = tokens.generate();

        assertThat(first.value()).matches("[A-Za-z0-9_-]{43}");
        assertThat(first.value()).isNotEqualTo(second.value());
        assertThat(tokens.digest(first.value())).isEqualTo(first.digest()).matches("[0-9a-f]{64}");
        assertThat(SignatureLinkTokens.fingerprint(first.digest())).hasSize(8).isEqualTo(first.digest().substring(0, 8));
        assertThat(SignatureLinkTokens.matches(SignatureLinkTokens.fingerprint(first.digest()), first.digest())).isTrue();
        assertThat(SignatureLinkTokens.matches(SignatureLinkTokens.fingerprint(second.digest()), first.digest())).isFalse();
        assertThat(first.toString()).doesNotContain(first.value());
    }

    @Test
    void refusesValuesThatCannotBeTokens() {
        assertThat(tokens.digest(null)).isNull();
        assertThat(tokens.digest("short")).isNull();
        assertThat(tokens.digest("a".repeat(42) + "=")).isNull();
        assertThat(tokens.digest("a".repeat(44))).isNull();
    }

    @Test
    void limitsTheRequestsPerAddressAndPerToken() {
        ApplicationProperties properties = new ApplicationProperties();
        properties.getSignature().setLinkRateLimitPerIpHour(2);
        properties.getSignature().setLinkRateLimitPerTokenHour(1);
        SignatureLinkRateLimiter limiter = new SignatureLinkRateLimiter(properties, Clock.fixed(Instant.parse("2026-10-19T10:00:00Z"), ZoneOffset.UTC));

        assertThat(limiter.allowRequest("192.0.2.1")).isTrue();
        assertThat(limiter.allowRequest("192.0.2.1")).isTrue();
        assertThat(limiter.allowRequest("192.0.2.1")).isFalse();
        assertThat(limiter.allowRequest("192.0.2.2")).isTrue();
        assertThat(limiter.allowToken("a".repeat(64))).isTrue();
        assertThat(limiter.allowToken("a".repeat(64))).isFalse();
        assertThat(limiter.allowToken("b".repeat(64))).isTrue();
    }
}
