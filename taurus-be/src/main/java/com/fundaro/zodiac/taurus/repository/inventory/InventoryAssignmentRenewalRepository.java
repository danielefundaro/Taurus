package com.fundaro.zodiac.taurus.repository.inventory;

import com.fundaro.zodiac.taurus.domain.inventory.InventoryAssignmentRenewal;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryAssignmentRenewalRepository extends JpaRepository<InventoryAssignmentRenewal, Long> {
    List<InventoryAssignmentRenewal> findAllBySheetIdAndDeletedFalseOrderByRenewedOnAscIdAsc(Long sheetId);
}
