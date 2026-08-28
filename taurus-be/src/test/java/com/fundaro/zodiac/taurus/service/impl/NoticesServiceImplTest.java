package com.fundaro.zodiac.taurus.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fundaro.zodiac.taurus.domain.Notices;
import com.fundaro.zodiac.taurus.domain.enumeration.RoleEnum;
import com.fundaro.zodiac.taurus.multitenancy.TenantTransactionExecutor;
import com.fundaro.zodiac.taurus.repository.NoticesRepository;
import com.fundaro.zodiac.taurus.service.UsersService;
import com.fundaro.zodiac.taurus.service.dto.NoticesDTO;
import com.fundaro.zodiac.taurus.service.mapper.NoticesMapper;
import com.fundaro.zodiac.taurus.utils.keycloak.domain.User;
import com.fundaro.zodiac.taurus.utils.keycloak.service.KeycloakService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class NoticesServiceImplTest {

    @Mock NoticesRepository noticesRepository;
    @Mock NoticesMapper noticesMapper;
    @Mock UsersService usersService;
    @Mock KeycloakService keycloakService;
    @Mock TenantTransactionExecutor tenantTransactionExecutor;

    private NoticesServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new NoticesServiceImpl(
            noticesRepository,
            noticesMapper,
            usersService,
            keycloakService,
            tenantTransactionExecutor
        );
    }

    @Test
    void shouldCreateTenantNoticeFromKeycloakWithoutQueryingTenantUsers() {
        User superAdmin = new User();
        superAdmin.setId("keycloak-super-admin");
        when(keycloakService.getUsersByClientRoles(RoleEnum.ROLE_SUPER_ADMIN)).thenReturn(List.of(superAdmin));
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(1).run();
            return null;
        }).when(tenantTransactionExecutor).execute(eq("BMCDG"), any(Runnable.class));
        when(noticesMapper.toEntity(any(NoticesDTO.class))).thenAnswer(invocation -> {
            NoticesDTO dto = invocation.getArgument(0);
            return new Notices().name(dto.getName()).message(dto.getMessage()).userId(dto.getUserId());
        });
        when(noticesRepository.save(any(Notices.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.addNoticesSuperAdminsForTenant("BMCDG", "Nuovo tenant", "Tenant creato", authentication());

        verify(tenantTransactionExecutor).execute(eq("BMCDG"), any(Runnable.class));
        verify(usersService, never()).findEntitiesByCriteria(any(), any(), any());

        ArgumentCaptor<Notices> noticeCaptor = ArgumentCaptor.forClass(Notices.class);
        verify(noticesRepository).save(noticeCaptor.capture());
        Notices notice = noticeCaptor.getValue();
        assertThat(notice.getUserId()).isEqualTo("keycloak-super-admin");
        assertThat(notice.getName()).isEqualTo("Nuovo tenant");
        assertThat(notice.getMessage()).isEqualTo("Tenant creato");
    }

    private JwtAuthenticationToken authentication() {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject("actor")
            .issuedAt(now)
            .expiresAt(now.plusSeconds(300))
            .build();
        return new JwtAuthenticationToken(jwt);
    }
}
