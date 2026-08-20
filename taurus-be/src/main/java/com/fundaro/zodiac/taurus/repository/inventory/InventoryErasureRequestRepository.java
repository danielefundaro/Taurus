package com.fundaro.zodiac.taurus.repository.inventory;

import com.fundaro.zodiac.taurus.domain.inventory.InventoryErasureRequest;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryErasureStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryErasureRequestRepository extends JpaRepository<InventoryErasureRequest, Long> {
    boolean existsByUserKeycloakIdAndStatus(String userKeycloakId, InventoryErasureStatus status);
    List<InventoryErasureRequest> findAllByStatusOrderByRequestedAtAsc(InventoryErasureStatus status);
}
