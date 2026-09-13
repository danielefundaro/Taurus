package com.fundaro.zodiac.taurus.repository.inventory;

import com.fundaro.zodiac.taurus.domain.inventory.InventoryErasureRequest;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryErasureStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InventoryErasureRequestRepository extends JpaRepository<InventoryErasureRequest, Long> {
    boolean existsByUserKeycloakIdAndStatus(String userKeycloakId, InventoryErasureStatus status);

    List<InventoryErasureRequest> findAllByStatusOrderByRequestedAtAsc(InventoryErasureStatus status);
}
