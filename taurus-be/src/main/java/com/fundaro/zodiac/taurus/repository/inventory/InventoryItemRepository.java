package com.fundaro.zodiac.taurus.repository.inventory;

import com.fundaro.zodiac.taurus.domain.inventory.InventoryItem;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long> {
    Page<InventoryItem> findAllByDeletedFalse(Pageable pageable);
    Optional<InventoryItem> findByIdAndDeletedFalse(Long id);
    boolean existsByInventoryNumberIgnoreCase(String inventoryNumber);
    boolean existsByInventoryNumberIgnoreCaseAndIdNot(String inventoryNumber, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InventoryItem i where i.id = :id and i.deleted = false")
    Optional<InventoryItem> findForUpdate(@Param("id") Long id);

    @Query("select i from InventoryItem i where i.deleted = false and (lower(i.name) like lower(concat('%', :query, '%')) or lower(i.inventoryNumber) like lower(concat('%', :query, '%')))")
    Page<InventoryItem> search(@Param("query") String query, Pageable pageable);

    List<InventoryItem> findAllByDeletedFalse();
}
