package com.fundaro.zodiac.taurus.repository.inventory;

import com.fundaro.zodiac.taurus.domain.inventory.InventoryLoanSheetCounter;
import jakarta.persistence.LockModeType;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryLoanSheetCounterRepository extends JpaRepository<InventoryLoanSheetCounter, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from InventoryLoanSheetCounter c where c.sheetYear = :year")
    Optional<InventoryLoanSheetCounter> findForUpdate(@Param("year") int year);
}
