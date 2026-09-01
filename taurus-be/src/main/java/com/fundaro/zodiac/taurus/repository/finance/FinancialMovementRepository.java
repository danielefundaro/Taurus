package com.fundaro.zodiac.taurus.repository.finance;

import com.fundaro.zodiac.taurus.domain.finance.FinancialMovement;
import com.fundaro.zodiac.taurus.domain.finance.FinancialMovementNature;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

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
}
