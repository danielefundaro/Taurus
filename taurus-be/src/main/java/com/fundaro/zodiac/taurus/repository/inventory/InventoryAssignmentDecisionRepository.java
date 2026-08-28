package com.fundaro.zodiac.taurus.repository.inventory;

import com.fundaro.zodiac.taurus.domain.inventory.InventoryAssignmentDecision;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryAssignmentStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryAssignmentDecisionRepository extends JpaRepository<InventoryAssignmentDecision, Long> {
    Optional<InventoryAssignmentDecision> findByRevision_Id(Long revisionId);
    List<InventoryAssignmentDecision> findAllByRevision_IdIn(Collection<Long> revisionIds);

    @Query("""
        select count(a) from InventoryAssignment a
        where a.deleted = false
          and a.status in :statuses
          and not exists (
            select d.id from InventoryAssignmentDecision d
            where d.revision.assignment = a
              and d.revision.revisionNumber = a.currentRevision
          )
        """)
    long countPendingCurrentRevisions(@Param("statuses") Collection<InventoryAssignmentStatus> statuses);

    @Query("""
        select count(a) from InventoryAssignment a
        where a.userKeycloakId = :userId
          and a.deleted = false
          and a.status in :statuses
          and not exists (
            select d.id from InventoryAssignmentDecision d
            where d.revision.assignment = a
              and d.revision.revisionNumber = a.currentRevision
          )
        """)
    long countPendingCurrentRevisionsForUser(
        @Param("userId") String userId,
        @Param("statuses") Collection<InventoryAssignmentStatus> statuses
    );
}
