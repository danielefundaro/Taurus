package com.fundaro.zodiac.taurus.config;

import com.fundaro.zodiac.taurus.security.SecurityUtils;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class AuditConfiguration {

    @Bean
    AuditorAware<String> auditorProvider() {
        return () -> Optional.of(resolveActor());
    }

    private static String resolveActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof AbstractAuthenticationToken token && authentication.isAuthenticated()) {
            try {
                String userId = SecurityUtils.getUserIdFromAuthentication(token);
                if (userId != null && !userId.isBlank()) {
                    return userId;
                }
            } catch (IllegalArgumentException ignored) {
                // Non OAuth/JWT authentication (for example tests): use its principal name.
            }
            String name = authentication.getName();
            if (name != null && !name.isBlank() && !"anonymousUser".equals(name)) {
                return name;
            }
        }
        return "system";
    }
}
