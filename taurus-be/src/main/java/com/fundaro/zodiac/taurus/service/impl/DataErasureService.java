package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.CalendarEvents;
import com.fundaro.zodiac.taurus.domain.QueueUploadFiles;
import com.fundaro.zodiac.taurus.domain.Users;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryErasureRequest;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryErasureStatus;
import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaProvisioningService;
import com.fundaro.zodiac.taurus.multitenancy.TenantTransactionExecutor;
import com.fundaro.zodiac.taurus.repository.CalendarEventsRepository;
import com.fundaro.zodiac.taurus.repository.LastResearchRepository;
import com.fundaro.zodiac.taurus.repository.MediaRepository;
import com.fundaro.zodiac.taurus.repository.NoticesRepository;
import com.fundaro.zodiac.taurus.repository.PreferencesRepository;
import com.fundaro.zodiac.taurus.repository.PushReminderRepository;
import com.fundaro.zodiac.taurus.repository.PushSubscriptionRepository;
import com.fundaro.zodiac.taurus.repository.QueueUploadFilesRepository;
import com.fundaro.zodiac.taurus.repository.UserLegalAcceptanceRepository;
import com.fundaro.zodiac.taurus.repository.UsersRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryAssignmentRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryErasureRequestRepository;
import java.io.IOException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@Transactional
public class DataErasureService {

    private static final Logger log = LoggerFactory.getLogger(DataErasureService.class);

    private final NoticesRepository noticesRepository;
    private final LastResearchRepository lastResearchRepository;
    private final PreferencesRepository preferencesRepository;
    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final PushReminderRepository pushReminderRepository;
    private final UserLegalAcceptanceRepository userLegalAcceptanceRepository;
    private final UsersRepository usersRepository;
    private final QueueUploadFilesRepository uploadRepository;
    private final CalendarEventsRepository calendarRepository;
    private final MediaRepository mediaRepository;
    private final TenantStorageService tenantStorageService;
    private final ApplicationProperties.RetentionProperties retentionProperties;
    private final InventoryAssignmentRepository inventoryAssignmentRepository;
    private final InventoryErasureRequestRepository inventoryErasureRequestRepository;
    private final TenantSchemaProvisioningService tenantSchemaProvisioningService;
    private final TenantTransactionExecutor tenantTransactionExecutor;

    public DataErasureService(
        NoticesRepository noticesRepository,
        LastResearchRepository lastResearchRepository,
        PreferencesRepository preferencesRepository,
        PushSubscriptionRepository pushSubscriptionRepository,
        PushReminderRepository pushReminderRepository,
        UserLegalAcceptanceRepository userLegalAcceptanceRepository,
        UsersRepository usersRepository,
        QueueUploadFilesRepository uploadRepository,
        CalendarEventsRepository calendarRepository,
        MediaRepository mediaRepository,
        TenantStorageService tenantStorageService,
        ApplicationProperties applicationProperties,
        InventoryAssignmentRepository inventoryAssignmentRepository,
        InventoryErasureRequestRepository inventoryErasureRequestRepository,
        TenantSchemaProvisioningService tenantSchemaProvisioningService,
        TenantTransactionExecutor tenantTransactionExecutor
    ) {
        this.noticesRepository = noticesRepository;
        this.lastResearchRepository = lastResearchRepository;
        this.preferencesRepository = preferencesRepository;
        this.pushSubscriptionRepository = pushSubscriptionRepository;
        this.pushReminderRepository = pushReminderRepository;
        this.userLegalAcceptanceRepository = userLegalAcceptanceRepository;
        this.usersRepository = usersRepository;
        this.uploadRepository = uploadRepository;
        this.calendarRepository = calendarRepository;
        this.mediaRepository = mediaRepository;
        this.tenantStorageService = tenantStorageService;
        this.retentionProperties = applicationProperties.getRetention();
        this.inventoryAssignmentRepository = inventoryAssignmentRepository;
        this.inventoryErasureRequestRepository = inventoryErasureRequestRepository;
        this.tenantSchemaProvisioningService = tenantSchemaProvisioningService;
        this.tenantTransactionExecutor = tenantTransactionExecutor;
    }

