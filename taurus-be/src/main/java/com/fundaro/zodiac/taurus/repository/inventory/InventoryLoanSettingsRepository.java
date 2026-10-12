package com.fundaro.zodiac.taurus.repository.inventory;

import com.fundaro.zodiac.taurus.domain.inventory.InventoryLoanSettings;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryLoanSettingsRepository extends JpaRepository<InventoryLoanSettings, Long> {
    Optional<InventoryLoanSettings> findFirstByDeletedFalse();
}
