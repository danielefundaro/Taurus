package com.fundaro.zodiac.taurus.repository.inventory;

import com.fundaro.zodiac.taurus.domain.inventory.InventoryLoanTemplate;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryLoanTemplateOrigin;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface InventoryLoanTemplateRepository extends JpaRepository<InventoryLoanTemplate, Long> {
    Optional<InventoryLoanTemplate> findByIdAndDeletedFalse(Long id);

    Optional<InventoryLoanTemplate> findFirstByOriginAndContentHashAndDeletedFalse(InventoryLoanTemplateOrigin origin, String contentHash);

    List<InventoryLoanTemplate> findAllByOriginAndDeletedFalseOrderByVersionNumberDesc(InventoryLoanTemplateOrigin origin);

    @Query("select coalesce(max(t.versionNumber), 0) from InventoryLoanTemplate t")
    int findMaxVersionNumber();

    /**
     * Serializes activation of templates, copies of the default and creation of the settings within the current
     * tenant schema.
     */
    @Query(value = "select 1 from pg_advisory_xact_lock(hashtextextended(current_schema() || ':inventory-loan-template', 0))", nativeQuery = true)
    Integer lockTenantTemplates();
}
