package com.fundaro.zodiac.taurus.repository.inventory;

import com.fundaro.zodiac.taurus.domain.inventory.InventoryAssignment;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryAssignmentStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

public interface InventoryAssignmentRepository extends JpaRepository<InventoryAssignment, Long> {
    List<InventoryAssignment> findAllByItem_IdAndDeletedFalseOrderByDisplayOrderAsc(Long itemId);
    List<InventoryAssignment> findAllByUserIndexAndDeletedFalseOrderByAssignedAtDesc(String userIndex);
    List<InventoryAssignment> findAllByUserKeycloakIdAndDeletedFalseOrderByAssignedAtDesc(String userId);
    Optional<InventoryAssignment> findByIdAndDeletedFalse(Long id);
    boolean existsByItem_IdAndUserKeycloakIdAndDeletedFalse(Long itemId, String userKeycloakId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from InventoryAssignment a where a.id = :id and a.deleted = false")
    Optional<InventoryAssignment> findForUpdate(@Param("id") Long id);

    @Query("select coalesce(sum(a.assignedQuantity - a.returnedQuantity), 0) from InventoryAssignment a where a.item.id = :itemId and a.deleted = false and a.status in :statuses")
    long sumOutstanding(@Param("itemId") Long itemId, @Param("statuses") Collection<InventoryAssignmentStatus> statuses);

    @Query("select count(a) > 0 from InventoryAssignment a where a.userKeycloakId = :userId and a.deleted = false and a.status in :statuses and a.assignedQuantity > a.returnedQuantity")
    boolean hasOutstanding(@Param("userId") String userId, @Param("statuses") Collection<InventoryAssignmentStatus> statuses);

    @Modifying
    @Query("update InventoryAssignment a set a.userIndex = :pseudonym, a.userKeycloakId = :pseudonym, a.userName = 'Utente', a.userLastName = 'eliminato' where a.userKeycloakId = :userId")
    int pseudonymizeUser(@Param("userId") String userId, @Param("pseudonym") String pseudonym);
}
