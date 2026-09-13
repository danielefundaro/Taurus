package com.fundaro.zodiac.taurus.repository.inventory;

import com.fundaro.zodiac.taurus.domain.inventory.InventoryQrRotation;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryQrRotationRepository extends JpaRepository<InventoryQrRotation, Long> {
    List<InventoryQrRotation> findAllByItem_IdAndDeletedFalseOrderByNewVersionDesc(Long itemId);
}
