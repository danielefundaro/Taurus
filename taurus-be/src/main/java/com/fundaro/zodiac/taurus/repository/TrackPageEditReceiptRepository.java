package com.fundaro.zodiac.taurus.repository;

import com.fundaro.zodiac.taurus.domain.TrackPageEditReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

public interface TrackPageEditReceiptRepository extends JpaRepository<TrackPageEditReceipt, Long> {
    Optional<TrackPageEditReceipt> findByRequestedByAndRequestKey(String requestedBy, UUID requestKey);

    @Modifying
    int deleteByCreatedAtBefore(ZonedDateTime threshold);
}
