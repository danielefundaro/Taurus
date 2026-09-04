package com.fundaro.zodiac.taurus.repository.inventory;

import com.fundaro.zodiac.taurus.domain.inventory.InventoryItem;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryAssignmentStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long> {
    Page<InventoryItem> findAllByDeletedFalse(Pageable pageable);
    long countByDeletedFalse();
    Optional<InventoryItem> findByIdAndDeletedFalse(Long id);
    boolean existsByInventoryNumberIgnoreCaseAndDeletedFalse(String inventoryNumber);
    boolean existsByInventoryNumberIgnoreCaseAndIdNotAndDeletedFalse(String inventoryNumber, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InventoryItem i where i.id = :id and i.deleted = false")
    Optional<InventoryItem> findForUpdate(@Param("id") Long id);

    @Query("select i from InventoryItem i where i.deleted = false and (lower(i.name) like lower(concat('%', :query, '%')) or lower(i.inventoryNumber) like lower(concat('%', :query, '%')))")
    Page<InventoryItem> search(@Param("query") String query, Pageable pageable);

    @Query("select coalesce(sum(i.totalQuantity), 0) from InventoryItem i where i.deleted = false")
    long sumTotalQuantity();

    List<InventoryItem> findAllByDeletedFalse();

    @Query("""
        select item from InventoryItem item
        where item.deleted = false
          and (:query = '' or lower(item.name) like lower(concat('%', :query, '%')) or lower(item.inventoryNumber) like lower(concat('%', :query, '%')))
          and exists (
            select assignment.id from InventoryAssignment assignment
            where assignment.item = item
              and assignment.deleted = false
              and assignment.status in :statuses
              and not exists (
                select decision.id from InventoryAssignmentDecision decision
                where decision.revision.assignment = assignment
                  and decision.revision.revisionNumber = assignment.currentRevision
              )
          )
        """)
    Page<InventoryItem> findWithPendingDecisions(
        @Param("query") String query,
        @Param("statuses") Collection<InventoryAssignmentStatus> statuses,
        Pageable pageable
    );

    @Query("""
        select item from InventoryItem item
        where item.deleted = false
          and (:query = '' or lower(item.name) like lower(concat('%', :query, '%')) or lower(item.inventoryNumber) like lower(concat('%', :query, '%')))
          and exists (
            select inventoryReturn.id from InventoryReturn inventoryReturn
            where inventoryReturn.assignment.item = item
              and inventoryReturn.deleted = false
              and inventoryReturn.status = com.fundaro.zodiac.taurus.domain.inventory.InventoryReturnStatus.REQUESTED
          )
        """)
    Page<InventoryItem> findWithPendingReturns(@Param("query") String query, Pageable pageable);

    @Query("""
        select item from InventoryItem item
        where item.deleted = false
          and (:query = '' or lower(item.name) like lower(concat('%', :query, '%')) or lower(item.inventoryNumber) like lower(concat('%', :query, '%')))
          and exists (
            select assignment.id from InventoryAssignment assignment
            where assignment.item = item
              and assignment.deleted = false
              and assignment.status in :statuses
              and assignment.assignedQuantity > assignment.returnedQuantity
              and assignment.expirationDate is not null
              and assignment.expirationDate <= :maximumDate
          )
        """)
    Page<InventoryItem> findWithExpiringAssignments(
        @Param("query") String query,
        @Param("statuses") Collection<InventoryAssignmentStatus> statuses,
        @Param("maximumDate") LocalDate maximumDate,
        Pageable pageable
    );
}
