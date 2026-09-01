package com.fundaro.zodiac.taurus.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fundaro.zodiac.taurus.aop.notices.NoticesAspect;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryAssignment;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryAssignmentStatus;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryExpirationNotice;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryExpirationNoticeType;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryItem;
import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaRegistry;
import com.fundaro.zodiac.taurus.multitenancy.TenantTransactionExecutor;
import com.fundaro.zodiac.taurus.repository.UsersRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryAssignmentRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryExpirationNoticeRepository;
import com.fundaro.zodiac.taurus.service.NoticesService;
import com.fundaro.zodiac.taurus.utils.keycloak.service.KeycloakService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

@ExtendWith(MockitoExtension.class)
class InventoryExpirationNotificationSchedulerTest {

    @Mock InventoryAssignmentRepository assignmentRepository;
    @Mock InventoryExpirationNoticeRepository expirationNoticeRepository;
    @Mock UsersRepository usersRepository;
    @Mock NoticesService noticesService;
    @Mock KeycloakService keycloakService;
    @Mock TenantSchemaRegistry tenantSchemaRegistry;
    @Mock TenantTransactionExecutor tenantTransactionExecutor;

    private InventoryExpirationNotificationScheduler scheduler;

    @BeforeEach
    void setUp() {
        InventoryNoticeDataService noticeDataService = new InventoryNoticeDataService(
            null,
            assignmentRepository,
            null,
            null,
            usersRepository,
            keycloakService
        );
        NoticesAspect noticesAspect = new NoticesAspect(
            noticesService,
            null,
            null,
            null,
            null,
            null,
            null,
            noticeDataService,
            null
        );
        AspectJProxyFactory proxyFactory = new AspectJProxyFactory(expirationNoticeRepository);
        proxyFactory.addAspect(noticesAspect);
        InventoryExpirationNoticeRepository advisedExpirationNoticeRepository = proxyFactory.getProxy();
        scheduler = new InventoryExpirationNotificationScheduler(
            assignmentRepository,
            advisedExpirationNoticeRepository,
            tenantSchemaRegistry,
            tenantTransactionExecutor
        );
    }

    @Test
    void shouldResolveEveryNotificationThreshold() {
        LocalDate today = LocalDate.of(2026, 9, 1);

        assertThat(InventoryExpirationNotificationScheduler.noticeType(today, today.plusDays(30)))
            .isEqualTo(InventoryExpirationNoticeType.THIRTY_DAYS);
        assertThat(InventoryExpirationNotificationScheduler.noticeType(today, today.plusDays(7)))
            .isEqualTo(InventoryExpirationNoticeType.SEVEN_DAYS);
        assertThat(InventoryExpirationNotificationScheduler.noticeType(today, today))
            .isEqualTo(InventoryExpirationNoticeType.DUE_TODAY);
        assertThat(InventoryExpirationNotificationScheduler.noticeType(today, today.minusDays(1)))
            .isEqualTo(InventoryExpirationNoticeType.OVERDUE);
        assertThat(InventoryExpirationNotificationScheduler.noticeType(today, today.plusDays(15))).isNull();
    }

    @Test
    void shouldNotifyAssigneeAndAdminsOnlyOnceForTheSameDateAndThreshold() {
        LocalDate today = LocalDate.of(2026, 9, 1);
        InventoryAssignment assignment = assignment(today.plusDays(7));
        when(usersRepository.findActiveKeycloakIdsByRolesIn(any())).thenReturn(List.of("admin-1"));
        when(keycloakService.getUsersByClientRoles(any())).thenReturn(List.of());
        when(assignmentRepository.findExpiringForUpdate(any(), eq(today.plusDays(30)))).thenReturn(List.of(assignment));
        when(expirationNoticeRepository.save(any(InventoryExpirationNotice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        scheduler.notifyCurrentTenant(today);

        verify(noticesService).addNoticeToUser(
            eq("user-1"),
            eq("Inventario: assegnazione in scadenza"),
            eq("La tua assegnazione dell'oggetto “INV-10 — Leggio” scadrà il 08/09/2026."),
            eq(InventoryExpirationNotificationScheduler.SYSTEM_ACTOR)
        );
        verify(noticesService).addNoticeToUser(
            eq("admin-1"),
            eq("Inventario: assegnazione in scadenza"),
            eq("L'assegnazione dell'oggetto “INV-10 — Leggio” a Mario Rossi scadrà il 08/09/2026."),
            eq(InventoryExpirationNotificationScheduler.SYSTEM_ACTOR)
        );
        ArgumentCaptor<InventoryExpirationNotice> delivery = ArgumentCaptor.forClass(InventoryExpirationNotice.class);
        verify(expirationNoticeRepository).save(delivery.capture());
        assertThat(delivery.getValue().getAssignment()).isSameAs(assignment);
        assertThat(delivery.getValue().getExpirationDate()).isEqualTo(today.plusDays(7));
        assertThat(delivery.getValue().getNoticeType()).isEqualTo(InventoryExpirationNoticeType.SEVEN_DAYS);

        when(expirationNoticeRepository.existsByAssignment_IdAndExpirationDateAndNoticeTypeAndDeletedFalse(
            assignment.getId(), assignment.getExpirationDate(), InventoryExpirationNoticeType.SEVEN_DAYS
        )).thenReturn(true);
        scheduler.notifyCurrentTenant(today);

        verify(expirationNoticeRepository, times(1)).save(any(InventoryExpirationNotice.class));
    }

    @Test
    void shouldSkipAnAlreadyDeliveredThreshold() {
        LocalDate today = LocalDate.of(2026, 9, 1);
        InventoryAssignment assignment = assignment(today);
        when(assignmentRepository.findExpiringForUpdate(any(), eq(today.plusDays(30)))).thenReturn(List.of(assignment));
        when(expirationNoticeRepository.existsByAssignment_IdAndExpirationDateAndNoticeTypeAndDeletedFalse(
            assignment.getId(), assignment.getExpirationDate(), InventoryExpirationNoticeType.DUE_TODAY
        )).thenReturn(true);

        scheduler.notifyCurrentTenant(today);

        verify(expirationNoticeRepository, never()).save(any());
        verify(noticesService, never()).addNoticeToUser(any(), any(), any(), any(String.class));
    }

    private InventoryAssignment assignment(LocalDate expirationDate) {
        InventoryItem item = new InventoryItem();
        item.setId(10L);
        item.setInventoryNumber("INV-10");
        item.setName("Leggio");
        InventoryAssignment assignment = new InventoryAssignment();
        assignment.setId(20L);
        assignment.setItem(item);
        assignment.setUserKeycloakId("user-1");
        assignment.setUserName("Mario");
        assignment.setUserLastName("Rossi");
        assignment.setAssignedQuantity(1);
        assignment.setReturnedQuantity(0);
        assignment.setStatus(InventoryAssignmentStatus.ACTIVE);
        assignment.setExpirationDate(expirationDate);
        return assignment;
    }
}
