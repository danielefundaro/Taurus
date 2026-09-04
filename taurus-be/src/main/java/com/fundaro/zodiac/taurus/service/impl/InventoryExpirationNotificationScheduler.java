package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.inventory.InventoryAssignment;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryAssignmentStatus;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryExpirationNotice;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryExpirationNoticeType;
import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaRegistry;
import com.fundaro.zodiac.taurus.multitenancy.TenantTransactionExecutor;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryAssignmentRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryExpirationNoticeRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class InventoryExpirationNotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(InventoryExpirationNotificationScheduler.class);
    public static final String SYSTEM_ACTOR = "inventory-expiration-scheduler";
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Europe/Rome");
    private static final List<InventoryAssignmentStatus> ACTIVE_STATUSES = List.of(
        InventoryAssignmentStatus.ACTIVE,
        InventoryAssignmentStatus.PARTIALLY_RETURNED
    );
    private final InventoryAssignmentRepository assignmentRepository;
    private final InventoryExpirationNoticeRepository expirationNoticeRepository;
    private final TenantSchemaRegistry tenantSchemaRegistry;
    private final TenantTransactionExecutor tenantTransactionExecutor;

    public InventoryExpirationNotificationScheduler(
        InventoryAssignmentRepository assignmentRepository,
        InventoryExpirationNoticeRepository expirationNoticeRepository,
        TenantSchemaRegistry tenantSchemaRegistry,
        TenantTransactionExecutor tenantTransactionExecutor
    ) {
        this.assignmentRepository = assignmentRepository;
        this.expirationNoticeRepository = expirationNoticeRepository;
        this.tenantSchemaRegistry = tenantSchemaRegistry;
        this.tenantTransactionExecutor = tenantTransactionExecutor;
    }

    @Scheduled(
        cron = "${application.inventory.expiration-notification-cron:0 0 8 * * *}",
        zone = "${application.inventory.expiration-notification-zone:Europe/Rome}"
    )
    public void notifyExpirations() {
        LocalDate today = LocalDate.now(DEFAULT_ZONE);
        tenantSchemaRegistry.findInventoryEnabledTenantCodes().forEach(tenantCode -> {
            try {
                tenantTransactionExecutor.execute(tenantCode, () -> notifyCurrentTenant(today));
            } catch (RuntimeException exception) {
                log.error("Unable to process inventory expiration notices for tenant {}", tenantCode, exception);
            }
        });
    }

    void notifyCurrentTenant(LocalDate today) {
        assignmentRepository.findExpiringForUpdate(ACTIVE_STATUSES, today.plusDays(30)).forEach(assignment -> {
            InventoryExpirationNoticeType noticeType = noticeType(today, assignment.getExpirationDate());
            if (noticeType == null || expirationNoticeRepository.existsByAssignment_IdAndExpirationDateAndNoticeTypeAndDeletedFalse(
                assignment.getId(), assignment.getExpirationDate(), noticeType)) {
                return;
            }
            persistDelivery(assignment, noticeType);
        });
    }

    private void persistDelivery(InventoryAssignment assignment, InventoryExpirationNoticeType noticeType) {
        InventoryExpirationNotice delivery = new InventoryExpirationNotice();
        delivery.initializeAudit(SYSTEM_ACTOR);
        delivery.setAssignment(assignment);
        delivery.setExpirationDate(assignment.getExpirationDate());
        delivery.setNoticeType(noticeType);
        delivery.setDeliveredAt(ZonedDateTime.now(DEFAULT_ZONE));
        expirationNoticeRepository.save(delivery);
    }

    static InventoryExpirationNoticeType noticeType(LocalDate today, LocalDate expirationDate) {
        long days = ChronoUnit.DAYS.between(today, expirationDate);
        if (days == 30) return InventoryExpirationNoticeType.THIRTY_DAYS;
        if (days == 7) return InventoryExpirationNoticeType.SEVEN_DAYS;
        if (days == 0) return InventoryExpirationNoticeType.DUE_TODAY;
        if (days < 0) return InventoryExpirationNoticeType.OVERDUE;
        return null;
    }

}
