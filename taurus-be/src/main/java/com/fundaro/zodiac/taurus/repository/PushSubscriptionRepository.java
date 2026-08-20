package com.fundaro.zodiac.taurus.repository;

import com.fundaro.zodiac.taurus.domain.PushSubscription;
import com.fundaro.zodiac.taurus.domain.criteria.PushSubscriptionCriteria;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PushSubscriptionRepository extends CommonRepository<PushSubscription, PushSubscriptionCriteria> {

    List<PushSubscription> findByUserIdAndDeleted(String userId, Boolean deleted);

    List<PushSubscription> findByUserIdInAndDeleted(List<String> userIds, Boolean deleted);

    Optional<PushSubscription> findByUserIdAndEndpoint(String userId, String endpoint);

    void deleteByEndpointAndUserId(String endpoint, String userId);
}
