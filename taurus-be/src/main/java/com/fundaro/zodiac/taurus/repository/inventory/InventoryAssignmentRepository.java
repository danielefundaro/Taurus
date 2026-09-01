package com.fundaro.zodiac.taurus.repository.inventory;

import com.fundaro.zodiac.taurus.domain.inventory.InventoryAssignment;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryAssignmentStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

public interface InventoryAssignmentRepository extends JpaRepository<InventoryAssignment, Long> {
    List<InventoryAssignment> findAllByItem_IdAndDeletedFalseOrderByDisplayOrderAsc(Long itemId);
    List<InventoryAssignment> findAllByUserIndexAndDeletedFalseOrderByAssignedAtDesc(Long userIndex);
    List<InventoryAssignment> findAllByUserKeycloakIdAndDeletedFalseOrderByAssignedAtDesc(String userId);
    Page<InventoryAssignment> findAllByUserKeycloakIdAndDeletedFalseAndStatusIn(
        String userKeycloakId,
        Collection<InventoryAssignmentStatus> statuses,
        Pageable pageable
    );
    Optional<InventoryAssignment> findByIdAndUserKeycloakIdAndDeletedFalse(Long id, String userKeycloakId);
    Optional<InventoryAssignment> findByIdAndDeletedFalse(Long id);
    boolean existsByItem_IdAndUserKeycloakIdAndDeletedFalse(Long itemId, String userKeycloakId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select a from InventoryAssignment a
        join fetch a.item
        where a.deleted = false
          and a.status in :statuses
          and a.assignedQuantity > a.returnedQuantity
          and a.expirationDate is not null
          and a.expirationDate <= :maximumDate
        """)
    List<InventoryAssignment> findExpiringForUpdate(
        @Param("statuses") Collection<InventoryAssignmentStatus> statuses,
        @Param("maximumDate") LocalDate maximumDate
    );

    @Query("""
        select a from InventoryAssignment a
        where a.userKeycloakId = :userId
          and a.deleted = false
          and a.status in :statuses
          and (
            lower(a.item.name) like lower(concat('%', :query, '%'))
            or lower(a.item.inventoryNumber) like lower(concat('%', :query, '%'))
          )
        """)
    Page<InventoryAssignment> searchOwn(
        @Param("userId") String userId,
        @Param("query") String query,
        @Param("statuses") Collection<InventoryAssignmentStatus> statuses,
        Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from InventoryAssignment a where a.id = :id and a.deleted = false")
    Optional<InventoryAssignment> findForUpdate(@Param("id") Long id);

    @Query("select coalesce(sum(a.assignedQuantity - a.returnedQuantity), 0) from InventoryAssignment a where a.item.id = :itemId and a.deleted = false and a.status in :statuses")
    long sumOutstanding(@Param("itemId") Long itemId, @Param("statuses") Collection<InventoryAssignmentStatus> statuses);

    @Query("select coalesce(sum(a.assignedQuantity - a.returnedQuantity), 0) from InventoryAssignment a where a.deleted = false and a.status in :statuses")
    long sumOutstanding(@Param("statuses") Collection<InventoryAssignmentStatus> statuses);

    long countByUserKeycloakIdAndDeletedFalseAndStatusIn(String userKeycloakId, Collection<InventoryAssignmentStatus> statuses);

    @Query("select coalesce(sum(a.assignedQuantity - a.returnedQuantity), 0) from InventoryAssignment a where a.userKeycloakId = :userId and a.deleted = false and a.status in :statuses")
    long sumOutstandingForUser(@Param("userId") String userId, @Param("statuses") Collection<InventoryAssignmentStatus> statuses);

    @Query("select max(a.assignedAt) from InventoryAssignment a where a.userKeycloakId = :userId and a.deleted = false and a.status in :statuses")
    ZonedDateTime findLatestAssignedAtForUser(@Param("userId") String userId, @Param("statuses") Collection<InventoryAssignmentStatus> statuses);

    @Query("select count(a) > 0 from InventoryAssignment a where a.userKeycloakId = :userId and a.deleted = false and a.status in :statuses and a.assignedQuantity > a.returnedQuantity")
    boolean hasOutstanding(@Param("userId") String userId, @Param("statuses") Collection<InventoryAssignmentStatus> statuses);

    @Modifying
    @Query("update InventoryAssignment a set a.userKeycloakId = :pseudonym, a.userName = 'Utente', a.userLastName = 'eliminato' where a.userKeycloakId = :userId")
    int pseudonymizeUser(@Param("userId") String userId, @Param("pseudonym") String pseudonym);
}
