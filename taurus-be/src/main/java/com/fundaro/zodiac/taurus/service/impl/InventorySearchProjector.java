package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.inventory.InventoryItem;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryOutboxOperation;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryOutboxStatus;
import com.fundaro.zodiac.taurus.domain.inventory.InventorySearchOutbox;
import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaRegistry;
import com.fundaro.zodiac.taurus.multitenancy.TenantTransactionExecutor;
import com.fundaro.zodiac.taurus.multitenancy.TenantContext;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryAssignmentRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryItemPhotoRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryItemRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventorySearchOutboxRepository;
import com.fundaro.zodiac.taurus.resolver.IndexResolver;
import com.fundaro.zodiac.taurus.service.OpenSearchService;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.List;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventorySearchProjector {
    private static final Logger log = LoggerFactory.getLogger(InventorySearchProjector.class);
    private static final int MAX_ATTEMPTS = 10;
    private static final String AUDIT_ACTOR = "inventory-search-projector";

    private final InventorySearchOutboxRepository outboxRepository;
    private final InventoryItemRepository itemRepository;
    private final InventoryItemPhotoRepository photoRepository;
    private final InventoryAssignmentRepository assignmentRepository;
    private final OpenSearchService openSearchService;
    private final IndexResolver indexResolver;
    private final TenantSchemaRegistry tenantSchemaRegistry;
    private final TenantTransactionExecutor tenantTransactionExecutor;

    public InventorySearchProjector(
        InventorySearchOutboxRepository outboxRepository,
        InventoryItemRepository itemRepository,
        InventoryItemPhotoRepository photoRepository,
        InventoryAssignmentRepository assignmentRepository,
        OpenSearchService openSearchService,
        IndexResolver indexResolver,
        TenantSchemaRegistry tenantSchemaRegistry,
        TenantTransactionExecutor tenantTransactionExecutor
    ) {
        this.outboxRepository = outboxRepository;
        this.itemRepository = itemRepository;
        this.photoRepository = photoRepository;
        this.assignmentRepository = assignmentRepository;
        this.openSearchService = openSearchService;
        this.indexResolver = indexResolver;
        this.tenantSchemaRegistry = tenantSchemaRegistry;
        this.tenantTransactionExecutor = tenantTransactionExecutor;
    }

    @Scheduled(fixedDelayString = "${application.inventory.search-outbox.poll-delay-ms:30000}")
    public void processPending() {
        tenantSchemaRegistry.findActiveTenantCodes().forEach(tenantCode ->
            tenantTransactionExecutor.execute(tenantCode, this::processCurrentTenantPending)
        );
    }

    private void processCurrentTenantPending() {
        List<InventorySearchOutbox> entries = outboxRepository.findTop100ByStatusInAndNextAttemptAtLessThanEqualOrderByIdAsc(
            List.of(InventoryOutboxStatus.PENDING), ZonedDateTime.now());
        entries.forEach(this::process);
    }

    @Transactional
    public void rebuild() {
        itemRepository.findAllByDeletedFalse().forEach(item -> enqueue(item.getId(), InventoryOutboxOperation.UPSERT));
    }

    @Transactional
    public void enqueue(long itemId, InventoryOutboxOperation operation) {
        InventorySearchOutbox entry = new InventorySearchOutbox();
        entry.initializeAudit(AUDIT_ACTOR);
        entry.setItemId(itemId);
        entry.setOperation(operation);
        entry.setStatus(InventoryOutboxStatus.PENDING);
        entry.setAttempts(0);
        entry.setCreatedAt(ZonedDateTime.now());
        entry.setNextAttemptAt(ZonedDateTime.now());
        outboxRepository.save(entry);
    }

    private void process(InventorySearchOutbox entry) {
        entry.setStatus(InventoryOutboxStatus.PROCESSING);
        entry.touchAudit(AUDIT_ACTOR);
        outboxRepository.save(entry);
        String tenantCode = TenantContext.getTenantCode().orElseThrow(() -> new IllegalStateException("Tenant context is required"));
        String index = indexResolver.resolve("InventoryItems", tenantCode);
        try {
            if (entry.getOperation() == InventoryOutboxOperation.DELETE) {
                openSearchService.deleteDocument(index, entry.getItemId().toString());
            } else {
                ensureIndex(index);
                InventoryItem item = itemRepository.findById(entry.getItemId()).orElse(null);
                if (item == null || item.isDeleted()) {
                    openSearchService.deleteDocument(index, entry.getItemId().toString());
                } else {
                    long assigned = assignmentRepository.sumOutstanding(item.getId(), InventoryService.OUTSTANDING_ASSIGNMENT_STATUSES);
                    InventorySearchDocument document = new InventorySearchDocument(
                        item.getId(), item.getInventoryNumber(), item.getName(), item.getDescription(), item.getTotalQuantity(),
                        assigned, item.getTotalQuantity() - assigned, item.getEstimatedUnitValue(), item.getCurrency(),
                        item.getConditionStatus().name(), item.getConditionNotes(),
                        photoRepository.countByItem_IdAndDeletedFalse(item.getId()), item.getEditDate());
                    openSearchService.index(new IndexRequest.Builder<InventorySearchDocument>().index(index).id(item.getId().toString()).document(document).build());
                }
            }
            entry.setStatus(InventoryOutboxStatus.COMPLETED);
            entry.setProcessedAt(ZonedDateTime.now());
            entry.setLastError(null);
        } catch (IOException | RuntimeException exception) {
            int attempts = entry.getAttempts() + 1;
            entry.setAttempts(attempts);
            entry.setLastError(limit(exception.getMessage()));
            if (attempts >= MAX_ATTEMPTS) {
                entry.setStatus(InventoryOutboxStatus.FAILED);
                log.error("Inventory projection failed permanently for tenant {}, item {}", tenantCode, entry.getItemId(), exception);
            } else {
                entry.setStatus(InventoryOutboxStatus.PENDING);
                long delaySeconds = Math.min(1800, 30L * (1L << Math.min(attempts - 1, 6)));
                entry.setNextAttemptAt(ZonedDateTime.now().plusSeconds(delaySeconds));
                log.warn("Inventory projection attempt {} failed for tenant {}, item {}", attempts, tenantCode, entry.getItemId());
            }
        }
        entry.touchAudit(AUDIT_ACTOR);
        outboxRepository.save(entry);
    }

    private void ensureIndex(String index) throws IOException {
        TypeMapping.Builder mapping = new TypeMapping.Builder()
            .properties("inventoryNumber", value -> value.keyword(keyword -> keyword))
            .properties("name", value -> value.text(text -> text.fields("keyword", field -> field.keyword(keyword -> keyword))))
            .properties("description", value -> value.text(text -> text))
            .properties("conditionStatus", value -> value.keyword(keyword -> keyword))
            .properties("currency", value -> value.keyword(keyword -> keyword))
            .properties("totalQuantity", value -> value.integer(integer -> integer))
            .properties("assignedQuantity", value -> value.integer(integer -> integer))
            .properties("availableQuantity", value -> value.integer(integer -> integer));
        openSearchService.createIndex(index, mapping);
    }

    private static String limit(String value) {
        if (value == null) return "Errore OpenSearch non specificato";
        return value.length() > 2000 ? value.substring(0, 2000) : value;
    }

    public record InventorySearchDocument(
        Long id, String inventoryNumber, String name, String description, int totalQuantity, long assignedQuantity,
        long availableQuantity, java.math.BigDecimal estimatedUnitValue, String currency, String conditionStatus,
        String conditionNotes, long photoCount, ZonedDateTime editDate
    ) {}
}
