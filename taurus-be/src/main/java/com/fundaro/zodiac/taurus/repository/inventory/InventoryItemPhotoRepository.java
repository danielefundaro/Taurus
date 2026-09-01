package com.fundaro.zodiac.taurus.repository.inventory;

import com.fundaro.zodiac.taurus.domain.inventory.InventoryItemPhoto;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryItemPhotoRepository extends JpaRepository<InventoryItemPhoto, Long> {
    List<InventoryItemPhoto> findAllByItem_IdAndDeletedFalseOrderByDisplayOrderAsc(Long itemId);
    Optional<InventoryItemPhoto> findByIdAndDeletedFalse(Long id);
    Optional<InventoryItemPhoto> findByIdAndItem_IdAndDeletedFalse(Long id, Long itemId);
    long countByItem_IdAndDeletedFalse(Long itemId);

    @Query("select p from InventoryItemPhoto p join fetch p.item join fetch p.mediaAsset where p.id = :id and p.deleted = false")
    Optional<InventoryItemPhoto> findNoticeTargetById(@Param("id") Long id);
}