    public boolean requestInventoryAwareErasure(
        String userId,
        Long userIndex,
        String tenantCode,
        String displayName,
        String email,
        String requestedBy
    ) {
        return tenantTransactionExecutor.execute(tenantCode, () -> {
            boolean outstanding = inventoryAssignmentRepository.hasOutstanding(
                userId,
                InventoryService.OUTSTANDING_ASSIGNMENT_STATUSES
            );
            if (outstanding && !inventoryErasureRequestRepository.existsByUserKeycloakIdAndStatus(
                userId,
                InventoryErasureStatus.PENDING_INVENTORY_RESOLUTION
            )) {
                InventoryErasureRequest request = new InventoryErasureRequest();
                request.initializeAudit(requestedBy);
                request.setUserIndex(userIndex);
                request.setUserKeycloakId(userId);
                request.setDisplayName(displayName == null || displayName.isBlank() ? "Utente" : displayName);
                request.setEmail(email);
                request.setStatus(InventoryErasureStatus.PENDING_INVENTORY_RESOLUTION);
                request.setRequestedAt(ZonedDateTime.now());
                request.setRequestedBy(requestedBy);
                inventoryErasureRequestRepository.save(request);
            }
            eraseUserDataInCurrentTenant(userId);
            return outstanding;
        });
    }

    public void eraseUserData(String userId, String tenantCode) {
        if (isBlank(userId) || isBlank(tenantCode)) return;
        tenantTransactionExecutor.execute(tenantCode, () -> eraseUserDataInCurrentTenant(userId));
    }

    public void softDeleteUserAccount(String userId, String tenantCode) {
        if (isBlank(userId) || isBlank(tenantCode)) return;
        tenantTransactionExecutor.execute(tenantCode, () -> usersRepository.findByKeycloakIdAndDeletedFalse(userId).ifPresent(user -> {
            user.setDeleted(true);
            user.setActive(false);
            user.setEditBy(userId);
            user.setEditDate(new Date());
            usersRepository.save(user);
        }));
    }

    public void eraseTenantData(String tenantCode) {
        tenantTransactionExecutor.execute(tenantCode, () -> {
            mediaRepository.findAll().forEach(media -> deleteManagedFile(media.getPath()));
            uploadRepository.findAll().forEach(upload -> deleteManagedFile(upload.getPath()));
        });
        try {
            tenantStorageService.deleteTenantDirectory(tenantCode);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to delete files for tenant " + tenantCode, exception);
        }
        dropTenantSchemaAfterCommit(tenantCode);
    }

    public void purgeExpiredData() {
        ZonedDateTime now = ZonedDateTime.now();
        long notices = noticesRepository.deleteAllByInsertDateBefore(now.minusDays(retentionProperties.getNoticesDays()));
        long searches = lastResearchRepository.deleteAllByInsertDateBefore(now.minusDays(retentionProperties.getLastResearchDays()));
        long reminders = pushReminderRepository.deleteAllBySentTrueAndSendAtBefore(
            Instant.now().minusSeconds(retentionProperties.getSentPushRemindersDays() * 86_400L)
        );
        log.info("Retention cleanup physically deleted {} notices, {} searches and {} sent reminders", notices, searches, reminders);
    }

    private void eraseUserDataInCurrentTenant(String userId) {
        List<QueueUploadFiles> uploads = uploadRepository.findAllByUser_KeycloakId(userId);
        uploads.forEach(upload -> deleteManagedFile(upload.getPath()));
        uploadRepository.deleteAll(uploads);

        Users user = usersRepository.findByKeycloakIdAndDeletedFalse(userId).orElse(null);
        if (user != null) {
            for (CalendarEvents event : calendarRepository.findAll()) {
                boolean changed = event.getAvailabilities().removeIf(entry -> entry.getUser().getId().equals(user.getId()));
                changed |= event.getPresences().removeIf(entry -> entry.getUser().getId().equals(user.getId()));
                if (changed) calendarRepository.save(event);
            }
            user.setDeleted(true);
            user.setActive(false);
            user.setName("Utente");
            user.setLastName("eliminato");
            user.setEmail(null);
            user.setBirthDate(null);
            user.setDescription(null);
            user.setRoles(java.util.Set.of());
            user.setInstruments(List.of());
            usersRepository.save(user);
        }

        long deleted = 0;
        deleted += noticesRepository.deleteAllByUserId(userId);
        deleted += lastResearchRepository.deleteAllByUserId(userId);
        deleted += preferencesRepository.deleteAllByUserId(userId);
        deleted += pushSubscriptionRepository.deleteAllByUserId(userId);
        deleted += pushReminderRepository.deleteAllByUserId(userId);
        deleted += userLegalAcceptanceRepository.deleteAllByUserId(userId);
        log.info("Physically deleted {} relational records for user {}", deleted, userId);
    }

    private void dropTenantSchemaAfterCommit(String tenantCode) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            tenantSchemaProvisioningService.dropSchema(tenantCode);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                tenantSchemaProvisioningService.dropSchema(tenantCode);
            }
        });
    }

    private void deleteManagedFile(String path) {
        try {
            tenantStorageService.deleteFileIfManaged(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to delete managed file", exception);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
