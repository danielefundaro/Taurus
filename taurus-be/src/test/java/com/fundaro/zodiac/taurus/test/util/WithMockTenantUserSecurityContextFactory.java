package com.fundaro.zodiac.taurus.test.util;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

import java.util.Arrays;
import java.util.List;

public class WithMockTenantUserSecurityContextFactory implements WithSecurityContextFactory<WithMockTenantUser> {

    @Override
    public SecurityContext createSecurityContext(WithMockTenantUser annotation) {
        Jwt jwt = Jwt.withTokenValue("mock-token")
            .header("alg", "none")
            .claim("sub", annotation.userId())
            .claim("preferred_username", annotation.userId())
            .claim("tenant", annotation.tenant())
            .build();
        List<GrantedAuthority> authorities = Arrays.stream(annotation.authorities())
            .map(authority -> (GrantedAuthority) new SimpleGrantedAuthority(authority))
            .toList();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new JwtAuthenticationToken(jwt, authorities));
        return context;
    }
}
