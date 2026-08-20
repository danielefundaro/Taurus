package com.fundaro.zodiac.taurus.repository.inventory;

import com.fundaro.zodiac.taurus.domain.inventory.InventoryAssignmentRevision;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryAssignmentRevisionRepository extends JpaRepository<InventoryAssignmentRevision, Long> {
    Optional<InventoryAssignmentRevision> findByAssignment_IdAndRevisionNumber(Long assignmentId, int revisionNumber);
    List<InventoryAssignmentRevision> findAllByAssignment_IdOrderByRevisionNumberDesc(Long assignmentId);
}
