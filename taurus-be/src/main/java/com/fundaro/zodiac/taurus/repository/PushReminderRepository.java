package com.fundaro.zodiac.taurus.repository;

import com.fundaro.zodiac.taurus.domain.PushReminder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface PushReminderRepository extends JpaRepository<PushReminder, Long> {

    List<PushReminder> findBySentFalseAndSendAtLessThanEqual(Instant now);

    long deleteAllByUserId(String userId);

    long deleteAllByUserIdAndTenantCode(String userId, String tenantCode);

    long deleteAllByTenantCode(String tenantCode);

    long deleteAllBySentTrueAndSendAtBefore(Instant cutoff);
}
