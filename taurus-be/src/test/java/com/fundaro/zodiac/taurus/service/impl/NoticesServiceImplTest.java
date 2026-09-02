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
import com.fundaro.zodiac.taurus.domain.finance.FinanceNotificationOutbox;
import com.fundaro.zodiac.taurus.domain.finance.FinanceNotificationSeverity;
import com.fundaro.zodiac.taurus.domain.finance.FinanceNotificationStatus;
import com.fundaro.zodiac.taurus.multitenancy.TenantTransactionExecutor;
import com.fundaro.zodiac.taurus.repository.NoticesRepository;
import com.fundaro.zodiac.taurus.repository.finance.FinanceNotificationOutboxRepository;
import com.fundaro.zodiac.taurus.service.NoticesService.FinanceNoticeCommand;
import com.fundaro.zodiac.taurus.service.UsersService;
import com.fundaro.zodiac.taurus.service.dto.NoticesDTO;
import com.fundaro.zodiac.taurus.service.mapper.NoticesMapper;
import com.fundaro.zodiac.taurus.service.mapper.NoticesMapperImpl;
import com.fundaro.zodiac.taurus.utils.keycloak.domain.User;
import com.fundaro.zodiac.taurus.utils.keycloak.service.KeycloakService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
    @Mock FinanceNotificationOutboxRepository financeNotificationOutboxRepository;

    private NoticesServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new NoticesServiceImpl(
            noticesRepository,
            noticesMapper,
            usersService,
            keycloakService,
            tenantTransactionExecutor,
            financeNotificationOutboxRepository
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

    @Test
    void shouldMapRequiredDefaultsForGenericNotices() {
        Notices entity = new NoticesMapperImpl().toEntity(new NoticesDTO());

        assertThat(entity.getSource()).isEqualTo("GENERAL");
        assertThat(entity.getSeverity()).isEqualTo("INFO");
    }

    @Test
    void shouldCreateNavigableFinanceNoticeWithDeliveryKey() {
        when(noticesRepository.findBySourceEventKeyAndUserId("event-1", "admin-1")).thenReturn(Optional.empty());

        service.addFinanceNoticeToUser(
            "admin-1",
            "event-1",
            "Economia: movimento registrato",
            "Mario ha registrato un movimento.",
            "INFO",
            "/finance?tab=movements&movementId=12",
            "actor-1"
        );

        ArgumentCaptor<Notices> captor = ArgumentCaptor.forClass(Notices.class);
        verify(noticesRepository).save(captor.capture());
        assertThat(captor.getValue().getSource()).isEqualTo("FINANCE");
        assertThat(captor.getValue().getSourceEventKey()).isEqualTo("event-1");
        assertThat(captor.getValue().getTargetPath()).isEqualTo("/finance?tab=movements&movementId=12");
        assertThat(captor.getValue().getInsertBy()).isEqualTo("actor-1");
    }

    @Test
    void shouldIgnoreRepeatedFinanceDeliveryForTheSameUser() {
        when(noticesRepository.findBySourceEventKeyAndUserId("event-1", "admin-1")).thenReturn(Optional.of(new Notices()));

        service.addFinanceNoticeToUser("admin-1", "event-1", "Titolo", "Messaggio", "INFO", "/finance", "actor-1");

        verify(noticesRepository, never()).save(any(Notices.class));
    }

    @Test
    void shouldPersistAlreadyComposedFinanceNoticeAndAudienceInOutbox() {
        service.enqueueFinanceNotice(new FinanceNoticeCommand(
            "event-1",
            "ACCOUNT",
            12L,
            "ACCOUNT_CREATED",
            "Economia: conto creato",
            "Mario Rossi ha creato il conto “Conto corrente”.",
            FinanceNotificationSeverity.INFO,
            "/finance?tab=accounts",
            "user-1",
            "Mario Rossi",
            Set.of(RoleEnum.ROLE_TREASURER, RoleEnum.ROLE_ADMIN, RoleEnum.ROLE_SUPER_ADMIN)
        ));

        ArgumentCaptor<FinanceNotificationOutbox> captor = ArgumentCaptor.forClass(FinanceNotificationOutbox.class);
        verify(financeNotificationOutboxRepository).save(captor.capture());
        assertThat(captor.getValue().getRecipientRoles()).isEqualTo("ROLE_ADMIN,ROLE_SUPER_ADMIN,ROLE_TREASURER");
        assertThat(captor.getValue().getStatus()).isEqualTo(FinanceNotificationStatus.PENDING);
        assertThat(captor.getValue().getInsertBy()).isEqualTo("user-1");
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
