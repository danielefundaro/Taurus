package com.fundaro.zodiac.taurus.repository.inventory;

import com.fundaro.zodiac.taurus.domain.inventory.InventoryLoanSheetLine;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryLoanSheetLineRepository extends JpaRepository<InventoryLoanSheetLine, Long> {
    List<InventoryLoanSheetLine> findAllBySheet_IdAndDeletedFalseOrderByLineNumberAsc(Long sheetId);
}
