package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.CalendarEvents;
import com.fundaro.zodiac.taurus.domain.ChildrenEntities;
import com.fundaro.zodiac.taurus.domain.Media;
import com.fundaro.zodiac.taurus.domain.QueueUploadFiles;
import com.fundaro.zodiac.taurus.domain.Users;
import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaProvisioningService;
import com.fundaro.zodiac.taurus.multitenancy.TenantTransactionExecutor;
import com.fundaro.zodiac.taurus.repository.LastResearchRepository;
import com.fundaro.zodiac.taurus.repository.NoticesRepository;
import com.fundaro.zodiac.taurus.repository.PreferencesRepository;
import com.fundaro.zodiac.taurus.repository.PushReminderRepository;
import com.fundaro.zodiac.taurus.repository.PushSubscriptionRepository;
import com.fundaro.zodiac.taurus.repository.UserLegalAcceptanceRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryAssignmentRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryErasureRequestRepository;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryErasureRequest;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryErasureStatus;
import com.fundaro.zodiac.taurus.resolver.IndexResolver;
import com.fundaro.zodiac.taurus.service.OpenSearchService;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

@Service
@Transactional
public class DataErasureService {

    private static final Logger log = LoggerFactory.getLogger(DataErasureService.class);
    private static final int SCAN_PAGE_SIZE = 500;

