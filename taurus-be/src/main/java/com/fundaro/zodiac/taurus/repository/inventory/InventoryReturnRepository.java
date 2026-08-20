package com.fundaro.zodiac.taurus.repository.inventory;

import com.fundaro.zodiac.taurus.domain.inventory.InventoryReturn;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryReturnStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryReturnRepository extends JpaRepository<InventoryReturn, Long> {
    List<InventoryReturn> findAllByAssignment_IdOrderByRequestedAtDesc(Long assignmentId);
    @Query("select coalesce(sum(r.quantity), 0) from InventoryReturn r where r.assignment.id = :assignmentId and r.status in :statuses")
    long sumQuantities(@Param("assignmentId") Long assignmentId, @Param("statuses") Collection<InventoryReturnStatus> statuses);
}
