package com.fundaro.zodiac.taurus.repository.inventory;

import com.fundaro.zodiac.taurus.domain.inventory.InventoryOutboxStatus;
import com.fundaro.zodiac.taurus.domain.inventory.InventorySearchOutbox;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventorySearchOutboxRepository extends JpaRepository<InventorySearchOutbox, Long> {
    List<InventorySearchOutbox> findTop100ByStatusInAndNextAttemptAtLessThanEqualOrderByIdAsc(Collection<InventoryOutboxStatus> statuses, ZonedDateTime now);
}