    private final NoticesRepository noticesRepository;
    private final LastResearchRepository lastResearchRepository;
    private final PreferencesRepository preferencesRepository;
    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final PushReminderRepository pushReminderRepository;
    private final UserLegalAcceptanceRepository userLegalAcceptanceRepository;
    private final OpenSearchService openSearchService;
    private final IndexResolver indexResolver;
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
        OpenSearchService openSearchService,
        IndexResolver indexResolver,
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
        this.openSearchService = openSearchService;
        this.indexResolver = indexResolver;
        this.tenantStorageService = tenantStorageService;
        this.retentionProperties = applicationProperties.getRetention();
        this.inventoryAssignmentRepository = inventoryAssignmentRepository;
        this.inventoryErasureRequestRepository = inventoryErasureRequestRepository;
        this.tenantSchemaProvisioningService = tenantSchemaProvisioningService;
        this.tenantTransactionExecutor = tenantTransactionExecutor;
    }

    public boolean requestInventoryAwareErasure(
        String userId,
        String userIndex,
        String tenantCode,
        String displayName,
        String email,
        String requestedBy
    ) {
        return tenantTransactionExecutor.execute(tenantCode, () -> requestInventoryAwareErasureInCurrentTenant(
            userId, userIndex, tenantCode, displayName, email, requestedBy
        ));
    }

    private boolean requestInventoryAwareErasureInCurrentTenant(
        String userId,
        String userIndex,
        String tenantCode,
        String displayName,
        String email,
        String requestedBy
    ) {
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
            request.setUserIndex(userIndex == null || userIndex.isBlank() ? userId : userIndex);
            request.setUserKeycloakId(userId);
            request.setDisplayName(displayName == null || displayName.isBlank() ? "Utente" : displayName);
            request.setEmail(email);
            request.setStatus(InventoryErasureStatus.PENDING_INVENTORY_RESOLUTION);
            request.setRequestedAt(ZonedDateTime.now());
            request.setRequestedBy(requestedBy);
            inventoryErasureRequestRepository.save(request);
        }
        eraseUserDataInCurrentTenant(userId, tenantCode);
        return outstanding;
    }

    public void eraseUserData(String userId, String tenantCode) {
        if (userId == null || userId.isBlank() || tenantCode == null || tenantCode.isBlank()) {
            return;
        }

        tenantTransactionExecutor.execute(tenantCode, () -> eraseUserDataInCurrentTenant(userId, tenantCode));
    }

    private void eraseUserDataInCurrentTenant(String userId, String tenantCode) {
        eraseOpenSearchUserData(userId, tenantCode);

        long deleted = 0;
        deleted += noticesRepository.deleteAllByUserId(userId);
        deleted += lastResearchRepository.deleteAllByUserId(userId);
        deleted += preferencesRepository.deleteAllByUserId(userId);
        deleted += pushSubscriptionRepository.deleteAllByUserId(userId);
        deleted += pushReminderRepository.deleteAllByUserId(userId);
        deleted += userLegalAcceptanceRepository.deleteAllByUserId(userId);
        log.info("Physically deleted {} relational records for user {} in tenant {}", deleted, userId, tenantCode);
    }

    public void softDeleteUserAccount(String userId, String tenantCode) {
        if (userId == null || userId.isBlank() || tenantCode == null || tenantCode.isBlank()) {
            return;
        }

        String indexName = indexResolver.resolve(Users.class.getSimpleName(), tenantCode);
        try {
            SearchResponse<Users> response = openSearchService.search(
                builder -> builder.index(indexName).size(SCAN_PAGE_SIZE).query(userQuery("keycloakId", "keycloak_id", userId)),
                Users.class
            );
            for (var hit : response.hits().hits()) {
                Users user = hit.source();
                if (user == null || Boolean.TRUE.equals(user.getDeleted())) {
                    continue;
                }
                user.setDeleted(true);
                user.setEditBy(userId);
                user.setEditDate(new Date());
                openSearchService.index(new IndexRequest.Builder<Users>()
                    .index(indexName)
                    .id(hit.id())
                    .document(user)
                    .build());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to soft-delete user account from " + indexName, e);
        }
    }

    public void eraseTenantData(String tenantCode) {
        deleteLegacyIndexedFiles(tenantCode);

        for (String index : retentionProperties.getTenantIndices()) {
            String resolvedIndex = indexResolver.resolve(index, tenantCode);
            try {
                openSearchService.deleteIndex(resolvedIndex);
            } catch (IOException e) {
                throw new IllegalStateException("Unable to delete OpenSearch index " + resolvedIndex, e);
            }
        }

        try {
            tenantStorageService.deleteTenantDirectory(tenantCode);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to delete files for tenant " + tenantCode, e);
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

    private void eraseOpenSearchUserData(String userId, String tenantCode) {
        List<String> userDocumentIds = deleteMatchingDocuments(
            indexResolver.resolve(Users.class.getSimpleName(), tenantCode),
            Users.class,
            userQuery("keycloakId", "keycloak_id", userId),
            ignored -> null
        );
        deleteMatchingDocuments(
            indexResolver.resolve(QueueUploadFiles.class.getSimpleName(), tenantCode),
            QueueUploadFiles.class,
            userQuery("userId", "user_id", userId),
            QueueUploadFiles::getPath
        );
        Set<String> calendarUserIds = new HashSet<>(userDocumentIds);
        calendarUserIds.add(userId);
        removeUserFromCalendarEvents(calendarUserIds, tenantCode);
    }

    private Query userQuery(String camelCaseField, String snakeCaseField, String userId) {
        return Query.of(query -> query.bool(bool -> bool
            .should(should -> should.match(match -> match.field(camelCaseField).query(FieldValue.of(userId))))
            .should(should -> should.match(match -> match.field(snakeCaseField).query(FieldValue.of(userId))))
            .minimumShouldMatch("1")));
    }

    private <T> List<String> deleteMatchingDocuments(String indexName, Class<T> documentClass, Query query, Function<T, String> pathExtractor) {
        List<String> deletedDocumentIds = new ArrayList<>();
        while (true) {
            try {
                SearchResponse<T> response = openSearchService.search(
                    builder -> builder.index(indexName).size(SCAN_PAGE_SIZE).query(query),
                    documentClass
                );
                if (response.hits().hits().isEmpty()) {
                    return deletedDocumentIds;
                }
                response.hits().hits().forEach(hit -> {
                    T document = hit.source();
                    if (document != null) {
                        deleteManagedFile(pathExtractor.apply(document), indexName);
                    }
                    try {
                        openSearchService.deleteDocument(indexName, hit.id());
                        deletedDocumentIds.add(hit.id());
                    } catch (IOException e) {
                        throw new IllegalStateException("Unable to delete document from " + indexName, e);
                    }
                });
            } catch (IOException e) {
                throw new IllegalStateException("Unable to erase user data from " + indexName, e);
            }
        }
    }

    private void removeUserFromCalendarEvents(Set<String> userIds, String tenantCode) {
        String indexName = indexResolver.resolve(CalendarEvents.class.getSimpleName(), tenantCode);
        int offset = 0;

        while (true) {
            try {
                int pageOffset = offset;
                SearchResponse<CalendarEvents> response = openSearchService.search(
                    builder -> builder.index(indexName).from(pageOffset).size(SCAN_PAGE_SIZE),
                    CalendarEvents.class
                );
                if (response.hits().hits().isEmpty()) {
                    return;
                }
                response.hits().hits().forEach(hit -> {
                    CalendarEvents event = hit.source();
                    if (event == null) {
                        return;
                    }
                    boolean changed = removeUsers(event.getPresentUsers(), userIds);
                    changed |= removeUsers(event.getAvailableUsers(), userIds);
                    changed |= removeUsers(event.getUnavailableUsers(), userIds);
                    if (changed) {
                        try {
                            openSearchService.index(new IndexRequest.Builder<CalendarEvents>()
                                .index(indexName)
                                .id(hit.id())
                                .document(event)
                                .build());
                        } catch (IOException e) {
                            throw new IllegalStateException("Unable to remove user references from " + indexName, e);
                        }
                    }
                });
                offset += response.hits().hits().size();
            } catch (IOException e) {
                throw new IllegalStateException("Unable to scan calendar events in " + indexName, e);
            }
        }
    }

    private boolean removeUsers(List<? extends ChildrenEntities> users, Set<String> userIds) {
        return users != null && users.removeIf(user -> userIds.contains(user.getIndex()));
    }

    private void deleteLegacyIndexedFiles(String tenantCode) {
        deleteFilesFromIndex(indexResolver.resolve(Media.class.getSimpleName(), tenantCode), Media.class, Media::getPath);
        deleteFilesFromIndex(
            indexResolver.resolve(QueueUploadFiles.class.getSimpleName(), tenantCode),
            QueueUploadFiles.class,
            QueueUploadFiles::getPath
        );
    }

    private <T> void deleteFilesFromIndex(String indexName, Class<T> documentClass, Function<T, String> pathExtractor) {
        int offset = 0;
        while (true) {
            try {
                int pageOffset = offset;
                SearchResponse<T> response = openSearchService.search(
                    builder -> builder.index(indexName).from(pageOffset).size(SCAN_PAGE_SIZE),
                    documentClass
                );
                if (response.hits().hits().isEmpty()) {
                    return;
                }
                response.hits().hits().stream()
                    .map(hit -> hit.source())
                    .filter(Objects::nonNull)
                    .forEach(document -> deleteManagedFile(pathExtractor.apply(document), indexName));
                offset += response.hits().hits().size();
            } catch (IOException e) {
                log.warn("Unable to scan legacy files from index {}: {}", indexName, e.getMessage());
                return;
            }
        }
    }

    private void deleteManagedFile(String path, String indexName) {
        try {
            if (tenantStorageService.deleteFileIfManaged(path)) {
                log.debug("Deleted managed file referenced by index {}", indexName);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to delete a managed file referenced by " + indexName, e);
        }
    }
}
