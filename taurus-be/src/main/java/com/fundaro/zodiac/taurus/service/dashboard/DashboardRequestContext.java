package com.fundaro.zodiac.taurus.service.dashboard;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Set;
import org.springframework.security.authentication.AbstractAuthenticationToken;

public record DashboardRequestContext(
    String subject,
    Set<String> authorities,
    ZonedDateTime generatedAt,
    ZoneId zoneId,
    AbstractAuthenticationToken authentication
) {
    public boolean hasAnyAuthority(String... expected) {
        for (String authority : expected) {
            if (authorities.contains(authority)) return true;
        }
        return false;
    }
}
