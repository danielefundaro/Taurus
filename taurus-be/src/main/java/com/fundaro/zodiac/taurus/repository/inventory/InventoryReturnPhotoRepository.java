package com.fundaro.zodiac.taurus.repository.inventory;

import com.fundaro.zodiac.taurus.domain.inventory.InventoryReturnPhoto;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryReturnPhotoRepository extends JpaRepository<InventoryReturnPhoto, Long> {
    List<InventoryReturnPhoto> findAllByInventoryReturn_IdAndDeletedFalseOrderByIdAsc(Long returnId);
    Optional<InventoryReturnPhoto> findByIdAndDeletedFalse(Long id);
    long countByInventoryReturn_IdAndDeletedFalse(Long returnId);
}
