package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.enumeration.RoleEnum;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryAssignment;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryAssignmentStatus;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryExpirationNotice;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryExpirationNoticeType;
import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaRegistry;
import com.fundaro.zodiac.taurus.multitenancy.TenantTransactionExecutor;
import com.fundaro.zodiac.taurus.repository.UsersRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryAssignmentRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryExpirationNoticeRepository;
import com.fundaro.zodiac.taurus.service.NoticesService;
import com.fundaro.zodiac.taurus.utils.keycloak.service.KeycloakService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class InventoryExpirationNotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(InventoryExpirationNotificationScheduler.class);
    static final String SYSTEM_ACTOR = "inventory-expiration-scheduler";
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Europe/Rome");
    private static final List<InventoryAssignmentStatus> ACTIVE_STATUSES = List.of(
        InventoryAssignmentStatus.ACTIVE,
        InventoryAssignmentStatus.PARTIALLY_RETURNED
    );
    private static final List<RoleEnum> ADMIN_ROLES = List.of(RoleEnum.ROLE_ADMIN, RoleEnum.ROLE_SUPER_ADMIN);

    private final InventoryAssignmentRepository assignmentRepository;
    private final InventoryExpirationNoticeRepository expirationNoticeRepository;
    private final UsersRepository usersRepository;
    private final NoticesService noticesService;
    private final KeycloakService keycloakService;
    private final TenantSchemaRegistry tenantSchemaRegistry;
    private final TenantTransactionExecutor tenantTransactionExecutor;

    public InventoryExpirationNotificationScheduler(
        InventoryAssignmentRepository assignmentRepository,
        InventoryExpirationNoticeRepository expirationNoticeRepository,
        UsersRepository usersRepository,
        NoticesService noticesService,
        KeycloakService keycloakService,
        TenantSchemaRegistry tenantSchemaRegistry,
        TenantTransactionExecutor tenantTransactionExecutor
    ) {
        this.assignmentRepository = assignmentRepository;
        this.expirationNoticeRepository = expirationNoticeRepository;
        this.usersRepository = usersRepository;
        this.noticesService = noticesService;
        this.keycloakService = keycloakService;
        this.tenantSchemaRegistry = tenantSchemaRegistry;
        this.tenantTransactionExecutor = tenantTransactionExecutor;
    }

    @Scheduled(
        cron = "${application.inventory.expiration-notification-cron:0 0 8 * * *}",
        zone = "${application.inventory.expiration-notification-zone:Europe/Rome}"
    )
    public void notifyExpirations() {
        LocalDate today = LocalDate.now(DEFAULT_ZONE);
        tenantSchemaRegistry.findActiveTenantCodes().forEach(tenantCode -> {
            try {
                tenantTransactionExecutor.execute(tenantCode, () -> notifyCurrentTenant(today));
            } catch (RuntimeException exception) {
                log.error("Unable to process inventory expiration notices for tenant {}", tenantCode, exception);
            }
        });
    }

    void notifyCurrentTenant(LocalDate today) {
        Set<String> adminIds = new LinkedHashSet<>(usersRepository.findActiveKeycloakIdsByRolesIn(ADMIN_ROLES));
        try {
            keycloakService.getUsersByClientRoles(RoleEnum.ROLE_SUPER_ADMIN).stream()
                .map(user -> user.getId())
                .filter(userId -> userId != null && !userId.isBlank())
                .forEach(adminIds::add);
        } catch (RuntimeException exception) {
            log.warn("Unable to load Keycloak super admins for inventory expiration notices", exception);
        }
        assignmentRepository.findExpiringForUpdate(ACTIVE_STATUSES, today.plusDays(30)).forEach(assignment -> {
            InventoryExpirationNoticeType noticeType = noticeType(today, assignment.getExpirationDate());
            if (noticeType == null || expirationNoticeRepository.existsByAssignment_IdAndExpirationDateAndNoticeTypeAndDeletedFalse(
                assignment.getId(), assignment.getExpirationDate(), noticeType)) {
                return;
            }
            persistDelivery(assignment, noticeType);
            sendNotices(assignment, noticeType, adminIds);
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

    private void sendNotices(
        InventoryAssignment assignment,
        InventoryExpirationNoticeType noticeType,
        Set<String> adminIds
    ) {
        String title = title(noticeType);
        String item = assignment.getItem().getInventoryNumber() + " - " + assignment.getItem().getName();
        String date = formatDate(assignment.getExpirationDate());
        noticesService.addNoticeToUser(
            assignment.getUserKeycloakId(),
            title,
            userMessage(noticeType, item, date),
            SYSTEM_ACTOR
        );
        adminIds.stream()
            .filter(adminId -> !adminId.equals(assignment.getUserKeycloakId()))
            .forEach(adminId -> noticesService.addNoticeToUser(
                adminId,
                title,
                adminMessage(noticeType, assignment, item, date),
                SYSTEM_ACTOR
            ));
    }

    static InventoryExpirationNoticeType noticeType(LocalDate today, LocalDate expirationDate) {
        long days = ChronoUnit.DAYS.between(today, expirationDate);
        if (days == 30) return InventoryExpirationNoticeType.THIRTY_DAYS;
        if (days == 7) return InventoryExpirationNoticeType.SEVEN_DAYS;
        if (days == 0) return InventoryExpirationNoticeType.DUE_TODAY;
        if (days < 0) return InventoryExpirationNoticeType.OVERDUE;
        return null;
    }

    private static String title(InventoryExpirationNoticeType type) {
        return switch (type) {
            case THIRTY_DAYS -> "Inventario: scadenza tra 30 giorni";
            case SEVEN_DAYS -> "Inventario: scadenza tra 7 giorni";
            case DUE_TODAY -> "Inventario: scadenza oggi";
            case OVERDUE -> "Inventario: assegnazione scaduta";
        };
    }

    private static String userMessage(InventoryExpirationNoticeType type, String item, String date) {
        return switch (type) {
            case THIRTY_DAYS, SEVEN_DAYS -> "La tua assegnazione " + item + " scadrà il " + date + ".";
            case DUE_TODAY -> "La tua assegnazione " + item + " scade oggi, " + date + ".";
            case OVERDUE -> "La tua assegnazione " + item + " è scaduta il " + date + ".";
        };
    }

    private static String adminMessage(
        InventoryExpirationNoticeType type,
        InventoryAssignment assignment,
        String item,
        String date
    ) {
        String owner = assignment.getUserName() + " " + assignment.getUserLastName();
        return switch (type) {
            case THIRTY_DAYS, SEVEN_DAYS -> "L'assegnazione " + item + " a " + owner + " scadrà il " + date + ".";
            case DUE_TODAY -> "L'assegnazione " + item + " a " + owner + " scade oggi, " + date + ".";
            case OVERDUE -> "L'assegnazione " + item + " a " + owner + " è scaduta il " + date + ".";
        };
    }

    private static String formatDate(LocalDate value) {
        return "%02d/%02d/%04d".formatted(value.getDayOfMonth(), value.getMonthValue(), value.getYear());
    }
}
