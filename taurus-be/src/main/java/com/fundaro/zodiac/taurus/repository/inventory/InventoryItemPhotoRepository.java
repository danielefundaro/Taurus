package com.fundaro.zodiac.taurus.repository.inventory;

import com.fundaro.zodiac.taurus.domain.inventory.InventoryItemPhoto;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryItemPhotoRepository extends JpaRepository<InventoryItemPhoto, Long> {
    List<InventoryItemPhoto> findAllByItem_IdAndDeletedFalseOrderByDisplayOrderAsc(Long itemId);
    Optional<InventoryItemPhoto> findByIdAndDeletedFalse(Long id);
    long countByItem_IdAndDeletedFalse(Long itemId);
}
