package com.fundaro.zodiac.taurus.repository.inventory;

import com.fundaro.zodiac.taurus.domain.inventory.InventoryLoanReturnOutcome;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryLoanSheet;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryLoanSheetStatus;
import jakarta.persistence.LockModeType;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryLoanSheetRepository extends JpaRepository<InventoryLoanSheet, Long> {
    Optional<InventoryLoanSheet> findByIdAndDeletedFalse(Long id);

    Optional<InventoryLoanSheet> findByIdAndUserKeycloakIdAndDeletedFalse(Long id, String userKeycloakId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from InventoryLoanSheet s where s.id = :id and s.deleted = false")
    Optional<InventoryLoanSheet> findForUpdate(@Param("id") Long id);

    Optional<InventoryLoanSheet> findFirstByUserIndexAndStatusAndDeletedFalse(Long userIndex, InventoryLoanSheetStatus status);

    List<InventoryLoanSheet> findAllByUserIndexAndDeletedFalseOrderByIssuedOnDescIdDesc(Long userIndex);

    List<InventoryLoanSheet> findAllByUserKeycloakIdAndDeletedFalseAndStatusInOrderByIssuedOnDescIdDesc(
        String userKeycloakId,
        Collection<InventoryLoanSheetStatus> statuses
    );

    List<InventoryLoanSheet> findAllByUserKeycloakIdAndDeletedFalse(String userKeycloakId);

    List<InventoryLoanSheet> findAllByStatusAndReturnOutcomeAndClosedAtBeforeAndDeletedFalse(
        InventoryLoanSheetStatus status,
        InventoryLoanReturnOutcome returnOutcome,
        ZonedDateTime closedBefore
    );

    Optional<InventoryLoanSheet> findFirstByAssociationSignatoryNameIsNotNullAndDeletedFalseOrderByIdDesc();

    /**
     * Serializes issue of sheets within the current tenant schema: yearly numbering and one issued sheet per member.
     */
    @Query(value = "select 1 from pg_advisory_xact_lock(hashtextextended(current_schema() || ':inventory-loan-sheet', 0))", nativeQuery = true)
    Integer lockTenantSheets();
}
