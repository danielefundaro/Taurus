package com.fundaro.zodiac.taurus.repository.inventory;

import com.fundaro.zodiac.taurus.domain.inventory.InventoryIssuePhoto;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryIssuePhotoRepository extends JpaRepository<InventoryIssuePhoto, Long> {
    List<InventoryIssuePhoto> findAllByIssue_IdAndDeletedFalseOrderByDisplayOrderAsc(Long issueId);

    Optional<InventoryIssuePhoto> findByIdAndDeletedFalse(Long id);

    long countByIssue_IdAndDeletedFalse(Long issueId);
}
