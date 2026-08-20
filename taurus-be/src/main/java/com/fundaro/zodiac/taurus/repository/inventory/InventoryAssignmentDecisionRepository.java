package com.fundaro.zodiac.taurus.repository.inventory;

import com.fundaro.zodiac.taurus.domain.inventory.InventoryAssignmentDecision;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryAssignmentDecisionRepository extends JpaRepository<InventoryAssignmentDecision, Long> {
    Optional<InventoryAssignmentDecision> findByRevision_Id(Long revisionId);
    List<InventoryAssignmentDecision> findAllByRevision_IdIn(Collection<Long> revisionIds);
}
