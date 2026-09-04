package com.fundaro.zodiac.taurus.repository.finance;

import com.fundaro.zodiac.taurus.domain.finance.FinancialMovement;
import com.fundaro.zodiac.taurus.domain.finance.FinancialMovementNature;
import com.fundaro.zodiac.taurus.domain.finance.AccountingYearStatus;
import com.fundaro.zodiac.taurus.repository.projection.FinanceUnreconciledProjection;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FinancialMovementRepository extends JpaRepository<FinancialMovement, Long>, JpaSpecificationExecutor<FinancialMovement> {
    Optional<FinancialMovement> findByIdAndDeletedFalse(Long id);
    Optional<FinancialMovement> findByRequestKeyAndDeletedFalse(UUID requestKey);
    Optional<FinancialMovement> findByAccountingYear_YearAndAccount_IdAndNatureAndDeletedFalse(
        int year,
        Long accountId,
        FinancialMovementNature nature
    );
    List<FinancialMovement> findAllByDeletedFalseAndBookingDateBetween(LocalDate from, LocalDate to);
    List<FinancialMovement> findAllByAccount_IdAndDeletedFalse(Long accountId);
    List<FinancialMovement> findAllByEvent_IdAndDeletedFalseOrderByBookingDateAscIdAsc(Long eventId);
    List<FinancialMovement> findAllByTransferGroupAndDeletedFalse(UUID transferGroup);
    long countByAccount_IdAndDeletedFalse(Long accountId);
    long countByCategory_IdAndDeletedFalse(Long categoryId);

    @Query("""
        select movement.currency as currency,
               count(movement.id) as movementCount,
               coalesce(sum(movement.amount), 0) as totalAmount,
               min(movement.bookingDate) as oldestBookingDate
        from FinancialMovement movement
        where movement.deleted = false
          and movement.nature = :nature
          and movement.reconciled = false
          and (movement.accountingYear.status = :openStatus or movement.bookingDate between :from and :to)
        group by movement.currency
        order by movement.currency
        """)
    List<FinanceUnreconciledProjection> summarizeUnreconciled(
        @Param("nature") FinancialMovementNature nature,
        @Param("openStatus") AccountingYearStatus openStatus,
        @Param("from") LocalDate from,
        @Param("to") LocalDate to
    );
}
