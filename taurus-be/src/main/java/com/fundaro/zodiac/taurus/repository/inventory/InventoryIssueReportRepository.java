package com.fundaro.zodiac.taurus.repository.inventory;

import com.fundaro.zodiac.taurus.domain.inventory.InventoryIssueReport;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryIssueSeverity;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryIssueStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

public interface InventoryIssueReportRepository extends JpaRepository<InventoryIssueReport, Long> {
    List<InventoryIssueReport> findAllByItem_IdAndDeletedFalseOrderByInsertDateDesc(Long itemId);
    Optional<InventoryIssueReport> findByIdAndDeletedFalse(Long id);
    Optional<InventoryIssueReport> findByIdAndAssignment_UserKeycloakIdAndDeletedFalse(Long id, String userId);
    long countByItem_IdAndDeletedFalseAndStatusIn(Long itemId, Collection<InventoryIssueStatus> statuses);
    boolean existsByItem_IdAndSeverityAndStatusInAndDeletedFalse(Long itemId, InventoryIssueSeverity severity, Collection<InventoryIssueStatus> statuses);
    long countBySeverityAndStatusInAndDeletedFalse(InventoryIssueSeverity severity, Collection<InventoryIssueStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select issue from InventoryIssueReport issue join fetch issue.item left join fetch issue.assignment where issue.id = :id and issue.deleted = false")
    Optional<InventoryIssueReport> findForUpdate(@Param("id") Long id);

    @Modifying
    @Query(value = """
        update inventory_issue_report
           set insert_by = case when insert_by = :userId then :pseudonym else insert_by end,
               edit_by = case when edit_by = :userId then :pseudonym else edit_by end,
               acknowledged_by = case when acknowledged_by = :userId then :pseudonym else acknowledged_by end,
               resolved_by = case when resolved_by = :userId then :pseudonym else resolved_by end
         where insert_by = :userId or edit_by = :userId or acknowledged_by = :userId or resolved_by = :userId
        """, nativeQuery = true)
    int pseudonymizeUser(@Param("userId") String userId, @Param("pseudonym") String pseudonym);
}
