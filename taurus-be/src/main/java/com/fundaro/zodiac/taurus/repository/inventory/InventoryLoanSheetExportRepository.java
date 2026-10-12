package com.fundaro.zodiac.taurus.repository.inventory;

import com.fundaro.zodiac.taurus.domain.inventory.InventoryLoanSheetExport;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryLoanSheetExportRepository extends JpaRepository<InventoryLoanSheetExport, Long> {
    List<InventoryLoanSheetExport> findAllBySheet_IdAndMediaAssetIsNotNull(Long sheetId);
}
