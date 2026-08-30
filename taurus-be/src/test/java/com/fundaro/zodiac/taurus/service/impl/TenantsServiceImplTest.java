package com.fundaro.zodiac.taurus.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fundaro.zodiac.taurus.domain.Tenants;
import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaProvisioningService;
import com.fundaro.zodiac.taurus.repository.TenantsRepository;
import com.fundaro.zodiac.taurus.service.dto.TenantsDTO;
import com.fundaro.zodiac.taurus.service.mapper.TenantsMapper;
import com.fundaro.zodiac.taurus.utils.keycloak.service.KeycloakService;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class TenantsServiceImplTest {

    @Test
    void softDeleteDeactivatesTheSchemaAndRemovesTheIdentityProviderGroup() {
        TenantsRepository repository = mock(TenantsRepository.class);
        TenantsMapper mapper = mock(TenantsMapper.class);
        KeycloakService keycloakService = mock(KeycloakService.class);
        DataErasureService dataErasureService = mock(DataErasureService.class);
        TenantSchemaProvisioningService provisioningService = mock(TenantSchemaProvisioningService.class);
        Tenants tenant = new Tenants();
        tenant.setId(7L);
        tenant.setCode("TENANT-A");
        tenant.setDeleted(false);
        TenantsDTO result = new TenantsDTO();

        when(repository.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(tenant));
        when(repository.save(tenant)).thenReturn(tenant);
        when(mapper.toDto(tenant)).thenReturn(result);
        when(keycloakService.getGroupIdByName("TENANT-A")).thenReturn("group-7");

        TenantsServiceImpl service = new TenantsServiceImpl(
            repository,
            mapper,
            keycloakService,
            dataErasureService,
            provisioningService
        );

        assertThat(service.delete(7L, authentication())).isSameAs(result);
        assertThat(tenant.getDeleted()).isTrue();
        verify(provisioningService).deactivate("TENANT-A", "user-1");
        verify(keycloakService).deleteGroup("group-7");
    }

    private JwtAuthenticationToken authentication() {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject("user-1")
            .claim("tenant", "TENANT-A")
            .issuedAt(now)
            .expiresAt(now.plusSeconds(300))
            .build();
        return new JwtAuthenticationToken(jwt);
    }
}
