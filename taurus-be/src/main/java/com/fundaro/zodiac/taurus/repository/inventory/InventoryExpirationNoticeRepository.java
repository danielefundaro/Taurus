package com.fundaro.zodiac.taurus.repository.inventory;

import com.fundaro.zodiac.taurus.domain.inventory.InventoryExpirationNotice;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryExpirationNoticeType;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryExpirationNoticeRepository extends JpaRepository<InventoryExpirationNotice, Long> {
    boolean existsByAssignment_IdAndExpirationDateAndNoticeTypeAndDeletedFalse(
        Long assignmentId,
        LocalDate expirationDate,
        InventoryExpirationNoticeType noticeType
    );
}
